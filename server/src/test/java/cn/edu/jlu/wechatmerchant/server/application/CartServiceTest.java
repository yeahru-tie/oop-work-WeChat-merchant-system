package cn.edu.jlu.wechatmerchant.server.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.server.persistence.SqliteDatabase;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class CartServiceTest {
    @TempDir Path directory;
    private SqliteDatabase fresh() throws Exception { var db=new SqliteDatabase(directory.resolve("test.db")); db.initialize(); return db; }
    @Test void mergesSetsRemovesAndNeverReusesAnOldItemId() throws Exception {
        var db=fresh(); var carts=new CartService(db);
        var first=carts.add(1,1001,2,0); long id=first.items().getFirst().itemId();
        var merged=carts.add(1,1001,3,1); assertEquals(5,merged.items().getFirst().quantity()); assertEquals(id,merged.items().getFirst().itemId());
        assertEquals(1,carts.edit(1,id,1,2).items().getFirst().quantity());
        assertTrue(carts.edit(1,id,null,3).items().isEmpty());
        assertTrue(carts.add(1,1001,1,4).items().getFirst().itemId()>id);
        assertEquals(0,carts.list(2).cartVersion()); assertTrue(carts.list(2).items().isEmpty());
    }
    @Test void stockFailureRollsBackAndStockDropStillAllowsDecreasingOrRemoving() throws Exception {
        var db=fresh(); var carts=new CartService(db);
        var cart=carts.add(1,1001,5,0); long id=cart.items().getFirst().itemId();
        try (var c=db.openWriteConnection(); var s=c.createStatement()) { s.executeUpdate("UPDATE products SET stock=2 WHERE product_id=1001"); }
        assertEquals("OUT_OF_STOCK",assertThrows(CartService.Rejected.class,() -> carts.add(1,1001,1,1)).code());
        assertEquals(1,carts.list(1).cartVersion()); assertEquals(5,carts.list(1).items().getFirst().quantity());
        var reduced=carts.edit(1,id,4,1); assertEquals(4,reduced.items().getFirst().quantity()); assertEquals(2,reduced.items().getFirst().stock());
        assertEquals("OUT_OF_STOCK",assertThrows(CartService.Rejected.class,() -> carts.edit(1,id,5,2)).code());
        assertTrue(carts.edit(1,id,null,2).items().isEmpty());
        assertEquals("NOT_FOUND",assertThrows(CartService.Rejected.class,() -> carts.add(1,9999,1,3)).code());
    }
    @Test void twoConcurrentWritesWithTheSameVersionCannotLoseUpdatesOrDoubleAdd() throws Exception {
        var db=fresh(); var carts=new CartService(db); var start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> { start.await(); try { carts.add(1,1001,1,0); return "OK"; } catch(CartService.Rejected e) { return e.code(); } });
            var two=pool.submit(() -> { start.await(); try { new CartService(db).add(1,1001,1,0); return "OK"; } catch(CartService.Rejected e) { return e.code(); } });
            start.countDown();
            assertEquals(java.util.Set.of("OK","CART_CHANGED"),java.util.Set.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS)));
        }
        assertEquals(1,carts.list(1).items().getFirst().quantity()); assertEquals(1,carts.list(1).cartVersion());
        assertEquals("CART_CHANGED",assertThrows(CartService.Rejected.class,() -> carts.add(1,1001,1,0)).code());
    }
    @Test void failedSqlDoesNotLeaveAParentVersionOrPartialItemBehind() throws Exception {
        var db=fresh(); var carts=new CartService(db);
        try (var c=db.openWriteConnection(); var s=c.createStatement()) {
            s.execute("CREATE TRIGGER fail_cart AFTER INSERT ON cart_items BEGIN SELECT RAISE(ABORT,'test'); END");
        }
        assertThrows(SQLException.class,() -> carts.add(1,1001,1,0));
        assertEquals(new CartSnapshot(0,java.util.List.of()),carts.list(1));
        try (var c=db.openReadOnlyConnection(); var s=c.createStatement(); var r=s.executeQuery("SELECT count(*) FROM carts")) { assertTrue(r.next()); assertEquals(0,r.getInt(1)); }
    }
    @Test void rejectsOtherUsersItemWithoutChangingTheirCart() throws Exception {
        var carts=new CartService(fresh()); var original=carts.add(2,1001,1,0);
        assertEquals("NOT_FOUND",assertThrows(CartService.Rejected.class,() -> carts.edit(1,original.items().getFirst().itemId(),null,0)).code());
        assertEquals(original,carts.list(2)); assertTrue(carts.list(1).items().isEmpty());
    }
    @Test void fullCartIsNotSilentlyTruncatedButExistingProductsCanStillBeMerged() throws Exception {
        var db=fresh(); var carts=new CartService(db);
        try (var c=db.openWriteConnection(); var s=c.prepareStatement("INSERT INTO products VALUES(?,'测试',1,10,'')")) {
            for(int i=2000;i<2101;i++) { s.setInt(1,i); s.addBatch(); } s.executeBatch();
        }
        for(int i=0;i<100;i++) carts.add(1,2000+i,1,i);
        assertEquals(100,carts.list(1).items().size());
        assertEquals("CART_LIMIT",assertThrows(CartService.Rejected.class,() -> carts.add(1,2100,1,100)).code());
        assertEquals(2,carts.add(1,2000,1,100).items().getFirst().quantity());
    }
}
