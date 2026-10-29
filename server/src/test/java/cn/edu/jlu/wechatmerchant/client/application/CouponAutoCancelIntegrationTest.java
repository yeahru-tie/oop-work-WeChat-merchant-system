package cn.edu.jlu.wechatmerchant.client.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.CouponCampaign;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.net.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class CouponAutoCancelIntegrationTest {
    @TempDir Path folder;
    @ParameterizedTest @ValueSource(strings={"EXPIRED","NOT_FOUND","EMPTY_SELECTION"})
    void knownInvalidCouponAutomaticallyClearsSelectionAndExplainsRequote(String reason) throws Exception {
        try(var f=new TransactionFixture(folder.resolve("cancel.db"))){
            f.carts.add(1,1001,2,0);long coupon=coupon(f,5000);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,queue::add)){
                var applied=loginAndApply(c,queue,coupon);assertEquals("2480",applied.coupons().quote().payableCents().toString());
                switch(reason){case "EXPIRED" -> {f.clock.advance(Duration.ofSeconds(5));SwingUtilities.invokeAndWait(c::refreshCart);}case "NOT_FOUND" -> SwingUtilities.invokeAndWait(() -> c.selectCoupon(Long.MAX_VALUE));case "EMPTY_SELECTION" -> SwingUtilities.invokeAndWait(() -> c.selectCartItem(applied.selectedIds().getFirst(),false));default -> fail();}
                var none=await(queue,s -> !s.busy() && s.coupons().selectedCoupon()==null && s.coupons().quote()!=null && s.coupons().message().contains("自动取消"));
                assertNull(none.coupons().quote().couponId());assertNull(none.orders().confirmation());
                if(reason.equals("EMPTY_SELECTION")){assertNull(none.coupons().quote().payableCents());assertTrue(none.coupons().message().contains("勾选"));SwingUtilities.invokeAndWait(() -> c.selectCartItem(applied.selectedIds().getFirst(),true));var again=await(queue,s -> !s.busy() && !s.selectedIds().isEmpty() && s.coupons().quote()!=null);assertNull(again.coupons().selectedCoupon());}
                else{assertEquals("2580",none.coupons().quote().payableCents().toString());assertTrue(none.coupons().message().contains(reason.equals("EXPIRED")?"已过期":"不属于"));}
                assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT used FROM user_coupons"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));f.assertConsistent();
            }
        }
    }
    @Test void preparingWithInvalidCouponShowsNewAmountAndRequiresAnotherUserAction() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("prepare.db"))){
            f.carts.add(1,1001,2,0);long coupon=coupon(f,5000);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,queue::add)){
                loginAndApply(c,queue,coupon);f.clock.advance(Duration.ofSeconds(5));SwingUtilities.invokeAndWait(c::prepareCheckout);var none=await(queue,s -> !s.busy() && s.coupons().message().contains("自动取消"));
                assertNull(none.orders().confirmation());assertEquals("2580",none.coupons().quote().payableCents().toString());SwingUtilities.invokeAndWait(c::confirmCheckout);assertEquals(0,f.value("SELECT count(*) FROM checkout_attempts"));
                SwingUtilities.invokeAndWait(c::prepareCheckout);var prepared=await(queue,s -> !s.busy() && s.orders().confirmation()!=null);assertNull(prepared.orders().confirmation().couponId());assertEquals("2580",prepared.orders().confirmation().payableCents().toString());assertEquals(0,f.value("SELECT count(*) FROM orders"));
            }
        }
    }
    @Test void knownTimeBoundaryClosesOldConfirmationAndRechecksOnceWithoutWriting() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("boundary.db"))){
            f.carts.add(1,1001,2,0);long coupon=coupon(f,500);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,queue::add)){
                loginAndApply(c,queue,coupon);SwingUtilities.invokeAndWait(c::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);
                f.clock.advance(Duration.ofMillis(500));Thread.sleep(600);SwingUtilities.invokeAndWait(() -> {c.recheckQuoteIfExpired();assertNull(c.state().orders().confirmation());assertTrue(c.state().busy());});
                var none=await(queue,s -> !s.busy() && s.coupons().quote()!=null && s.coupons().message().contains("自动取消"));assertNull(none.orders().confirmation());assertNull(none.coupons().selectedCoupon());
                SwingUtilities.invokeAndWait(() -> {c.recheckQuoteIfExpired();assertSame(none,c.state());});assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT count(*) FROM checkout_attempts"));f.assertConsistent();
            }
        }
    }
    @Test void invalidationDetectedOnlyAtSubmitNeverCreatesAnotherPurchaseAutomatically() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("submit.db"))){
            f.carts.add(1,1001,2,0);long coupon=coupon(f,5000);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,queue::add)){
                loginAndApply(c,queue,coupon);SwingUtilities.invokeAndWait(c::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);f.clock.advance(Duration.ofSeconds(5));SwingUtilities.invokeAndWait(c::confirmCheckout);
                var none=await(queue,s -> !s.busy() && s.orders().pending()==null && s.cart().status()==ClientController.CartStatus.READY && s.coupons().selectedCoupon()==null && s.coupons().quote()!=null);
                assertNull(none.orders().confirmation());assertTrue(none.orders().message().contains("本次未成交"));assertEquals("2580",none.coupons().quote().payableCents().toString());assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(1,f.value("SELECT count(*) FROM checkout_attempts WHERE state='FAILED'"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT used FROM user_coupons"));
                SwingUtilities.invokeAndWait(c::prepareCheckout);await(queue,s -> !s.busy() && s.orders().confirmation()!=null);SwingUtilities.invokeAndWait(c::confirmCheckout);await(queue,s -> !s.busy() && s.orders().detail()!=null && s.cart().status()==ClientController.CartStatus.READY);
                assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(2,f.value("SELECT count(*) FROM checkout_attempts"));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT used FROM user_coupons"));f.assertConsistent();
            }
        }
    }
    @Test void couponUsedByAnotherSessionIsCancelledWhenRemainingCartIsRefreshed() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("used.db"))){
            f.carts.add(1,1001,2,0);var cart=f.carts.add(1,1002,1,1);long coupon=coupon(f,5000);f.start();var queue=new LinkedBlockingQueue<ClientController.State>();
            try(var client=new MerchantClient("127.0.0.1",f.port(),1000,5000);var c=new ClientController(client,queue::add)){
                loginAndApply(c,queue,coupon);var order=f.orders.checkout(f.buyer,f.request(f.buyer,cart,coupon,List.of(cart.items().getFirst().itemId()))).order();SwingUtilities.invokeAndWait(c::refreshCart);
                var none=await(queue,s -> !s.busy() && s.coupons().message().contains("已使用") && s.coupons().selectedCoupon()==null);assertEquals("2590",none.coupons().quote().payableCents().toString());assertEquals(order.orderId(),f.value("SELECT used_order_id FROM user_coupons"));assertEquals(1,f.value("SELECT count(*) FROM orders"));f.assertConsistent();
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void brokenQuoteClearsOldPayableButOnlyKnownIneligibilityCancelsCoupon(boolean invalidKnown) throws Exception {
        try(var f=new TransactionFixture(folder.resolve("broken.db"))){
            f.carts.add(1,1001,2,0);long coupon=coupon(f,5000);f.start();try(var proxy=new QuoteFailureProxy(f.port())){
                var queue=new LinkedBlockingQueue<ClientController.State>();try(var client=new MerchantClient("127.0.0.1",proxy.port(),1000,5000);var c=new ClientController(client,queue::add)){
                    loginAndApply(c,queue,coupon);if(invalidKnown)f.clock.advance(Duration.ofSeconds(5));proxy.fail.set(invalidKnown?"NONE":"ANY");SwingUtilities.invokeAndWait(c::refreshQuote);
                    var broken=await(queue,s -> !s.busy() && s.coupons().quote()==null);assertNull(broken.orders().confirmation());if(invalidKnown){assertNull(broken.coupons().selectedCoupon());assertTrue(broken.coupons().message().contains("自动取消"));}else{assertEquals(coupon,broken.coupons().selectedCoupon());assertFalse(broken.coupons().message().contains("自动取消"));}
                    SwingUtilities.invokeAndWait(c::refreshQuote);var recovered=await(queue,s -> !s.busy() && s.coupons().quote()!=null);assertEquals(invalidKnown?"2580":"2480",recovered.coupons().quote().payableCents().toString());assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT used FROM user_coupons"));f.assertConsistent();
                }
            }
        }
    }
    private static long coupon(TransactionFixture f,long lifetime) throws Exception {var p=f.coupons.saveDraft(f.admin,new CouponCampaign(0,0,"自动取消测试券",0,100,f.clock.millis(),f.clock.millis()+lifetime,10,0,false,false),UUID.randomUUID().toString());f.coupons.publish(f.admin,p.campaignId(),p.version());return f.coupons.claim(f.buyer,p.campaignId()).couponId();}
    private static ClientController.State loginAndApply(ClientController c,BlockingQueue<ClientController.State> q,long coupon) throws Exception {SwingUtilities.invokeAndWait(() -> c.login("buyer_a","BuyerA#2026!".toCharArray()));await(q,s -> !s.busy() && s.identity()!=null);SwingUtilities.invokeAndWait(c::refreshCart);await(q,ClientController.State::canModifyCart);SwingUtilities.invokeAndWait(() -> c.selectCoupon(coupon));return await(q,s -> !s.busy() && s.coupons().quote()!=null && "APPLIED".equals(s.coupons().quote().couponStatus()));}
    private static ClientController.State await(BlockingQueue<ClientController.State> q,Predicate<ClientController.State> predicate) throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(System.nanoTime()<end){var s=q.poll(40,TimeUnit.MILLISECONDS);if(s!=null && predicate.test(s))return s;}throw new AssertionError("Expected coupon state not received");}
    private static final class QuoteFailureProxy implements AutoCloseable {
        final ServerSocket listener=new ServerSocket(0);final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();final AtomicReference<String> fail=new AtomicReference<>("");final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        QuoteFailureProxy(int port) throws Exception {workers.submit(() -> {while(!listener.isClosed()){try{var socket=listener.accept();sockets.add(socket);workers.submit(() -> forward(socket,port));}catch(IOException ex){if(!listener.isClosed())throw new RuntimeException(ex);}}});}
        int port(){return listener.getLocalPort();}
        private void forward(Socket socket,int port){try(socket;var upstream=new Socket("127.0.0.1",port)){sockets.add(upstream);try{while(true){var request=FrameCodec.read(socket,5000);if(request==null)return;String mode=fail.get();if(request.get("operation").getAsInt()==Messages.QUOTE_CART && (mode.equals("ANY") || mode.equals("NONE") && request.getAsJsonObject("body").get("couponId").isJsonNull()) && fail.compareAndSet(mode,""))return;FrameCodec.write(upstream.getOutputStream(),request);var reply=FrameCodec.read(upstream,5000);FrameCodec.write(socket.getOutputStream(),reply);}}finally{sockets.remove(upstream);}}catch(IOException ex){if(!listener.isClosed())throw new RuntimeException(ex);}finally{sockets.remove(socket);}}
        public void close() throws Exception {listener.close();for(var s:sockets)s.close();workers.shutdownNow();assertTrue(workers.awaitTermination(6,TimeUnit.SECONDS));}
    }
}
