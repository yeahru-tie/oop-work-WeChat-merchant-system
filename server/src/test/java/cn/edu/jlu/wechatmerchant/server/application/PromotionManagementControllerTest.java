package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class PromotionManagementControllerTest {
    @TempDir Path directory;
    @Test void closingManagementDiscardsQueuedCallbacksAndDoesNotBlockEdtOrCallOldView() throws Exception {
        var database=new SqliteDatabase(directory.resolve("controller.db")); database.initialize();
        var sessions=new SessionStore();
        String token=sessions.create(new UserDao(database).findByUsername("shop_admin").orElseThrow().identity());
        var callbacks=new LinkedBlockingQueue<PromotionManagementController.Status>();
        var result=new AtomicBoolean(); var reference=new AtomicReference<PromotionManagementController>();
        SwingUtilities.invokeAndWait(() -> {
            var controller=new PromotionManagementController(new PromotionManagementService(database,sessions),() -> token,
                    state -> { assertTrue(SwingUtilities.isEventDispatchThread()); callbacks.add(state); },() -> fail("Unexpected denial"));
            reference.set(controller);
            controller.load(1001,value -> result.set(true));
            assertTrue(callbacks.remove().busy());
            // The worker can finish, but its EDT callback cannot run before this close.
            controller.close();
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertNull(callbacks.poll(200,TimeUnit.MILLISECONDS)); assertFalse(result.get());
        SwingUtilities.invokeAndWait(() -> reference.get().list(0,page -> fail("Closed controller ran")));
    }
}
