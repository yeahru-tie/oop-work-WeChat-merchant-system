package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromotionPriceTest {
    private final Gson gson=new Gson();
    private PromotionPrice price() { return new PromotionPrice(3,1290,100,900,1190,1071); }
    @Test void productAndCartRoundTripExactPricingIncludingPerUnitRounding() throws Exception {
        var product=new ProductPage(List.of(new ProductPage.Item(1001,"商品",1290,20,"",price())),20,0,false);
        assertEquals(product,ProductPage.parse(gson.toJsonTree(product).getAsJsonObject(),20,0));
        var cart=new CartSnapshot(2,List.of(new CartSnapshot.Item(1,1001,"商品",1290,20,2,price())));
        assertEquals(cart,CartSnapshot.parse(gson.toJsonTree(cart).getAsJsonObject()));
        assertEquals("2142",cart.estimatedTotalCents().toString());
        var small=new PromotionPrice(1,5,0,900,5,5);
        assertEquals("15",new CartSnapshot(1,List.of(new CartSnapshot.Item(1,1,"微价",5,3,3,small))).estimatedTotalCents().toString());
    }
    @ParameterizedTest @ValueSource(strings={"configurationVersion","originalUnitCents","reductionCents","discountHundredths","afterReductionCents","estimatedUnitCents"})
    void malformedOrInconsistentPricesRejectEntireCart(String field) {
        var cart=new CartSnapshot(2,List.of(new CartSnapshot.Item(1,1001,"商品",1290,20,2,price())));
        JsonObject body=gson.toJsonTree(cart).getAsJsonObject();
        var pricing=body.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("promotion");
        pricing.addProperty(field,-1);
        assertThrows(ProtocolException.class,() -> CartSnapshot.parse(body));
        pricing.addProperty(field,"1");
        assertThrows(ProtocolException.class,() -> CartSnapshot.parse(body));
    }
    @Test void priceMismatchAndExtraPromotionFieldsDoNotPassProductParser() {
        var page=new ProductPage(List.of(new ProductPage.Item(1001,"商品",1290,20,"",price())),20,0,false);
        JsonObject body=gson.toJsonTree(page).getAsJsonObject();
        var item=body.getAsJsonArray("items").get(0).getAsJsonObject(); item.addProperty("priceCents",2000);
        assertThrows(ProtocolException.class,() -> ProductPage.parse(body,20,0));
        item.addProperty("priceCents",1290); item.getAsJsonObject("promotion").addProperty("arbitraryClass","evil");
        assertThrows(ProtocolException.class,() -> ProductPage.parse(body,20,0));
    }
}
