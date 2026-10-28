package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import java.util.*;

/** IDs of whole cart lines, never client supplied prices or quantities. */
public final class CartSelection {
    private CartSelection() { }
    public static List<Long> validate(List<Long> ids) {
        ids=List.copyOf(ids);
        if(ids.size()>CartSnapshot.MAX_ITEMS)throw new IllegalArgumentException("Too many selected items.");
        long previous=0;
        for(long id:ids){if(id<=previous)throw new IllegalArgumentException("Selected IDs must be positive, unique and ascending.");previous=id;}
        return ids;
    }
    public static List<Long> parse(JsonElement value) {
        if(value==null || !value.isJsonArray())throw new IllegalArgumentException("Item IDs must be an array.");
        if(value.getAsJsonArray().size()>CartSnapshot.MAX_ITEMS)throw new IllegalArgumentException("Too many selected items.");
        var result=new ArrayList<Long>();
        for(var element:value.getAsJsonArray()){
            if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Integer ID required.");
            result.add(element.getAsBigDecimal().longValueExact());
        }
        return validate(result);
    }
    public static List<Long> all(CartSnapshot cart){return cart.items().stream().map(CartSnapshot.Item::itemId).toList();}
    public static CartSnapshot select(CartSnapshot cart,List<Long> ids) {
        if(ids==null)return cart;
        ids=validate(ids);var selected=new HashSet<>(ids);
        var items=cart.items().stream().filter(i -> selected.contains(i.itemId())).toList();
        if(items.size()!=ids.size())throw new IllegalArgumentException("Selected item no longer belongs to this cart.");
        return new CartSnapshot(cart.cartVersion(),items);
    }
}
