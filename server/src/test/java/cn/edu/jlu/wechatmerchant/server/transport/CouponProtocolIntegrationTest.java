package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import com.google.gson.JsonObject;
import java.net.Socket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CouponProtocolIntegrationTest {
    @TempDir Path folder;
    @Test void tcpRejectsAnonymousWrongRoleForgedFieldsAndExpiredOwnerWithoutWrites() throws Exception {
        var db=new SqliteDatabase(folder.resolve("protocol.db"));db.initialize();var sessions=new SessionStore();
        var users=new UserDao(db);String buyer=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity()),admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());
        var identities=new IdentityHandler(new AuthenticationService(users),sessions);
        try(var server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(db))),identities,new CartHandler(new CartService(db),identities),new CouponHandler(new CouponService(db,sessions),identities))) {
            server.start();for(int operation:new int[]{Messages.QUOTE_CART,Messages.CLAIM_COUPON,Messages.LIST_COUPONS}) {
                assertEquals("UNAUTHENTICATED",exchange(server,operation,null,new JsonObject()));assertEquals("FORBIDDEN",exchange(server,operation,admin,new JsonObject()));
                var forged=new JsonObject();forged.addProperty("userId",2);assertEquals("INVALID_ARGUMENT",exchange(server,operation,buyer,forged));
            }
            var page=new JsonObject();page.addProperty("kind","OWNED");page.addProperty("limit",20);page.addProperty("offset",0);assertEquals("OK",exchange(server,Messages.LIST_COUPONS,buyer,page));
            page.addProperty("limit",1.2);assertEquals("INVALID_ARGUMENT",exchange(server,Messages.LIST_COUPONS,buyer,page));
            var quote=new JsonObject();quote.addProperty("cartVersion",0);quote.add("couponId",com.google.gson.JsonNull.INSTANCE);quote.addProperty("priceCents",1);
            assertEquals("INVALID_ARGUMENT",exchange(server,Messages.QUOTE_CART,buyer,quote));
            sessions.revoke(buyer);assertEquals("UNAUTHENTICATED",exchange(server,Messages.LIST_COUPONS,buyer,page));
            try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM user_coupons")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
        }
    }
    private static String exchange(ProbeServer server,int op,String token,JsonObject body) throws Exception {
        try(var socket=new Socket("127.0.0.1",server.port())){FrameCodec.write(socket.getOutputStream(),Messages.authenticatedRequest("coupon-test",op,token,body));return FrameCodec.read(socket,5000).get("code").getAsString();}
    }
}
