package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouponContractsTest {
    private final Gson gson=new GsonBuilder().serializeNulls().create();
    private CouponPage.Item coupon(){return new CouponPage.Item(5,2,"满150减20",15000,2000,1,10000,10,1,CouponPage.Status.USABLE);}
    private QuoteSnapshot quote(){
        var cart=new CartSnapshot(3,List.of(new CartSnapshot.Item(1,1001,"商品",10000,20,2,new PromotionPrice(1,10000,1000,900,9000,8100))));
        return new QuoteSnapshot(cart,5L,"APPLIED",BigInteger.valueOf(2000),BigInteger.valueOf(14200),100,10000,"a".repeat(64));
    }
    @Test void strictRoundTripKeepsOwnedCouponQuoteAndUnavailableNullAmount() throws Exception {
        var page=new CouponPage("OWNED",List.of(coupon()),20,0,false);
        assertEquals(page,CouponPage.parse(gson.toJsonTree(page).getAsJsonObject(),"OWNED",20,0));
        var value=quote();assertEquals(value,QuoteSnapshot.parse(gson.toJsonTree(value).getAsJsonObject(),3,5L));
        var invalid=new QuoteSnapshot(value.cart(),5L,"EXPIRED",BigInteger.ZERO,null,100,10000,"a".repeat(64));
        assertEquals(invalid,QuoteSnapshot.parse(gson.toJsonTree(invalid).getAsJsonObject(),3,5L));
        assertFalse(invalid.ready());
    }
    @ParameterizedTest @ValueSource(strings={"couponId","campaignId","thresholdCents","discountCents","startsAt","endsAt","total","claimed"})
    void numericStringsOrFractionsInCouponAreRejected(String key) {
        var page=new CouponPage("OWNED",List.of(coupon()),20,0,false);
        var body=gson.toJsonTree(page).getAsJsonObject();var item=body.getAsJsonArray("items").get(0).getAsJsonObject();
        item.addProperty(key,"1");assertThrows(ProtocolException.class,() -> CouponPage.parse(body,"OWNED",20,0));
        item.addProperty(key,1.5);assertThrows(ProtocolException.class,() -> CouponPage.parse(body,"OWNED",20,0));
    }
    @Test void quoteCorrelationMoneyConservationAndUnavailableAmountsAreEnforced() {
        var body=gson.toJsonTree(quote()).getAsJsonObject();
        assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,4,5L));assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,3,6L));
        body.addProperty("payableCents",14201);assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,3,5L));
        body.addProperty("couponStatus","EXPIRED");body.addProperty("payableCents",16200);body.addProperty("couponReductionCents",0);
        assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,3,5L));
        body.add("payableCents",JsonNull.INSTANCE);body.addProperty("extra","forged");assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(body,3,5L));
    }
    @Test void unorderedPagesMissingFieldsUnknownStatusAndBigIntegerOverflowRejectReply() {
        var page=new CouponPage("OWNED",List.of(coupon()),20,0,false);var body=gson.toJsonTree(page).getAsJsonObject();
        body.getAsJsonArray("items").add(gson.toJsonTree(coupon()));assertThrows(ProtocolException.class,() -> CouponPage.parse(body,"OWNED",20,0));
        var quote=gson.toJsonTree(quote()).getAsJsonObject();quote.addProperty("couponReductionCents",new BigInteger("1"+"0".repeat(1000)));
        assertThrows(ProtocolException.class,() -> QuoteSnapshot.parse(quote,3,5L));
        var bad=gson.toJsonTree(page).getAsJsonObject();bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("status","UNKNOWN");
        assertThrows(ProtocolException.class,() -> CouponPage.parse(bad,"OWNED",20,0));
    }
}
