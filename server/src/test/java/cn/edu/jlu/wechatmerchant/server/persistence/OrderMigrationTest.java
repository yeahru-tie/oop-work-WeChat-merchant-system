package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.application.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.*;
import cn.edu.jlu.wechatmerchant.server.security.SessionStore;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrderMigrationTest {
    @TempDir Path folder;
    private SqliteDatabase legacy() throws Exception {
        var db=new SqliteDatabase(folder.resolve("已有 券与车.db"));db.initialize();
        new CartService(db).add(1,1001,2,0);var sessions=new SessionStore();var users=new UserDao(db);
        String admin=sessions.create(users.findByUsername("shop_admin").orElseThrow().identity()),buyer=sessions.create(users.findByUsername("buyer_a").orElseThrow().identity());
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);new PromotionManagementService(db,sessions).save(admin,new PromotionConfiguration(1001,0,new PromotionTerms(100,900),now.minusSeconds(60),now.plusSeconds(3600),true));
        var coupons=new CouponService(db,sessions);var draft=coupons.saveDraft(admin,new CouponCampaign(0,0,"保留的券",0,100,now.minusSeconds(60).toEpochMilli(),now.plusSeconds(3600).toEpochMilli(),7,0,false,false),java.util.UUID.randomUUID().toString());
        coupons.publish(admin,draft.campaignId(),draft.version());coupons.claim(buyer,draft.campaignId());
        try(var c=db.openWriteConnection();var s=c.createStatement()) {
            s.execute("DROP TABLE message_reads");s.execute("DROP TABLE notifications");s.execute("DROP TABLE refunds");s.execute("DROP TABLE return_attempts");s.execute("DROP TABLE after_sales");s.execute("ALTER TABLE user_coupons DROP COLUMN used_order_id");s.execute("DROP TABLE checkout_attempts");s.execute("DROP TABLE order_items");s.execute("DROP TABLE orders");s.execute("PRAGMA user_version=5");s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=2");
        }
        return db;
    }
    @Test void v5UpgradePreservesIssuedTermsCartPromotionAccountsAndOriginalBackup() throws Exception {
        var db=legacy();assertThrows(SQLException.class,db::openReadOnlyConnection);db.initialize();
        assertEquals(2,new CartService(db).list(1).items().getFirst().quantity());assertFalse(new UserDao(db).findByUsername("buyer_b").orElseThrow().enabled());
        try(var c=db.openReadOnlyConnection();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT c.name,c.claimed,c.total,u.used,u.used_order_id FROM coupon_campaigns c JOIN user_coupons u ON u.campaign_id=c.campaign_id")){assertTrue(r.next());assertEquals("保留的券",r.getString(1));assertEquals(1,r.getInt(2));assertEquals(7,r.getInt(3));assertEquals(0,r.getInt(4));assertNull(r.getObject(5));}
            try(var r=s.executeQuery("SELECT count(*) FROM orders")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            assertThrows(SQLException.class,() -> s.executeUpdate("UPDATE coupon_campaigns SET discount_cents=2"));
        }
        try(var files=Files.list(folder)) {
            Path backup=files.filter(p -> p.getFileName().toString().contains(".v5-")).findFirst().orElseThrow();
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup);var s=c.createStatement()) {
                try(var r=s.executeQuery("PRAGMA user_version")){assertTrue(r.next());assertEquals(5,r.getInt(1));}
                try(var r=s.executeQuery("SELECT count(*) FROM user_coupons WHERE used=0")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
                assertThrows(SQLException.class,() -> s.executeQuery("SELECT used_order_id FROM user_coupons"));assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM orders"));
            }
        }
        byte[] before=Files.readAllBytes(db.file());db.initialize();new CartService(db).list(1);assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
    @Test void conflictingTableRollsBackAllNewTablesAndUnknownVersionRemainsUntouched() throws Exception {
        var db=legacy();try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("CREATE TABLE order_items(important TEXT)");s.execute("INSERT INTO order_items VALUES('keep')");}
        byte[] before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
        try(var c=db.openWriteConnection();var s=c.createStatement()){try(var r=s.executeQuery("SELECT important FROM order_items")){assertTrue(r.next());assertEquals("keep",r.getString(1));}assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM orders"));s.execute("PRAGMA user_version=999");}
        before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
}
