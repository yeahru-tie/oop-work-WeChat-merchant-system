package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CouponMigrationTest {
    @TempDir Path folder;
    private SqliteDatabase legacy() throws Exception {
        var db=new SqliteDatabase(folder.resolve("已有 活动.db"));db.initialize();
        new CartService(db).add(1,1001,2,0);var sessions=new SessionStore();
        String admin=sessions.create(new UserDao(db).findByUsername("shop_admin").orElseThrow().identity());
        new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(100,900),Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2030-01-01T00:00:00Z"),true));
        try(var c=db.openWriteConnection();var s=c.createStatement()) {
            s.execute("DROP TABLE checkout_attempts");s.execute("DROP TABLE order_items");s.execute("DROP TABLE orders");s.execute("DROP TABLE user_coupons");s.execute("DROP TABLE coupon_campaigns");s.execute("PRAGMA user_version=4");s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=2");
        }
        return db;
    }
    @Test void v4BackupAndAtomicUpgradePreserveCartAccountsAndExistingPromotionWithoutSeedingCoupons() throws Exception {
        var db=legacy();assertThrows(SQLException.class,db::openReadOnlyConnection);db.initialize();
        var cart=new CartService(db).list(1);assertEquals(1,cart.cartVersion());assertEquals(2,cart.items().getFirst().quantity());assertEquals("2142",cart.estimatedTotalCents().toString());
        assertFalse(new UserDao(db).findByUsername("buyer_b").orElseThrow().enabled());
        try(var c=db.openReadOnlyConnection();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM coupon_campaigns")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
        try(var paths=Files.list(folder)) {
            Path backup=paths.filter(p -> p.getFileName().toString().contains(".v4-")).findFirst().orElseThrow();
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup);var s=c.createStatement()) {
                try(var r=s.executeQuery("PRAGMA user_version")){assertTrue(r.next());assertEquals(4,r.getInt(1));}
                try(var r=s.executeQuery("SELECT reduction_cents FROM promotions")){assertTrue(r.next());assertEquals(100,r.getInt(1));}
                assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM user_coupons"));
            }
        }
        byte[] before=Files.readAllBytes(db.file());db.initialize();assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
    @Test void conflictRollsBackAndUnknownIdentityRemainsUntouched() throws Exception {
        var db=legacy();try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("CREATE TABLE user_coupons(important TEXT)");s.execute("INSERT INTO user_coupons VALUES('keep')");}
        byte[] before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
        try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("PRAGMA application_id=12345");}
        before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
}
