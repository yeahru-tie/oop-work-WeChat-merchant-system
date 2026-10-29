package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.persistence.NotificationDao;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InboxServiceTest {
    @TempDir Path folder;
    @Test void announcementReadIsPersonalPersistentAndRepeatedChecksDoNotCreateAnything() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("inbox.db"))){long id;
            try(var c=f.db.openWriteConnection()){id=NotificationDao.insert(c,null,"test-announcement","ANNOUNCEMENT","活动说明","公告不改变真实优惠",f.clock.millis(),null,1001L);}
            var service=new InboxService(f.db,f.sessions,f.clock);
            assertEquals(1,service.list(f.buyer,20,0).unreadCount());assertEquals(1,service.list(f.other,20,0).unreadCount());
            assertEquals(0,service.read(f.buyer,id,20,0).unreadCount());assertEquals(0,new InboxService(f.db,f.sessions,f.clock).read(f.buyer,id,20,0).unreadCount());
            assertEquals(1,service.list(f.other,20,0).unreadCount());assertEquals(1,f.value("SELECT count(*) FROM notifications"));assertEquals(1,f.value("SELECT count(*) FROM message_reads"));f.assertConsistent();
        }
    }
    @Test void privateMessagesCannotBeReadByOtherBuyerAndAdminCannotUseBuyerInbox() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("private.db"))){var cart=f.carts.add(1,1001,1,0);var order=f.orders.checkout(f.buyer,f.request(f.buyer,cart,null,java.util.List.of(cart.items().getFirst().itemId()))).order();long id;
            try(var c=f.db.openWriteConnection()){id=NotificationDao.insert(c,1L,"test-private","ORDER","成交","本人订单",f.clock.millis(),order.orderId(),null);}
            var service=new InboxService(f.db,f.sessions,f.clock);assertTrue(service.list(f.other,20,0).items().isEmpty());assertThrows(SecurityException.class,() -> service.read(f.other,id,20,0));assertThrows(SecurityException.class,() -> service.list(f.admin,20,0));assertThrows(SecurityException.class,() -> service.list(null,20,0));
            assertEquals(0,f.value("SELECT count(*) FROM message_reads"));assertFalse(service.read(f.buyer,id,20,0).items().isEmpty());
        }
    }
}
