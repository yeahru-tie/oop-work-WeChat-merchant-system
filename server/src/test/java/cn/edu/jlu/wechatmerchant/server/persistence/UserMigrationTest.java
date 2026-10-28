package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.protocol.UserIdentity;
import cn.edu.jlu.wechatmerchant.server.application.AuthenticationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserMigrationTest {
    @TempDir Path directory;

    @Test
    void migratesV1WithAReadableBackupAndPreservesActualProductEdits() throws Exception {
        var database = legacy("old.db");
        database.initialize();
        try (var connection = database.openReadOnlyConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT name,price_cents,stock FROM products")) {
            assertTrue(rows.next());
            assertEquals("已修改商品", rows.getString(1));
            assertEquals(999, rows.getInt(2));
            assertEquals(0, rows.getInt(3));
            assertFalse(rows.next());
        }
        try (var paths = Files.list(directory)) {
            Path backup = paths.filter(path -> path.getFileName().toString().startsWith("old.db.v1-")).findFirst().orElseThrow();
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + backup); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("PRAGMA user_version")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
                try (var rows = statement.executeQuery("SELECT count(*) FROM products")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                }
            }
        }
        var auth = new AuthenticationService(new UserDao(database));
        assertEquals(1, auth.authenticate("buyer_a", "BuyerA#2026!".toCharArray(), UserIdentity.Role.BUYER).orElseThrow().userId());
        assertEquals(2, auth.authenticate("buyer_b", "BuyerB#2026!".toCharArray(), UserIdentity.Role.BUYER).orElseThrow().userId());
        assertEquals(UserIdentity.Role.ADMIN, auth.authenticate("shop_admin", "ShopAdmin#2026!".toCharArray(),
                UserIdentity.Role.ADMIN).orElseThrow().role());
    }

    @Test
    void migrationFailureRollsBackAndDoesNotDeleteConflictingUserData() throws Exception {
        var database = legacy("conflict.db");
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users(important TEXT)");
            statement.execute("INSERT INTO users VALUES('keep-me')");
        }
        byte[] before = Files.readAllBytes(database.file());
        assertThrows(SQLException.class, database::initialize);
        assertArrayEquals(before, Files.readAllBytes(database.file()));
    }

    @Test
    void repeatedV2InitializationPreservesDisabledAndDeletedAccounts() throws Exception {
        var database = new SqliteDatabase(directory.resolve("fresh.db"));
        database.initialize();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET enabled=0 WHERE username='buyer_a'");
            statement.executeUpdate("DELETE FROM users WHERE username='buyer_b'");
        }
        byte[] before = Files.readAllBytes(database.file());
        database.initialize();
        assertArrayEquals(before, Files.readAllBytes(database.file()));
        var dao = new UserDao(database);
        assertFalse(dao.findByUsername("buyer_a").orElseThrow().enabled());
        assertTrue(dao.findByUsername("buyer_b").isEmpty());
        assertTrue(dao.findByUsername("' OR 1=1 --").isEmpty());
        assertFalse(dao.findByUsername("shop_admin").orElseThrow().toString().contains("pbkdf2"));
    }

    private SqliteDatabase legacy(String name) throws Exception {
        var database = new SqliteDatabase(directory.resolve(name));
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement();
                var resource = SqliteDatabase.class.getResourceAsStream("/db/schema-v1.sql")) {
            statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            statement.execute("INSERT INTO products VALUES(1001,'已修改商品',999,0,'保留说明')");
            statement.execute("PRAGMA application_id=" + SqliteDatabase.APPLICATION_ID);
            statement.execute("PRAGMA user_version=1");
        }
        return database;
    }
}
