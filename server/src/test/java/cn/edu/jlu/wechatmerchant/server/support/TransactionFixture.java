package cn.edu.jlu.wechatmerchant.server.support;

import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import com.google.gson.*;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Real SQLite and services; network tests issue independently authenticated TCP requests. */
public final class TransactionFixture implements AutoCloseable {
    public static final Gson JSON=new GsonBuilder().serializeNulls().create();
    public final SqliteDatabase db;
    public final MutableClock clock=new MutableClock();
    public final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
    public final CouponService coupons;
    public final CartService carts;
    public final OrderService orders;
    public final AfterSaleService returns;
    public final String buyer,other,admin;
    private ProbeServer server;
    public TransactionFixture(Path file) throws Exception {
        db=new SqliteDatabase(file);db.initialize();var users=new UserDao(db);
        buyer=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity());other=sessions.create(users.findByUsername("buyer_b").orElseThrow().identity());admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());
        coupons=new CouponService(db,sessions,clock);carts=new CartService(db,new PromotionPricing(clock));orders=new OrderService(db,sessions,coupons);
        returns=new AfterSaleService(db,sessions,clock);
    }
    public ProbeServer start() throws Exception {
        if(server!=null)throw new IllegalStateException("Already started");
        var identities=new IdentityHandler(new AuthenticationService(new UserDao(db)),sessions);
        server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(db),new PromotionPricing(clock))),identities,new CartHandler(carts,identities),new CouponHandler(coupons,identities),new OrderHandler(orders,identities),new AfterSaleHandler(returns,identities),new InboxHandler(new InboxService(db,sessions,clock),identities));server.start();return server;
    }
    public int port(){return server.port();}
    public void sql(String sql) throws Exception {try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute(sql);}}
    public long value(String sql) throws Exception {try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new AssertionError("No row");return r.getLong(1);}}
    public long coupon() throws Exception {
        var p=coupons.saveDraft(admin,new CouponCampaign(0,0,"并发核对券",0,100,clock.millis(),clock.millis()+3600000,20,0,false,false),UUID.randomUUID().toString());coupons.publish(admin,p.campaignId(),p.version());return coupons.claim(buyer,p.campaignId()).couponId();
    }
    public String addBuyer(int id) throws Exception {
        try(var c=db.openWriteConnection();var s=c.prepareStatement("INSERT INTO users(user_id,username,display_name,password_hash,role) SELECT ?,?,?,password_hash,'BUYER' FROM users WHERE user_id=1")){s.setInt(1,id);s.setString(2,"race_"+id);s.setString(3,"测试买家"+id);s.executeUpdate();}
        return sessions.create(new UserIdentity(id,"race_"+id,"测试买家"+id,UserIdentity.Role.BUYER));
    }
    public CheckoutRequest request(String token,CartSnapshot cart,Long coupon,List<Long> ids) throws Exception {var q=coupons.quote(token,cart.cartVersion(),coupon,ids);return new CheckoutRequest(UUID.randomUUID().toString(),cart.cartVersion(),q.digest(),coupon,ids);}
    public JsonObject exchange(int op,String token,JsonObject body) throws Exception {
        try(var socket=new Socket("127.0.0.1",port())){String id=UUID.randomUUID().toString();FrameCodec.write(socket.getOutputStream(),Messages.authenticatedRequest(id,op,token,body));var reply=FrameCodec.read(socket,8000);Messages.validateResponse(reply,id,op+1000);return reply;}
    }
    public CheckoutReceipt checkout(String token,CheckoutRequest request) throws Exception {var reply=exchange(Messages.CHECKOUT,token,JSON.toJsonTree(request).getAsJsonObject());if(!"OK".equals(reply.get("code").getAsString()))throw new AssertionError("Unexpected code: "+reply.get("code"));return CheckoutReceipt.parse(reply.getAsJsonObject("body"),request.checkoutKey());}
    public void assertConsistent() throws Exception {
        try(var c=db.openReadOnlyConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("PRAGMA integrity_check")){if(!r.next() || !"ok".equals(r.getString(1)))throw new AssertionError("Integrity check failed");}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){if(r.next())throw new AssertionError("Broken foreign key");}
        }
        for(String sql:List.of("SELECT count(*) FROM checkout_attempts WHERE state='PENDING'", "SELECT count(*) FROM orders o LEFT JOIN checkout_attempts a ON a.order_id=o.order_id AND a.user_id=o.user_id AND a.checkout_key=o.checkout_key AND a.state='SUCCESS' WHERE a.order_id IS NULL", "SELECT count(*) FROM orders o WHERE NOT EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.order_id)","SELECT count(*) FROM user_coupons u LEFT JOIN orders o ON o.order_id=u.used_order_id AND o.user_id=u.user_id WHERE u.used=1 AND o.order_id IS NULL"))if(value(sql)!=0)throw new AssertionError("Inconsistent transaction result");
    }
    @Override public void close(){if(server!=null)server.close();sessions.clear();}
}
