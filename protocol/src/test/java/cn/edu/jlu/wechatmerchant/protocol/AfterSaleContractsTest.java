package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AfterSaleContractsTest {
    final Gson json=new GsonBuilder().serializeNulls().create();final String key="12345678-1234-1234-1234-123456789abc";
    AfterSaleSnapshot sample(){return new AfterSaleSnapshot(1,9,key,1,"REQUESTED","申请原因","",1000,0,0,BigInteger.valueOf(14200));}
    @Test void requestReceiptHistoryAndAllApprovedStatesRoundTrip() throws Exception {
        var request=new ApplyReturnRequest(key,9,"申请原因");assertEquals(request,ApplyReturnRequest.parse(json.toJsonTree(request).getAsJsonObject()));assertFalse(request.toString().contains("申请原因"));
        var receipt=new AfterSaleReceipt(key,"SUCCESS",sample(),null);assertEquals(receipt,AfterSaleReceipt.parse(json.toJsonTree(receipt).getAsJsonObject(),key));var page=new AfterSalePage(9,List.of(sample()),20,0,false);assertEquals(page,AfterSalePage.parse(json.toJsonTree(page).getAsJsonObject(),9,20,0));
        for(var sale:List.of(new AfterSaleSnapshot(1,9,key,2,"APPROVED","原因","",1000,2000,0,BigInteger.ZERO),new AfterSaleSnapshot(1,9,key,2,"REJECTED","原因","拒绝说明",1000,2000,0,BigInteger.ZERO),new AfterSaleSnapshot(1,9,key,3,"COMPLETED","原因","",1000,2000,3000,BigInteger.ZERO)))assertEquals(sale,AfterSaleSnapshot.parse(json.toJsonTree(sale).getAsJsonObject()));
    }
    @ParameterizedTest @ValueSource(strings={"version","status","reviewedAt","completedAt","refundCents","reason","reviewNote"})
    void inconsistentStatusMoneyAndTextAreRejected(String field){var body=json.toJsonTree(sample()).getAsJsonObject();switch(field){case "version" -> body.addProperty(field,2);case "status" -> body.addProperty(field,"APPROVED");case "reviewedAt","completedAt" -> body.addProperty(field,1);case "refundCents" -> body.addProperty(field,"14200");case "reason" -> body.addProperty(field," ");case "reviewNote" -> body.addProperty(field,"尚未审核就有说明");}assertThrows(ProtocolException.class,() -> AfterSaleSnapshot.parse(body));}
    @Test void forgedOwnerWrongCorrelationAndDuplicateHistoryIdsAreRejected() throws Exception {
        var body=json.toJsonTree(new ApplyReturnRequest(key,9,"原因")).getAsJsonObject();body.addProperty("userId",2);assertThrows(IllegalArgumentException.class,() -> ApplyReturnRequest.parse(body));
        var receipt=json.toJsonTree(new AfterSaleReceipt(key,"SUCCESS",sample(),null)).getAsJsonObject();assertThrows(ProtocolException.class,() -> AfterSaleReceipt.parse(receipt,UUID.randomUUID().toString()));receipt.add("application",JsonNull.INSTANCE);assertThrows(ProtocolException.class,() -> AfterSaleReceipt.parse(receipt,key));
        assertThrows(IllegalArgumentException.class,() -> new AfterSalePage(9,List.of(sample(),sample()),20,0,false));var page=json.toJsonTree(new AfterSalePage(9,List.of(sample()),20,0,false)).getAsJsonObject();assertThrows(ProtocolException.class,() -> AfterSalePage.parse(page,10,20,0));assertThrows(IllegalArgumentException.class,() -> new ApplyReturnRequest(key,9,"x".repeat(501)));assertThrows(IllegalArgumentException.class,() -> new ApplyReturnRequest("-".repeat(36),9,"原因"));
    }
}
