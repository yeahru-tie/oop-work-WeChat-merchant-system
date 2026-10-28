package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;

import cn.edu.jlu.wechatmerchant.server.application.ProductCatalog;
import cn.edu.jlu.wechatmerchant.server.domain.Product;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void initializesRealSqliteWithStrictSchemaAndPackagedChineseSeeds() throws Exception {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("商品 资料/merchant.db"));
        database.initialize();
        var page = catalog(database).list(20, 0);
        assertEquals(3, page.items().size());
        assertEquals("演示笔记本", page.items().getFirst().name());
        assertEquals(1290L, page.items().getFirst().priceCents());
        try (var connection = database.openReadOnlyConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(rows.next());
                assertEquals(SqliteDatabase.VERSION, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("PRAGMA application_id")) {
                assertTrue(rows.next());
                assertEquals(SqliteDatabase.APPLICATION_ID, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT strict FROM pragma_table_list WHERE name='products'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT sqlite_version()")) {
                assertTrue(rows.next());
                assertEquals("3.53.4", rows.getString(1));
            }
        }
    }

    @Test
    void reopeningAndRepeatedInitializationPreserveEditsAndDeletions() throws Exception {
        Path file = directory.resolve("merchant.db");
        SqliteDatabase database = new SqliteDatabase(file);
        database.initialize();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE products SET name='已修改商品',price_cents=999,stock=0 WHERE product_id=1001");
            statement.executeUpdate("DELETE FROM products WHERE product_id=1002");
        }
        byte[] before = Files.readAllBytes(file);
        SqliteDatabase reopened = new SqliteDatabase(file);
        reopened.initialize();
        reopened.initialize();
        var products = catalog(reopened).list(20, 0).items();
        assertEquals(2, products.size());
        assertEquals("已修改商品", products.getFirst().name());
        assertEquals(999L, products.getFirst().priceCents());
        assertEquals(0, products.getFirst().stock());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test
    void anIntentionallyEmptyCatalogIsNotReseeded() throws Exception {
        SqliteDatabase database = fresh();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM products");
        }
        database.initialize();
        var page = catalog(database).list(20, 0);
        assertTrue(page.items().isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void failedSeedingRollsBackSchemaRowsAndMetadataTogether() throws Exception {
        Path file = directory.resolve("failed.db");
        Product duplicate = new Product(1, "重复编号", 100, 1, "");
        SqliteDatabase broken = new SqliteDatabase(file, List.of(duplicate, duplicate));
        assertThrows(SQLException.class, broken::initialize);
        try (var connection = broken.openWriteConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("PRAGMA application_id")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM sqlite_schema WHERE name='products'")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
        SqliteDatabase recovered = new SqliteDatabase(file);
        recovered.initialize();
        assertEquals(3, catalog(recovered).list(20, 0).items().size());
    }

    @Test
    void refusesUnownedExistingDatabaseWithoutChangingItsContents() throws Exception {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("foreign.db"));
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE important_data(value TEXT)");
            statement.executeUpdate("INSERT INTO important_data VALUES('keep-me')");
        }
        byte[] before = Files.readAllBytes(database.file());
        assertThrows(SQLException.class, database::initialize);
        assertArrayEquals(before, Files.readAllBytes(database.file()));
    }

    @Test
    void refusesNewerSchemaWithoutResettingIt() throws Exception {
        SqliteDatabase database = fresh();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=" + (SqliteDatabase.VERSION + 1));
        }
        byte[] before = Files.readAllBytes(database.file());
        assertThrows(SQLException.class, database::initialize);
        assertThrows(SQLException.class, () -> catalog(database).list(20, 0));
        assertArrayEquals(before, Files.readAllBytes(database.file()));
    }

    @Test
    void missingDatabaseQueryDoesNotCreateAFile() {
        Path missing = directory.resolve("missing.db");
        assertThrows(SQLException.class, () -> catalog(new SqliteDatabase(missing)).list(20, 0));
        assertFalse(Files.exists(missing));
    }

    @Test
    void queryConnectionsAreActuallyReadOnly() throws Exception {
        SqliteDatabase database = fresh();
        byte[] before = Files.readAllBytes(database.file());
        try (var connection = database.openReadOnlyConnection(); var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE products SET stock=999"));
        }
        assertEquals(3, catalog(database).list(20, 0).items().size());
        assertArrayEquals(before, Files.readAllBytes(database.file()));
    }

    @Test
    void constraintsRejectNegativeFractionalAndOverflowingValues() throws Exception {
        SqliteDatabase database = fresh();
        try (var connection = database.openWriteConnection(); var statement = connection.createStatement()) {
            for (String assignment : List.of("price_cents=-1", "price_cents=1.25", "stock=-1",
                    "stock=2147483648", "name=''", "name=NULL")) {
                assertThrows(SQLException.class,
                        () -> statement.executeUpdate("UPDATE products SET " + assignment + " WHERE product_id=1001"));
            }
        }
        assertEquals(1290L, catalog(database).list(20, 0).items().getFirst().priceCents());
    }

    @Test
    void paginationIsBoundedOrderedAndReportsWhetherMoreRowsExist() throws Exception {
        ProductCatalog catalog = catalog(fresh());
        var first = catalog.list(2, 0);
        assertEquals(List.of(1001L, 1002L), first.items().stream().map(cn.edu.jlu.wechatmerchant.protocol.ProductPage.Item::productId).toList());
        assertTrue(first.hasMore());
        var last = catalog.list(2, 2);
        assertEquals(List.of(1003L), last.items().stream().map(cn.edu.jlu.wechatmerchant.protocol.ProductPage.Item::productId).toList());
        assertFalse(last.hasMore());
        assertTrue(catalog.list(20, 99).items().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> catalog.list(0, 0));
        assertThrows(IllegalArgumentException.class, () -> catalog.list(101, 0));
        assertThrows(IllegalArgumentException.class, () -> catalog.list(20, -1));
    }

    @Test
    void integerAmountsAreNotConvertedToFloatingPoint() throws Exception {
        SqliteDatabase database = fresh();
        try (var connection = database.openWriteConnection();
                var statement = connection.prepareStatement("UPDATE products SET price_cents=?,stock=? WHERE product_id=1001")) {
            statement.setLong(1, Long.MAX_VALUE);
            statement.setInt(2, Integer.MAX_VALUE);
            statement.executeUpdate();
        }
        var product = catalog(database).list(1, 0).items().getFirst();
        assertEquals(Long.MAX_VALUE, product.priceCents());
        assertEquals(Integer.MAX_VALUE, product.stock());
    }

    private SqliteDatabase fresh() throws SQLException {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("merchant.db"));
        database.initialize();
        return database;
    }

    private static ProductCatalog catalog(SqliteDatabase database) {
        return new ProductCatalog(new ProductDao(database));
    }
}
