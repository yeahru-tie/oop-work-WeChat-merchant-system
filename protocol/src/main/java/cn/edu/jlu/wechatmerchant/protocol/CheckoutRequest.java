package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.List;

public record CheckoutRequest(String checkoutKey,long cartVersion,String quoteDigest,Long couponId,List<Long> itemIds) {
    public CheckoutRequest {
        if(checkoutKey==null || !checkoutKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || cartVersion<0 || quoteDigest==null || !quoteDigest.matches("[0-9a-f]{64}") || couponId!=null && couponId<=0)throw new IllegalArgumentException("Invalid checkout request.");
        itemIds=CartSelection.validate(itemIds);
        if(itemIds.isEmpty())throw new IllegalArgumentException("Select at least one item.");
    }
    public static CheckoutRequest parse(JsonObject body) {
        JsonFields.fields(body,"checkoutKey","cartVersion","quoteDigest","couponId","itemIds");
        return new CheckoutRequest(JsonFields.text(body,"checkoutKey"),JsonFields.number(body,"cartVersion"),JsonFields.text(body,"quoteDigest"),
                body.get("couponId").isJsonNull()?null:JsonFields.number(body,"couponId"),CartSelection.parse(body.get("itemIds")));
    }
}
