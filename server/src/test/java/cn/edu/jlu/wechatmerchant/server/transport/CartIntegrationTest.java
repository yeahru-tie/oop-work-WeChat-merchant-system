package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.client.transport.RemoteServiceException;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import com.google.gson.JsonObject;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class CartIntegrationTest {
    @TempDir Path directory;
    private Fixture fixture() throws Exception { return new Fixture(directory.resolve("shop.db")); }
    private static final class Fixture implements AutoCloseable {
        final SqliteDatabase database;
        final MutableClock clock=new MutableClock();
        final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
        final ProbeServer server;
        Fixture(Path file) throws Exception {
            database=new SqliteDatabase(file); database.initialize();
            var identities=new IdentityHandler(new AuthenticationService(new UserDao(database)),sessions);
            server=new ProbeServer(0,new ProductQueryHandler(new ProductCatalog(new ProductDao(database))),identities,
                    new CartHandler(new CartService(database),identities)); server.start();
        }
        MerchantClient client() { return new MerchantClient("127.0.0.1",server.port(),1000,5000); }
        @Override public void close() { server.close(); sessions.clear(); }
    }
    private static void login(MerchantClient client,String buyer) throws Exception {
        client.login("buyer_"+buyer,(buyer.equals("a") ? "BuyerA#2026!" : "BuyerB#2026!").toCharArray(),() -> false);
    }
    private static JsonObject exchange(Fixture fixture,int operation,String token,JsonObject body) throws Exception {
        try(Socket socket=new Socket("127.0.0.1",fixture.server.port())) {
            FrameCodec.write(socket.getOutputStream(),Messages.authenticatedRequest("cart-test",operation,token,body));
            return FrameCodec.read(socket,5000);
        }
    }
    private static JsonObject addBody(long product,int quantity,long version) {
        var body=new JsonObject(); body.addProperty("productId",product); body.addProperty("quantity",quantity); body.addProperty("cartVersion",version); return body;
    }
    @Test void realBuyersAreIsolatedAndCartSurvivesLogoutReconnectAndServerRestart() throws Exception {
        CartSnapshot saved;
        try(var fixture=fixture(); var a=fixture.client(); var b=fixture.client()) {
            assertEquals(3,a.listProducts(20,0,() -> false).items().size());
            assertEquals("UNAUTHENTICATED",assertThrows(RemoteServiceException.class,() -> a.listCart(() -> false)).code());
            login(a,"a"); login(b,"b");
            var first=a.addToCart(1001,2,0,() -> false);
            var merged=a.addToCart(1001,1,first.cartVersion(),() -> false);
            assertEquals(3,merged.items().getFirst().quantity());
            assertTrue(b.listCart(() -> false).items().isEmpty());
            assertEquals("NOT_FOUND",assertThrows(RemoteServiceException.class,
                    () -> b.editCart(merged.items().getFirst().itemId(),null,0,() -> false)).code());
            assertEquals(merged,a.listCart(() -> false));
            saved=a.editCart(merged.items().getFirst().itemId(),2,2,() -> false);
            a.disconnect(); assertEquals(saved,a.listCart(() -> false));
            a.logout(() -> false); login(a,"a"); assertEquals(saved,a.listCart(() -> false));
            assertEquals(3,a.listProducts(20,0,() -> false).items().size());
        }
        try(var restarted=fixture(); var client=restarted.client()) {
            assertEquals("UNAUTHENTICATED",assertThrows(RemoteServiceException.class,() -> client.listCart(() -> false)).code());
            login(client,"a"); assertEquals(saved,client.listCart(() -> false));
            assertTrue(client.editCart(saved.items().getFirst().itemId(),null,saved.cartVersion(),() -> false).items().isEmpty());
        }
    }
    @Test void invalidFieldsQuantitiesActionsAndForgedOwnersAreRejectedWithoutWrites() throws Exception {
        try(var fixture=fixture()) {
            var reply=exchange(fixture,Messages.LOGIN,null,Messages.login("id","buyer_a","BuyerA#2026!".toCharArray()).getAsJsonObject("body"));
            String token=reply.get("sessionToken").getAsString();
            for(String problem:new String[]{"zero","negative","fraction","string","overflow","missing","owner","price","negativeVersion"}) {
                var body=addBody(1001,1,0);
                switch(problem) {
                    case "zero" -> body.addProperty("quantity",0); case "negative" -> body.addProperty("quantity",-1);
                    case "fraction" -> body.addProperty("quantity",1.2); case "string" -> body.addProperty("quantity","1");
                    case "overflow" -> body.addProperty("quantity",2147483648L); case "missing" -> body.remove("productId");
                    case "owner" -> body.addProperty("userId",2); case "price" -> body.addProperty("priceCents",1);
                    case "negativeVersion" -> body.addProperty("cartVersion",-1);
                }
                assertEquals("INVALID_ARGUMENT",exchange(fixture,Messages.ADD_TO_CART,token,body).get("code").getAsString(),problem);
            }
            var query=new JsonObject(); query.addProperty("userId",2);
            assertEquals("INVALID_ARGUMENT",exchange(fixture,Messages.LIST_CART,token,query).get("code").getAsString());
            var remove=new JsonObject(); remove.addProperty("itemId",1); remove.addProperty("action","REMOVE");
            remove.addProperty("cartVersion",0); remove.addProperty("quantity",0);
            assertEquals("INVALID_ARGUMENT",exchange(fixture,Messages.EDIT_CART,token,remove).get("code").getAsString());
            remove.remove("quantity"); remove.addProperty("action","DELETE_ALL");
            assertEquals("INVALID_ARGUMENT",exchange(fixture,Messages.EDIT_CART,token,remove).get("code").getAsString());
            assertEquals(new CartSnapshot(0,java.util.List.of()),new CartService(fixture.database).list(1));
        }
    }
    @Test void cartEndpointsRejectWrongRolesForgedExpiredAndDisabledSessions() throws Exception {
        try(var fixture=fixture()) {
            String admin=fixture.sessions.create(new UserDao(fixture.database).findByUsername("shop_admin").orElseThrow().identity());
            for(int operation:new int[]{Messages.LIST_CART,Messages.ADD_TO_CART,Messages.EDIT_CART}) {
                assertEquals("FORBIDDEN",exchange(fixture,operation,admin,new JsonObject()).get("code").getAsString());
                assertEquals("UNAUTHENTICATED",exchange(fixture,operation,"x".repeat(43),new JsonObject()).get("code").getAsString());
            }
            try(var client=fixture.client()) {
                login(client,"a"); fixture.clock.advance(Duration.ofMinutes(30));
                assertEquals("UNAUTHENTICATED",assertThrows(RemoteServiceException.class,() -> client.listCart(() -> false)).code());
                assertTrue(client.identity().isEmpty()); login(client,"a");
                try(var c=fixture.database.openWriteConnection(); var s=c.createStatement()) { s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=1"); }
                assertEquals("UNAUTHENTICATED",assertThrows(RemoteServiceException.class,() -> client.addToCart(1001,1,0,() -> false)).code());
                assertTrue(client.identity().isEmpty());
            }
        }
    }
    @Test void currentPriceUsesExactAmountsAndAddingNeverDecrementsStock() throws Exception {
        try(var fixture=fixture(); var client=fixture.client()) {
            try(var c=fixture.database.openWriteConnection(); var s=c.createStatement()) { s.executeUpdate("UPDATE products SET price_cents=9223372036854775807,stock=3 WHERE product_id=1001"); }
            login(client,"a"); var result=client.addToCart(1001,2,0,() -> false);
            assertEquals("18446744073709551614",result.totalCents().toString()); assertEquals(3,result.items().getFirst().stock());
            assertEquals(3,client.listProducts(20,0,() -> false).items().getFirst().stock());
        }
    }
    @Test void controllerClearsPrivateRowsImmediatelyOnLogoutAndReloadsOnlyTheNewBuyer() throws Exception {
        var updates=new LinkedBlockingQueue<ClientController.State>();
        try(var fixture=fixture(); var client=fixture.client(); var controller=new ClientController(client,state -> {
            assertTrue(SwingUtilities.isEventDispatchThread()); updates.add(state);
        })) {
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));
            await(updates,s -> !s.busy() && s.identity()!=null);
            SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,s -> s.canModifyCart());
            SwingUtilities.invokeAndWait(() -> controller.addToCart(1001,2));
            await(updates,s -> s.canModifyCart() && !s.cart().contents().items().isEmpty());
            SwingUtilities.invokeAndWait(() -> { controller.logout(); assertNull(controller.state().identity()); assertNull(controller.state().cart().contents()); });
            await(updates,s -> !s.busy() && s.identity()==null);
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_b","BuyerB#2026!".toCharArray()));
            await(updates,s -> !s.busy() && s.identity()!=null);
            SwingUtilities.invokeAndWait(controller::refreshCart);
            assertTrue(await(updates,s -> s.canModifyCart()).cart().contents().items().isEmpty());
            fixture.clock.advance(Duration.ofMinutes(30));
            SwingUtilities.invokeAndWait(controller::refreshCart);
            var expired=await(updates,s -> !s.busy() && s.identity()==null);
            assertEquals(ClientController.CartStatus.GUEST,expired.cart().status()); assertNull(expired.cart().contents());
        }
    }
    @Test void busyDatabaseGivesAnExplicitRetryErrorWithoutChangingTheCart() throws Exception {
        try(var fixture=fixture(); var client=fixture.client()) {
            login(client,"a");
            try(var lock=fixture.database.openImmediateConnection()) {
                lock.setAutoCommit(false);
                assertEquals("BUSY_RETRY",assertThrows(RemoteServiceException.class,() -> client.addToCart(1001,1,0,() -> false)).code());
                lock.rollback();
            }
            assertEquals(new CartSnapshot(0,java.util.List.of()),client.listCart(() -> false));
        }
    }
    @Test void lostCommittedReplyIsUncertainAndRefreshDoesNotReplayTheAdd() throws Exception {
        var updates=new LinkedBlockingQueue<ClientController.State>();
        try(var fixture=fixture(); var proxy=new ReplyGate(fixture.server.port(),false);
                var client=new MerchantClient("127.0.0.1",proxy.port(),1000,2000);
                var controller=new ClientController(client,updates::add)) {
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));
            await(updates,s -> !s.busy() && s.identity()!=null);
            SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,s -> s.canModifyCart());
            SwingUtilities.invokeAndWait(() -> controller.addToCart(1001,2));
            var uncertain=await(updates,s -> !s.busy() && s.cart().status()==ClientController.CartStatus.UNCERTAIN);
            assertNull(uncertain.cart().contents()); assertFalse(uncertain.canModifyCart());
            SwingUtilities.invokeAndWait(() -> controller.addToCart(1001,2));
            SwingUtilities.invokeAndWait(controller::refreshCart);
            var restored=await(updates,s -> s.canModifyCart());
            assertEquals(2,restored.cart().contents().items().getFirst().quantity()); assertEquals(1,restored.cart().contents().cartVersion());
        }
    }
    @Test void cancelAfterCommitDiscardsLateReplyAndRequiresARefresh() throws Exception {
        var updates=new LinkedBlockingQueue<ClientController.State>();
        try(var fixture=fixture(); var proxy=new ReplyGate(fixture.server.port(),true);
                var client=new MerchantClient("127.0.0.1",proxy.port(),1000,5000);
                var controller=new ClientController(client,updates::add)) {
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","BuyerA#2026!".toCharArray()));
            await(updates,s -> !s.busy() && s.identity()!=null);
            SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,s -> s.canModifyCart());
            SwingUtilities.invokeAndWait(() -> controller.addToCart(1001,2));
            assertTrue(proxy.committed.await(4,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(controller::cancel);
            assertEquals(ClientController.CartStatus.UNCERTAIN,await(updates,s -> !s.busy()).cart().status());
            proxy.release.countDown();
            SwingUtilities.invokeAndWait(controller::refreshCart);
            assertEquals(2,await(updates,s -> s.canModifyCart()).cart().contents().items().getFirst().quantity());
        }
    }
    private static ClientController.State await(BlockingQueue<ClientController.State> updates,Predicate<ClientController.State> predicate) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            var state=updates.poll(100,TimeUnit.MILLISECONDS);
            if (state!=null && predicate.test(state)) return state;
        }
        throw new AssertionError("Controller state timed out");
    }
    /** Real TCP proxy: lets SQLite commit, then drops/delays only the first add response. */
    private static final class ReplyGate implements AutoCloseable {
        final ServerSocket listener;
        final ExecutorService tasks=Executors.newVirtualThreadPerTaskExecutor();
        final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        final AtomicBoolean intercepted=new AtomicBoolean();
        final CountDownLatch committed=new CountDownLatch(1),release=new CountDownLatch(1);
        ReplyGate(int backend,boolean delay) throws Exception {
            listener=new ServerSocket(0,8,java.net.InetAddress.getLoopbackAddress());
            tasks.submit(() -> {
                while(!listener.isClosed()) {
                    try {
                        Socket front=listener.accept(); sockets.add(front);
                        tasks.submit(() -> {
                            try(front; Socket back=new Socket("127.0.0.1",backend)) {
                                sockets.add(back);
                                while(true) {
                                    var request=FrameCodec.read(front,5000); if(request==null) return;
                                    FrameCodec.write(back.getOutputStream(),request);
                                    var reply=FrameCodec.read(back,5000);
                                    if(request.get("operation").getAsInt()==Messages.ADD_TO_CART && intercepted.compareAndSet(false,true)) {
                                        committed.countDown(); if(delay) release.await(5,TimeUnit.SECONDS); return;
                                    }
                                    FrameCodec.write(front.getOutputStream(),reply);
                                }
                            } catch(Exception expectedDisconnect) { } finally { sockets.remove(front); }
                        });
                    } catch(Exception stopped) { return; }
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        @Override public void close() throws Exception { release.countDown(); listener.close(); for(var socket:sockets) socket.close(); tasks.shutdownNow(); assertTrue(tasks.awaitTermination(3,TimeUnit.SECONDS)); }
    }
}
