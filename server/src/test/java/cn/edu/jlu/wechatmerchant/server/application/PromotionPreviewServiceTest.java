package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.server.domain.promotion.PromotionTerms;
import cn.edu.jlu.wechatmerchant.server.persistence.ProductDao;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromotionPreviewServiceTest {
    @TempDir Path directory;
    private SqliteDatabase fresh() throws Exception {
        var db = new SqliteDatabase(directory.resolve("preview.db")); db.initialize(); return db;
    }

    @Test void previewReadsCurrentPriceAndDoesNotChangeProductsCartAccountsOrSchema() throws Exception {
        var db = fresh();
        new CartService(db).add(1, 1001, 1, 0);
        try (var c = db.openWriteConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE products SET price_cents=10000,stock=2 WHERE product_id=1001");
        }
        byte[] before = Files.readAllBytes(db.file());
        var service = new PromotionPreviewService(new ProductDao(db));
        var result = service.preview(1001, 3, PromotionTerms.parse("10", "9"));
        assertEquals(2, result.product().stock());
        assertEquals(new BigInteger("24300"), result.breakdown().estimatedTotalCents());
        assertArrayEquals(before, Files.readAllBytes(db.file()));
        try (var c = db.openWriteConnection(); var s = c.createStatement()) {
            s.executeUpdate("UPDATE products SET price_cents=9999 WHERE product_id=1001");
        }
        var changed = service.preview(1001, 3, PromotionTerms.parse("10", "9"));
        assertEquals(9999, changed.product().priceCents());
        assertEquals(new BigInteger("24297"), changed.breakdown().estimatedTotalCents());
        assertEquals(10000, result.product().priceCents());
        assertEquals(1, new CartService(db).list(1).items().getFirst().quantity());
    }

    @Test void invalidOrMissingProductAndQuantityLeaveTheDatabaseUntouched() throws Exception {
        var db = fresh(); var service = new PromotionPreviewService(new ProductDao(db));
        var terms = PromotionTerms.parse("0", "10"); byte[] before = Files.readAllBytes(db.file());
        assertThrows(IllegalArgumentException.class, () -> service.preview(9999, 1, terms));
        assertThrows(IllegalArgumentException.class, () -> service.preview(0, 1, terms));
        assertThrows(IllegalArgumentException.class, () -> service.preview(1001, 0, terms));
        assertArrayEquals(before, Files.readAllBytes(db.file()));
    }

    @Test void previewDoesNotCreateAMissingDatabase() {
        Path missing = directory.resolve("missing.db");
        var service = new PromotionPreviewService(new ProductDao(new SqliteDatabase(missing)));
        assertThrows(SQLException.class, () -> service.preview(1001, 1, PromotionTerms.parse("0", "9")));
        assertFalse(Files.exists(missing));
    }

    @Test void previewRefusesOldSchemaWithoutMigratingIt() throws Exception {
        var db = fresh();
        try (var c = db.openWriteConnection(); var s = c.createStatement()) { s.execute("PRAGMA user_version=2"); }
        byte[] before = Files.readAllBytes(db.file());
        var service = new PromotionPreviewService(new ProductDao(db));
        assertThrows(SQLException.class, () -> service.preview(1001, 1, PromotionTerms.parse("0", "9")));
        assertArrayEquals(before, Files.readAllBytes(db.file()));
    }
}
