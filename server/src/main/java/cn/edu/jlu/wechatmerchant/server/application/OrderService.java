package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.sql.*;
import java.util.*;

public final class OrderService {
    public static final class Rejected extends Exception {
        private static final long serialVersionUID=1L;public Rejected(String code){super(code);}
    }
    private final SqliteDatabase database;
    private final SessionAccess access;
    private final CouponService pricing;
    private final OrderDao orders=new OrderDao();
    private final CartDao carts=new CartDao();
    public OrderService(SqliteDatabase database,SessionStore sessions,CouponService pricing){this.database=database;this.access=new SessionAccess(sessions);this.pricing=pricing;}
    public CheckoutReceipt checkout(String token,CheckoutRequest request) throws SQLException,Rejected {
        try(var c=database.openImmediateConnection()){
            c.setAutoCommit(false);
            try {
                long user=access.require(c,token,UserIdentity.Role.BUYER).userId();
                String hash=hash(new com.google.gson.GsonBuilder().serializeNulls().create().toJson(request));
                var prior=orders.receipt(c,user,request.checkoutKey());
                if(prior.isPresent()){
                    if(!prior.get().requestHash().equals(hash))throw new Rejected("IDEMPOTENCY_CONFLICT");
                    c.commit();return prior.get().receipt();
                }
                orders.reserve(c,user,request.checkoutKey(),hash);var savepoint=c.setSavepoint();
                try {
                    if(request.cartVersion()==Long.MAX_VALUE)throw new Rejected("CART_CHANGED");
                    QuoteSnapshot quote;
                    try{quote=pricing.quote(c,user,request.cartVersion(),request.couponId(),request.itemIds());}
                    catch(CouponService.Rejected ex){throw new Rejected(ex.getMessage());}
                    if(!quote.ready())throw new Rejected("COUPON_INVALID");
                    if(!quote.digest().equals(request.quoteDigest()))throw new Rejected("PRICE_CHANGED");
                    var selected=quote.selectedCart();if(selected.items().isEmpty())throw new Rejected("EMPTY_SELECTION");
                    for(var item:selected.items().stream().sorted(Comparator.comparingLong(CartSnapshot.Item::productId)).toList()){
                        try(var s=c.prepareStatement("UPDATE products SET stock=stock-? WHERE product_id=? AND stock>=?")){
                            s.setInt(1,item.quantity());s.setLong(2,item.productId());s.setInt(3,item.quantity());if(s.executeUpdate()!=1)throw new Rejected("OUT_OF_STOCK");
                        }
                    }
                    OrderSnapshot.Coupon coupon=null;
                    if(request.couponId()!=null){var owned=new CouponDao().owned(c,user,request.couponId(),false).orElseThrow(() -> new Rejected("COUPON_INVALID"));var p=owned.campaign();
                        coupon=new OrderSnapshot.Coupon(owned.couponId(),p.campaignId(),p.name(),p.thresholdCents(),p.discountCents(),p.startsAt(),p.endsAt());}
                    var order=orders.insert(c,user,request,quote,coupon);
                    if(request.couponId()!=null){try(var s=c.prepareStatement("UPDATE user_coupons SET used=1,used_order_id=? WHERE user_id=? AND coupon_id=? AND used=0")){
                        s.setLong(1,order.orderId());s.setLong(2,user);s.setLong(3,request.couponId());if(s.executeUpdate()!=1)throw new Rejected("COUPON_INVALID");}}
                    for(long item:request.itemIds())if(carts.remove(c,user,item)!=1)throw new Rejected("CART_CHANGED");
                    carts.advance(c,user,request.cartVersion()+1);
                    NotificationDao.insert(c,user,"order:"+order.orderId(),"ORDER","模拟成交成功","订单 #"+order.orderId()+" 已成交，可查看商品与原实付明细。",order.createdAt(),order.orderId(),null);
                    orders.finish(c,user,request.checkoutKey(),order.orderId(),null);c.releaseSavepoint(savepoint);c.commit();
                    return new CheckoutReceipt(request.checkoutKey(),"SUCCESS",order,null);
                } catch(Rejected ex){
                    c.rollback(savepoint);c.releaseSavepoint(savepoint);orders.finish(c,user,request.checkoutKey(),null,ex.getMessage());c.commit();
                    return new CheckoutReceipt(request.checkoutKey(),"FAILED",null,ex.getMessage());
                }
            }catch(SQLException | Rejected | RuntimeException ex){try{c.rollback();}catch(SQLException failure){ex.addSuppressed(failure);}throw ex;}
        }
    }
    public CheckoutReceipt byKey(String token,String key) throws SQLException {
        if(key==null || !key.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid checkout key.");
        try(var c=database.openReadOnlyConnection()){c.setAutoCommit(false);long user=access.require(c,token,UserIdentity.Role.BUYER).userId();var found=orders.receipt(c,user,key);var value=found.map(OrderDao.Recorded::receipt).orElse(new CheckoutReceipt(key,"NOT_FOUND",null,null));c.commit();return value;}
    }
    public OrderSnapshot detail(String token,long id) throws SQLException,Rejected {
        if(id<=0)throw new IllegalArgumentException("Invalid order ID.");
        try(var c=database.openReadOnlyConnection()){c.setAutoCommit(false);long user=access.require(c,token,UserIdentity.Role.BUYER).userId();var value=orders.find(c,user,id).orElseThrow(() -> new Rejected("NOT_FOUND"));c.commit();return value;}
    }
    public OrderPage list(String token,int limit,int offset) throws SQLException {
        if(limit<1 || limit>100 || offset<0)throw new IllegalArgumentException("Invalid pagination.");
        try(var c=database.openReadOnlyConnection()){c.setAutoCommit(false);long user=access.require(c,token,UserIdentity.Role.BUYER).userId();var value=orders.list(c,user,limit,offset);c.commit();return value;}
    }
    private static String hash(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
