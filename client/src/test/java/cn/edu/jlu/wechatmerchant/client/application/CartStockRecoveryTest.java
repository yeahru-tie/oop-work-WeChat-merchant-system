package cn.edu.jlu.wechatmerchant.client.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class CartStockRecoveryTest {
    @ParameterizedTest
    @ValueSource(strings={"disconnect","expired","cancel"})
    void failedExpiredOrCancelledRecoveryNeverRestoresUnverifiedCartOrReplaysAdd(String mode) throws Exception {
        var updates=new LinkedBlockingQueue<ClientController.State>();
        var last=new AtomicReference<ClientController.State>();
        try (var peer=new Peer(mode);
                var client=new MerchantClient("127.0.0.1",peer.listener.getLocalPort(),1000,3000);
                var controller=new ClientController(client,state -> {
                    assertTrue(SwingUtilities.isEventDispatchThread()); last.set(state); updates.add(state);
                })) {
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a","demo-password".toCharArray()));
            await(updates,s -> !s.busy() && s.identity()!=null);
            SwingUtilities.invokeAndWait(controller::refreshCart); await(updates,ClientController.State::canModifyCart);
            SwingUtilities.invokeAndWait(() -> controller.addToCart(1001,999));
            assertTrue(peer.recovering.await(3,TimeUnit.SECONDS));
            if (mode.equals("cancel")) SwingUtilities.invokeAndWait(controller::cancel);
            var terminal=await(updates,s -> !s.busy() && !s.canModifyCart());
            assertNull(terminal.cart().contents());
            if (mode.equals("expired")) {
                assertNull(terminal.identity()); assertEquals(ClientController.CartStatus.GUEST,terminal.cart().status());
            } else {
                assertNotNull(terminal.identity()); assertEquals(ClientController.CartStatus.ERROR,terminal.cart().status());
            }
            peer.task.get(4,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> {});
            assertSame(terminal,last.get());
            assertEquals(1,peer.adds.get()); assertEquals(2,peer.reads.get());
        }
    }

    private static ClientController.State await(BlockingQueue<ClientController.State> updates,Predicate<ClientController.State> predicate) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime()<deadline) {
            var state=updates.poll(100,TimeUnit.MILLISECONDS);
            if (state!=null && predicate.test(state)) return state;
        }
        throw new AssertionError("Controller state timed out");
    }

    private static final class Peer implements AutoCloseable {
        final ServerSocket listener;
        final ExecutorService worker=Executors.newVirtualThreadPerTaskExecutor();
        final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        final CountDownLatch recovering=new CountDownLatch(1);
        final AtomicInteger adds=new AtomicInteger(),reads=new AtomicInteger();
        final Future<?> task;
        Peer(String mode) throws Exception {
            listener=new ServerSocket(0,4,java.net.InetAddress.getLoopbackAddress());
            listener.setSoTimeout(5000);
            task=worker.submit(() -> {
                try {
                    try (Socket first=accept()) {
                        var login=read(first,Messages.LOGIN);
                        var identity=new JsonObject(); identity.addProperty("userId",1);
                        identity.addProperty("username","buyer_a"); identity.addProperty("displayName","买家 A"); identity.addProperty("role","BUYER");
                        FrameCodec.write(first.getOutputStream(),Messages.response(login,"OK","ok",identity,"A".repeat(43)));
                        var list=read(first,Messages.LIST_CART); replyCart(first,list);
                        var add=read(first,Messages.ADD_TO_CART);
                        FrameCodec.write(first.getOutputStream(),Messages.response(add,"OUT_OF_STOCK","库存不足",new JsonObject()));
                        assertEquals(-1,first.getInputStream().read());
                    }
                    try (Socket second=accept()) {
                        var refresh=read(second,Messages.LIST_CART);
                        recovering.countDown();
                        if (mode.equals("expired")) {
                            FrameCodec.write(second.getOutputStream(),Messages.response(refresh,"UNAUTHENTICATED","已失效",new JsonObject()));
                        } else if (mode.equals("cancel")) {
                            assertEquals(-1,second.getInputStream().read());
                            try { replyCart(second,refresh); } catch (java.io.IOException cancelled) { }
                        }
                        // Disconnect mode intentionally returns no recovery response.
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
        private Socket accept() throws Exception {
            Socket socket=listener.accept(); socket.setSoTimeout(5000); sockets.add(socket); return socket;
        }
        private Messages.Request read(Socket socket,int operation) throws Exception {
            var request=Messages.parseRequest(FrameCodec.read(socket,3000));
            assertEquals(operation,request.operation());
            if (operation==Messages.ADD_TO_CART) adds.incrementAndGet();
            if (operation==Messages.LIST_CART) reads.incrementAndGet();
            return request;
        }
        private static void replyCart(Socket socket,Messages.Request request) throws Exception {
            var body=new Gson().toJsonTree(new CartSnapshot(0,List.of())).getAsJsonObject();
            FrameCodec.write(socket.getOutputStream(),Messages.response(request,"OK","ok",body));
        }
        @Override public void close() throws Exception {
            listener.close(); for (var socket:sockets) socket.close(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));
        }
    }
}
