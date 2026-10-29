package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(25)
class TransactionConcurrencyTest {
    @TempDir Path folder;
    @RepeatedTest(3) void tenTcpBuyersHaveExactlyFiveSuccessesAndFiveUnconsumedCarts() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("stock.db"))){
            f.sql("UPDATE products SET stock=5 WHERE product_id=1001");var tokens=new ArrayList<String>();var commands=new ArrayList<CheckoutRequest>();
            for(int i=10;i<20;i++){String token=f.addBuyer(i);tokens.add(token);var cart=f.carts.add(i,1001,1,0);commands.add(f.request(token,cart,null,CartSelection.all(cart)));}
            f.start();var work=new ArrayList<Callable<CheckoutReceipt>>();for(int i=0;i<10;i++){int j=i;work.add(() -> f.checkout(tokens.get(j),commands.get(j)));}
            var results=TransactionConcurrencyTest.<CheckoutReceipt>concurrently(work);assertEquals(5,results.stream().filter(r -> r.state().equals("SUCCESS")).count());assertEquals(5,results.stream().filter(r -> "OUT_OF_STOCK".equals(r.failureCode())).count());
            for(int i=0;i<10;i++){
                boolean success=results.get(i).order()!=null;assertEquals(results.get(i),f.orders.byKey(tokens.get(i),commands.get(i).checkoutKey()));
                assertEquals(success?2:1,f.carts.list(i+10).cartVersion());assertEquals(success?0:1,f.carts.list(i+10).items().size());
            }
            assertEquals(0,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(5,f.value("SELECT count(*) FROM orders"));assertEquals(5,f.value("SELECT sum(quantity) FROM order_items"));assertEquals(5,f.value("SELECT count(*) FROM cart_items"));assertEquals(10,f.value("SELECT count(*) FROM checkout_attempts"));f.assertConsistent();
        }
    }
    @Test void sixTcpReplaysReturnSameOrderAndSameKeyIsScopedToEachBuyer() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("keys.db"))){
            var cart=f.carts.add(1,1001,2,0);long coupon=f.coupon();var command=f.request(f.buyer,cart,coupon,CartSelection.all(cart));f.start();
            var results=TransactionConcurrencyTest.<CheckoutReceipt>concurrently(Collections.nCopies(6,() -> f.checkout(f.buyer,command)));var result=results.getFirst();for(var r:results)assertEquals(result,r);
            assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(1,f.value("SELECT used FROM user_coupons"));assertEquals(result.order().orderId(),f.value("SELECT used_order_id FROM user_coupons"));
            var changed=new CheckoutRequest(command.checkoutKey(),command.cartVersion(),"b".repeat(64),coupon,command.itemIds());assertEquals("IDEMPOTENCY_CONFLICT",f.exchange(Messages.CHECKOUT,f.buyer,TransactionFixture.JSON.toJsonTree(changed).getAsJsonObject()).get("code").getAsString());
            var otherCart=f.carts.add(2,1002,1,0);var q=f.coupons.quote(f.other,1,null);var otherRequest=new CheckoutRequest(command.checkoutKey(),1,q.digest(),null,CartSelection.all(otherCart));var other=f.checkout(f.other,otherRequest);assertEquals("SUCCESS",other.state());assertNotEquals(result.order().orderId(),other.order().orderId());
            assertEquals(2,f.value("SELECT count(*) FROM orders"));assertEquals(14,f.value("SELECT stock FROM products WHERE product_id=1002"));f.assertConsistent();
        }
    }
    @Test void differentKeysForTwoSubsetsCannotConsumeOneCouponTwiceOrSilentlyDropIt() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("coupon.db"))){
            f.carts.add(1,1001,1,0);var cart=f.carts.add(1,1002,1,1);long coupon=f.coupon();var one=f.request(f.buyer,cart,coupon,List.of(cart.items().getFirst().itemId()));var two=f.request(f.buyer,cart,coupon,List.of(cart.items().getLast().itemId()));f.start();
            var results=TransactionConcurrencyTest.<CheckoutReceipt>concurrently(List.of(() -> f.checkout(f.buyer,one),() -> f.checkout(f.buyer,two)));assertEquals(1,results.stream().filter(r -> r.order()!=null).count());assertEquals(1,results.stream().filter(r -> "CART_CHANGED".equals(r.failureCode())).count());
            var remaining=f.carts.list(1);assertEquals(3,remaining.cartVersion());assertEquals(1,remaining.items().size());var denied=f.checkout(f.buyer,f.request(f.buyer,remaining,coupon,CartSelection.all(remaining)));assertEquals("COUPON_INVALID",denied.failureCode());
            var order=results.stream().filter(r -> r.order()!=null).findFirst().orElseThrow().order();assertEquals(coupon,order.coupon().couponId());assertEquals(order.orderId(),f.value("SELECT used_order_id FROM user_coupons"));assertEquals(1,f.value("SELECT count(*) FROM orders"));assertEquals(1,f.value("SELECT sum(quantity) FROM order_items"));assertEquals(34,f.value("SELECT sum(stock) FROM products WHERE product_id IN(1001,1002)"));f.assertConsistent();
        }
    }
    @RepeatedTest(3) void cartEditAndCheckoutAtSameVersionHaveOnlyOneWinner() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("edit.db"))){
            var cart=f.carts.add(1,1001,1,0);var command=f.request(f.buyer,cart,null,CartSelection.all(cart));f.start();var edit=new com.google.gson.JsonObject();edit.addProperty("action","SET");edit.addProperty("itemId",command.itemIds().getFirst());edit.addProperty("quantity",2);edit.addProperty("cartVersion",1);
            var go=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
                var purchase=pool.submit(() -> {go.await();return f.checkout(f.buyer,command);});var change=pool.submit(() -> {go.await();return f.exchange(Messages.EDIT_CART,f.buyer,edit);});go.countDown();var receipt=purchase.get(10,TimeUnit.SECONDS);var edited=change.get(10,TimeUnit.SECONDS);
                if(receipt.order()!=null){assertEquals("CART_CHANGED",edited.get("code").getAsString());assertTrue(f.carts.list(1).items().isEmpty());assertEquals(19,f.value("SELECT stock FROM products WHERE product_id=1001"));}
                else{assertEquals("CART_CHANGED",receipt.failureCode());assertEquals("OK",edited.get("code").getAsString());assertEquals(2,f.carts.list(1).items().getFirst().quantity());assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));}
                assertEquals(2,f.carts.list(1).cartVersion());assertEquals(receipt,f.checkout(f.buyer,command));assertEquals(receipt.order()==null?0:1,f.value("SELECT count(*) FROM orders"));f.assertConsistent();
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"BEGIN","COMMIT"})
    void busyAtReservationOrCommitReturnsUnknownWithoutPartialEffectsThenOriginalKeySucceeds(String stage) throws Exception {
        try(var f=new TransactionFixture(folder.resolve("busy.db"))){
            var cart=f.carts.add(1,1001,2,0);long coupon=f.coupon();var command=f.request(f.buyer,cart,coupon,CartSelection.all(cart));f.start();
            try(var lock=stage.equals("BEGIN")?f.db.openImmediateConnection():f.db.openReadOnlyConnection()){
                lock.setAutoCommit(false);if(stage.equals("COMMIT"))try(var s=lock.createStatement();var r=s.executeQuery("SELECT stock FROM products")){assertTrue(r.next());}
                long start=System.nanoTime();var response=f.exchange(Messages.CHECKOUT,f.buyer,TransactionFixture.JSON.toJsonTree(command).getAsJsonObject());long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                assertEquals("RESULT_UNKNOWN",response.get("code").getAsString());assertTrue(elapsed<7000,"Busy wait must be bounded");lock.rollback();
            }
            assertEquals("NOT_FOUND",f.orders.byKey(f.buyer,command.checkoutKey()).state());assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(0,f.value("SELECT count(*) FROM order_items"));assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT used FROM user_coupons"));assertEquals(cart,f.carts.list(1));
            var done=f.checkout(f.buyer,command);assertEquals("SUCCESS",done.state());assertEquals(done,f.checkout(f.buyer,command));assertEquals(18,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(done.order().orderId(),f.value("SELECT used_order_id FROM user_coupons"));f.assertConsistent();
        }
    }
    @ParameterizedTest @ValueSource(strings={"END","REPLACED","UNSELECTED"})
    void promotionBoundaryAndConfigurationChangesAreCheckedInsideCheckout(String change) throws Exception {
        try(var f=new TransactionFixture(folder.resolve("promotion.db"))){
            var manager=new cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService(f.db,f.sessions);var p=new PromotionConfiguration(1001,0,new PromotionTerms(100,900),f.clock.instant(),f.clock.instant().plusSeconds(60),true);var saved=manager.save(f.admin,p);
            var cart=f.carts.add(1,1001,1,0);cart=f.carts.add(1,1002,1,1);var command=f.request(f.buyer,cart,null,List.of(cart.items().getFirst().itemId()));f.start();
            switch(change){case "END" -> f.clock.advance(Duration.ofSeconds(60));case "REPLACED" -> manager.save(f.admin,new PromotionConfiguration(1001,saved.configuration().version(),p.terms(),p.startsAt(),p.endsAt(),true));case "UNSELECTED" -> manager.save(f.admin,new PromotionConfiguration(1002,0,new PromotionTerms(1,999),p.startsAt(),p.endsAt(),true));default -> fail();}
            var result=f.checkout(f.buyer,command);if(change.equals("UNSELECTED")){assertEquals("SUCCESS",result.state());assertEquals(19,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(1,f.carts.list(1).items().size());}
            else{assertEquals("PRICE_CHANGED",result.failureCode());assertEquals(20,f.value("SELECT stock FROM products WHERE product_id=1001"));assertEquals(0,f.value("SELECT count(*) FROM orders"));assertEquals(2,f.carts.list(1).items().size());}
            assertEquals(15,f.value("SELECT stock FROM products WHERE product_id=1002"));f.assertConsistent();
        }
    }
    private static <T> List<T> concurrently(List<Callable<T>> actions) throws Exception {
        var go=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(actions.size())){var futures=new ArrayList<Future<T>>();for(var action:actions)futures.add(pool.submit(() -> {go.await();return action.call();}));go.countDown();var results=new ArrayList<T>();for(var future:futures)results.add(future.get(15,TimeUnit.SECONDS));return results;}
    }
}
