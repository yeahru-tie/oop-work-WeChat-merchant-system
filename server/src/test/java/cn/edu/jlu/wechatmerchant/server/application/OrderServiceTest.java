package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Timeout(25)
class OrderServiceTest {
    @TempDir Path folder;private SqliteDatabase db;private final MutableClock clock=new MutableClock();private final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
    private CouponService pricing;private OrderService orders;private CartService carts;private String a,b,admin;
    @BeforeEach void setup() throws Exception {
        db=new SqliteDatabase(folder.resolve("orders.db"));db.initialize();pricing=new CouponService(db,sessions,clock);orders=new OrderService(db,sessions,pricing);carts=new CartService(db,new PromotionPricing(clock));
        var users=new UserDao(db);a=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity());b=sessions.create(users.findByUsername("buyer_b").orElseThrow().identity());admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());
    }
    private void sql(String text) throws Exception {try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute(text);}}
    private long value(String text) throws Exception {try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery(text)){assertTrue(r.next());return r.getLong(1);}}
    private CheckoutRequest request(String token,CartSnapshot cart,Long coupon,List<Long> ids) throws Exception {var quote=pricing.quote(token,cart.cartVersion(),coupon,ids);return new CheckoutRequest(UUID.randomUUID().toString(),cart.cartVersion(),quote.digest(),coupon,ids);}
    private long coupon(long threshold,long cut) throws Exception {var p=pricing.saveDraft(admin,new CouponCampaign(0,0,"测试满减",threshold,cut,clock.millis(),clock.millis()+60_000,20,0,false,false),UUID.randomUUID().toString());pricing.publish(admin,p.campaignId(),p.version());return pricing.claim(a,p.campaignId()).couponId();}
    @Test void partialCheckout142PreservesUnselectedLinesAndStoresImmutableHistoryAndCouponLink() throws Exception {
        sql("UPDATE products SET price_cents=10000 WHERE product_id=1001");
        new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(1000,900),clock.instant(),clock.instant().plusSeconds(60),true));
        var cart=carts.add(1,1001,2,0);cart=carts.add(1,1002,3,1);long chosen=cart.items().getFirst().itemId();long coupon=coupon(15000,2000);
        var command=request(a,cart,coupon,List.of(chosen));var receipt=orders.checkout(a,command);assertEquals("SUCCESS",receipt.state());var order=receipt.order();
        assertEquals("14200",order.paidCents().toString());assertEquals(1,order.items().size());assertEquals("20000",order.originalCents().toString());
        var remaining=carts.list(1);assertEquals(3,remaining.cartVersion());assertEquals(1002,remaining.items().getFirst().productId());assertEquals(3,remaining.items().getFirst().quantity());
        assertEquals(18,value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(15,value("SELECT stock FROM products WHERE product_id=1002"));
        assertEquals(order.orderId(),value("SELECT used_order_id FROM user_coupons"));assertEquals(1,value("SELECT used FROM user_coupons"));
        sql("UPDATE products SET price_cents=1,name='改名' WHERE product_id=1001");assertEquals(order,orders.detail(a,order.orderId()));assertEquals(receipt,orders.checkout(a,command));
        assertEquals(receipt,orders.byKey(a,command.checkoutKey()));assertEquals("NOT_FOUND",orders.byKey(b,command.checkoutKey()).state());assertThrows(OrderService.Rejected.class,() -> orders.detail(b,order.orderId()));
        assertEquals(order.paidCents(),orders.list(a,20,0).items().getFirst().paidCents());assertTrue(orders.list(b,20,0).items().isEmpty());
        db.initialize();var restarted=new OrderService(new SqliteDatabase(db.file()),sessions,new CouponService(new SqliteDatabase(db.file()),sessions,clock));assertEquals(receipt,restarted.byKey(a,command.checkoutKey()));
    }
    @Test void subsetControlsThresholdAndUnselectedPriceChangesDoNotAlterDigest() throws Exception {
        sql("UPDATE products SET price_cents=8000 WHERE product_id=1001");sql("UPDATE products SET price_cents=10000 WHERE product_id=1002");var cart=carts.add(1,1001,1,0);cart=carts.add(1,1002,1,1);long coupon=coupon(15000,2000);
        var selected=List.of(cart.items().getFirst().itemId());assertEquals("THRESHOLD_NOT_MET",pricing.quote(a,2,coupon,selected).couponStatus());
        var quote=pricing.quote(a,2,null,selected);sql("UPDATE products SET price_cents=999999 WHERE product_id=1002");assertEquals(quote.digest(),pricing.quote(a,2,null,selected).digest());
        assertEquals(CouponPage.Status.THRESHOLD_NOT_MET,pricing.list(a,"OWNED",20,0,selected).items().getFirst().status());
        assertNull(pricing.quote(a,2,null,List.of()).payableCents());
    }
    @Test void changedQuotePersistsTerminalFailureAndSameKeyDifferentParametersConflict() throws Exception {
        var cart=carts.add(1,1001,1,0);var command=request(a,cart,null,CartSelection.all(cart));sql("UPDATE products SET price_cents=1 WHERE product_id=1001");
        var failed=orders.checkout(a,command);assertEquals("FAILED",failed.state());assertEquals("PRICE_CHANGED",failed.failureCode());
        assertEquals(20,value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,value("SELECT count(*) FROM orders"));assertEquals(1,carts.list(1).cartVersion());
        sql("UPDATE products SET price_cents=1290 WHERE product_id=1001");assertEquals(failed,orders.checkout(a,command));assertEquals(failed,orders.byKey(a,command.checkoutKey()));
        var different=new CheckoutRequest(command.checkoutKey(),1,"b".repeat(64),null,command.itemIds());assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(OrderService.Rejected.class,() -> orders.checkout(a,different)).getMessage());
    }
    @Test void secondProductShortageRollsBackEarlierStockAndCouponAndCart() throws Exception {
        var cart=carts.add(1,1001,1,0);cart=carts.add(1,1002,1,1);long coupon=coupon(0,100);var command=request(a,cart,coupon,CartSelection.all(cart));sql("UPDATE products SET stock=0 WHERE product_id=1002");
        var result=orders.checkout(a,command);assertEquals("OUT_OF_STOCK",result.failureCode());assertEquals(20,value("SELECT stock FROM products WHERE product_id=1001"));
        assertEquals(0,value("SELECT used FROM user_coupons"));assertEquals(0,value("SELECT count(*) FROM orders"));assertEquals(2,carts.list(1).cartVersion());assertEquals(2,carts.list(1).items().size());
        assertEquals("FAILED",orders.byKey(a,command.checkoutKey()).state());
    }
    @Test void sqlFailureAfterWritingOrderRollsBackAllBusinessAndReceiptAndSameKeyCanSafelyRetry() throws Exception {
        var cart=carts.add(1,1001,2,0);long coupon=coupon(0,100);var command=request(a,cart,coupon,CartSelection.all(cart));
        sql("CREATE TRIGGER fail_use BEFORE UPDATE OF used ON user_coupons WHEN NEW.used=1 BEGIN SELECT RAISE(ABORT,'test failure'); END");
        assertThrows(SQLException.class,() -> orders.checkout(a,command));assertEquals("NOT_FOUND",orders.byKey(a,command.checkoutKey()).state());
        assertEquals(20,value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,value("SELECT count(*) FROM orders"));assertEquals(0,value("SELECT count(*) FROM order_items"));assertEquals(0,value("SELECT used FROM user_coupons"));assertEquals(1,carts.list(1).cartVersion());
        sql("DROP TRIGGER fail_use");assertEquals("SUCCESS",orders.checkout(a,command).state());assertEquals(18,value("SELECT stock FROM products WHERE product_id=1001"));
    }
    @Test void simultaneousSameKeyReturnsOneOrderOneDeductionAndOneCouponUse() throws Exception {
        var cart=carts.add(1,1001,2,0);long coupon=coupon(0,100);var command=request(a,cart,coupon,CartSelection.all(cart));var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(6)){
            var futures=new ArrayList<Future<CheckoutReceipt>>();for(int i=0;i<6;i++)futures.add(pool.submit(() -> {start.await();return orders.checkout(a,command);}));start.countDown();
            long id=futures.getFirst().get().order().orderId();for(var f:futures){var r=f.get();assertEquals("SUCCESS",r.state());assertEquals(id,r.order().orderId());}
        }
        assertEquals(1,value("SELECT count(*) FROM orders"));assertEquals(18,value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(1,value("SELECT used FROM user_coupons"));assertEquals(2,carts.list(1).cartVersion());assertTrue(carts.list(1).items().isEmpty());
    }
    @Test void differentKeysCompetingForSameCartAndCouponDoNotFallBackToNoCoupon() throws Exception {
        var cart=carts.add(1,1001,1,0);cart=carts.add(1,1002,1,1);long coupon=coupon(0,100);
        var first=request(a,cart,coupon,List.of(cart.items().getFirst().itemId()));var second=request(a,cart,coupon,List.of(cart.items().getLast().itemId()));var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(() -> {start.await();return orders.checkout(a,first);});var two=pool.submit(() -> {start.await();return orders.checkout(a,second);});start.countDown();var results=List.of(one.get(),two.get());assertEquals(1,results.stream().filter(r -> r.state().equals("SUCCESS")).count());assertEquals(1,results.stream().filter(r -> "CART_CHANGED".equals(r.failureCode())).count());}
        var remaining=carts.list(1);var invalid=request(a,remaining,coupon,CartSelection.all(remaining));var fail=orders.checkout(a,invalid);assertEquals("COUPON_INVALID",fail.failureCode());assertEquals(1,value("SELECT count(*) FROM orders"));assertEquals(1,remaining.items().size());
    }
    @Test void fiveUnitsTenBuyersProduceExactlyFiveOrdersAndFiveStockFailures() throws Exception {
        sql("UPDATE products SET stock=5 WHERE product_id=1001");var tokens=new ArrayList<String>();var commands=new ArrayList<CheckoutRequest>();
        for(int i=10;i<20;i++){
            try(var c=db.openWriteConnection();var s=c.prepareStatement("INSERT INTO users(user_id,username,display_name,password_hash,role) SELECT ?,?,?,password_hash,'BUYER' FROM users WHERE user_id=1")){s.setInt(1,i);s.setString(2,"buyer_"+i);s.setString(3,"买家 "+i);s.executeUpdate();}
            String token=sessions.create(new UserIdentity(i,"buyer_"+i,"买家 "+i,UserIdentity.Role.BUYER));tokens.add(token);var cart=carts.add(i,1001,1,0);commands.add(request(token,cart,null,CartSelection.all(cart)));
        }
        var go=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(10)){var futures=new ArrayList<Future<CheckoutReceipt>>();for(int i=0;i<10;i++){int index=i;futures.add(pool.submit(() -> {go.await();return orders.checkout(tokens.get(index),commands.get(index));}));}go.countDown();int success=0,stock=0;for(var future:futures){var result=future.get();if(result.state().equals("SUCCESS"))success++;if("OUT_OF_STOCK".equals(result.failureCode()))stock++;}assertEquals(5,success);assertEquals(5,stock);}
        assertEquals(0,value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(5,value("SELECT count(*) FROM orders"));assertEquals(5,value("SELECT count(*) FROM cart_items"));
    }
    @Test void expiredCouponAndWrongRolesAndDisabledAccountsAreRejected() throws Exception {
        var cart=carts.add(1,1001,1,0);long coupon=coupon(0,100);var command=request(a,cart,coupon,CartSelection.all(cart));clock.advance(Duration.ofSeconds(60));
        assertEquals("COUPON_INVALID",orders.checkout(a,command).failureCode());assertEquals(0,value("SELECT used FROM user_coupons"));
        assertThrows(SecurityException.class,() -> orders.checkout(admin,command));assertThrows(SecurityException.class,() -> orders.list(admin,20,0));assertThrows(SecurityException.class,() -> orders.byKey(admin,command.checkoutKey()));
        sql("UPDATE users SET enabled=0 WHERE user_id=1");assertThrows(SecurityException.class,() -> orders.byKey(a,command.checkoutKey()));
    }
    @Test void busyTransactionDoesNotCreateReceiptAndCanBeRetriedWithOriginalKey() throws Exception {
        var cart=carts.add(1,1001,1,0);var command=request(a,cart,null,CartSelection.all(cart));
        try(var held=db.openImmediateConnection()){held.setAutoCommit(false);assertThrows(SQLException.class,() -> orders.checkout(a,command));held.rollback();}
        assertEquals("NOT_FOUND",orders.byKey(a,command.checkoutKey()).state());assertEquals("SUCCESS",orders.checkout(a,command).state());
    }
}
