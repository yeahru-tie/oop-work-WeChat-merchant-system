package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import cn.edu.jlu.wechatmerchant.server.ui.AfterSaleManagementPanel;
import java.awt.*;
import java.net.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class AfterSaleFlowIntegrationTest {
    @TempDir Path folder;
    private long buy(TransactionFixture f) throws Exception {var cart=f.carts.add(1,1001,2,0);long coupon=f.coupon();return f.orders.checkout(f.buyer,f.request(f.buyer,cart,coupon,CartSelection.all(cart))).order().orderId();}
    @Test void actualBuyerAndAdminControlsApplyApproveAndCompleteOnceAndClearPrivateHistoryOnLogout() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("flow.db"))){long order=buy(f);f.start();var view=new AtomicReference<OrdersPanel>();var admin=new AtomicReference<AfterSaleManagementPanel>();var updates=new LinkedBlockingQueue<ClientController.State>();var store=new PendingReturns(folder.resolve("pending"));
            SwingUtilities.invokeAndWait(() -> {view.set(new OrdersPanel());admin.set(new AfterSaleManagementPanel(f.returns,() -> f.admin,() -> fail("Admin must remain valid")));});
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,s -> {view.get().showState(s);updates.add(s);},new PendingCheckouts(),store)){
                SwingUtilities.invokeAndWait(() -> {view.get().bind(() -> {},c::refreshOrders,c::loadOrder,c::queryPending,c::retryPending);view.get().bindReturns(c::applyReturn,c::loadReturnHistory,c::queryPendingReturn,c::retryPendingReturn);});login(c,updates);SwingUtilities.invokeAndWait(() -> c.refreshOrders(0));await(updates,s -> !s.busy() && s.orders().page()!=null);SwingUtilities.invokeAndWait(() -> c.loadOrder(order));await(updates,ClientController.State::canApplyReturn);
                SwingUtilities.invokeAndWait(() -> {((JButton)named(view.get(),"apply-return")).doClick();var form=view.get().requestForm();assertNotNull(form);((JTextArea)named(form,"return-reason")).setText("不合适，申请全部退货");((JButton)named(form,"submit-return")).doClick();((JButton)named(form,"submit-return")).doClick();});var requested=await(updates,s -> !s.busy() && s.returns().pending()==null && s.returns().history()!=null && !s.returns().history().items().isEmpty());assertEquals("REQUESTED",requested.returns().history().items().getFirst().status());assertEquals("RETURN_REQUESTED",requested.orders().detail().status());assertTrue(store.first(requested.identity()).isEmpty());assertEquals(1,f.value("SELECT count(*) FROM return_attempts"));assertEquals(0,f.value("SELECT count(*) FROM refunds"));
                assertEquals(1,requested.orders().page().items().size());assertEquals("RETURN_REQUESTED",requested.orders().page().items().getFirst().status());SwingUtilities.invokeAndWait(() -> assertEquals(1,((JList<?>)named(view.get(),"order-list")).getModel().getSize()));
                SwingUtilities.invokeAndWait(() -> admin.get().start());awaitUi(() -> named(admin.get(),"returns-approve").isEnabled());click(admin.get(),"returns-approve");awaitUi(() -> named(admin.get(),"returns-complete").isEnabled());assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT count(*) FROM refunds"));
                SwingUtilities.invokeAndWait(() -> c.loadReturnHistory(0));var approved=await(updates,s -> !s.busy() && s.returns().history()!=null && s.returns().history().items().getFirst().status().equals("APPROVED"));assertFalse(approved.canApplyReturn());
                SwingUtilities.invokeAndWait(() -> {((JButton)named(admin.get(),"returns-complete")).doClick();((JButton)named(admin.get(),"returns-complete")).doClick();});awaitUi(() -> ((JTextArea)named(admin.get(),"returns-status")).getText().contains("整单退货已完成"));
                SwingUtilities.invokeAndWait(() -> c.loadReturnHistory(0));var done=await(updates,s -> !s.busy() && s.orders().detail()!=null && s.orders().detail().status().equals("REFUNDED"));assertEquals("COMPLETED",done.returns().history().items().getFirst().status());assertEquals("2480",done.returns().history().items().getFirst().refundCents().toString());assertEquals(1,f.value("SELECT count(*) FROM refunds"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(1,f.value("SELECT used FROM user_coupons"));
                assertEquals("REFUNDED",done.orders().page().items().getFirst().status());SwingUtilities.invokeAndWait(() -> {assertFalse(named(view.get(),"apply-return").isEnabled());assertTrue(ViewText.of(named(view.get(),"return-history")).contains("24.80"));c.logout();});await(updates,s -> !s.busy() && s.identity()==null);SwingUtilities.invokeAndWait(() -> {assertFalse(ViewText.of(view.get()).contains("不合适"));assertNull(view.get().requestForm());});f.assertConsistent();
            }finally{SwingUtilities.invokeAndWait(() -> admin.get().close());}
        }
    }
    @Test void rejectedApplicationCanBeReappliedThroughTheSameOrderWhilePreservingHistory() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("reject-ui.db"))){long order=buy(f);f.start();var q=new LinkedBlockingQueue<ClientController.State>();var manager=new AtomicReference<AfterSaleManagementPanel>();SwingUtilities.invokeAndWait(() -> manager.set(new AfterSaleManagementPanel(f.returns,() -> f.admin,() -> fail("Unexpected logout"))));
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,q::add)){
                login(c,q);SwingUtilities.invokeAndWait(() -> c.loadOrder(order));await(q,ClientController.State::canApplyReturn);SwingUtilities.invokeAndWait(() -> c.applyReturn(order,"第一次原因"));await(q,s -> !s.busy() && s.returns().history()!=null && !s.returns().history().items().isEmpty());SwingUtilities.invokeAndWait(() -> manager.get().start());awaitUi(() -> named(manager.get(),"returns-reject").isEnabled());click(manager.get(),"returns-reject");awaitUi(() -> ((JTextArea)named(manager.get(),"returns-status")).getText().contains("须填写"));assertEquals("REQUESTED",f.returns.history(f.buyer,order,20,0).items().getFirst().status());
                SwingUtilities.invokeAndWait(() -> ((JTextArea)named(manager.get(),"returns-note")).setText("请补充原因"));click(manager.get(),"returns-reject");awaitUi(() -> ViewText.of(named(manager.get(),"returns-content")).contains("请补充原因"));SwingUtilities.invokeAndWait(() -> c.loadReturnHistory(0));await(q,ClientController.State::canApplyReturn);SwingUtilities.invokeAndWait(() -> c.applyReturn(order,"补充后的原因"));var again=await(q,s -> !s.busy() && s.returns().history()!=null && s.returns().history().items().size()==2);assertEquals("REQUESTED",again.returns().history().items().getFirst().status());assertEquals("REJECTED",again.returns().history().items().getLast().status());assertEquals("请补充原因",again.returns().history().items().getLast().reviewNote());assertEquals(0,f.value("SELECT count(*) FROM refunds"));
            }finally{SwingUtilities.invokeAndWait(() -> manager.get().close());}
        }
    }
    @ParameterizedTest @ValueSource(strings={"DROP","CANCEL","BEFORE","BAD_REPLY"})
    void unknownApplySurvivesBothSidesRestartAndOnlyOriginalRequestIsReconciled(String mode) throws Exception {
        Path file=folder.resolve("recover.db"),local=folder.resolve("local");long order;String key;
        try(var f=new TransactionFixture(file)){order=buy(f);f.start();try(var proxy=new ServerSocket(0);var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var seen=new CountDownLatch(1);var release=new CountDownLatch(1);var writes=new AtomicInteger();var forwarding=pool.submit(() -> {try(var downstream=proxy.accept();var upstream=new Socket("127.0.0.1",f.port())){while(true){var request=FrameCodec.read(downstream,5000);if(request==null)return;boolean apply=request.get("operation").getAsInt()==Messages.APPLY_RETURN;if(apply){writes.incrementAndGet();if(mode.equals("BEFORE")){seen.countDown();return;}}FrameCodec.write(upstream.getOutputStream(),request);var reply=FrameCodec.read(upstream,5000);if(apply){seen.countDown();if(mode.equals("CANCEL"))release.await();if(mode.equals("BAD_REPLY")){reply.getAsJsonObject("body").getAsJsonObject("application").addProperty("orderId",Long.MAX_VALUE);FrameCodec.write(downstream.getOutputStream(),reply);}return;}FrameCodec.write(downstream.getOutputStream(),reply);}}catch(Exception ex){throw new RuntimeException(ex);}});
            var q=new LinkedBlockingQueue<ClientController.State>();try(var client=new MerchantClient("127.0.0.1",proxy.getLocalPort(),1000,5000);var c=new ClientController(client,q::add,new PendingCheckouts(),new PendingReturns(local))){login(c,q);long id=order;SwingUtilities.invokeAndWait(() -> c.loadOrder(id));await(q,ClientController.State::canApplyReturn);SwingUtilities.invokeAndWait(() -> c.applyReturn(id,"原始申请原因"));assertTrue(seen.await(6,TimeUnit.SECONDS));if(mode.equals("CANCEL")){SwingUtilities.invokeAndWait(c::cancel);release.countDown();}var pending=await(q,s -> !s.busy() && s.returns().pending()!=null);key=pending.returns().pending().returnKey();assertEquals(key,new PendingReturns(local).first(pending.identity()).orElseThrow().returnKey());assertFalse(pending.canApplyReturn());SwingUtilities.invokeAndWait(() -> c.applyReturn(id,"不能新建"));assertEquals(1,writes.get());}finally{release.countDown();}forwarding.get(6,TimeUnit.SECONDS);
        }}
        try(var f=new TransactionFixture(file)){f.start();var q=new LinkedBlockingQueue<ClientController.State>();try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,q::add,new PendingCheckouts(),new PendingReturns(local))){login(c,q);SwingUtilities.invokeAndWait(() -> assertEquals(key,c.state().returns().pending().returnKey()));SwingUtilities.invokeAndWait(c::queryPendingReturn);if(mode.equals("BEFORE")){await(q,s -> !s.busy() && s.returns().pending()!=null && s.returns().message().contains("暂未查到"));assertEquals(0,f.value("SELECT count(*) FROM after_sales"));SwingUtilities.invokeAndWait(c::retryPendingReturn);}var done=await(q,s -> !s.busy() && s.returns().pending()==null && s.returns().history()!=null);assertEquals(key,done.returns().history().items().getFirst().returnKey());assertEquals(1,f.value("SELECT count(*) FROM after_sales"));assertEquals(1,f.value("SELECT count(*) FROM return_attempts"));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT count(*) FROM refunds"));assertTrue(new PendingReturns(local).first(done.identity()).isEmpty());f.assertConsistent();}}
    }
    @Test void openingTheReturnFormAndSubmittingBlankTextNeverCreatesAnApplication() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("form-only.db"))){
            long order=buy(f);f.start();var view=new AtomicReference<OrdersPanel>();var updates=new LinkedBlockingQueue<ClientController.State>();
            SwingUtilities.invokeAndWait(() -> view.set(new OrdersPanel()));
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,s -> {view.get().showState(s);updates.add(s);})){
                SwingUtilities.invokeAndWait(() -> view.get().bindReturns(c::applyReturn,c::loadReturnHistory,c::queryPendingReturn,c::retryPendingReturn));
                login(c,updates);SwingUtilities.invokeAndWait(() -> c.loadOrder(order));await(updates,ClientController.State::canApplyReturn);
                SwingUtilities.invokeAndWait(() -> {((JButton)named(view.get(),"apply-return")).doClick();var form=view.get().requestForm();assertNotNull(form);((JButton)named(form,"submit-return")).doClick();assertTrue(ViewText.of(form).contains("请填写退货原因"));});
                assertEquals(0,f.value("SELECT count(*) FROM return_attempts"));assertEquals(0,f.value("SELECT count(*) FROM after_sales"));
            }
        }
    }
    private static void login(ClientController c,BlockingQueue<ClientController.State> q) throws Exception {SwingUtilities.invokeAndWait(() -> c.login("buyer_a","BuyerA#2026!".toCharArray()));await(q,s -> !s.busy() && s.identity()!=null);}
    private static ClientController.State await(BlockingQueue<ClientController.State> q,Predicate<ClientController.State> condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){var s=q.poll(40,TimeUnit.MILLISECONDS);if(s!=null && condition.test(s))return s;}throw new AssertionError("Expected after-sale state missing");}
    private static void awaitUi(BooleanSupplier condition) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){var ready=new AtomicBoolean();SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));if(ready.get())return;Thread.sleep(15);}throw new AssertionError("Expected manager state missing");}
    private static void click(Container view,String name) throws Exception {SwingUtilities.invokeAndWait(() -> {var button=(JButton)named(view,name);assertTrue(button.isEnabled(),name);button.doClick();});}
    private static Component named(Container c,String name){var found=find(c,name);assertNotNull(found,name);return found;}
    private static Component find(Container c,String name){for(var child:c.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container sub){var found=find(sub,name);if(found!=null)return found;}}return null;}
}
