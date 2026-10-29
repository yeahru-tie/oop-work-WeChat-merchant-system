package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.application.CartService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CartMigrationTest {
    @TempDir Path directory;
    private SqliteDatabase v2(String name) throws Exception {
        var database=new SqliteDatabase(directory.resolve(name)); database.initialize();
        try (var c=database.openWriteConnection(); var s=c.createStatement()) {
            s.execute("DROP TABLE message_reads");s.execute("DROP TABLE notifications");s.execute("DROP TABLE refunds");s.execute("DROP TABLE return_attempts");s.execute("DROP TABLE after_sales");s.execute("DROP TABLE checkout_attempts");s.execute("DROP TABLE order_items");s.execute("DROP TABLE orders");s.execute("DROP TABLE user_coupons"); s.execute("DROP TABLE coupon_campaigns"); s.execute("DROP TABLE promotions"); s.execute("DROP TABLE cart_items"); s.execute("DROP TABLE carts"); s.execute("PRAGMA user_version=2");
            s.executeUpdate("UPDATE users SET enabled=0 WHERE user_id=1");
            s.executeUpdate("DELETE FROM users WHERE user_id=2");
            s.executeUpdate("UPDATE products SET stock=7,name='实际商品' WHERE product_id=1001");
        }
        return database;
    }
    @Test void upgradesV2WithBackupWithoutReseedingProductsOrAccounts() throws Exception {
        var database=v2("old.db");
        assertThrows(SQLException.class,database::openReadOnlyConnection);
        database.initialize();
        assertFalse(new UserDao(database).findByUsername("buyer_a").orElseThrow().enabled());
        assertTrue(new UserDao(database).findByUsername("buyer_b").isEmpty());
        assertEquals("实际商品",new ProductDao(database).findPage(1,0).getFirst().name());
        assertTrue(new CartService(database).list(1).items().isEmpty());
        try (var paths=Files.list(directory)) {
            Path backup=paths.filter(p -> p.getFileName().toString().startsWith("old.db.v2-")).findFirst().orElseThrow();
            try (var c=DriverManager.getConnection("jdbc:sqlite:"+backup); var s=c.createStatement()) {
                try (var r=s.executeQuery("PRAGMA user_version")) { assertTrue(r.next()); assertEquals(2,r.getInt(1)); }
                try (var r=s.executeQuery("SELECT enabled FROM users WHERE user_id=1")) { assertTrue(r.next()); assertEquals(0,r.getInt(1)); }
                try (var r=s.executeQuery("SELECT stock FROM products WHERE product_id=1001")) { assertTrue(r.next()); assertEquals(7,r.getInt(1)); }
                assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM carts"));
            }
        }
    }
    @Test void conflictingTableRollsBackMigrationAndKeepsExistingData() throws Exception {
        var database=v2("conflict.db");
        try (var c=database.openWriteConnection(); var s=c.createStatement()) {
            s.execute("CREATE TABLE cart_items(important TEXT)"); s.execute("INSERT INTO cart_items VALUES('keep')");
        }
        byte[] before=Files.readAllBytes(database.file());
        assertThrows(SQLException.class,database::initialize);
        assertArrayEquals(before,Files.readAllBytes(database.file()));
    }
    @Test void repeatedLatestInitializationPreservesCartIdsVersionsAndQuantity() throws Exception {
        var database=new SqliteDatabase(directory.resolve("current.db")); database.initialize();
        var carts=new CartService(database);
        var before=carts.add(1,1001,2,0);
        byte[] bytes=Files.readAllBytes(database.file());
        database.initialize(); database.initialize();
        assertArrayEquals(bytes,Files.readAllBytes(database.file()));
        assertEquals(before,new CartService(new SqliteDatabase(database.file())).list(1));
        try (var paths=Files.list(directory)) { assertEquals(1,paths.count()); }
    }
}
