package cn.edu.jlu.wechatmerchant.server.persistence;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import cn.edu.jlu.wechatmerchant.server.support.TransactionFixture;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AfterSaleMigrationTest {
    @TempDir Path folder;
    private SqliteDatabase old() throws Exception {
        try(var f=new TransactionFixture(folder.resolve("已有成交 已用券.db"))){var cart=f.carts.add(1,1001,2,0);long coupon=f.coupon();f.orders.checkout(f.buyer,f.request(f.buyer,cart,coupon,CartSelection.all(cart)));f.sql("UPDATE users SET enabled=0 WHERE user_id=2");f.sql("DROP TABLE message_reads");f.sql("DROP TABLE notifications");f.sql("DROP TABLE refunds");f.sql("DROP TABLE return_attempts");f.sql("DROP TABLE after_sales");f.sql("PRAGMA user_version=6");return f.db;}
    }
    private Map<String,List<List<Object>>> contents(Path file) throws Exception {
        var map=new TreeMap<String,List<List<Object>>>();try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){for(String table:List.of("products","users","carts","cart_items","promotions","coupon_campaigns","user_coupons","orders","order_items","checkout_attempts")){var data=new ArrayList<List<Object>>();try(var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){while(r.next()){var row=new ArrayList<Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.add(r.getObject(i));data.add(row);}}map.put(table,data);}}return map;
    }
    @Test void v6BackupPreservesAllTenTablesAndIssuedRightsAndOrdersWithoutSeedingReturns() throws Exception {
        var db=old();var before=contents(db.file());assertThrows(SQLException.class,db::openReadOnlyConnection);db.initialize();assertTrue(before.equals(contents(db.file())),"Original tables must be preserved; do not print account fields.");try(var c=db.openReadOnlyConnection();var s=c.createStatement()){for(String table:List.of("after_sales","return_attempts","refunds"))try(var r=s.executeQuery("SELECT count(*) FROM "+table)){assertTrue(r.next());assertEquals(0,r.getInt(1));}}
        try(var files=Files.list(folder)){var backup=files.filter(p -> p.getFileName().toString().contains(".v6-")).findFirst().orElseThrow();assertTrue(before.equals(contents(backup)),"Backup must preserve original tables without logging account fields.");try(var c=DriverManager.getConnection("jdbc:sqlite:"+backup);var s=c.createStatement();var r=s.executeQuery("PRAGMA user_version")){assertTrue(r.next());assertEquals(6,r.getInt(1));}}
        byte[] stable=Files.readAllBytes(db.file());db.initialize();assertArrayEquals(stable,Files.readAllBytes(db.file()));assertFalse(new UserDao(db).findByUsername("buyer_b").orElseThrow().enabled());
    }
    @Test void conflictRollsBackAndUnknownSchemaIsNotReset() throws Exception {
        var db=old();try(var c=db.openWriteConnection();var s=c.createStatement()){s.execute("CREATE TABLE refunds(important TEXT)");s.execute("INSERT INTO refunds VALUES('keep')");}byte[] before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));try(var c=db.openWriteConnection();var s=c.createStatement()){assertThrows(SQLException.class,() -> s.executeQuery("SELECT * FROM after_sales"));s.execute("PRAGMA user_version=99");}before=Files.readAllBytes(db.file());assertThrows(SQLException.class,db::initialize);assertArrayEquals(before,Files.readAllBytes(db.file()));
    }
}
