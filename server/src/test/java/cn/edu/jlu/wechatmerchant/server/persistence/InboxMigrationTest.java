package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.CartSelection;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InboxMigrationTest {
    @TempDir Path folder;
    private SqliteDatabase old() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("历史 订单.db"))){
            var cart=f.carts.add(1,1001,1,0);f.orders.checkout(f.buyer,f.request(f.buyer,cart,null,CartSelection.all(cart)));
            f.sql("DROP TABLE message_reads");f.sql("DROP TABLE notifications");f.sql("PRAGMA user_version=7");return f.db;
        }
    }
    @Test void v7BackupKeepsHistoricalTransactionsAndDoesNotInventOldMessages() throws Exception {
        var db=old();byte[] old=Files.readAllBytes(db.file());db.initialize();
        try(var paths=Files.list(folder)){var backup=paths.filter(p -> p.toString().contains(".v7-")).findFirst().orElseThrow();
            try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup);var s=c.createStatement();var r=s.executeQuery("PRAGMA user_version")){assertTrue(r.next());assertEquals(7,r.getInt(1));}}
        try(var c=db.openReadOnlyConnection();var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT count(*) FROM orders")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            try(var r=s.executeQuery("SELECT count(*) FROM notifications")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            try(var r=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(r.next());}
        }
        assertFalse(java.util.Arrays.equals(old,Files.readAllBytes(db.file())));byte[] stable=Files.readAllBytes(db.file());db.initialize();assertArrayEquals(stable,Files.readAllBytes(db.file()));
    }
    @Test void conflictingNewTableRollsBackWithoutResettingOriginal() throws Exception {
        var db=old();try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("CREATE TABLE message_reads(important TEXT)");s.execute("INSERT INTO message_reads VALUES('preserve')");}
        byte[] before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
        try(var c=db.openWriteConnection();var s=c.createStatement()){try(var r=s.executeQuery("SELECT important FROM message_reads")){assertTrue(r.next());assertEquals("preserve",r.getString(1));}assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM notifications"));}
    }
}
