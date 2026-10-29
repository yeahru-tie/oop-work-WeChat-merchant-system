package cn.edu.jlu.wechatmerchant.client.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class CheckoutHandoffIntegrationTest {
    @TempDir Path folder;
    @Test void terminalResultReachesEdtBeforeJournalRemovalAndLateCancelCannotLoseIt() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("handoff.db"))){
            f.carts.add(1,1001,1,0);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();var journal=new PausingJournal();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var controller=new ClientController(client,queue::add,journal)){
                login(controller,queue);SwingUtilities.invokeAndWait(controller::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(controller::confirmCheckout);
                assertTrue(journal.removing.await(6,TimeUnit.SECONDS));
                try {
                    SwingUtilities.invokeAndWait(() -> {var state=controller.state();assertNotNull(state.orders().detail(),"已核对的结果必须先交给界面，再清理恢复记录");assertTrue(state.orders().message().contains("成交成功"));controller.cancel();assertNotNull(controller.state().orders().detail());assertTrue(controller.state().busy(),"本地清理不能取消成未知交易");});
                }finally{journal.release.countDown();}
                var done=await(queue,s -> !s.busy() && s.orders().pending()==null && s.cart().status()==ClientController.CartStatus.READY);assertNotNull(done.orders().detail());assertTrue(journal.delegate.first(done.identity()).isEmpty());assertEquals(1,f.value("SELECT count(*) FROM orders"));f.assertConsistent();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"SAVE","REMOVE","AFTER_REMOVE"})
    void journalIoFailuresDoNotHideKnownResultsOrPermitNewPurchase(String stage) throws Exception {
        try(var f=new TransactionFixture(folder.resolve("failure.db"))){
            f.carts.add(1,1001,1,0);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();var journal=new FailingJournal(stage);
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var controller=new ClientController(client,queue::add,journal)){
                login(controller,queue);SwingUtilities.invokeAndWait(controller::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(controller::confirmCheckout);
                var blocked=await(queue,s -> !s.busy() && (s.orders().storageBlocked() || s.orders().pending()!=null));assertFalse(blocked.canModifyCart());
                if(stage.equals("SAVE")){assertNull(blocked.orders().detail());assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));}
                else {assertNotNull(blocked.orders().detail());assertTrue(blocked.orders().message().contains("成交成功"));assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(19,f.value("SELECT stock FROM products WHERE product_id=1001"));
                    if(stage.equals("REMOVE")){assertTrue(journal.delegate.first(blocked.identity()).isPresent());SwingUtilities.invokeAndWait(controller::queryPending);await(queue,s -> !s.busy() && s.orders().pending()==null && s.cart().status()==ClientController.CartStatus.READY);assertEquals(1,f.value("SELECT count(*) FROM orders"));}
                    else{assertNull(blocked.orders().pending());assertTrue(blocked.orders().storageBlocked());}
                }
                f.assertConsistent();
            }
        }
    }
    @Test void closeDuringJournalCleanupLeavesOriginalRecordForNextLogin() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("close.db"))){
            f.carts.add(1,1001,1,0);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();var journal=new PausingJournal();UserIdentity owner;
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var controller=new ClientController(client,queue::add,journal)){
                login(controller,queue);SwingUtilities.invokeAndWait(controller::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(controller::confirmCheckout);assertTrue(journal.removing.await(6,TimeUnit.SECONDS));var current=new AtomicReference<ClientController.State>();SwingUtilities.invokeAndWait(() -> current.set(controller.state()));assertNotNull(current.get().orders().detail());owner=current.get().identity();controller.close();
            }finally{journal.release.countDown();}
            assertTrue(journal.delegate.first(owner).isPresent());var next=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var controller=new ClientController(client,next::add,journal.delegate)){
                login(controller,next);SwingUtilities.invokeAndWait(controller::queryPending);var done=await(next,s -> !s.busy() && s.orders().pending()==null && s.cart().status()==ClientController.CartStatus.READY);assertNotNull(done.orders().detail());assertTrue(journal.delegate.first(owner).isEmpty());assertEquals(1,f.value("SELECT count(*) FROM orders"));f.assertConsistent();
            }
        }
    }
    private static final class FailingJournal implements CheckoutJournal {
        final PendingCheckouts delegate=new PendingCheckouts();final String stage;boolean removed,failed;
        FailingJournal(String stage){this.stage=stage;}
        public Optional<PendingCheckouts.Entry> first(UserIdentity owner) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());if(stage.equals("AFTER_REMOVE") && removed)throw new IOException("Other recovery record damaged");return delegate.first(owner);}
        public void save(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());if(stage.equals("SAVE"))throw new IOException("Cannot persist");delegate.save(owner,entry);}
        public void remove(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());if(stage.equals("REMOVE") && !failed){failed=true;throw new IOException("Cannot remove");}delegate.remove(owner,entry);removed=true;}
    }
    private static final class PausingJournal implements CheckoutJournal {
        final PendingCheckouts delegate=new PendingCheckouts();final CountDownLatch removing=new CountDownLatch(1),release=new CountDownLatch(1);
        public Optional<PendingCheckouts.Entry> first(UserIdentity owner) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());return delegate.first(owner);}
        public void save(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());delegate.save(owner,entry);}
        public void remove(UserIdentity owner,PendingCheckouts.Entry entry) throws IOException {assertFalse(SwingUtilities.isEventDispatchThread());removing.countDown();try{if(!release.await(8,TimeUnit.SECONDS))throw new IOException("Test timed out");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException(ex);}delegate.remove(owner,entry);}
    }
    private static void login(ClientController c,BlockingQueue<ClientController.State> q) throws Exception {SwingUtilities.invokeAndWait(() -> c.login("buyer_a","BuyerA#2026!".toCharArray()));await(q,s -> !s.busy() && s.identity()!=null);SwingUtilities.invokeAndWait(c::refreshCart);await(q,s -> !s.busy() && s.cart().status()==ClientController.CartStatus.READY);}
    private static ClientController.State await(BlockingQueue<ClientController.State> q,Predicate<ClientController.State> predicate) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){var s=q.poll(40,TimeUnit.MILLISECONDS);if(s!=null && predicate.test(s))return s;}throw new AssertionError("No expected state");}
}
