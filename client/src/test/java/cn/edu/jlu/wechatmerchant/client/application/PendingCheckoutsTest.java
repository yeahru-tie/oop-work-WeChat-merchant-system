package cn.edu.jlu.wechatmerchant.client.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingCheckoutsTest {
    @TempDir Path directory;
    private final UserIdentity a=new UserIdentity(1,"buyer_a","买家 A",UserIdentity.Role.BUYER),b=new UserIdentity(2,"buyer_b","买家 B",UserIdentity.Role.BUYER);
    private PendingCheckouts.Entry entry(){return new PendingCheckouts.Entry(new CheckoutRequest(UUID.randomUUID().toString(),1,"a".repeat(64),null,List.of(1L)),List.of(1L,2L));}
    @Test void atomicRecordSurvivesReopenAndIsOwnerScopedWithoutCredentials() throws Exception {
        var store=new PendingCheckouts(directory);var first=entry();store.save(a,first);store.save(a,first);assertEquals(first,new PendingCheckouts(directory).first(a).orElseThrow());assertTrue(store.first(b).isEmpty());
        try(var files=Files.list(directory)){var json=Files.readString(files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow());assertFalse(json.contains("password"));assertFalse(json.contains("sessionToken"));}
        store.remove(a,first);assertTrue(store.first(a).isEmpty());
    }
    @Test void damagedRecordBlocksNewPurchasesAndIsPreservedInsteadOfIgnored() throws Exception {
        var store=new PendingCheckouts(directory);var first=entry();store.save(a,first);Path file=directory.resolve("1-"+first.request().checkoutKey()+".json");Files.writeString(file,"{broken");
        assertThrows(java.io.IOException.class,() -> store.first(a));assertEquals("{broken",Files.readString(file));assertTrue(store.first(b).isEmpty());
    }
    @Test void multiplePendingKeysRemainUntilIndividuallyResolvedAndConflictsDoNotOverwrite() throws Exception {
        var store=new PendingCheckouts(directory);var first=entry();var second=entry();store.save(a,first);store.save(a,second);var chosen=store.first(a).orElseThrow();store.remove(a,chosen);assertTrue(store.first(a).isPresent());
        var changed=new PendingCheckouts.Entry(new CheckoutRequest(store.first(a).orElseThrow().request().checkoutKey(),2,"b".repeat(64),null,List.of(1L)),List.of(1L,2L));assertThrows(java.io.IOException.class,() -> store.save(a,changed));
    }
}
