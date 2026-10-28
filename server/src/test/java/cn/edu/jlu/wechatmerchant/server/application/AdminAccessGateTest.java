package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import org.junit.jupiter.api.Test;

class AdminAccessGateTest {
    private final UserIdentity administrator = new UserIdentity(3, "shop_admin", "管理员", UserIdentity.Role.ADMIN);

    @Test
    void initialBusyRejectedAndWrongRoleStatesNeverConstructManagement() {
        var views = new Views();
        var gate = new AdminAccessGate(views, true);
        for (AdminController.State state : new AdminController.State[] {
                new AdminController.State(null, false, "管理员尚未登录"),
                new AdminController.State(null, true, "正在验证管理员…"),
                new AdminController.State(null, false, "账号或密码不正确"),
                new AdminController.State(new UserIdentity(1, "buyer_a", "买家", UserIdentity.Role.BUYER), false, "买家"),
                new AdminController.State(administrator, true, "尚在验证")}) {
            gate.accept(state);
            assertTrue(views.login);
            assertFalse(views.management);
            assertEquals(0, views.managementConstructions);
            assertNull(views.lastLogin.identity());
        }
    }

    @Test
    void logoutAndExpiryDestroyManagementAndSuccessfulReentryUsesANewWindow() {
        var views = new Views();
        var gate = new AdminAccessGate(views, true);
        gate.accept(new AdminController.State(administrator, false, "管理员已登录"));
        gate.accept(new AdminController.State(administrator, false, "管理员已登录"));
        assertEquals(1, views.managementConstructions);
        assertFalse(views.login);
        gate.accept(new AdminController.State(null, false, "管理员已退出"));
        assertTrue(views.login);
        assertFalse(views.management);
        assertEquals(0, views.closes);
        gate.accept(new AdminController.State(administrator, false, "管理员已登录"));
        assertEquals(2, views.managementConstructions);
        gate.accept(new AdminController.State(null, false, "管理员登录已失效，请重新登录"));
        assertTrue(views.login);
        assertFalse(views.management);
        assertEquals("管理员登录已失效，请重新登录", views.lastLogin.message());
    }

    @Test
    void startupFailureCannotFallBackToAVisitorOrAuthenticatedManagementWindow() {
        var views = new Views();
        var gate = new AdminAccessGate(views, false);
        gate.accept(new AdminController.State(administrator, false, "错误的就绪身份"));
        assertTrue(views.login);
        assertTrue(views.lastLogin.busy());
        assertTrue(views.lastLogin.message().startsWith("服务启动失败"));
        assertEquals(0, views.managementConstructions);
    }

    @Test
    void shutdownIsIdempotentAndLateSuccessCannotReopenWindows() {
        var views = new Views();
        var gate = new AdminAccessGate(views, true);
        gate.accept(new AdminController.State(null, false, "等待登录"));
        gate.close();
        gate.close();
        gate.accept(new AdminController.State(administrator, false, "迟到的验证成功"));
        assertFalse(views.login);
        assertFalse(views.management);
        assertEquals(1, views.closes);
        assertEquals(0, views.managementConstructions);
    }

    private static final class Views implements AdminAccessGate.Views {
        boolean login;
        boolean management;
        int managementConstructions;
        int closes;
        AdminController.State lastLogin;

        @Override public void showLogin(AdminController.State state) {
            management = false;
            login = true;
            lastLogin = state;
        }
        @Override public void showManagement(UserIdentity identity) {
            login = false;
            management = true;
            managementConstructions++;
        }
        @Override public void closeWindows() { login = false; management = false; closes++; }
    }
}
