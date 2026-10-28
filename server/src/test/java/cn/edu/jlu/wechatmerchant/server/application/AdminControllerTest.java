package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import cn.edu.jlu.wechatmerchant.server.persistence.UserDao;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class AdminControllerTest {
    @TempDir Path directory;

    @Test
    void buyersCannotUnlockManagementAndAdminExpiryDoesNotRefreshItself() throws Exception {
        var database = new SqliteDatabase(directory.resolve("admin.db"));
        database.initialize();
        var clock = new MutableClock();
        var sessions = new SessionStore(clock, Duration.ofMinutes(30));
        var service = new AuthenticationService(new UserDao(database));
        CountDownLatch denied = new CountDownLatch(1);
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicReference<AdminController.State> last = new AtomicReference<>();
        try (var controller = new AdminController(service, sessions, state -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            last.set(state);
            if ("账号或密码不正确".equals(state.message())) denied.countDown();
            if (state.identity() != null) accepted.countDown();
        })) {
            SwingUtilities.invokeAndWait(() -> controller.login("buyer_a", "BuyerA#2026!".toCharArray()));
            assertTrue(denied.await(5, TimeUnit.SECONDS));
            assertNull(last.get().identity());
            char[] password = "ShopAdmin#2026!".toCharArray();
            SwingUtilities.invokeAndWait(() -> controller.login("shop_admin", password));
            assertTrue(accepted.await(5, TimeUnit.SECONDS));
            assertEquals(UserIdentity.Role.ADMIN, last.get().identity().role());
            assertArrayEquals(new char[password.length], password);
            clock.advance(Duration.ofMinutes(29));
            SwingUtilities.invokeAndWait(controller::checkExpiry);
            assertNotNull(last.get().identity());
            clock.advance(Duration.ofMinutes(1));
            SwingUtilities.invokeAndWait(controller::checkExpiry);
            assertNull(last.get().identity());
            assertEquals("管理员登录已失效，请重新登录", last.get().message());
        }
    }

    @Test
    void cancelAndCloseDoNotAllowLatePasswordVerificationToRestoreAdminState() throws Exception {
        var database = new SqliteDatabase(directory.resolve("cancel.db"));
        database.initialize();
        var sessions = new SessionStore();
        var service = new AuthenticationService(new UserDao(database));
        AtomicReference<AdminController.State> last = new AtomicReference<>();
        try (var controller = new AdminController(service, sessions, last::set)) {
            SwingUtilities.invokeAndWait(() -> {
                controller.login("shop_admin", "ShopAdmin#2026!".toCharArray());
                controller.cancel();
            });
            controller.close();
            assertTrue(controller.awaitStopped(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> { });
            assertNull(last.get().identity());
            assertEquals("已取消管理员登录", last.get().message());
        }
    }
}
