package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderContractsTest {
    private final Gson gson=new GsonBuilder().serializeNulls().create();private final String key="10000000-0000-0000-0000-000000000001";
    private OrderSnapshot order(){return new OrderSnapshot(1,key,100,"COMPLETED",List.of(new OrderSnapshot.Item(1,1001,"商品",10000,2,new PromotionPrice(1,10000,1000,900,9000,8100))),new OrderSnapshot.Coupon(1,2,"满150减20",15000,2000,0,10000),BigInteger.valueOf(20000),BigInteger.valueOf(3800),BigInteger.valueOf(2000),BigInteger.valueOf(14200));}
    @Test void orderReceiptAndSummaryRoundTripWithoutCurrentProductValues() throws Exception {
        var original=order();assertEquals(original,OrderSnapshot.parse(gson.toJsonTree(original).getAsJsonObject()));
        var receipt=new CheckoutReceipt(key,"SUCCESS",original,null);assertEquals(receipt,CheckoutReceipt.parse(gson.toJsonTree(receipt).getAsJsonObject(),key));
        var failed=new CheckoutReceipt(key,"FAILED",null,"PRICE_CHANGED");assertEquals(failed,CheckoutReceipt.parse(gson.toJsonTree(failed).getAsJsonObject(),key));
        var page=new OrderPage(List.of(new OrderPage.Summary(1,100,"COMPLETED",1,original.paidCents())),20,0,false);assertEquals(page,OrderPage.parse(gson.toJsonTree(page).getAsJsonObject(),20,0));
    }
    @ParameterizedTest @ValueSource(strings={"originalCents","productReductionCents","couponReductionCents","paidCents"})
    void forgedHistoricalMoneyOrNumericStringsRejectResponse(String field){var body=gson.toJsonTree(order()).getAsJsonObject();body.addProperty(field,1);assertThrows(ProtocolException.class,() -> OrderSnapshot.parse(body));body.addProperty(field,"14200");assertThrows(ProtocolException.class,() -> OrderSnapshot.parse(body));}
    @Test void partialQuoteConservesSelectedAmountsAndRejectsDifferentSelection() throws Exception {
        var one=new CartSnapshot.Item(1,1001,"商品",10000,20,2,new PromotionPrice(1,10000,1000,900,9000,8100));var two=new CartSnapshot.Item(2,1002,"未选",300,20,2,new PromotionPrice(0,300,0,1000,300,300));
        var cart=new CartSnapshot(1,List.of(one,two));var quote=new QuoteSnapshot(cart,List.of(1L),1L,"APPLIED",BigInteger.valueOf(2000),BigInteger.valueOf(14200),100,10000,"a".repeat(64));
        var body=gson.toJsonTree(quote).getAsJsonObject();assertEquals(quote,QuoteSnapshot.parse(body,1,1L,List.of(1L)));assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,1,1L,List.of(2L)));assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,1,1L));
        body.add("itemIds",JsonParser.parseString("[2,1]"));assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,1,1L,List.of(1L)));
    }
    @ParameterizedTest @ValueSource(strings={"[]","[1,1]","[2,1]","[0]","[-1]","[1.5]","[\"1\"]","[9223372036854775808]"})
    void invalidSelectionCannotBecomeCheckout(String selection){var body=new JsonObject();body.addProperty("checkoutKey",key);body.addProperty("cartVersion",1);body.addProperty("quoteDigest","a".repeat(64));body.add("couponId",JsonNull.INSTANCE);body.add("itemIds",JsonParser.parseString(selection));assertThrows(RuntimeException.class,() -> CheckoutRequest.parse(body));}
    @Test void wrongReceiptKeyOrderOwnerFieldsAndUnknownSuccessShapesAreRejected() {
        var body=gson.toJsonTree(new CheckoutReceipt(key,"SUCCESS",order(),null)).getAsJsonObject();assertThrows(ProtocolException.class,() -> CheckoutReceipt.parse(body,"20000000-0000-0000-0000-000000000002"));
        body.addProperty("state","FAILED");assertThrows(ProtocolException.class,() -> CheckoutReceipt.parse(body,key));
        var command=gson.toJsonTree(new CheckoutRequest(key,1,"a".repeat(64),null,List.of(1L))).getAsJsonObject();command.addProperty("userId",2);assertThrows(IllegalArgumentException.class,() -> CheckoutRequest.parse(command));
    }
}
