package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.*;
import cn.edu.jlu.wechatmerchant.client.transport.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import java.awt.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class CheckoutFlowIntegrationTest {
    @TempDir Path folder;
    private static final class Fixture implements AutoCloseable {
        final SqliteDatabase db;final MutableClock clock=new MutableClock();final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));final CouponService coupons;final OrderService orders;final ProbeServer server;final long coupon;
        Fixture(Path path,boolean seed) throws Exception {
            db=new SqliteDatabase(path);db.initialize();coupons=new CouponService(db,sessions,clock);orders=new OrderService(db,sessions,coupons);
            var users=new UserDao(db);String admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity());String buyer=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity());
            if(seed){try(var c=db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE products SET price_cents=10000 WHERE product_id=1001");}
                new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(1000,900),clock.instant(),clock.instant().plusSeconds(3600),true));
                var p=coupons.saveDraft(admin,new CouponCampaign(0,0,"满150减20",15000,2000,clock.millis(),clock.millis()+3600000,20,0,false,false),java.util.UUID.randomUUID().toString());coupons.publish(admin,p.campaignId(),p.version());coupon=coupons.claim(buyer,p.campaignId()).couponId();
                var carts=new CartService(db);carts.add(1,1001,2,0);carts.add(1,1002,1,1);
            }else coupon=1;
            var identities=new IdentityHandler(new AuthenticationService(users),sessions);server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(db),new PromotionPricing(clock))),identities,new CartHandler(new CartService(db,new PromotionPricing(clock)),identities),new CouponHandler(coupons,identities),new OrderHandler(orders,identities));server.start();
        }
        MerchantClient client(){return client(server.port());}MerchantClient client(int port){return new MerchantClient("127.0.0.1",port,1000,5000);}
        long value(String query) throws Exception {try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery(query)){assertTrue(r.next());return r.getLong(1);}}
        @Override public void close(){server.close();sessions.clear();}
    }
    @Test void cardCheckboxAndExplicitSecondConfirmationProduce142AndPreserveUnselectedItemAndHistory() throws Exception {
        try(var f=new Fixture(folder.resolve("ui.db"),true)){
            var images=ProductImages.load();var view=new AtomicReference<CartPanel>();var history=new AtomicReference<OrdersPanel>();var updates=new LinkedBlockingQueue<ClientController.State>();
            SwingUtilities.invokeAndWait(() -> {view.set(new CartPanel(images));history.set(new OrdersPanel());});var store=new PendingCheckouts(folder.resolve("pending"));
            try(var client=f.client();var controller=new ClientController(client,s -> {view.get().showState(s);history.get().showState(s);updates.add(s);},store)){
                SwingUtilities.invokeAndWait(() -> {view.get().bindActions(() -> fail("Already logged in"),controller::refreshCart,controller::editCart);view.get().bindCouponActions(controller::refreshCoupons,controller::claimCoupon,controller::selectCoupon,controller::refreshQuote,() -> fail("Already logged in"));view.get().bindCheckoutActions(controller::selectCartItem,controller::prepareCheckout,controller::queryPending,controller::retryPending);history.get().bind(() -> {},controller::refreshOrders,controller::loadOrder,controller::queryPending,controller::retryPending);});
                var ready=login(controller,updates);long first=ready.cart().contents().items().getFirst().itemId(),second=ready.cart().contents().items().getLast().itemId();assertEquals(2,ready.selectedIds().size());
                SwingUtilities.invokeAndWait(() -> ((JCheckBox)named(view.get(),"cart-selected-"+second)).doClick());await(updates,s -> !s.busy() && s.selectedIds().size()==1 && s.coupons().quote()!=null);
                SwingUtilities.invokeAndWait(() -> controller.selectCoupon(f.coupon));await(updates,s -> !s.busy() && s.coupons().quote()!=null && "APPLIED".equals(s.coupons().quote().couponStatus()));
                click(view.get(),"prepare-checkout");var prepared=await(updates,s -> !s.busy() && s.orders().confirmation()!=null);assertEquals("14200",prepared.orders().confirmation().payableCents().toString());assertEquals(0,f.value("SELECT count(*) FROM orders"));
                SwingUtilities.invokeAndWait(() -> {var panel=new CheckoutConfirmationPanel(prepared.orders().confirmation(),() -> controller.state().orders().confirmation()!=null && controller.state().coupons().fresh(),controller::confirmCheckout,controller::cancelConfirmation);((JButton)named(panel,"checkout-confirm")).doClick();((JButton)named(panel,"checkout-confirm")).doClick();});
                var completed=await(updates,s -> !s.busy() && s.orders().detail()!=null && s.cart().status()==ClientController.CartStatus.READY);
                assertEquals("14200",completed.orders().detail().paidCents().toString());assertEquals(1,completed.cart().contents().items().size());assertEquals(second,completed.cart().contents().items().getFirst().itemId());assertTrue(completed.selectedIds().isEmpty());assertTrue(store.first(completed.identity()).isEmpty());
                assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(15,f.value("SELECT stock FROM products WHERE product_id=1002"));assertEquals(1,f.value("SELECT used FROM user_coupons"));
                SwingUtilities.invokeAndWait(() -> assertFalse(((JCheckBox)named(view.get(),"cart-selected-"+second)).isSelected()));
                try(var c=f.db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE products SET name='改名',price_cents=1 WHERE product_id=1001");}
                SwingUtilities.invokeAndWait(() -> controller.refreshOrders(0));var page=await(updates,s -> !s.busy() && s.orders().page()!=null);long id=page.orders().page().items().getFirst().orderId();SwingUtilities.invokeAndWait(() -> controller.loadOrder(id));await(updates,s -> !s.busy() && s.orders().detail()!=null);
                SwingUtilities.invokeAndWait(() -> {String text=ViewText.of(named(history.get(),"order-content"));assertTrue(text.contains("142.00"));assertTrue(text.contains("演示笔记本"));assertFalse(text.contains("改名"));});
                SwingUtilities.invokeAndWait(controller::logout);await(updates,s -> !s.busy() && s.identity()==null);SwingUtilities.invokeAndWait(() -> assertEquals(0,((JList<?>)named(history.get(),"order-list")).getModel().getSize()));
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"DROP_AFTER_COMMIT","CANCEL_AFTER_COMMIT","DROP_BEFORE_FORWARD"})
    void unknownCheckoutSurvivesClientAndServerRestartAndUsesOriginalKeyOnly(String mode) throws Exception {
        Path db=folder.resolve("recover.db");var store=new PendingCheckouts(folder.resolve("pending"));String key;UserIdentity identity;
        try(var f=new Fixture(db,true);var proxy=new ServerSocket(0);var workers=Executors.newVirtualThreadPerTaskExecutor()){
            var seen=new CountDownLatch(1);var release=new CountDownLatch(1);var writes=new AtomicInteger();
            workers.submit(() -> {try(var downstream=proxy.accept();var upstream=new Socket("127.0.0.1",f.server.port())){
                while(true){var request=FrameCodec.read(downstream,5000);if(request==null)return;
                    boolean checkout=request.get("operation").getAsInt()==Messages.CHECKOUT;
                    if(checkout){writes.incrementAndGet();if(mode.equals("DROP_BEFORE_FORWARD")){seen.countDown();return;}}
                    FrameCodec.write(upstream.getOutputStream(),request);var reply=FrameCodec.read(upstream,5000);
                    if(checkout){seen.countDown();if(mode.equals("CANCEL_AFTER_COMMIT"))release.await();return;}FrameCodec.write(downstream.getOutputStream(),reply);
                }
            }catch(Exception ex){throw new RuntimeException(ex);}});
            var updates=new LinkedBlockingQueue<ClientController.State>();
            try(var client=f.client(proxy.getLocalPort());var controller=new ClientController(client,updates::add,store)){
                var ready=login(controller,updates);identity=ready.identity();SwingUtilities.invokeAndWait(controller::prepareCheckout);await(updates,s -> !s.busy() && s.orders().confirmation()!=null);
                SwingUtilities.invokeAndWait(controller::confirmCheckout);assertTrue(seen.await(6,TimeUnit.SECONDS));if(mode.equals("CANCEL_AFTER_COMMIT")){SwingUtilities.invokeAndWait(controller::cancel);release.countDown();}
                var unknown=await(updates,s -> !s.busy() && s.orders().pending()!=null);key=unknown.orders().pending().request().checkoutKey();assertFalse(unknown.canModifyCart());assertEquals(key,store.first(identity).orElseThrow().request().checkoutKey());assertEquals(1,writes.get());
                SwingUtilities.invokeAndWait(() -> {controller.addToCart(1003,1);controller.prepareCheckout();});assertEquals(1,writes.get());
            }finally{release.countDown();}
            assertEquals(mode.equals("DROP_BEFORE_FORWARD")?0:1,f.value("SELECT count(*) FROM orders"));
        }
        try(var restarted=new Fixture(db,false);var client=restarted.client()){
            var updates=new LinkedBlockingQueue<ClientController.State>();try(var controller=new ClientController(client,updates::add,new PendingCheckouts(folder.resolve("pending")))){
                SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));var restored=await(updates,s -> !s.busy() && s.identity()!=null);assertEquals(key,restored.orders().pending().request().checkoutKey());
                SwingUtilities.invokeAndWait(controller::queryPending);
                if(mode.equals("DROP_BEFORE_FORWARD")){await(updates,s -> !s.busy() && s.orders().pending()!=null && s.orders().message().contains("暂未查到"));assertEquals(0,restarted.value("SELECT count(*) FROM orders"));SwingUtilities.invokeAndWait(controller::retryPending);}
                var completed=await(updates,s -> !s.busy() && s.orders().pending()==null && s.orders().detail()!=null && s.cart().status()==ClientController.CartStatus.READY);
                assertEquals(key,completed.orders().detail().checkoutKey());assertEquals(1,restarted.value("SELECT count(*) FROM orders"));assertEquals(18,restarted.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(14,restarted.value("SELECT stock FROM products WHERE product_id=1002"));assertTrue(store.first(completed.identity()).isEmpty());
            }
        }
    }
    @Test void priceChangeAfterSecondConfirmationDoesNotAutoBuyAndCancelDoesNotWrite() throws Exception {
        try(var f=new Fixture(folder.resolve("change.db"),true);var client=f.client()){
            var updates=new LinkedBlockingQueue<ClientController.State>();var store=new PendingCheckouts(folder.resolve("pending"));try(var controller=new ClientController(client,updates::add,store)){
                login(controller,updates);SwingUtilities.invokeAndWait(controller::prepareCheckout);await(updates,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(controller::cancelConfirmation);assertEquals(0,f.value("SELECT count(*) FROM checkout_attempts"));
                SwingUtilities.invokeAndWait(controller::prepareCheckout);await(updates,s -> !s.busy() && s.orders().confirmation()!=null);
                try(var c=f.db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE products SET price_cents=11000 WHERE product_id=1001");}
                SwingUtilities.invokeAndWait(controller::confirmCheckout);var failed=await(updates,s -> !s.busy() && s.orders().pending()==null && s.cart().status()==ClientController.CartStatus.READY && s.orders().message().contains("本次未成交"));
                assertNull(failed.orders().confirmation());assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertTrue(store.first(failed.identity()).isEmpty());
                SwingUtilities.invokeAndWait(controller::prepareCheckout);await(updates,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(controller::confirmCheckout);await(updates,s -> !s.busy() && s.orders().detail()!=null && s.cart().status()==ClientController.CartStatus.READY);assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(2,f.value("SELECT count(*) FROM checkout_attempts"));
            }
        }
    }
    private static ClientController.State login(ClientController controller,BlockingQueue<ClientController.State> queue) throws Exception {SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));await(queue,s -> !s.busy() && s.identity()!=null);SwingUtilities.invokeAndWait(controller::refreshCart);return await(queue,s -> !s.busy() && s.cart().status()==ClientController.CartStatus.READY);}
    private static void click(Container parent,String key) throws Exception {SwingUtilities.invokeAndWait(() -> {var button=(JButton)named(parent,key);assertTrue(button.isEnabled(),key);button.doClick();});}
    private static ClientController.State await(BlockingQueue<ClientController.State> queue,Predicate<ClientController.State> condition) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<until){var state=queue.poll(40,TimeUnit.MILLISECONDS);if(state!=null && condition.test(state))return state;}throw new AssertionError("Timed out waiting for checkout state");}
    private static Component named(Container parent,String key){var value=find(parent,key);assertNotNull(value,key);return value;}
    private static Component find(Container parent,String key){for(var child:parent.getComponents()){if(key.equals(child.getName()))return child;if(child instanceof Container c){var value=find(c,key);if(value!=null)return value;}}return null;}
}
