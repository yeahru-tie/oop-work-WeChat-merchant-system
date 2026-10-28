package cn.edu.jlu.wechatmerchant.server.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.awt.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class CouponManagementPanelTest {
    @TempDir Path folder;
    @Test void actualDraftFormSavesThenPublishesLocksTermsAndPausesOnlyIssuance() throws Exception {
        var db=new SqliteDatabase(folder.resolve("admin.db"));db.initialize();var sessions=new SessionStore();
        String token=sessions.create(new UserDao(db).findByUsername("shop_admin").orElseThrow().identity());var service=new CouponService(db,sessions);
        var ref=new AtomicReference<CouponManagementPanel>();
        SwingUtilities.invokeAndWait(() -> {ref.set(new CouponManagementPanel(service,() -> token,() -> fail("Unexpected denial")));ref.get().start();});
        try {
            await(() -> named(ref.get(),"coupon-save").isEnabled());assertTrue(service.adminPage(token,0).items().isEmpty());
            SwingUtilities.invokeAndWait(() -> {
                field(ref.get(),"coupon-name","全店满150减20");field(ref.get(),"coupon-threshold","150.00");field(ref.get(),"coupon-discount","20.00");
                field(ref.get(),"coupon-start","2026-10-28 08:00:00");field(ref.get(),"coupon-end","2026-10-29 08:00:00");((JSpinner)named(ref.get(),"coupon-total")).setValue(2);
                assertFalse(named(ref.get(),"coupon-publish").isEnabled());((JButton)named(ref.get(),"coupon-save")).doClick();
            });
            await(() -> named(ref.get(),"coupon-publish").isEnabled());var draft=service.adminPage(token,0).items().getFirst();
            assertFalse(draft.published());assertEquals(Instant.parse("2026-10-28T00:00:00Z").toEpochMilli(),draft.startsAt());
            SwingUtilities.invokeAndWait(() -> {field(ref.get(),"coupon-discount","25.00");assertFalse(named(ref.get(),"coupon-publish").isEnabled());((JButton)named(ref.get(),"coupon-save")).doClick();});
            await(() -> named(ref.get(),"coupon-publish").isEnabled());SwingUtilities.invokeAndWait(() -> ((JButton)named(ref.get(),"coupon-publish")).doClick());
            await(() -> named(ref.get(),"coupon-pause").isEnabled());
            SwingUtilities.invokeAndWait(() -> {assertFalse(named(ref.get(),"coupon-discount").isEnabled());assertFalse(named(ref.get(),"coupon-save").isEnabled());((JButton)named(ref.get(),"coupon-pause")).doClick();});
            await(() -> named(ref.get(),"coupon-pause").isEnabled() && ((JButton)named(ref.get(),"coupon-pause")).getText().equals("恢复领取"));
            var published=service.adminPage(token,0).items().getFirst();assertTrue(published.published());assertTrue(published.paused());assertEquals(2500,published.discountCents());
        } finally {SwingUtilities.invokeAndWait(ref.get()::close);}
    }
    @Test void invalidFormDoesNotWriteAndClosingControllerDropsLateCallbacks() throws Exception {
        var db=new SqliteDatabase(folder.resolve("close.db"));db.initialize();var sessions=new SessionStore();
        String token=sessions.create(new UserDao(db).findByUsername("shop_admin").orElseThrow().identity());var service=new CouponService(db,sessions);var ref=new AtomicReference<CouponManagementPanel>();
        SwingUtilities.invokeAndWait(() -> {ref.set(new CouponManagementPanel(service,() -> token,() -> fail("Unexpected denial")));ref.get().start();});
        await(() -> named(ref.get(),"coupon-save").isEnabled());byte[] before=Files.readAllBytes(db.file());
        SwingUtilities.invokeAndWait(() -> {field(ref.get(),"coupon-discount","0");((JButton)named(ref.get(),"coupon-save")).doClick();field(ref.get(),"coupon-discount","2");field(ref.get(),"coupon-start","2026-02-30 00:00:00");((JButton)named(ref.get(),"coupon-save")).doClick();ref.get().close();});
        assertArrayEquals(before,Files.readAllBytes(db.file()));
        var events=new LinkedBlockingQueue<CouponManagementController.Status>();var success=new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {var controller=new CouponManagementController(service,() -> token,events::add,() -> fail("Unexpected denial"));controller.list(0,value -> success.set(true));assertTrue(events.remove().busy());controller.close();});
        SwingUtilities.invokeAndWait(() -> {});assertNull(events.poll(100,TimeUnit.MILLISECONDS));assertFalse(success.get());
    }
    private static void field(Container parent,String key,String value){((JTextField)named(parent,key)).setText(value);}
    private static void await(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<until){var value=new AtomicBoolean();SwingUtilities.invokeAndWait(() -> value.set(condition.getAsBoolean()));if(value.get())return;Thread.sleep(10);}fail("Timed out");
    }
    private static Component named(Container parent,String key){var item=find(parent,key);assertNotNull(item,key);return item;}
    private static Component find(Container parent,String key){for(var child:parent.getComponents()){if(key.equals(child.getName()))return child;if(child instanceof Container c){var found=find(c,key);if(found!=null)return found;}}return null;}
}
