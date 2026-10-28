package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.CartService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromotionMigrationTest {
    @TempDir Path directory;
    private SqliteDatabase v3() throws Exception {
        var db=new SqliteDatabase(directory.resolve("原始 购物库.db")); db.initialize();
        new CartService(db).add(1,1001,2,0);
        try(var c=db.openWriteConnection(); var s=c.createStatement()) {
            s.execute("DROP TABLE checkout_attempts");s.execute("DROP TABLE order_items");s.execute("DROP TABLE orders");s.execute("DROP TABLE user_coupons"); s.execute("DROP TABLE coupon_campaigns"); s.execute("DROP TABLE promotions"); s.execute("PRAGMA user_version=3");
            s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=2");
            s.executeUpdate("UPDATE products SET price_cents=777 WHERE product_id=1001");
        }
        return db;
    }
    @Test void migrationBacksUpV3AndPreservesOriginalProductsUsersAndCart() throws Exception {
        var db=v3(); byte[] original=Files.readAllBytes(db.file());
        assertThrows(SQLException.class,db::openReadOnlyConnection);
        assertArrayEquals(original,Files.readAllBytes(db.file()));
        db.initialize();
        var cart=new CartService(db).list(1);
        assertEquals(1,cart.cartVersion()); assertEquals(2,cart.items().getFirst().quantity());
        assertEquals(777,cart.items().getFirst().priceCents());
        assertEquals(0,cart.items().getFirst().promotion().configurationVersion());
        assertFalse(new UserDao(db).findByUsername("buyer_b").orElseThrow().enabled());
        try(var paths=Files.list(directory)) {
            var backup=paths.filter(p -> p.getFileName().toString().contains(".v3-")).findFirst().orElseThrow();
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup); var s=c.createStatement()) {
                try(var rows=s.executeQuery("PRAGMA user_version")) { assertTrue(rows.next()); assertEquals(3,rows.getInt(1)); }
                try(var rows=s.executeQuery("SELECT quantity FROM cart_items")) { assertTrue(rows.next()); assertEquals(2,rows.getInt(1)); }
                assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM promotions"));
            }
        }
        byte[] current=Files.readAllBytes(db.file()); db.initialize();
        assertArrayEquals(current,Files.readAllBytes(db.file()));
    }
    @Test void conflictingPromotionTableRollsBackAndUnknownSchemaIsNotModified() throws Exception {
        var db=v3();
        try(var c=db.openWriteConnection(); var s=c.createStatement()) { s.execute("CREATE TABLE promotions(important TEXT)"); s.execute("INSERT INTO promotions VALUES('keep')"); }
        byte[] before=Files.readAllBytes(db.file());
        assertThrows(SQLException.class,db::initialize);
        assertArrayEquals(before,Files.readAllBytes(db.file()));
        try(var c=db.openWriteConnection(); var s=c.createStatement()) { s.execute("PRAGMA user_version=99"); }
        before=Files.readAllBytes(db.file());
        assertThrows(SQLException.class,db::initialize); assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
}
