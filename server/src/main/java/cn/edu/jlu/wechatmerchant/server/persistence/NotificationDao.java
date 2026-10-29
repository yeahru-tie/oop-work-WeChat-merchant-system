package cn.edu.jlu.wechatmerchant.server.persistence;

import cn.edu.jlu.wechatmerchant.protocol.InboxPage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

/** No connection ownership. Producers use their existing business transaction. */
public final class NotificationDao {
    public static long insert(Connection c,Long user,String key,String kind,String title,String text,long now,Long order,Long product) throws SQLException {
        new InboxPage.Item(1,kind,title,text,now,false,order,product);
        try(var s=c.prepareStatement("INSERT INTO notifications(user_id,event_key,kind,title,body,created_at,order_id,product_id) VALUES(?,?,?,?,?,?,?,?) RETURNING message_id")){
            s.setObject(1,user);s.setString(2,key);s.setString(3,kind);s.setString(4,title);s.setString(5,text);s.setLong(6,now);s.setObject(7,order);s.setObject(8,product);
            try(var r=s.executeQuery()){if(!r.next())throw new SQLException("Message insert failed.");return r.getLong(1);}
        }
    }
    public InboxPage list(Connection c,long user,int limit,int offset) throws SQLException {
        long unread;
        try(var s=c.prepareStatement("SELECT count(*) FROM notifications n WHERE (n.user_id=? OR n.user_id IS NULL) AND NOT EXISTS(SELECT 1 FROM message_reads r WHERE r.message_id=n.message_id AND r.user_id=?)")){
            s.setLong(1,user);s.setLong(2,user);try(var r=s.executeQuery()){r.next();unread=r.getLong(1);}
        }
        var items=new ArrayList<InboxPage.Item>();
        try(var s=c.prepareStatement("SELECT n.*,EXISTS(SELECT 1 FROM message_reads r WHERE r.message_id=n.message_id AND r.user_id=?) AS is_read FROM notifications n WHERE n.user_id=? OR n.user_id IS NULL ORDER BY message_id DESC LIMIT ? OFFSET ?")){
            s.setLong(1,user);s.setLong(2,user);s.setInt(3,limit+1);s.setInt(4,offset);
            try(var r=s.executeQuery()){while(r.next())items.add(new InboxPage.Item(r.getLong("message_id"),r.getString("kind"),r.getString("title"),r.getString("body"),r.getLong("created_at"),r.getBoolean("is_read"),r.getObject("order_id")==null?null:r.getLong("order_id"),r.getObject("product_id")==null?null:r.getLong("product_id")));}
        }
        boolean more=items.size()>limit;return new InboxPage(more?items.subList(0,limit):items,limit,offset,more,unread);
    }
    public boolean markRead(Connection c,long user,long message,long now) throws SQLException {
        try(var s=c.prepareStatement("SELECT 1 FROM notifications WHERE message_id=? AND (user_id=? OR user_id IS NULL)")){s.setLong(1,message);s.setLong(2,user);try(var r=s.executeQuery()){if(!r.next())return false;}}
        try(var s=c.prepareStatement("INSERT INTO message_reads(message_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(message_id,user_id) DO NOTHING")){s.setLong(1,message);s.setLong(2,user);s.setLong(3,now);s.executeUpdate();}return true;
    }
}
