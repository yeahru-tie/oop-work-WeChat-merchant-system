package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.protocol.*;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;

/** No transaction ownership here: all calls use the application service's connection. */
public final class AfterSaleDao {
    public record Owned(long user,AfterSaleSnapshot value){}
    public record Recorded(String hash,AfterSaleReceipt receipt){}
    public Optional<Owned> find(Connection c,long id) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM after_sales WHERE after_sale_id=?")){s.setLong(1,id);try(var r=s.executeQuery()){return r.next()?Optional.of(new Owned(r.getLong("user_id"),read(r))):Optional.empty();}}
    }
    public Optional<Recorded> receipt(Connection c,long user,String key) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM return_attempts WHERE user_id=? AND return_key=?")){s.setLong(1,user);s.setString(2,key);try(var r=s.executeQuery()){
            if(!r.next())return Optional.empty();String state=r.getString("state");AfterSaleSnapshot value=null;
            if(state.equals("SUCCESS")){var found=find(c,r.getLong("after_sale_id")).orElseThrow(() -> new SQLException("Missing application."));if(found.user()!=user)throw new SQLException("Wrong application owner.");value=found.value();}
            return Optional.of(new Recorded(r.getString("request_hash"),new AfterSaleReceipt(key,state.equals("PENDING")?"UNKNOWN":state,value,r.getString("failure_code"))));
        }}
    }
    public void reserve(Connection c,long user,String key,String hash) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO return_attempts(user_id,return_key,request_hash,state) VALUES(?,?,?,'PENDING')")){s.setLong(1,user);s.setString(2,key);s.setString(3,hash);s.executeUpdate();}
    }
    public void finish(Connection c,long user,String key,Long id,String failure) throws SQLException {
        try(var s=c.prepareStatement("UPDATE return_attempts SET state=?,after_sale_id=?,failure_code=? WHERE user_id=? AND return_key=?")){s.setString(1,id==null?"FAILED":"SUCCESS");if(id==null)s.setNull(2,Types.INTEGER);else s.setLong(2,id);s.setString(3,failure);s.setLong(4,user);s.setString(5,key);if(s.executeUpdate()!=1)throw new SQLException("Missing return attempt.");}
    }
    public AfterSaleSnapshot insert(Connection c,long user,ApplyReturnRequest request,long now,BigInteger amount) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO after_sales(order_id,user_id,return_key,version,status,reason,applied_at,refund_cents) VALUES(?,?,?,1,'REQUESTED',?,?,?) RETURNING after_sale_id")){
            s.setLong(1,request.orderId());s.setLong(2,user);s.setString(3,request.returnKey());s.setString(4,request.reason());s.setLong(5,now);s.setString(6,amount.toString());try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Missing application ID.");return new AfterSaleSnapshot(r.getLong(1),request.orderId(),request.returnKey(),1,"REQUESTED",request.reason(),"",now,0,0,amount);}
        }
    }
    public AfterSalePage history(Connection c,long user,long order,int limit,int offset) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM after_sales WHERE user_id=? AND order_id=? ORDER BY after_sale_id DESC LIMIT ? OFFSET ?")){s.setLong(1,user);s.setLong(2,order);s.setInt(3,limit+1);s.setInt(4,offset);var items=new ArrayList<AfterSaleSnapshot>();try(var r=s.executeQuery()){while(r.next())items.add(read(r));}boolean more=items.size()>limit;return new AfterSalePage(order,more?items.subList(0,limit):items,limit,offset,more);}
    }
    public AfterSaleSnapshot transition(Connection c,AfterSaleSnapshot old,String status,String note,long now,long admin) throws SQLException {
        try(var s=c.prepareStatement("UPDATE after_sales SET version=version+1,status=?,review_note=?,reviewed_at=?,completed_at=?,reviewer_id=? WHERE after_sale_id=? AND version=? AND status=?")){
            s.setString(1,status);s.setString(2,note);s.setLong(3,status.equals("COMPLETED")?old.reviewedAt():now);s.setLong(4,status.equals("COMPLETED")?now:0);s.setLong(5,admin);s.setLong(6,old.afterSaleId());s.setLong(7,old.version());s.setString(8,old.status());if(s.executeUpdate()!=1)throw new SQLException("Return state changed.");
        }
        return find(c,old.afterSaleId()).orElseThrow().value();
    }
    private static AfterSaleSnapshot read(ResultSet r) throws SQLException {
        try{return new AfterSaleSnapshot(r.getLong("after_sale_id"),r.getLong("order_id"),r.getString("return_key"),r.getLong("version"),r.getString("status"),r.getString("reason"),r.getString("review_note"),r.getLong("applied_at"),r.getLong("reviewed_at"),r.getLong("completed_at"),new BigInteger(r.getString("refund_cents")));}catch(RuntimeException ex){throw new SQLException("Invalid persisted application.",ex);}
    }
}
