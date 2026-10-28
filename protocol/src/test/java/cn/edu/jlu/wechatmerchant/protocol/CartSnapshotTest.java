package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CartSnapshotTest {
    private CartSnapshot sample() { return new CartSnapshot(3,List.of(new CartSnapshot.Item(1,1001,"商品",Long.MAX_VALUE,0,2))); }
    @Test void roundTripsExactLargeAmountsAndKeepsInsufficientStockVisible() throws Exception {
        var value=sample();
        assertEquals(value,CartSnapshot.parse(new Gson().toJsonTree(value).getAsJsonObject()));
        assertEquals(new BigInteger("18446744073709551614"),value.totalCents());
        assertThrows(UnsupportedOperationException.class,() -> value.items().clear());
    }
    @ParameterizedTest @ValueSource(strings={"fraction","string","negative","duplicate","unordered","extra","missing","null","array","huge"})
    void rejectsMalformedCartBodies(String problem) {
        JsonObject body=new Gson().toJsonTree(sample()).getAsJsonObject();
        var item=body.getAsJsonArray("items").get(0).getAsJsonObject();
        switch(problem) {
            case "fraction" -> item.addProperty("quantity",1.5);
            case "string" -> item.addProperty("priceCents","12");
            case "negative" -> body.addProperty("cartVersion",-1);
            case "duplicate" -> body.getAsJsonArray("items").add(item.deepCopy());
            case "unordered" -> { var next=item.deepCopy(); next.addProperty("itemId",0); next.addProperty("productId",1002); body.getAsJsonArray("items").add(next); }
            case "extra" -> body.addProperty("ownerId",2);
            case "missing" -> item.remove("name");
            case "null" -> body.add("items",com.google.gson.JsonNull.INSTANCE);
            case "array" -> body.getAsJsonArray("items").add("invalid");
            case "huge" -> item.addProperty("quantity",2147483648L);
        }
        assertThrows(ProtocolException.class,() -> CartSnapshot.parse(body));
    }
}
