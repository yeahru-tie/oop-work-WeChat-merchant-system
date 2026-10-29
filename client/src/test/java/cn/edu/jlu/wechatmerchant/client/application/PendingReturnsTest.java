package cn.edu.jlu.wechatmerchant.client.application;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jlu.wechatmerchant.protocol.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingReturnsTest {
    @TempDir Path folder;
    private final UserIdentity a=new UserIdentity(1,"buyer_a","A",UserIdentity.Role.BUYER),b=new UserIdentity(2,"buyer_b","B",UserIdentity.Role.BUYER);
    @Test void originalRequestSurvivesRestartAndIsScopedWithoutCredentials() throws Exception {
        var value=new ApplyReturnRequest(UUID.randomUUID().toString(),9,"保留原申请原因");new PendingReturns(folder).save(a,value);var restored=new PendingReturns(folder);assertEquals(value,restored.first(a).orElseThrow());assertTrue(restored.first(b).isEmpty());try(var paths=Files.list(folder)){var text=Files.readString(paths.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow());assertFalse(text.contains("password"));assertFalse(text.contains("sessionToken"));}restored.remove(a,value);assertTrue(new PendingReturns(folder).first(a).isEmpty());
    }
    @Test void malformedAndWrongOwnerRecordsBlockAndArePreserved() throws Exception {
        var value=new ApplyReturnRequest(UUID.randomUUID().toString(),9,"原因");var store=new PendingReturns(folder);store.save(a,value);Path file=folder.resolve(a.userId()+"-"+value.returnKey()+".json");Files.writeString(file,"{broken");byte[] bytes=Files.readAllBytes(file);assertThrows(IOException.class,() -> store.first(a));assertArrayEquals(bytes,Files.readAllBytes(file));assertTrue(store.first(b).isEmpty());
    }
    @Test void sameKeyCannotOverwriteAnotherBodyAndMultipleRequestsAreResolvedIndividually() throws Exception {
        var store=new PendingReturns(folder);var one=new ApplyReturnRequest(UUID.randomUUID().toString(),9,"原因一");var two=new ApplyReturnRequest(UUID.randomUUID().toString(),10,"原因二");store.save(a,one);store.save(a,one);assertThrows(IOException.class,() -> store.save(a,new ApplyReturnRequest(one.returnKey(),9,"换原因")));store.save(a,two);var first=store.first(a).orElseThrow();store.remove(a,first);assertNotEquals(first,store.first(a).orElseThrow());
    }
}
