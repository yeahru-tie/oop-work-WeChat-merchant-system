package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.persistence.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import cn.edu.jlu.wechatmerchant.server.support.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromotionManagementServiceTest {
    @TempDir Path directory;
    private final MutableClock clock=new MutableClock();
    private final SessionStore sessions=new SessionStore(clock,Duration.ofMinutes(30));
    private SqliteDatabase database;
    private PromotionManagementService service;
    private String admin;
    private void setup() throws Exception {
        database=new SqliteDatabase(directory.resolve("shop.db")); database.initialize();
        service=new PromotionManagementService(database,sessions);
        admin=sessions.create(new UserDao(database).findByUsername("shop_admin").orElseThrow().identity());
    }
    private PromotionConfiguration draft(long version, boolean enabled) {
        return new PromotionConfiguration(1001,version,new PromotionTerms(100,900),clock.instant(),clock.instant().plusSeconds(60),enabled);
    }
    @Test void draftsAndPreviewDoNotPublishOrWriteAndSavedConfigurationSurvivesReopen() throws Exception {
        setup();
        assertNull(service.load(admin,1001).configuration());
        byte[] before=Files.readAllBytes(database.file());
        assertEquals(3,service.list(admin,0).products().size());
        var preview=service.preview(admin,1001,2,draft(0,true).terms());
        assertEquals("2142",preview.breakdown().estimatedTotalCents().toString());
        assertArrayEquals(before,Files.readAllBytes(database.file()));
        var saved=service.save(admin,draft(0,true)); assertEquals(1,saved.configuration().version());
        database.initialize();
        assertEquals(saved,new PromotionManagementService(new SqliteDatabase(database.file()),sessions).load(admin,1001));
        assertEquals(20,new ProductDao(database).findById(1001).orElseThrow().stock());
        assertTrue(new CartService(database).list(1).items().isEmpty());
    }
    @Test void everyManagementOperationRejectsAnonymousBuyerExpiredAndDisabledIdentity() throws Exception {
        setup();
        String buyer=sessions.create(new UserDao(database).findByUsername("buyer_a").orElseThrow().identity());
        for (String token:new String[]{"",buyer,"x".repeat(43)}) assertDenied(token);
        clock.advance(Duration.ofMinutes(30)); assertDenied(admin);
        admin=sessions.create(new UserDao(database).findByUsername("shop_admin").orElseThrow().identity());
        try(var c=database.openWriteConnection(); var s=c.createStatement()) { s.executeUpdate("UPDATE users SET enabled=0 WHERE role='ADMIN'"); }
        assertDenied(admin);
        try(var c=database.openReadOnlyConnection(); var s=c.createStatement(); var rows=s.executeQuery("SELECT count(*) FROM promotions")) {
            assertTrue(rows.next()); assertEquals(0,rows.getInt(1));
        }
    }
    private void assertDenied(String token) {
        assertThrows(SecurityException.class,() -> service.list(token,0));
        assertThrows(SecurityException.class,() -> service.load(token,1001));
        assertThrows(SecurityException.class,() -> service.preview(token,1001,1,new PromotionTerms(0,1000)));
        assertThrows(SecurityException.class,() -> service.save(token,draft(0,true)));
    }
    @Test void staleAndConcurrentSavesNeverOverwriteNewerConfiguration() throws Exception {
        setup();
        var draft=draft(0,true);
        try(var worker=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> write=() -> { start.await(); try { service.save(admin,draft); return true; }
                catch(PromotionManagementService.Conflict expected) { return false; } };
            var first=worker.submit(write); var second=worker.submit(write); start.countDown();
            assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
        }
        var saved=service.load(admin,1001);
        assertEquals(1,saved.configuration().version());
        byte[] before=Files.readAllBytes(database.file());
        assertThrows(PromotionManagementService.Conflict.class,() -> service.save(admin,draft));
        assertArrayEquals(before,Files.readAllBytes(database.file()));
        assertThrows(IllegalArgumentException.class,() -> service.preview(admin,9999,1,new PromotionTerms(0,900)));
        assertThrows(IllegalArgumentException.class,() -> service.preview(admin,1001,0,new PromotionTerms(0,900)));
        assertThrows(IllegalArgumentException.class,() -> new PromotionConfiguration(1001,1,new PromotionTerms(0,1000),clock.instant(),clock.instant(),true));
    }
    @Test void effectivePricingUsesInclusiveStartExclusiveEndAndNeutralPricesWhenDisabled() throws Exception {
        setup(); var pricing=new PromotionPricing(clock); var catalog=new ProductCatalog(new ProductDao(database),pricing);
        var future=new PromotionConfiguration(1001,0,new PromotionTerms(100,900),clock.instant().plusSeconds(1),clock.instant().plusSeconds(2),true);
        service.save(admin,future);
        assertEquals(1290,catalog.list(20,0).items().getFirst().promotion().estimatedUnitCents());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(1071,catalog.list(20,0).items().getFirst().promotion().estimatedUnitCents());
        clock.advance(Duration.ofMillis(999));
        assertEquals(1071,catalog.list(20,0).items().getFirst().promotion().estimatedUnitCents());
        clock.advance(Duration.ofMillis(1));
        assertEquals(1290,catalog.list(20,0).items().getFirst().promotion().estimatedUnitCents());
        service.save(admin,draft(1,false));
        assertEquals(1290,catalog.list(20,0).items().getFirst().promotion().estimatedUnitCents());
    }

    @Test void failedSaveRollsBackAndPreviewReadsChangedCurrentPriceWithoutAlteringSavedActivity() throws Exception {
        setup(); service.save(admin,draft(0,true));
        try(var c=database.openWriteConnection(); var s=c.createStatement()) {
            s.executeUpdate("UPDATE products SET price_cents=5 WHERE product_id=1001");
            s.execute("CREATE TRIGGER reject_change BEFORE UPDATE ON promotions BEGIN SELECT RAISE(ABORT,'test failure'); END");
        }
        var before=service.load(admin,1001).configuration();
        assertThrows(java.sql.SQLException.class,() -> service.save(admin,draft(1,false)));
        assertEquals(before,service.load(admin,1001).configuration());
        byte[] bytes=Files.readAllBytes(database.file());
        var preview=service.preview(admin,1001,3,new PromotionTerms(0,900));
        assertEquals("15",preview.breakdown().estimatedTotalCents().toString());
        assertArrayEquals(bytes,Files.readAllBytes(database.file()));
    }
}
