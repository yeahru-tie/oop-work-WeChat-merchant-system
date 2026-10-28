package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;

/** All calls share the application's connection and transaction. */
public final class OrderDao {
    private static final Gson GSON=new GsonBuilder().serializeNulls().create();
    public record Recorded(String requestHash,CheckoutReceipt receipt) { }
    public Optional<Recorded> receipt(Connection c,long user,String key) throws SQLException {
        try(var s=c.prepareStatement("SELECT request_hash,state,order_id,failure_code FROM checkout_attempts WHERE user_id=? AND checkout_key=?")){
            s.setLong(1,user);s.setString(2,key);try(var r=s.executeQuery()){
                if(!r.next())return Optional.empty();String state=r.getString("state");
                var order=state.equals("SUCCESS")?find(c,user,r.getLong("order_id")).orElseThrow(() -> new SQLException("Missing committed order.")):null;
                return Optional.of(new Recorded(r.getString("request_hash"),new CheckoutReceipt(key,state.equals("PENDING")?"UNKNOWN":state,order,r.getString("failure_code"))));
            }
        }
    }
    public void reserve(Connection c,long user,String key,String hash) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO checkout_attempts(user_id,checkout_key,request_hash,state) VALUES(?,?,?,'PENDING')")){s.setLong(1,user);s.setString(2,key);s.setString(3,hash);s.executeUpdate();}
    }
    public void finish(Connection c,long user,String key,Long order,String failure) throws SQLException {
        try(var s=c.prepareStatement("UPDATE checkout_attempts SET state=?,order_id=?,failure_code=? WHERE user_id=? AND checkout_key=?")){
            s.setString(1,order==null?"FAILED":"SUCCESS");if(order==null)s.setNull(2,Types.INTEGER);else s.setLong(2,order);s.setString(3,failure);s.setLong(4,user);s.setString(5,key);
            if(s.executeUpdate()!=1)throw new SQLException("Missing checkout result.");
        }
    }
    public OrderSnapshot insert(Connection c,long user,CheckoutRequest request,QuoteSnapshot quote,OrderSnapshot.Coupon coupon) throws SQLException {
        var selected=quote.selectedCart();BigInteger original=selected.totalCents(),productReduction=original.subtract(selected.estimatedTotalCents());long id;
        try(var s=c.prepareStatement("INSERT INTO orders(user_id,checkout_key,created_at,status,coupon_json,original_cents,product_reduction_cents,coupon_reduction_cents,paid_cents) VALUES(?,?,?,'COMPLETED',?,?,?,?,?) RETURNING order_id")){
            s.setLong(1,user);s.setString(2,request.checkoutKey());s.setLong(3,quote.quotedAt());s.setString(4,coupon==null?null:GSON.toJson(coupon));
            s.setString(5,original.toString());s.setString(6,productReduction.toString());s.setString(7,quote.couponReductionCents().toString());s.setString(8,quote.payableCents().toString());
            try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Missing order identifier.");id=r.getLong(1);}
        }
        var items=new ArrayList<OrderSnapshot.Item>();int sequence=0;
        try(var s=c.prepareStatement("INSERT INTO order_items(order_id,item_seq,cart_item_id,product_id,name,price_cents,quantity,promotion_json) VALUES(?,?,?,?,?,?,?,?)")){
            for(var item:selected.items()){
                items.add(new OrderSnapshot.Item(item.itemId(),item.productId(),item.name(),item.priceCents(),item.quantity(),item.promotion()));
                s.setLong(1,id);s.setInt(2,sequence++);s.setLong(3,item.itemId());s.setLong(4,item.productId());s.setString(5,item.name());s.setLong(6,item.priceCents());s.setInt(7,item.quantity());s.setString(8,GSON.toJson(item.promotion()));s.addBatch();
            }s.executeBatch();
        }
        return new OrderSnapshot(id,request.checkoutKey(),quote.quotedAt(),"COMPLETED",items,coupon,original,productReduction,quote.couponReductionCents(),quote.payableCents());
    }
    public Optional<OrderSnapshot> find(Connection c,long user,long id) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM orders WHERE user_id=? AND order_id=?")){
            s.setLong(1,user);s.setLong(2,id);try(var r=s.executeQuery()){
                if(!r.next())return Optional.empty();
                try{
                    var items=new ArrayList<OrderSnapshot.Item>();
                    try(var rows=c.prepareStatement("SELECT * FROM order_items WHERE order_id=? ORDER BY item_seq")){
                        rows.setLong(1,id);try(var data=rows.executeQuery()){while(data.next())items.add(new OrderSnapshot.Item(data.getLong("cart_item_id"),data.getLong("product_id"),data.getString("name"),data.getLong("price_cents"),data.getInt("quantity"),PromotionPrice.parse(JsonParser.parseString(data.getString("promotion_json")),data.getLong("price_cents"))));}
                    }
                    String coupon=r.getString("coupon_json");
                    return Optional.of(new OrderSnapshot(id,r.getString("checkout_key"),r.getLong("created_at"),r.getString("status"),items,coupon==null?null:OrderSnapshot.parseCoupon(JsonParser.parseString(coupon).getAsJsonObject()),
                            new BigInteger(r.getString("original_cents")),new BigInteger(r.getString("product_reduction_cents")),new BigInteger(r.getString("coupon_reduction_cents")),new BigInteger(r.getString("paid_cents"))));
                }catch(RuntimeException ex){throw new SQLException("Invalid historical order.",ex);}
            }
        }
    }
    public OrderPage list(Connection c,long user,int limit,int offset) throws SQLException {
        try(var s=c.prepareStatement("SELECT o.*, (SELECT count(*) FROM order_items i WHERE i.order_id=o.order_id) AS item_count FROM orders o WHERE user_id=? ORDER BY order_id DESC LIMIT ? OFFSET ?")){
            s.setLong(1,user);s.setInt(2,limit+1);s.setInt(3,offset);var items=new ArrayList<OrderPage.Summary>();
            try(var r=s.executeQuery()){while(r.next())items.add(new OrderPage.Summary(r.getLong("order_id"),r.getLong("created_at"),r.getString("status"),r.getInt("item_count"),new BigInteger(r.getString("paid_cents"))));}
            boolean more=items.size()>limit;return new OrderPage(more?items.subList(0,limit):items,limit,offset,more);
        }
    }
}
