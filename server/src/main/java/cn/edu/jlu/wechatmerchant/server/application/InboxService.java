package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.InboxPage;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.persistence.NotificationDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.sql.SQLException;
import java.time.Clock;

public final class InboxService {
    private final SqliteDatabase db;
    private final SessionAccess access;
    private final Clock clock;
    private final NotificationDao dao=new NotificationDao();
    public InboxService(SqliteDatabase db,SessionStore sessions){this(db,sessions,Clock.systemUTC());}
    public InboxService(SqliteDatabase db,SessionStore sessions,Clock clock){this.db=db;this.access=new SessionAccess(sessions);this.clock=clock;}
    public InboxPage list(String token,int limit,int offset) throws SQLException {
        page(limit,offset);
        try(var c=db.openReadOnlyConnection()){
            c.setAutoCommit(false);long user=access.require(c,token,UserIdentity.Role.BUYER,false).userId();
            var value=dao.list(c,user,limit,offset);c.commit();return value;
        }
    }
    public InboxPage read(String token,long message,int limit,int offset) throws SQLException {
        page(limit,offset);if(message<=0)throw new IllegalArgumentException("Invalid message.");
        try(var c=db.openImmediateConnection()){
            c.setAutoCommit(false);
            try {
                long user=access.require(c,token,UserIdentity.Role.BUYER).userId();
                if(!dao.markRead(c,user,message,clock.millis()))throw new SecurityException("NOT_FOUND");
                var value=dao.list(c,user,limit,offset);c.commit();return value;
            }catch(SQLException | RuntimeException ex){c.rollback();throw ex;}
        }
    }
    private static void page(int limit,int offset){if(limit<1 || limit>100 || offset<0)throw new IllegalArgumentException("Invalid pagination.");}
    public record Announcement(long messageId,String publishKey,String title,String text,long createdAt,Long productId){}
    public long publish(String token,String key,String title,String text,Long product) throws SQLException {
        if(!cn.edu.jlu.wechatmerchant.protocol.ApplyReturnRequest.validKey(key) || !InboxPage.validText(title,80) || !InboxPage.validText(text,2000) || product!=null && product<=0)throw new IllegalArgumentException("标题1—80字，正文1—2000字，关联商品编号须为正整数。");
        try(var c=db.openImmediateConnection()){
            c.setAutoCommit(false);try {
                access.require(c,token,UserIdentity.Role.ADMIN);
                try(var s=c.prepareStatement("SELECT message_id,title,body,product_id FROM notifications WHERE event_key=?")){
                    s.setString(1,"announcement:"+key);try(var r=s.executeQuery()){if(r.next()){
                        Long linked=r.getObject(4)==null?null:r.getLong(4);
                        if(!r.getString(2).equals(title) || !r.getString(3).equals(text) || !java.util.Objects.equals(linked,product))throw new IllegalArgumentException("同一发布标识的公告内容不能更改，请载入记录核对。");
                        long id=r.getLong(1);c.commit();return id;
                    }}
                }
                if(product!=null && new cn.edu.jlu.wechatmerchant.server.persistence.ProductDao(db).findById(c,product).isEmpty())throw new IllegalArgumentException("关联商品不存在。");
                long id=NotificationDao.insert(c,null,"announcement:"+key,"ANNOUNCEMENT",title,text,clock.millis(),null,product);c.commit();return id;
            }catch(SQLException | RuntimeException ex){c.rollback();throw ex;}
        }
    }
    public java.util.List<Announcement> announcements(String token,int offset) throws SQLException {
        page(20,offset);try(var c=db.openReadOnlyConnection()){
            c.setAutoCommit(false);access.require(c,token,UserIdentity.Role.ADMIN);var items=new java.util.ArrayList<Announcement>();
            try(var s=c.prepareStatement("SELECT message_id,event_key,title,body,created_at,product_id FROM notifications WHERE kind='ANNOUNCEMENT' ORDER BY message_id DESC LIMIT 20 OFFSET ?")){
                s.setInt(1,offset);try(var r=s.executeQuery()){while(r.next())items.add(new Announcement(r.getLong(1),r.getString(2).substring("announcement:".length()),r.getString(3),r.getString(4),r.getLong(5),r.getObject(6)==null?null:r.getLong(6)));}
            }c.commit();return java.util.List.copyOf(items);
        }
    }
}
