package cn.edu.jlu.wechatmerchant.client.transport;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.protocol.ProductPage;
import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.protocol.ProtocolException;
import com.google.gson.JsonObject;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class MerchantClientTest {
    @Test
    void cancellingAfterLoginReplyBeforeEdtCallbackCannotRestoreIdentity() throws Exception {
        AtomicReference<ClientController.State> last = new AtomicReference<>();
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            FrameCodec.write(socket.getOutputStream(), loginReply(request, "BUYER"));
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 2000);
                ClientController controller = new ClientController(client, last::set)) {
            SwingUtilities.invokeAndWait(() -> {
                controller.login("buyer_a", "test-password".toCharArray());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                // Hold the EDT callback to exercise cancel after the transport has accepted the reply.
                while (client.identity().isEmpty() && System.nanoTime() < deadline) {
                    try { Thread.sleep(5); } catch (InterruptedException exception) { throw new AssertionError(exception); }
                }
                assertTrue(client.identity().isPresent());
                controller.cancel();
            });
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(client.identity().isEmpty());
            assertNull(last.get().identity());
            assertEquals("已取消登录", last.get().authMessage());
            peer.await();
        }
    }

    @Test
    void losingLogoutResponseStillClearsLocalIdentity() throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            FrameCodec.write(socket.getOutputStream(), loginReply(request, "BUYER"));
            request = FrameCodec.read(socket, 2000);
            assertEquals(Messages.LOGOUT, request.get("operation").getAsInt());
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 200)) {
            client.login("buyer_a", "test-password".toCharArray(), () -> false);
            assertTrue(client.identity().isPresent());
            assertThrows(SocketTimeoutException.class, () -> client.logout(() -> false));
            assertTrue(client.identity().isEmpty());
            peer.await();
        }
    }

    @Test
    void unexpectedAdminIdentityInBuyerLoginIsRejected() throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            FrameCodec.write(socket.getOutputStream(), loginReply(request, "ADMIN"));
        }); MerchantClient client = client(peer, 2000)) {
            assertThrows(ProtocolException.class, () -> client.login("buyer_a", "test-password".toCharArray(), () -> false));
            assertTrue(client.identity().isEmpty());
            peer.await();
        }
    }

    private static JsonObject loginReply(JsonObject request, String role) throws IOException {
        JsonObject identity = new JsonObject();
        identity.addProperty("userId", 1);
        identity.addProperty("username", "buyer_a");
        identity.addProperty("displayName", "演示买家 A");
        identity.addProperty("role", role);
        return Messages.response(Messages.parseRequest(request), "OK", "ok", identity, "A".repeat(43));
    }

    @Test
    void readsProductsAndPingOnTheSameConnectionWithExactCents() throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            assertEquals(Messages.LIST_PRODUCTS, request.get("operation").getAsInt());
            JsonObject body = productBody(20, 0, 1, false);
            body.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("priceCents", 9007199254740993L);
            FrameCodec.write(socket.getOutputStream(), Messages.response(Messages.parseRequest(request), "OK", "ok", body));
            reply(socket, FrameCodec.read(socket, 2000));
        }); MerchantClient client = client(peer, 2000)) {
            ProductPage page = client.listProducts(20, 0, () -> false);
            assertEquals(9007199254740993L, page.items().getFirst().priceCents());
            assertEquals("演示商品", page.items().getFirst().name());
            assertEquals("merchant-server", client.ping());
            peer.await();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "operation", "version", "type", "amount", "offset"})
    void productRequestsRejectInvalidRepliesAndCloseTheSocket(String problem) throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            JsonObject response = Messages.response(Messages.parseRequest(request), "OK", "ok", productBody(20, 0, 1, false));
            switch (problem) {
                case "id" -> response.addProperty("requestId", "different");
                case "operation" -> response.addProperty("operation", Messages.PONG);
                case "version" -> response.addProperty("version", 2);
                case "type" -> response.addProperty("type", "REQUEST");
                case "amount" -> response.getAsJsonObject("body").getAsJsonArray("items").get(0)
                        .getAsJsonObject().addProperty("priceCents", "12.90");
                case "offset" -> response.getAsJsonObject("body").addProperty("offset", 1);
                default -> fail("Unknown case");
            }
            FrameCodec.write(socket.getOutputStream(), response);
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 2000)) {
            assertThrows(ProtocolException.class, () -> client.listProducts(20, 0, () -> false));
            peer.await();
        }
    }

    @Test
    void productControllerCanCancelWithoutBlockingEdtThenRetryAnEmptyResult() throws Exception {
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean callbacksOnEdt = new AtomicBoolean(true);
        AtomicReference<ClientController.State> last = new AtomicReference<>();
        try (Peer peer = new Peer(2, socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            if (calls.getAndIncrement() == 0) {
                firstRead.countDown();
                assertEquals(-1, socket.getInputStream().read());
            } else {
                FrameCodec.write(socket.getOutputStream(), Messages.response(Messages.parseRequest(request), "OK", "ok",
                        productBody(20, 0, 0, false)));
            }
        }); MerchantClient client = client(peer, 5000);
                ClientController controller = new ClientController(client, state -> {
                    callbacksOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
                    last.set(state);
                    if (state.catalog().status() == ClientController.CatalogStatus.EMPTY) {
                        complete.countDown();
                    }
                })) {
            SwingUtilities.invokeAndWait(() -> {
                controller.refreshProducts();
                controller.refreshProducts();
                controller.testConnection();
                assertTrue(controller.state().busy());
                assertFalse(controller.state().canGoNext());
            });
            assertTrue(firstRead.await(2, TimeUnit.SECONDS));
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(responsive::countDown);
            assertTrue(responsive.await(1, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(controller::cancel);
            assertEquals(ClientController.CatalogStatus.CANCELLED, last.get().catalog().status());
            SwingUtilities.invokeAndWait(controller::refreshProducts);
            assertTrue(complete.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> { });
            assertEquals(ClientController.CatalogStatus.EMPTY, last.get().catalog().status());
            assertEquals("暂无商品", last.get().catalog().message());
            assertFalse(last.get().busy());
            assertFalse(last.get().canGoPrevious());
            assertFalse(last.get().canGoNext());
            assertEquals(2, calls.get(), "No queued duplicate, PING or automatic retry may be sent.");
            assertTrue(callbacksOnEdt.get());
            peer.await();
        }
    }

    @Test
    void failedNextPageClearsStaleItemsAndManualRetryKeepsItsOffset() throws Exception {
        CountDownLatch firstPage = new CountDownLatch(1);
        CountDownLatch failedPage = new CountDownLatch(1);
        CountDownLatch retriedPage = new CountDownLatch(1);
        AtomicInteger connections = new AtomicInteger();
        AtomicReference<ClientController.State> last = new AtomicReference<>();
        try (Peer peer = new Peer(2, socket -> {
            if (connections.getAndIncrement() == 0) {
                JsonObject request = FrameCodec.read(socket, 2000);
                FrameCodec.write(socket.getOutputStream(), Messages.response(Messages.parseRequest(request), "OK", "ok",
                        productBody(20, 0, 20, true)));
                request = FrameCodec.read(socket, 2000);
                assertEquals(20, request.getAsJsonObject("body").get("offset").getAsInt());
                FrameCodec.write(socket.getOutputStream(), Messages.response(Messages.parseRequest(request), "INTERNAL_ERROR",
                        "SQL password=do-not-show", new JsonObject()));
                assertEquals(-1, socket.getInputStream().read());
            } else {
                JsonObject request = FrameCodec.read(socket, 2000);
                assertEquals(20, request.getAsJsonObject("body").get("offset").getAsInt());
                FrameCodec.write(socket.getOutputStream(), Messages.response(Messages.parseRequest(request), "OK", "ok",
                        productBody(20, 20, 1, false)));
            }
        }); MerchantClient client = client(peer, 2000);
                ClientController controller = new ClientController(client, state -> {
                    last.set(state);
                    if (state.catalog().status() == ClientController.CatalogStatus.ERROR) failedPage.countDown();
                    if (state.catalog().status() == ClientController.CatalogStatus.READY) {
                        if (state.catalog().targetOffset() == 0) firstPage.countDown();
                        else retriedPage.countDown();
                    }
                })) {
            SwingUtilities.invokeAndWait(controller::refreshProducts);
            assertTrue(firstPage.await(2, TimeUnit.SECONDS));
            assertTrue(last.get().canGoNext());
            SwingUtilities.invokeAndWait(controller::nextPage);
            assertTrue(failedPage.await(2, TimeUnit.SECONDS));
            assertNull(last.get().catalog().page());
            assertEquals(20, last.get().catalog().targetOffset());
            assertEquals("商品服务暂不可用，请稍后刷新重试", last.get().catalog().message());
            assertTrue(last.get().canGoPrevious());
            assertFalse(last.get().canGoNext());
            SwingUtilities.invokeAndWait(controller::refreshProducts);
            assertTrue(retriedPage.await(2, TimeUnit.SECONDS));
            assertEquals(1021, last.get().catalog().page().items().getFirst().productId());
            assertFalse(last.get().canGoNext());
            peer.await();
        }
    }

    @Test
    void closingControllerCancelsAProductReadAndPreventsCallbacks() throws Exception {
        CountDownLatch requestRead = new CountDownLatch(1);
        AtomicInteger updates = new AtomicInteger();
        try (Peer peer = new Peer(socket -> {
            FrameCodec.read(socket, 2000);
            requestRead.countDown();
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 5000);
                ClientController controller = new ClientController(client, state -> updates.incrementAndGet())) {
            SwingUtilities.invokeAndWait(controller::refreshProducts);
            assertTrue(requestRead.await(2, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(controller::close);
            peer.await();
            SwingUtilities.invokeAndWait(() -> { });
            assertEquals(1, updates.get());
            assertThrows(IOException.class, () -> client.listProducts(20, 0, () -> false));
        }
    }

    private static JsonObject productBody(int limit, int offset, int count, boolean more) {
        JsonObject body = new JsonObject();
        var items = new com.google.gson.JsonArray();
        for (int index = 0; index < count; index++) {
            JsonObject item = new JsonObject();
            item.addProperty("productId", 1001 + offset + index);
            item.addProperty("name", "演示商品");
            item.addProperty("priceCents", 1290);
            item.addProperty("stock", 20);
            item.addProperty("description", "课程演示");
            items.add(item);
        }
        body.add("items", items);
        body.addProperty("limit", limit);
        body.addProperty("offset", offset);
        body.addProperty("hasMore", more);
        return body;
    }

    @Test
    void serializesConcurrentCallsOnOneConnection() throws Exception {
        try (Peer peer = new Peer(socket -> {
            reply(socket, FrameCodec.read(socket, 2000));
            reply(socket, FrameCodec.read(socket, 2000));
        }); MerchantClient client = client(peer, 2000);
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = pool.submit(() -> { return client.ping(); });
            var two = pool.submit(() -> { return client.ping(); });
            assertEquals("merchant-server", one.get(3, TimeUnit.SECONDS));
            assertEquals("merchant-server", two.get(3, TimeUnit.SECONDS));
            peer.await();
        }
    }

    @Test
    void silentResponseTimesOutAndClosesConnection() throws Exception {
        try (Peer peer = new Peer(socket -> {
            FrameCodec.read(socket, 2000);
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 150)) {
            assertThrows(SocketTimeoutException.class, client::ping);
            peer.await();
        }
    }

    @Test
    void trickledBytesDoNotRestartTheWholeFrameDeadline() throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            var output = new java.io.ByteArrayOutputStream();
            FrameCodec.write(output, pong(request));
            for (byte value : output.toByteArray()) {
                socket.getOutputStream().write(value);
                socket.getOutputStream().flush();
                Thread.sleep(60);
            }
        }); MerchantClient client = client(peer, 250)) {
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThrows(SocketTimeoutException.class, client::ping));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-id", "wrong-operation", "invalid-json", "eof"})
    void rejectsBrokenOrUnrelatedResponses(String scenario) throws Exception {
        try (Peer peer = new Peer(socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            JsonObject response = pong(request);
            switch (scenario) {
                case "wrong-id" -> response.addProperty("requestId", "unrelated");
                case "wrong-operation" -> response.addProperty("operation", 2001);
                case "invalid-json" -> {
                    socket.getOutputStream().write(ByteBuffer.allocate(5).putInt(1).put((byte) '{').array());
                    socket.getOutputStream().flush();
                    return;
                }
                case "eof" -> { return; }
                default -> fail("Unexpected scenario.");
            }
            FrameCodec.write(socket.getOutputStream(), response);
        }); MerchantClient client = client(peer, 2000)) {
            if ("eof".equals(scenario)) {
                assertThrows(EOFException.class, client::ping);
            } else {
                assertThrows(ProtocolException.class, client::ping);
            }
            peer.await();
        }
    }

    @Test
    void closedClientRefusesFurtherRequests() throws Exception {
        try (Peer peer = new Peer(socket -> fail("Closed client must not connect."));
                MerchantClient client = client(peer, 1000)) {
            client.close();
            assertThrows(IOException.class, client::ping);
        }
    }

    @Test
    void controllerLeavesEdtResponsiveAndCanCancelThenRetry() throws Exception {
        CountDownLatch firstRequest = new CountDownLatch(1);
        CountDownLatch success = new CountDownLatch(1);
        AtomicInteger connections = new AtomicInteger();
        AtomicBoolean callbacksOnEdt = new AtomicBoolean(true);
        AtomicReference<ClientController.State> last = new AtomicReference<>();
        try (Peer peer = new Peer(2, socket -> {
            JsonObject request = FrameCodec.read(socket, 2000);
            if (connections.getAndIncrement() == 0) {
                firstRequest.countDown();
                assertEquals(-1, socket.getInputStream().read());
            } else {
                reply(socket, request);
            }
        }); MerchantClient client = client(peer, 5000);
                ClientController controller = new ClientController(client, state -> {
                    callbacksOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
                    last.set(state);
                    if ("通信测试通过".equals(state.connectionMessage())) {
                        success.countDown();
                    }
                })) {
            SwingUtilities.invokeAndWait(controller::testConnection);
            assertTrue(firstRequest.await(2, TimeUnit.SECONDS));
            CountDownLatch edtResponsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(edtResponsive::countDown);
            assertTrue(edtResponsive.await(1, TimeUnit.SECONDS), "Network waiting blocked the EDT.");
            SwingUtilities.invokeAndWait(controller::cancel);
            assertEquals("已取消连接测试", last.get().connectionMessage());
            SwingUtilities.invokeAndWait(controller::testConnection);
            assertTrue(success.await(3, TimeUnit.SECONDS));
            assertFalse(last.get().busy());
            assertTrue(callbacksOnEdt.get());
            peer.await();
        }
    }

    @Test
    void closingControllerReleasesPendingRead() throws Exception {
        CountDownLatch requestRead = new CountDownLatch(1);
        try (Peer peer = new Peer(socket -> {
            FrameCodec.read(socket, 2000);
            requestRead.countDown();
            assertEquals(-1, socket.getInputStream().read());
        }); MerchantClient client = client(peer, 5000);
                ClientController controller = new ClientController(client, state -> { })) {
            SwingUtilities.invokeAndWait(controller::testConnection);
            assertTrue(requestRead.await(2, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(controller::close);
            peer.await();
            assertThrows(IOException.class, client::ping);
        }
    }

    private static MerchantClient client(Peer peer, int timeout) {
        return new MerchantClient("127.0.0.1", peer.port(), 1000, timeout);
    }

    private static JsonObject pong(JsonObject request) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("service", "merchant-server");
        return Messages.response(Messages.parseRequest(request), "OK", "pong", body);
    }

    private static void reply(Socket socket, JsonObject request) throws IOException {
        FrameCodec.write(socket.getOutputStream(), pong(request));
    }

    @FunctionalInterface
    private interface Handler {
        void handle(Socket socket) throws Exception;
    }

    private static final class Peer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private final AtomicReference<Socket> accepted = new AtomicReference<>();
        private final Future<?> task;

        Peer(Handler handler) throws IOException {
            this(1, handler);
        }

        Peer(int count, Handler handler) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            task = worker.submit(() -> {
                for (int i = 0; i < count; i++) {
                    try (Socket socket = listener.accept()) {
                        accepted.set(socket);
                        socket.setSoTimeout(4000);
                        handler.handle(socket);
                    }
                }
                return null;
            });
        }

        int port() {
            return listener.getLocalPort();
        }

        void await() throws Exception {
            task.get(3, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            listener.close();
            Socket socket = accepted.get();
            if (socket != null) {
                socket.close();
            }
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
