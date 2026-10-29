package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NotificationFlowTest {
    @TempDir Path folder;
    @Test void orderAndEveryReturnTransitionAreOnceOnlyPrivateAndCommittedWithBusiness() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("flow.db"))){var cart=f.carts.add(1,1001,2,0);var request=f.request(f.buyer,cart,null,CartSelection.all(cart));var order=f.orders.checkout(f.buyer,request).order();f.orders.checkout(f.buyer,request);
            var apply=new ApplyReturnRequest(UUID.randomUUID().toString(),order.orderId(),"不合适");var sale=f.returns.apply(f.buyer,apply).application();f.returns.apply(f.buyer,apply);
            f.returns.manage(f.admin,sale.afterSaleId(),1,"REJECT","补充原因");f.returns.manage(f.admin,sale.afterSaleId(),1,"REJECT","补充原因");
            sale=f.returns.apply(f.buyer,new ApplyReturnRequest(UUID.randomUUID().toString(),order.orderId(),"补充具体原因")).application();f.returns.manage(f.admin,sale.afterSaleId(),1,"APPROVE","");f.returns.manage(f.admin,sale.afterSaleId(),2,"COMPLETE","");f.returns.manage(f.admin,sale.afterSaleId(),2,"COMPLETE","");
            var service=new InboxService(f.db,f.sessions,f.clock);var inbox=service.list(f.buyer,20,0);assertEquals(6,inbox.unreadCount());assertEquals(6,inbox.items().size());assertEquals(1,inbox.items().stream().filter(i -> i.kind().equals("ORDER")).count());assertTrue(inbox.items().stream().allMatch(i -> i.orderId()==order.orderId()));assertTrue(service.list(f.other,20,0).items().isEmpty());f.assertConsistent();
        }
    }
    @Test void notificationFailureRollsBackOrderInventoryCouponCartAndLedger() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("rollback.db"))){var cart=f.carts.add(1,1001,2,0);long coupon=f.coupon();var request=f.request(f.buyer,cart,coupon,CartSelection.all(cart));
            f.sql("CREATE TRIGGER fail_message BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT,'test notification failure'); END");
            assertThrows(java.sql.SQLException.class,() -> f.orders.checkout(f.buyer,request));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT used FROM user_coupons"));assertEquals(1,f.value("SELECT count(*) FROM cart_items"));assertEquals(0,f.value("SELECT count(*) FROM checkout_attempts"));
            f.sql("DROP TRIGGER fail_message");assertEquals("SUCCESS",f.orders.checkout(f.buyer,request).state());assertEquals(1,f.value("SELECT count(*) FROM notifications"));f.assertConsistent();
        }
    }
    @Test void notificationFailureRollsBackReturnCompletionIncludingRefundAndRestock() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("return-rollback.db"))){var cart=f.carts.add(1,1001,1,0);var order=f.orders.checkout(f.buyer,f.request(f.buyer,cart,null,CartSelection.all(cart))).order();var sale=f.returns.apply(f.buyer,new ApplyReturnRequest(UUID.randomUUID().toString(),order.orderId(),"申请退货")).application();f.returns.manage(f.admin,sale.afterSaleId(),1,"APPROVE","");
            f.sql("CREATE TRIGGER fail_completed_message BEFORE INSERT ON notifications WHEN NEW.event_key LIKE '%:COMPLETED' BEGIN SELECT RAISE(ABORT,'test failure'); END");
            assertThrows(java.sql.SQLException.class,() -> f.returns.manage(f.admin,sale.afterSaleId(),2,"COMPLETE",""));assertEquals(19,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT count(*) FROM refunds"));assertEquals("APPROVED",f.returns.adminDetail(f.admin,order.orderId(),0).history().items().getFirst().status());
            f.sql("DROP TRIGGER fail_completed_message");f.returns.manage(f.admin,sale.afterSaleId(),2,"COMPLETE","");assertEquals(4,f.value("SELECT count(*) FROM notifications"));assertEquals(1,f.value("SELECT count(*) FROM refunds"));f.assertConsistent();
        }
    }
    @Test void concurrentSameAnnouncementIsSingleAndFutureBuyerHasIndependentReadState() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("announcement.db"))){var service=new InboxService(f.db,f.sessions,f.clock);String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);var futures=new ArrayList<Future<Long>>();try{for(int i=0;i<4;i++)futures.add(pool.submit(() -> {start.await();return service.publish(f.admin,key,"公告","纯文字活动说明",1001L);}));start.countDown();long id=futures.getFirst().get();for(var result:futures)assertEquals(id,result.get());assertEquals(1,f.value("SELECT count(*) FROM notifications"));service.read(f.buyer,id,20,0);String future=f.addBuyer(10);assertEquals(1,service.list(future,20,0).unreadCount());assertEquals(0,service.list(f.buyer,20,0).unreadCount());assertEquals(1,service.announcements(f.admin,0).size());}finally{pool.shutdownNow();}}
    }
    @Test void publishingChecksAdminCurrentAccountParametersProductAndImmutableKey() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("rights.db"))){var s=new InboxService(f.db,f.sessions,f.clock);String key=UUID.randomUUID().toString();assertThrows(SecurityException.class,() -> s.publish(f.buyer,key,"标题","内容",null));assertThrows(IllegalArgumentException.class,() -> s.publish(f.admin,key,"标题","内容",9999L));assertEquals(0,f.value("SELECT count(*) FROM notifications"));
            s.publish(f.admin,key,"原文标题","原文内容",null);assertThrows(IllegalArgumentException.class,() -> s.publish(f.admin,key,"修改标题","原文内容",null));f.sql("UPDATE users SET enabled=0 WHERE user_id=3");assertThrows(SecurityException.class,() -> s.announcements(f.admin,0));assertEquals(1,f.value("SELECT count(*) FROM notifications"));}
    }
}
