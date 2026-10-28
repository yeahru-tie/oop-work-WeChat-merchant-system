package cn.edu.jlu.wechatmerchant.server.ui;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.PromotionManagementService;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class PromotionManagementPanelTest {
    @TempDir Path directory;
    private SqliteDatabase database;
    private PromotionManagementService service;
    private String admin;
    private PromotionManagementPanel panel;
    private void setup() throws Exception {
        database=new SqliteDatabase(directory.resolve("ui.db")); database.initialize();
        var sessions=new SessionStore();
        admin=sessions.create(new UserDao(database).findByUsername("shop_admin").orElseThrow().identity());
        service=new PromotionManagementService(database,sessions);
        SwingUtilities.invokeAndWait(() -> {
            panel=new PromotionManagementPanel(service,() -> admin,() -> fail("Unexpected authentication expiry")); panel.start();
        });
        await(() -> named(panel,"promotion-save").isEnabled());
    }
    @Test void actualFormPreviewsWithoutPublishingThenSavesBeijingTimeAndRestoresConfiguration() throws Exception {
        setup();
        try {
            byte[] before=Files.readAllBytes(database.file());
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(((JCheckBox)named(panel,"promotion-enabled")).isSelected());
                text("promotion-reduction","1.00"); text("promotion-discount","9");
                ((JSpinner)named(panel,"promotion-quantity")).setValue(2);
                ((JButton)named(panel,"promotion-preview")).doClick();
                assertFalse(named(panel,"promotion-save").isEnabled());
            });
            await(() -> ((JTextArea)named(panel,"promotion-preview-result")).getText().contains("21.42"));
            assertArrayEquals(before,Files.readAllBytes(database.file()));
            assertNull(service.load(admin,1001).configuration());
            SwingUtilities.invokeAndWait(() -> {
                text("promotion-start","2026-10-28 08:00:00"); text("promotion-end","2026-10-29 08:00:00");
                ((JCheckBox)named(panel,"promotion-enabled")).setSelected(true);
                ((JButton)named(panel,"promotion-save")).doClick();
            });
            await(() -> named(panel,"promotion-save").isEnabled());
            var config=service.load(admin,1001).configuration();
            assertNotNull(config); assertEquals(Instant.parse("2026-10-28T00:00:00Z"),config.startsAt());
            assertEquals(Instant.parse("2026-10-29T00:00:00Z"),config.endsAt());
            assertTrue(config.enabled()); assertEquals(new PromotionTerms(100,900),config.terms());
            SwingUtilities.invokeAndWait(() -> {
                text("promotion-reduction","2"); ((JButton)named(panel,"promotion-reload")).doClick();
            });
            await(() -> named(panel,"promotion-save").isEnabled());
            SwingUtilities.invokeAndWait(() -> assertEquals("1.00",((JTextField)named(panel,"promotion-reduction")).getText()));
        } finally { SwingUtilities.invokeAndWait(panel::close); }
    }
    @Test void invalidInputsDoNotWriteAndVersionConflictRequiresReload() throws Exception {
        setup();
        try {
            byte[] before=Files.readAllBytes(database.file());
            SwingUtilities.invokeAndWait(() -> {
                text("promotion-discount","0"); ((JButton)named(panel,"promotion-save")).doClick();
                text("promotion-discount","9"); text("promotion-start","2026-02-30 08:00:00");
                ((JButton)named(panel,"promotion-save")).doClick();
                text("promotion-start","2026-10-28 08:00:00"); text("promotion-end","2026-10-28 08:00:00");
                ((JButton)named(panel,"promotion-save")).doClick();
            });
            assertArrayEquals(before,Files.readAllBytes(database.file()));
            var external=new PromotionConfiguration(1001,0,new PromotionTerms(100,800),Instant.parse("2026-10-28T00:00:00Z"),Instant.parse("2026-10-29T00:00:00Z"),true);
            service.save(admin,external);
            SwingUtilities.invokeAndWait(() -> { text("promotion-end","2026-10-29 08:00:00"); ((JButton)named(panel,"promotion-save")).doClick(); });
            await(() -> named(panel,"promotion-reload").isEnabled() && !named(panel,"promotion-save").isEnabled());
            assertEquals(800,service.load(admin,1001).configuration().terms().discountHundredths());
            SwingUtilities.invokeAndWait(() -> ((JButton)named(panel,"promotion-reload")).doClick());
            await(() -> named(panel,"promotion-save").isEnabled());
            SwingUtilities.invokeAndWait(() -> assertEquals("8",((JTextField)named(panel,"promotion-discount")).getText()));
        } finally { SwingUtilities.invokeAndWait(panel::close); }
    }
    private void text(String name,String value) { ((JTextField)named(panel,name)).setText(value); }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            var result=new AtomicReference<Boolean>(); SwingUtilities.invokeAndWait(() -> result.set(condition.getAsBoolean()));
            if(result.get()) return; Thread.sleep(10);
        }
        fail("Timed out waiting for management view");
    }
    private static Component named(Container parent,String name) {
        Component found=find(parent,name); assertNotNull(found,name); return found;
    }
    private static Component find(Container parent,String name) {
        for(Component child:parent.getComponents()) {
            if(name.equals(child.getName())) return child;
            if(child instanceof Container nested) { Component found=find(nested,name); if(found!=null) return found; }
        }
        return null;
    }
}
