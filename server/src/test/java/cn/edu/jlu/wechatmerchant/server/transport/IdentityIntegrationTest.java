package cn.edu.jlu.wechatmerchant.server.transport;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.client.application.ClientController;
import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.client.transport.RemoteServiceException;
import cn.edu.jlu.wechatmerchant.protocol.FrameCodec;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.application.AuthenticationService;
import cn.edu.jlu.wechatmerchant.server.application.IdentityHandler;
import cn.edu.jlu.wechatmerchant.server.application.ProductCatalog;
import cn.edu.jlu.wechatmerchant.server.application.ProductQueryHandler;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import cn.edu.jlu.wechatmerchant.server.persistence.UserDao;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import com.google.gson.JsonObject;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class IdentityIntegrationTest {
    @TempDir Path directory;

    @Test
    void realClientsAuthenticateSeparatelyReconnectAndLogoutWithoutAffectingOthers() throws Exception {
        try (Fixture fixture = fixture(); MerchantClient one = fixture.client(); MerchantClient two = fixture.client()) {
            assertEquals(3, one.listProducts(20, 0, () -> false).items().size());
            assertEquals("UNAUTHENTICATED", assertThrows(RemoteServiceException.class,
                    () -> one.currentUser(() -> false)).code());
            var a = one.login("buyer_a", "BuyerA#2026!".toCharArray(), () -> false);
            var b = two.login("buyer_b", "BuyerB#2026!".toCharArray(), () -> false);
            assertNotEquals(a.userId(), b.userId());
            one.disconnect();
            assertEquals(a, one.currentUser(() -> false));
            assertEquals(b, two.currentUser(() -> false));
            one.logout(() -> false);
            assertTrue(one.identity().isEmpty());
            assertEquals("UNAUTHENTICATED", assertThrows(RemoteServiceException.class,
                    () -> one.currentUser(() -> false)).code());
            assertEquals(b, two.currentUser(() -> false));
        }
    }

    @Test
    void wrongUnknownDisabledAndAdminAccountsCannotObtainBuyerIdentity() throws Exception {
        try (Fixture fixture = fixture(); MerchantClient client = fixture.client()) {
            for (String[] attempt : new String[][] {{"buyer_a", "wrong"}, {"unknown_user", "anything"},
                    {"shop_admin", "ShopAdmin#2026!"}}) {
                assertEquals("INVALID_CREDENTIALS", assertThrows(RemoteServiceException.class,
                        () -> client.login(attempt[0], attempt[1].toCharArray(), () -> false)).code());
                assertTrue(client.identity().isEmpty());
            }
            try (var connection = fixture.database.openWriteConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE users SET enabled=0 WHERE username='buyer_a'");
            }
            assertEquals("INVALID_CREDENTIALS", assertThrows(RemoteServiceException.class,
                    () -> client.login("buyer_a", "BuyerA#2026!".toCharArray(), () -> false)).code());
        }
    }

    @Test
    void expiredAndDisabledSessionsClearClientIdentityWhileGuestBrowsingRemainsPossible() throws Exception {
        try (Fixture fixture = fixture(); MerchantClient client = fixture.client()) {
            client.login("buyer_a", "BuyerA#2026!".toCharArray(), () -> false);
            fixture.clock.advance(Duration.ofMinutes(30));
            assertEquals("UNAUTHENTICATED", assertThrows(RemoteServiceException.class,
                    () -> client.listProducts(20, 0, () -> false)).code());
            assertTrue(client.identity().isEmpty());
            assertEquals(3, client.listProducts(20, 0, () -> false).items().size());
            client.login("buyer_a", "BuyerA#2026!".toCharArray(), () -> false);
            try (var connection = fixture.database.openWriteConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE users SET enabled=0 WHERE username='buyer_a'");
            }
            assertEquals("UNAUTHENTICATED", assertThrows(RemoteServiceException.class,
                    () -> client.currentUser(() -> false)).code());
            assertTrue(client.identity().isEmpty());
        }
    }

    @Test
    void forgedIdentityAndWrongRoleAreRejectedAndLogoutRevokesTheOriginalToken() throws Exception {
        try (Fixture fixture = fixture()) {
            JsonObject login = exchange(fixture, Messages.login("login", "buyer_a", "BuyerA#2026!".toCharArray()));
            String token = login.get("sessionToken").getAsString();
            assertEquals("OK", login.get("code").getAsString());
            assertFalse(login.getAsJsonObject("body").has("passwordHash"));
            assertFalse(Messages.validateResponse(login, "login", 2006).toString().contains(token));
            var request = Messages.authenticatedRequest("forged", Messages.CURRENT_USER, token, new JsonObject());
            request.getAsJsonObject("body").addProperty("userId", 2);
            assertEquals("INVALID_ARGUMENT", exchange(fixture, request).get("code").getAsString());
            String adminToken = fixture.sessions.create(new UserDao(fixture.database).findByUsername("shop_admin").orElseThrow().identity());
            assertEquals("FORBIDDEN", exchange(fixture, Messages.authenticatedRequest("admin", Messages.CURRENT_USER,
                    adminToken, new JsonObject())).get("code").getAsString());
            assertEquals("UNAUTHENTICATED", exchange(fixture, Messages.authenticatedRequest("fake", Messages.CURRENT_USER,
                    "x".repeat(43), new JsonObject())).get("code").getAsString());
            assertEquals("OK", exchange(fixture, Messages.authenticatedRequest("logout", Messages.LOGOUT, token,
                    new JsonObject())).get("code").getAsString());
            assertEquals("UNAUTHENTICATED", exchange(fixture, Messages.authenticatedRequest("old", Messages.CURRENT_USER,
                    token, new JsonObject())).get("code").getAsString());
        }
    }

    @Test
    void buyerControllerLoginAndLogoutKeepTheEdtResponsiveAndClearPasswordMemory() throws Exception {
        CountDownLatch login = new CountDownLatch(1);
        CountDownLatch logout = new CountDownLatch(1);
        AtomicReference<ClientController.State> last = new AtomicReference<>();
        try (Fixture fixture = fixture(); MerchantClient client = fixture.client();
                ClientController controller = new ClientController(client, state -> {
                    assertTrue(SwingUtilities.isEventDispatchThread());
                    last.set(state);
                    if (state.identity() != null && !state.busy()) login.countDown();
                    if (state.identity() == null && "已退出登录".equals(state.authMessage())) logout.countDown();
                })) {
            char[] password = "BuyerA#2026!".toCharArray();
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a", password));
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(responsive::countDown);
            assertTrue(responsive.await(1, TimeUnit.SECONDS));
            assertTrue(login.await(5, TimeUnit.SECONDS));
            assertEquals(UserIdentity.Role.BUYER, last.get().identity().role());
            assertEquals(0, Arrays.compare(password, new char[password.length]));
            SwingUtilities.invokeAndWait(controller::logout);
            assertTrue(logout.await(3, TimeUnit.SECONDS));
            assertNull(last.get().identity());
        }
    }

    private Fixture fixture() throws Exception {
        var database = new SqliteDatabase(directory.resolve(java.util.UUID.randomUUID() + ".db"));
        database.initialize();
        var clock = new MutableClock();
        var sessions = new SessionStore(clock, Duration.ofMinutes(30));
        var server = new ProbeServer(0, new ProductQueryHandler(new ProductCatalog(new ProductDao(database))),
                new IdentityHandler(new AuthenticationService(new UserDao(database)), sessions));
        server.start();
        return new Fixture(database, clock, sessions, server);
    }

    private record Fixture(SqliteDatabase database, MutableClock clock, SessionStore sessions, ProbeServer server)
            implements AutoCloseable {
        MerchantClient client() { return new MerchantClient("127.0.0.1", server.port(), 1000, 5000); }
        @Override public void close() { server.close(); sessions.clear(); }
    }

    private static JsonObject exchange(Fixture fixture, JsonObject request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", fixture.server.port())) {
            FrameCodec.write(socket.getOutputStream(), request);
            return FrameCodec.read(socket, 5000);
        }
    }
}
