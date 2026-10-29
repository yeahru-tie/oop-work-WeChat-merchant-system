package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.desktop.Theme;
import cn.edu.jlu.wechatmerchant.server.application.InboxService;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import cn.edu.jlu.wechatmerchant.server.ui.AnnouncementPanel;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InboxFlowIntegrationTest {
    @TempDir Path folder;
    @Test void offlineInboxReadJumpAccountIsolationPollingConfirmationAndThemeKeepBusinessState() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("full.db"))){var cart=f.carts.add(1,1001,1,0);var order=f.orders.checkout(f.buyer,f.request(f.buyer,cart,null,CartSelection.all(cart))).order();new InboxService(f.db,f.sessions,f.clock).publish(f.admin,UUID.randomUUID().toString(),"活动公告","查看商品活动",1001L);f.carts.add(1,1002,1,2);f.start();
            var queue=new LinkedBlockingQueue<ClientController.State>();var panel=new AtomicReference<InboxPanel>();var jumps=new ArrayList<Long>();SwingUtilities.invokeAndWait(() -> panel.set(new InboxPanel()));
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,s -> {assertTrue(SwingUtilities.isEventDispatchThread());panel.get().showState(s);queue.add(s);})){
                SwingUtilities.invokeAndWait(() -> {panel.get().bind(c::refreshMessages,jumps::add,jumps::add,() -> {});c.login("buyer_a","BuyerA#2026!".toCharArray());});await(queue,s -> !s.busy() && s.identity()!=null);
                SwingUtilities.invokeAndWait(c::pollMessagesIfDue);await(queue,s -> !s.busy() && s.inbox().page()!=null && s.inbox().page().unreadCount()==2);
                SwingUtilities.invokeAndWait(() -> ((JList<?>)named(panel.get(),"message-list")).setSelectedIndex(0));await(queue,s -> !s.busy() && s.inbox().page().unreadCount()==1);
                SwingUtilities.invokeAndWait(() -> ((JButton)named(panel.get(),"message-jump")).doClick());assertEquals(1001L,jumps.getFirst());
                SwingUtilities.invokeAndWait(() -> ((JList<?>)named(panel.get(),"message-list")).setSelectedIndex(1));await(queue,s -> !s.busy() && s.inbox().page().unreadCount()==0);
                SwingUtilities.invokeAndWait(() -> ((JButton)named(panel.get(),"message-jump")).doClick());assertEquals(order.orderId(),jumps.getLast());
                SwingUtilities.invokeAndWait(c::refreshCart);await(queue,s -> !s.busy() && s.canModifyCart());SwingUtilities.invokeAndWait(() -> {assertEquals(0,c.state().inbox().page().unreadCount());c.prepareCheckout();});await(queue,s -> !s.busy() && s.orders().confirmation()!=null);
                byte[] before=Files.readAllBytes(f.db.file());SwingUtilities.invokeAndWait(() -> {var confirmation=c.state().orders().confirmation();c.pollMessagesIfDue();assertSame(confirmation,c.state().orders().confirmation());assertFalse(c.state().busy());Theme.install(Theme.Mode.DARK);Theme.restyle(panel.get());Theme.install(Theme.Mode.LIGHT);assertSame(confirmation,c.state().orders().confirmation());c.cancelConfirmation();});assertArrayEquals(before,Files.readAllBytes(f.db.file()));
                SwingUtilities.invokeAndWait(c::logout);await(queue,s -> !s.busy() && s.identity()==null);SwingUtilities.invokeAndWait(() -> {assertEquals(0,((JList<?>)named(panel.get(),"message-list")).getModel().getSize());assertEquals("",((JTextArea)named(panel.get(),"message-detail")).getText());c.login("buyer_b","BuyerB#2026!".toCharArray());});await(queue,s -> !s.busy() && s.identity()!=null);SwingUtilities.invokeAndWait(c::pollMessagesIfDue);var other=await(queue,s -> !s.busy() && s.inbox().page()!=null);assertEquals(1,other.inbox().page().unreadCount());assertTrue(other.inbox().page().items().stream().allMatch(i -> i.orderId()==null));
            }f.assertConsistent();
        }
    }
    @Test void actualAdminControlsPublishOnceAndCaptureTokenOnEdt() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("admin.db"))){var panel=new AtomicReference<AnnouncementPanel>();var service=new InboxService(f.db,f.sessions,f.clock);
            SwingUtilities.invokeAndWait(() -> {panel.set(new AnnouncementPanel(service,() -> {assertTrue(SwingUtilities.isEventDispatchThread());return f.admin;},() -> fail("Unexpected expiry")));panel.get().start();});
            try{waitUi(() -> named(panel.get(),"announcement-publish").isEnabled());SwingUtilities.invokeAndWait(() -> {((JTextField)named(panel.get(),"announcement-title")).setText("店铺优惠");((JTextArea)named(panel.get(),"announcement-text")).setText("公告文字不会更改成交金额");((JTextField)named(panel.get(),"announcement-product")).setText("1001");var button=(JButton)named(panel.get(),"announcement-publish");button.doClick();button.doClick();});waitUi(() -> ViewText.of(named(panel.get(),"announcement-history")).contains("店铺优惠"));assertEquals(1,f.value("SELECT count(*) FROM notifications"));assertEquals(1,service.list(f.buyer,20,0).unreadCount());}finally{SwingUtilities.invokeAndWait(() -> panel.get().close());}
        }
    }
    private static ClientController.State await(BlockingQueue<ClientController.State> q,Predicate<ClientController.State> test) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){var s=q.poll(30,TimeUnit.MILLISECONDS);if(s!=null && test.test(s))return s;}throw new AssertionError("Expected message state missing");}
    private static void waitUi(BooleanSupplier test) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){var b=new AtomicBoolean();SwingUtilities.invokeAndWait(() -> b.set(test.getAsBoolean()));if(b.get())return;Thread.sleep(15);}throw new AssertionError("Expected UI state missing");}
    private static Component named(Container c,String name){for(var child:c.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container nested){var found=named(nested,name);if(found!=null)return found;}}return null;}
}
