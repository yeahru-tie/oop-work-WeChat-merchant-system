package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.*;

public record AfterSalePage(long orderId,List<AfterSaleSnapshot> items,int limit,int offset,boolean hasMore) {
    public AfterSalePage {items=List.copyOf(items);if(orderId<=0 || limit<1 || limit>100 || offset<0 || items.size()>limit || hasMore && items.size()!=limit)throw new IllegalArgumentException("Invalid history page.");long last=Long.MAX_VALUE;for(var item:items){if(item.orderId()!=orderId || item.afterSaleId()>=last)throw new IllegalArgumentException("Wrong history ordering or owner.");last=item.afterSaleId();}}
    public static AfterSalePage parse(JsonObject body,long order,int limit,int offset) throws ProtocolException {
        try{JsonFields.fields(body,"orderId","items","limit","offset","hasMore");var array=body.getAsJsonArray("items");if(array.size()>100)throw new IllegalArgumentException("Too many records.");var items=new ArrayList<AfterSaleSnapshot>();for(var item:array)items.add(AfterSaleSnapshot.parse(item.getAsJsonObject()));var result=new AfterSalePage(JsonFields.number(body,"orderId"),items,Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")),JsonFields.bool(body,"hasMore"));if(result.orderId!=order || result.limit!=limit || result.offset!=offset)throw new IllegalArgumentException("Wrong history correlation.");return result;}
        catch(RuntimeException ex){throw new ProtocolException("Invalid history page.",ex);}
    }
}
