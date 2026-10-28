package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import java.util.Objects;

/** Routes verified controller state to exactly one UI surface. Called on the EDT. */
public final class AdminAccessGate implements AutoCloseable {
    public interface Views {
        /** Must dispose any management window before showing the login entry. */
        void showLogin(AdminController.State state);
        /** Must dispose the login entry before showing a newly authenticated management window. */
        void showManagement(UserIdentity administrator);
        void closeWindows();
    }

    private final Views views;
    private final boolean ready;
    private UserIdentity current;
    private boolean closed;

    public AdminAccessGate(Views views, boolean ready) {
        this.views = Objects.requireNonNull(views);
        this.ready = ready;
    }

    public void accept(AdminController.State state) {
        if (closed) return;
        UserIdentity identity = state.identity();
        if (ready && !state.busy() && identity != null && identity.role() == UserIdentity.Role.ADMIN) {
            if (!identity.equals(current)) {
                views.showManagement(identity);
                current = identity;
            }
            return;
        }
        current = null;
        if (!ready) {
            views.showLogin(new AdminController.State(null, true, "服务启动失败，请检查数据库或端口后重启"));
        } else if (identity != null && identity.role() == UserIdentity.Role.ADMIN && state.busy()) {
            views.showLogin(new AdminController.State(null, true, state.message()));
        } else if (identity != null) {
            views.showLogin(new AdminController.State(null, false, "请使用管理员账号登录"));
        } else {
            views.showLogin(state);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        current = null;
        views.closeWindows();
    }
}
