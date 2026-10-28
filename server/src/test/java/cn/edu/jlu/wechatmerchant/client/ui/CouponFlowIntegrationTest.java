package cn.edu.jlu.wechatmerchant.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class CouponFlowIntegrationTest {
    @TempDir Path folder;
    private static final class Fixture implements AutoCloseable {
        final SqliteDatabase db;final MutableClock clock=new MutableClock();final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
        final CouponService coupons;final String admin;final ProbeServer server;final long campaign;
        Fixture(Path file) throws Exception {
            db=new SqliteDatabase(file);db.initialize();coupons=new CouponService(db,sessions,clock);
            admin=sessions.create(new UserDao(db).findByUsername("shop_admin").orElseThrow().identity());
            try(var c=db.openWriteConnection();var s=c.createStatement()){s.executeUpdate("UPDATE products SET price_cents=10000 WHERE product_id=1001");}
            new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(1000,900),clock.instant(),clock.instant().plusSeconds(60),true));
            var draft=coupons.saveDraft(admin,new CouponCampaign(0,0,"满150减20",15000,2000,clock.millis(),clock.millis()+10000,2,0,false,false),java.util.UUID.randomUUID().toString());
            campaign=coupons.publish(admin,draft.campaignId(),draft.version()).campaignId();
            var identities=new IdentityHandler(new AuthenticationService(new UserDao(db)),sessions);
            server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(db),new PromotionPricing(clock))),identities,
                    new CartHandler(new CartService(db,new PromotionPricing(clock)),identities),new CouponHandler(coupons,identities));server.start();
        }
        MerchantClient client(){return client(server.port());}
        MerchantClient client(int port){return new MerchantClient("127.0.0.1",port,1000,5000);}
        @Override public void close(){server.close();sessions.clear();}
    }
    @Test void actualCartControlsClaimSelectRequoteRejectInvalidCouponAndClearOnLogout() throws Exception {
        try(var f=new Fixture(folder.resolve("flow.db"))) {
            new CartService(f.db).add(1,1001,2,0);var image=ProductImages.load();var ref=new AtomicReference<CartPanel>();
            SwingUtilities.invokeAndWait(() -> ref.set(new CartPanel(image)));var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var transport=f.client();var controller=new ClientController(transport,s -> {assertTrue(SwingUtilities.isEventDispatchThread());ref.get().showState(s);queue.add(s);})) {
                SwingUtilities.invokeAndWait(() -> {
                    ref.get().bindActions(() -> fail("Already logged in"),controller::refreshCart,controller::editCart);
                    ref.get().bindCouponActions(controller::refreshCoupons,controller::claimCoupon,controller::selectCoupon,controller::refreshQuote,() -> fail("Already logged in"));
                    controller.login("buyer_a","BuyerA#2026!".toCharArray());
                });
                await(queue,s -> !s.busy() && s.identity()!=null);SwingUtilities.invokeAndWait(controller::refreshCart);await(queue,ClientController.State::canModifyCart);
                click(ref.get(),"open-coupons");await(queue,s -> !s.busy() && s.coupons().page()!=null);
                click(ref.get(),"coupon-available");await(queue,s -> !s.busy() && s.coupons().page()!=null && s.coupons().page().kind().equals("AVAILABLE"));
                click(ref.get(),"claim-coupon");var claimed=await(queue,s -> !s.busy() && s.coupons().page()!=null && s.coupons().page().kind().equals("OWNED") && !s.coupons().page().items().isEmpty());
                assertNull(claimed.coupons().selectedCoupon());click(ref.get(),"use-coupon");
                var applied=await(queue,s -> !s.busy() && s.coupons().quote()!=null && "APPLIED".equals(s.coupons().quote().couponStatus()));
                assertEquals("14200",applied.coupons().quote().payableCents().toString());
                click(ref.get(),"coupon-back");long item=applied.cart().contents().items().getFirst().itemId();
                SwingUtilities.invokeAndWait(() -> ((JSpinner)named(ref.get(),"cart-quantity-"+item)).setValue(1));
                var under=await(queue,s -> !s.busy() && s.coupons().quote()!=null && "THRESHOLD_NOT_MET".equals(s.coupons().quote().couponStatus()));
                assertNull(under.coupons().quote().payableCents());assertEquals(applied.coupons().selectedCoupon(),under.coupons().selectedCoupon());
                SwingUtilities.invokeAndWait(() -> assertFalse(((JTextArea)named(ref.get(),"cart-total")).getText().contains("预估应付")));
                click(ref.get(),"open-coupons");await(queue,s -> !s.busy() && s.coupons().page()!=null);click(ref.get(),"no-coupon");
                var none=await(queue,s -> !s.busy() && s.coupons().quote()!=null && s.coupons().selectedCoupon()==null);assertEquals("8100",none.coupons().quote().payableCents().toString());
                click(ref.get(),"coupon-back");SwingUtilities.invokeAndWait(() -> ((JSpinner)named(ref.get(),"cart-quantity-"+item)).setValue(2));
                await(queue,s -> !s.busy() && s.coupons().quote()!=null && s.cart().contents().items().getFirst().quantity()==2);
                click(ref.get(),"open-coupons");await(queue,s -> !s.busy() && s.coupons().page()!=null);click(ref.get(),"use-coupon");await(queue,s -> !s.busy() && s.coupons().selectedCoupon()!=null);
                f.clock.advance(Duration.ofSeconds(10));SwingUtilities.invokeAndWait(controller::refreshCart);
                var expired=await(queue,s -> !s.busy() && s.coupons().quote()!=null && "EXPIRED".equals(s.coupons().quote().couponStatus()));assertNull(expired.coupons().quote().payableCents());
                SwingUtilities.invokeAndWait(controller::logout);var guest=await(queue,s -> !s.busy() && s.identity()==null);assertNull(guest.coupons().quote());assertNull(guest.coupons().page());
                SwingUtilities.invokeAndWait(() -> {assertEquals("",((JTextArea)named(ref.get(),"cart-total")).getText());assertEquals(0,((JList<?>)named(ref.get(),"buyer-coupons")).getModel().getSize());});
                assertEquals(1,f.coupons.adminPage(f.admin,0).items().getFirst().claimed());
                try(var c=f.db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery("SELECT used FROM user_coupons")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void committedClaimWithLostOrCancelledReplyIsRecoveredByReadingAndNeverAutomaticallyReplayed(boolean cancel) throws Exception {
        try(var f=new Fixture(folder.resolve("lost.db"));var proxy=new ServerSocket(0)) {
            var committed=new CountDownLatch(1);var release=new CountDownLatch(1);var claims=new AtomicInteger();
            try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
                workers.submit(() -> {
                    try(var downstream=proxy.accept();var upstream=new Socket("127.0.0.1",f.server.port())) {
                        while(true) {
                            var request=FrameCodec.read(downstream,5000);if(request==null)return;
                            FrameCodec.write(upstream.getOutputStream(),request);var reply=FrameCodec.read(upstream,5000);
                            if(request.get("operation").getAsInt()==Messages.CLAIM_COUPON){claims.incrementAndGet();committed.countDown();if(cancel)release.await();return;}
                            FrameCodec.write(downstream.getOutputStream(),reply);
                        }
                    } catch(Exception ex){throw new RuntimeException(ex);}
                });
                var updates=new LinkedBlockingQueue<ClientController.State>();
                try(var client=f.client(proxy.getLocalPort());var controller=new ClientController(client,updates::add)) {
                    SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));await(updates,s -> !s.busy() && s.identity()!=null);
                    SwingUtilities.invokeAndWait(controller::refreshCart);await(updates,ClientController.State::canModifyCart);
                    SwingUtilities.invokeAndWait(() -> controller.claimCoupon(f.campaign));assertTrue(committed.await(5,TimeUnit.SECONDS));
                    if(cancel){SwingUtilities.invokeAndWait(controller::cancel);release.countDown();}
                    var unknown=await(updates,s -> !s.busy() && s.coupons().message().contains("未确认"));assertNull(unknown.coupons().quote());assertEquals(1,claims.get());
                    try(var direct=f.client()) {
                        direct.login("buyer_a","BuyerA#2026!".toCharArray(),() -> false);
                        var page=direct.listCoupons("OWNED",20,0,() -> false);assertEquals(1,page.items().size());
                        assertEquals(page.items().getFirst().couponId(),direct.claimCoupon(f.campaign,() -> false).couponId());
                    }
                    assertEquals(1,f.coupons.adminPage(f.admin,0).items().getFirst().claimed());
                } finally {release.countDown();}
            }
        }
    }
    private static void click(Container view,String key) throws Exception {SwingUtilities.invokeAndWait(() -> {var b=(JButton)named(view,key);assertTrue(b.isEnabled(),key);b.doClick();});}
    private static ClientController.State await(BlockingQueue<ClientController.State> queue,Predicate<ClientController.State> predicate) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<deadline){var value=queue.poll(50,TimeUnit.MILLISECONDS);if(value!=null && predicate.test(value))return value;}
        throw new AssertionError("Timed out waiting for coupon state");
    }
    private static Component named(Container parent,String name) {
        var value=find(parent,name);assertNotNull(value,name);return value;
    }
    private static Component find(Container parent,String name) {
        for(var child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container c){var v=find(c,name);if(v!=null)return v;}}return null;
    }
}
