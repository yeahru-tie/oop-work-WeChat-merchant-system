package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public final class AdminController implements AutoCloseable {
    public record State(UserIdentity identity, boolean busy, String message) {
    }

    private final AuthenticationService authentication;
    private final SessionStore sessions;
    private final Consumer<State> listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("admin-auth-", 0).factory());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private volatile State state = new State(null, false, "管理员尚未登录");
    private volatile String token;

    public AdminController(AuthenticationService authentication, SessionStore sessions, Consumer<State> listener) {
        this.authentication = authentication;
        this.sessions = sessions;
        this.listener = listener;
    }

    public State state() { return state; }
    /** Passed only to local protected services; never expose in view state or logs. */
    public String sessionToken() { return token; }

    public void login(String username, char[] password) {
        requireEdt();
        if (closed.get() || state.busy() || state.identity() != null) {
            Arrays.fill(password, '\0');
            return;
        }
        long attempt = generation.incrementAndGet();
        publish(attempt, new State(null, true, "正在验证管理员…"), null);
        try {
            worker.submit(() -> {
                State next;
                String created = null;
                try {
                    var identity = authentication.authenticate(username, password, UserIdentity.Role.ADMIN);
                    if (identity.isEmpty()) next = new State(null, false, "账号或密码不正确");
                    else {
                        created = sessions.create(identity.get());
                        next = new State(identity.get(), false, "管理员已登录");
                    }
                } catch (AuthenticationService.Busy | IllegalStateException exception) {
                    next = new State(null, false, "登录服务繁忙，请稍后重试");
                } catch (SQLException | RuntimeException exception) {
                    next = new State(null, false, "管理身份服务暂不可用，请稍后重试");
                } finally {
                    Arrays.fill(password, '\0');
                }
                publish(attempt, next, created);
            });
        } catch (RejectedExecutionException exception) {
            Arrays.fill(password, '\0');
        }
    }

    public void logout() {
        requireEdt();
        generation.incrementAndGet();
        sessions.revoke(token);
        token = null;
        state = new State(null, false, "管理员已退出");
        if (!closed.get()) listener.accept(state);
    }

    public void cancel() {
        requireEdt();
        if (state.busy()) {
            long attempt = generation.incrementAndGet();
            publish(attempt, new State(null, false, "已取消管理员登录"), null);
        }
    }

    public void checkExpiry() {
        requireEdt();
        if (!closed.get() && token != null && sessions.peek(token).isEmpty()) {
            token = null;
            state = new State(null, false, "管理员登录已失效，请重新登录");
            listener.accept(state);
        }
    }

    private void publish(long attempt, State next, String createdToken) {
        Runnable update = () -> {
            if (closed.get() || generation.get() != attempt) {
                sessions.revoke(createdToken);
                return;
            }
            state = next;
            if (createdToken != null) token = createdToken;
            listener.accept(next);
        };
        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Admin actions must run on the EDT.");
    }

    @Override public void close() {
        closed.set(true);
        generation.incrementAndGet();
        sessions.revoke(token);
        token = null;
        worker.shutdownNow();
    }

    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return worker.awaitTermination(timeout, unit);
    }
}
