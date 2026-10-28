package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.client.transport.MerchantClient;
import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import cn.edu.jlu.wechatmerchant.server.persistence.UserDao;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import cn.edu.jlu.wechatmerchant.server.transport.ProbeServer;
import cn.edu.jlu.wechatmerchant.server.ui.ServerDesktop;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class AdminEntryIntegrationTest {
    @TempDir Path directory;

    @Test
    void realVerificationGatesEntryAndLogoutOrExpiryReturnsToLoginWithoutDisconnectingBuyers() throws Exception {
        try (Fixture fixture = fixture(); MerchantClient buyer = fixture.buyer()) {
            var views = new Views();
            var gate = new AdminAccessGate(views, true);
            try (var controller = new AdminController(fixture.authentication, fixture.sessions, gate::accept)) {
                SwingUtilities.invokeAndWait(() -> gate.accept(controller.state()));
                assertTrue(views.login);
                assertEquals(0, views.constructions);
                UserIdentity buyerIdentity = buyer.login("buyer_a", "BuyerA#2026!".toCharArray(), () -> false);
                SwingUtilities.invokeAndWait(() -> controller.login("buyer_a", "BuyerA#2026!".toCharArray()));
                views.await("账号或密码不正确");
                assertEquals(0, views.constructions);
                SwingUtilities.invokeAndWait(() -> controller.login("shop_admin", "ShopAdmin#2026!".toCharArray()));
                views.await("MANAGEMENT");
                assertFalse(views.login);
                assertEquals(1, views.constructions);
                SwingUtilities.invokeAndWait(controller::logout);
                assertTrue(views.login);
                assertFalse(views.management);
                assertEquals(buyerIdentity, buyer.currentUser(() -> false));
                assertEquals(3, buyer.listProducts(20, 0, () -> false).items().size());
                SwingUtilities.invokeAndWait(() -> controller.login("shop_admin", "ShopAdmin#2026!".toCharArray()));
                views.await("MANAGEMENT");
                assertEquals(2, views.constructions);
                fixture.clock.advance(Duration.ofMinutes(29));
                assertEquals(buyerIdentity, buyer.currentUser(() -> false));
                fixture.clock.advance(Duration.ofMinutes(1));
                SwingUtilities.invokeAndWait(controller::checkExpiry);
                assertTrue(views.login);
                assertFalse(views.management);
                assertEquals(buyerIdentity, buyer.currentUser(() -> false));
                assertEquals(0, views.closes);
                SwingUtilities.invokeAndWait(gate::close);
            }
        }
    }

    @Test
    void closingTheEntryDuringVerificationPreventsLateSuccessFromCreatingManagement() throws Exception {
        try (Fixture fixture = fixture()) {
            var views = new Views();
            var gate = new AdminAccessGate(views, true);
            try (var controller = new AdminController(fixture.authentication, fixture.sessions, gate::accept)) {
                SwingUtilities.invokeAndWait(() -> {
                    gate.accept(controller.state());
                    controller.login("shop_admin", "ShopAdmin#2026!".toCharArray());
                    controller.close();
                    gate.close();
                });
                assertTrue(controller.awaitStopped(5, TimeUnit.SECONDS));
                SwingUtilities.invokeAndWait(() -> { });
                assertEquals(0, views.constructions);
                assertEquals(1, views.closes);
                assertFalse(views.login);
                assertFalse(views.management);
            }
        }
    }

    @Test
    void applicationShutdownBeforeShowingEntryClosesListenerAndClearsSessions() throws Exception {
        try (Fixture fixture = fixture()) {
            String token = fixture.sessions.create(new UserIdentity(3, "shop_admin", "管理员", UserIdentity.Role.ADMIN));
            SwingUtilities.invokeAndWait(() -> {
                // Construction is lazy and safe in headless tests: no window is made until start/state routing.
                var desktop = new ServerDesktop(fixture.server, fixture.authentication, fixture.sessions, "测试状态", true);
                desktop.close();
                desktop.close();
            });
            assertTrue(fixture.server.awaitStopped(3, TimeUnit.SECONDS));
            assertTrue(fixture.sessions.resolve(token).isEmpty());
        }
    }

    private Fixture fixture() throws Exception {
        var database = new SqliteDatabase(directory.resolve(java.util.UUID.randomUUID() + ".db"));
        database.initialize();
        var clock = new MutableClock();
        var sessions = new SessionStore(clock, Duration.ofMinutes(30));
        var authentication = new AuthenticationService(new UserDao(database));
        var server = new ProbeServer(0, new ProductQueryHandler(new ProductCatalog(new ProductDao(database))),
                new IdentityHandler(authentication, sessions));
        server.start();
        return new Fixture(server, authentication, sessions, clock);
    }

    private record Fixture(ProbeServer server, AuthenticationService authentication, SessionStore sessions, MutableClock clock)
            implements AutoCloseable {
        MerchantClient buyer() { return new MerchantClient("127.0.0.1", server.port(), 1000, 5000); }
        @Override public void close() { server.close(); sessions.clear(); }
    }

    private static final class Views implements AdminAccessGate.Views {
        private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        volatile boolean login;
        volatile boolean management;
        volatile int constructions;
        volatile int closes;

        @Override public void showLogin(AdminController.State state) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            management = false;
            login = true;
            events.add(state.message());
        }
        @Override public void showManagement(UserIdentity identity) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            assertEquals(UserIdentity.Role.ADMIN, identity.role());
            login = false;
            management = true;
            constructions++;
            events.add("MANAGEMENT");
        }
        @Override public void closeWindows() { login = false; management = false; closes++; }

        void await(String expected) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                String next = events.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (expected.equals(next)) return;
            }
            fail("Missing UI transition: " + expected);
        }
    }
}
