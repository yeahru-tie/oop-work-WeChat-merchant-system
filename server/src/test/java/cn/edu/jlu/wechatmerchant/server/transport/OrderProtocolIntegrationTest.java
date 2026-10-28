package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import com.google.gson.*;
import java.net.Socket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrderProtocolIntegrationTest {
    @TempDir Path folder;
    @Test void tcpRejectsAnonymousWrongRoleForgedFieldsAndCrossUserQueries() throws Exception {
        var db=new SqliteDatabase(folder.resolve("orders.db"));db.initialize();var sessions=new SessionStore();var users=new UserDao(db);
        String a=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity()),b=sessions.create(users.findByUsername("buyer_b").orElseThrow().identity()),admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());
        var identities=new IdentityHandler(new AuthenticationService(users),sessions);var coupons=new CouponService(db,sessions);var orders=new OrderService(db,sessions,coupons);
        var cart=new CartService(db).add(1,1001,1,0);var quote=coupons.quote(a,cart.cartVersion(),null);var command=new CheckoutRequest(java.util.UUID.randomUUID().toString(),cart.cartVersion(),quote.digest(),null,CartSelection.all(cart));
        var order=orders.checkout(a,command).order();var gson=new GsonBuilder().serializeNulls().create();
        try(var server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(db))),identities,new CartHandler(new CartService(db),identities),new CouponHandler(coupons,identities),new OrderHandler(orders,identities))) {
            server.start();for(int op:new int[]{Messages.CHECKOUT,Messages.QUERY_ORDERS}) {
                assertEquals("UNAUTHENTICATED",exchange(server,op,null,new JsonObject()).get("code").getAsString());assertEquals("FORBIDDEN",exchange(server,op,admin,new JsonObject()).get("code").getAsString());
            }
            var forged=gson.toJsonTree(command).getAsJsonObject();forged.addProperty("userId",2);assertEquals("INVALID_ARGUMENT",exchange(server,Messages.CHECKOUT,a,forged).get("code").getAsString());
            var detail=new JsonObject();detail.addProperty("mode","DETAIL");detail.addProperty("orderId",order.orderId());assertEquals("NOT_FOUND",exchange(server,Messages.QUERY_ORDERS,b,detail).get("code").getAsString());assertEquals("OK",exchange(server,Messages.QUERY_ORDERS,a,detail).get("code").getAsString());
            var key=new JsonObject();key.addProperty("mode","BY_KEY");key.addProperty("checkoutKey",command.checkoutKey());assertEquals("NOT_FOUND",exchange(server,Messages.QUERY_ORDERS,b,key).getAsJsonObject("body").get("state").getAsString());
            var page=new JsonObject();page.addProperty("mode","LIST");page.addProperty("limit",20);page.addProperty("offset",0);assertEquals(0,exchange(server,Messages.QUERY_ORDERS,b,page).getAsJsonObject("body").getAsJsonArray("items").size());page.addProperty("userId",1);assertEquals("INVALID_ARGUMENT",exchange(server,Messages.QUERY_ORDERS,b,page).get("code").getAsString());
            var replay=exchange(server,Messages.CHECKOUT,a,gson.toJsonTree(command).getAsJsonObject());assertEquals(order.orderId(),CheckoutReceipt.parse(replay.getAsJsonObject("body"),command.checkoutKey()).order().orderId());
            sessions.revoke(a);assertEquals("UNAUTHENTICATED",exchange(server,Messages.CHECKOUT,a,gson.toJsonTree(command).getAsJsonObject()).get("code").getAsString());
            try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM orders")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
        }
    }
    private static JsonObject exchange(ProbeServer server,int op,String token,JsonObject body) throws Exception {
        try(var socket=new Socket("127.0.0.1",server.port())){FrameCodec.write(socket.getOutputStream(),Messages.authenticatedRequest("order-test",op,token,body));return FrameCodec.read(socket,5000);}
    }
}
