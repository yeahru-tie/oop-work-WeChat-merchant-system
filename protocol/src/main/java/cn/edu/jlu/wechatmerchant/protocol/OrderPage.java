package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.*;

public record OrderPage(List<Summary> items,int limit,int offset,boolean hasMore) {
    public record Summary(long orderId,long createdAt,String status,int itemCount,BigInteger paidCents){
        public Summary {if(orderId<=0 || createdAt<0 || !OrderSnapshot.validStatus(status) || itemCount<1 || itemCount>100 || paidCents==null || paidCents.signum()<0 || paidCents.bitLength()>128)throw new IllegalArgumentException("Invalid order summary.");}
    }
    public OrderPage {items=List.copyOf(items);if(limit<1 || limit>100 || offset<0 || items.size()>limit || hasMore && items.size()!=limit)throw new IllegalArgumentException("Invalid order page.");
        Long previous=null;for(var item:items){if(previous!=null && item.orderId()>=previous)throw new IllegalArgumentException("Orders must be descending.");previous=item.orderId();}}
    public static OrderPage parse(JsonObject body,int limit,int offset) throws ProtocolException {
        try{JsonFields.fields(body,"items","limit","offset","hasMore");var array=body.getAsJsonArray("items");if(array.size()>100)throw new IllegalArgumentException("Too many orders.");var items=new ArrayList<Summary>();
            for(var value:array){var item=value.getAsJsonObject();JsonFields.fields(item,"orderId","createdAt","status","itemCount","paidCents");items.add(new Summary(JsonFields.number(item,"orderId"),JsonFields.number(item,"createdAt"),JsonFields.text(item,"status"),Math.toIntExact(JsonFields.number(item,"itemCount")),JsonFields.big(item,"paidCents")));}
            var result=new OrderPage(items,Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")),JsonFields.bool(body,"hasMore"));if(result.limit!=limit || result.offset!=offset)throw new IllegalArgumentException("Wrong pagination.");return result;
        }catch(RuntimeException ex){throw new ProtocolException("Invalid order page.",ex);}
    }
}
