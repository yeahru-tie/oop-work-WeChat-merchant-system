package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.client.transport.RemoteServiceException;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class PromotionIntegrationTest {
    @TempDir Path directory;
    @Test void savedActivityRefreshesRealGuestProductAndPrivateCartWithoutChangingStockOrCartVersion() throws Exception {
        var database=new SqliteDatabase(directory.resolve("联动.db")); database.initialize();
        var clock=new MutableClock(); var sessions=new SessionStore(clock,Duration.ofMinutes(30));
        var pricing=new PromotionPricing(clock);
        var identities=new IdentityHandler(new AuthenticationService(new UserDao(database)),sessions);
        var management=new PromotionManagementService(database,sessions);
        String admin=sessions.create(new UserDao(database).findByUsername("shop_admin").orElseThrow().identity());
        try(var server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(database),pricing)),identities,
                new CartHandler(new CartService(database,pricing),identities))) {
            server.start();
            try(var guest=new MerchantClient("127.0.0.1",server.port(),1000,5000);
                    var buyer=new MerchantClient("127.0.0.1",server.port(),1000,5000)) {
                assertEquals(1290,guest.listProducts(20,0,() -> false).items().getFirst().promotion().estimatedUnitCents());
                assertEquals("UNAUTHENTICATED",assertThrows(RemoteServiceException.class,() -> guest.listCart(() -> false)).code());
                buyer.login("buyer_a","BuyerA#2026!".toCharArray(),() -> false);
                var original=buyer.addToCart(1001,2,0,() -> false);
                var draft=new PromotionConfiguration(1001,0,new PromotionTerms(100,900),clock.instant(),clock.instant().plusSeconds(60),true);
                assertEquals("2142",management.preview(admin,1001,2,draft.terms()).breakdown().estimatedTotalCents().toString());
                assertEquals(original,buyer.listCart(() -> false)); // Draft does not publish.
                management.save(admin,draft);
                assertEquals(1071,guest.listProducts(20,0,() -> false).items().getFirst().promotion().estimatedUnitCents());
                var discounted=buyer.listCart(() -> false);
                assertEquals(original.cartVersion(),discounted.cartVersion());
                assertEquals("2142",discounted.estimatedTotalCents().toString());
                assertEquals("2580",discounted.totalCents().toString());
                assertEquals(20,discounted.items().getFirst().stock());
                var changed=buyer.editCart(discounted.items().getFirst().itemId(),3,discounted.cartVersion(),() -> false);
                assertEquals("3213",changed.estimatedTotalCents().toString());
                clock.advance(Duration.ofSeconds(60));
                var expired=buyer.listCart(() -> false);
                assertEquals("3870",expired.estimatedTotalCents().toString());
                assertEquals(changed.cartVersion(),expired.cartVersion());
                assertEquals(1290,guest.listProducts(20,0,() -> false).items().getFirst().promotion().estimatedUnitCents());
                management.save(admin,new PromotionConfiguration(1001,1,new PromotionTerms(0,885),clock.instant(),clock.instant().plusSeconds(60),true));
                assertEquals(1142,guest.listProducts(20,0,() -> false).items().getFirst().promotion().estimatedUnitCents());
                assertEquals("3426",buyer.listCart(() -> false).estimatedTotalCents().toString());
                assertTrue(new CartService(database,pricing).list(2).items().isEmpty());
            }
        }
    }
}
