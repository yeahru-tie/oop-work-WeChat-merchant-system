package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.math.BigInteger;
import java.sql.*;
import java.time.Clock;
import java.util.*;

public final class AfterSaleService {
    public static final class Rejected extends Exception {private static final long serialVersionUID=1L;public Rejected(String code){super(code);}}
    public record AdminRow(long orderId,String buyer,String status,BigInteger paidCents){}
    public record AdminPage(List<AdminRow> items,int offset,boolean hasMore){public AdminPage{items=List.copyOf(items);}}
    public record Detail(OrderSnapshot order,AfterSalePage history){}
    private final SqliteDatabase db;private final SessionAccess access;private final Clock clock;private final AfterSaleDao dao=new AfterSaleDao();private final OrderDao orders=new OrderDao();
    public AfterSaleService(SqliteDatabase db,SessionStore sessions){this(db,sessions,Clock.systemUTC());}
    public AfterSaleService(SqliteDatabase db,SessionStore sessions,Clock clock){this.db=db;access=new SessionAccess(sessions);this.clock=clock;}
    public AfterSaleReceipt apply(String token,ApplyReturnRequest request) throws SQLException,Rejected {
        return transaction(token,UserIdentity.Role.BUYER,true,(c,user) -> {
            String hash=hash(request);var prior=dao.receipt(c,user,request.returnKey());if(prior.isPresent()){if(!hash.equals(prior.get().hash()))throw new Rejected("IDEMPOTENCY_CONFLICT");return prior.get().receipt();}
            dao.reserve(c,user,request.returnKey(),hash);var point=c.setSavepoint();
            try{var order=orders.find(c,user,request.orderId()).orElseThrow(() -> new Rejected("NOT_FOUND"));if(!order.status().equals("COMPLETED"))throw new Rejected("STATE_CHANGED");
                var value=dao.insert(c,user,request,clock.millis(),order.paidCents());changeOrder(c,order.orderId(),"COMPLETED","RETURN_REQUESTED");dao.finish(c,user,request.returnKey(),value.afterSaleId(),null);
                NotificationDao.insert(c,user,"return:"+value.afterSaleId()+":REQUESTED","RETURN","售后申请已提交","订单 #"+order.orderId()+" 的整单退货申请已提交，等待管理员审核。",value.appliedAt(),order.orderId(),null);
                c.releaseSavepoint(point);return new AfterSaleReceipt(request.returnKey(),"SUCCESS",value,null);
            }catch(Rejected ex){c.rollback(point);c.releaseSavepoint(point);dao.finish(c,user,request.returnKey(),null,ex.getMessage());return new AfterSaleReceipt(request.returnKey(),"FAILED",null,ex.getMessage());}
        });
    }
    public AfterSaleReceipt byKey(String token,String key) throws SQLException,Rejected {
        if(!ApplyReturnRequest.validKey(key))throw new IllegalArgumentException("Invalid key.");return transaction(token,UserIdentity.Role.BUYER,false,(c,user) -> dao.receipt(c,user,key).map(AfterSaleDao.Recorded::receipt).orElse(new AfterSaleReceipt(key,"NOT_FOUND",null,null)));
    }
    public AfterSalePage history(String token,long order,int limit,int offset) throws SQLException,Rejected {
        page(limit,offset);return transaction(token,UserIdentity.Role.BUYER,false,(c,user) -> {if(orders.find(c,user,order).isEmpty())throw new Rejected("NOT_FOUND");return dao.history(c,user,order,limit,offset);});
    }
    public AdminPage adminPage(String token,int offset) throws SQLException,Rejected {
        page(20,offset);return transaction(token,UserIdentity.Role.ADMIN,false,(c,user) -> {
            try(var s=c.prepareStatement("SELECT o.order_id,u.display_name,o.status,o.paid_cents FROM orders o JOIN users u ON u.user_id=o.user_id ORDER BY o.order_id DESC LIMIT 21 OFFSET ?")){s.setInt(1,offset);var items=new ArrayList<AdminRow>();try(var r=s.executeQuery()){while(r.next())items.add(new AdminRow(r.getLong(1),r.getString(2),r.getString(3),new BigInteger(r.getString(4))));}boolean more=items.size()>20;return new AdminPage(more?items.subList(0,20):items,offset,more);}
        });
    }
    public Detail adminDetail(String token,long order,int offset) throws SQLException,Rejected {
        page(20,offset);return transaction(token,UserIdentity.Role.ADMIN,false,(c,admin) -> {long user=owner(c,order);return new Detail(orders.find(c,user,order).orElseThrow(() -> new Rejected("NOT_FOUND")),dao.history(c,user,order,20,offset));});
    }
    public AfterSaleSnapshot manage(String token,long id,long version,String action,String note) throws SQLException,Rejected {
        if(id<=0 || version<1 || !Set.of("APPROVE","REJECT","COMPLETE").contains(action) || note==null || (action.equals("REJECT")?!ApplyReturnRequest.validText(note):!note.isEmpty()))throw new IllegalArgumentException("Invalid review.");
        return transaction(token,UserIdentity.Role.ADMIN,true,(c,admin) -> {
            var owned=dao.find(c,id).orElseThrow(() -> new Rejected("NOT_FOUND"));var old=owned.value();String target=switch(action){case "APPROVE" -> "APPROVED";case "REJECT" -> "REJECTED";default -> "COMPLETED";};
            if(old.status().equals(target)){if(!old.reviewNote().equals(note))throw new Rejected("STATE_CHANGED");return old;}
            if(old.version()!=version || !old.status().equals(action.equals("COMPLETE")?"APPROVED":"REQUESTED"))throw new Rejected("STATE_CHANGED");
            var order=orders.find(c,owned.user(),old.orderId()).orElseThrow(() -> new Rejected("NOT_FOUND"));if(!order.status().equals("RETURN_REQUESTED") || !old.refundCents().equals(order.paidCents()))throw new Rejected("STATE_CHANGED");
            long now=Math.max(clock.millis(),Math.max(old.appliedAt(),old.reviewedAt()));
            if(action.equals("COMPLETE")){
                for(var item:order.items().stream().sorted(Comparator.comparingLong(OrderSnapshot.Item::productId)).toList())try(var s=c.prepareStatement("UPDATE products SET stock=stock+? WHERE product_id=? AND stock<=?")){s.setInt(1,item.quantity());s.setLong(2,item.productId());s.setInt(3,Integer.MAX_VALUE-item.quantity());if(s.executeUpdate()!=1)throw new Rejected("STOCK_RESTORE_FAILED");}
                try(var s=c.prepareStatement("INSERT INTO refunds(after_sale_id,order_id,amount_cents,completed_at,operator_id) VALUES(?,?,?,?,?)")){s.setLong(1,id);s.setLong(2,order.orderId());s.setString(3,order.paidCents().toString());s.setLong(4,now);s.setLong(5,admin);s.executeUpdate();}
                changeOrder(c,order.orderId(),"RETURN_REQUESTED","REFUNDED");
            }else if(action.equals("REJECT"))changeOrder(c,order.orderId(),"RETURN_REQUESTED","COMPLETED");
            var value=dao.transition(c,old,target,note,now,admin);
            String title=switch(target){case "APPROVED" -> "售后申请已批准";case "REJECTED" -> "售后申请未通过";default -> "退货与模拟退款已完成";};
            String text=switch(target){case "APPROVED" -> "管理员已批准申请，尚未退款；请等待明确完成。";case "REJECTED" -> "拒绝原因："+note+"。可在原订单填写新原因再次申请。";default -> "已按订单原实付完成模拟退款并补回原数量；已用券不返还。";};
            NotificationDao.insert(c,owned.user(),"return:"+id+":"+target,"RETURN",title,"订单 #"+order.orderId()+"："+text,now,order.orderId(),null);return value;
        });
    }
    private static long owner(Connection c,long order) throws SQLException,Rejected {try(var s=c.prepareStatement("SELECT user_id FROM orders WHERE order_id=?")){s.setLong(1,order);try(var r=s.executeQuery()){if(!r.next())throw new Rejected("NOT_FOUND");return r.getLong(1);}}}
    private static void changeOrder(Connection c,long order,String from,String to) throws SQLException,Rejected {try(var s=c.prepareStatement("UPDATE orders SET status=? WHERE order_id=? AND status=?")){s.setString(1,to);s.setLong(2,order);s.setString(3,from);if(s.executeUpdate()!=1)throw new Rejected("STATE_CHANGED");}}
    private static void page(int limit,int offset){if(limit<1 || limit>100 || offset<0)throw new IllegalArgumentException("Invalid pagination.");}
    private static String hash(ApplyReturnRequest request){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new com.google.gson.Gson().toJson(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private <T> T transaction(String token,UserIdentity.Role role,boolean write,Work<T> work) throws SQLException,Rejected {
        try(var c=write?db.openImmediateConnection():db.openReadOnlyConnection()){c.setAutoCommit(false);try{long user=access.require(c,token,role).userId();T value=work.run(c,user);c.commit();return value;}catch(SQLException | Rejected | RuntimeException ex){try{c.rollback();}catch(SQLException failure){ex.addSuppressed(failure);}throw ex;}}
    }
    @FunctionalInterface private interface Work<T>{T run(Connection c,long user) throws SQLException,Rejected;}
}
