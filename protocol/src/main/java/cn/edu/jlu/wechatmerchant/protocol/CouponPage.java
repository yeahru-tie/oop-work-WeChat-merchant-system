package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

public record CouponPage(String kind,List<Item> items,int limit,int offset,boolean hasMore) {
    public enum Status { CAN_CLAIM,ALREADY_CLAIMED,PAUSED,NOT_STARTED,EXPIRED,SOLD_OUT,USABLE,THRESHOLD_NOT_MET,USED,EMPTY_SELECTION }
    public record Item(long couponId,long campaignId,String name,long thresholdCents,long discountCents,
            long startsAt,long endsAt,int total,int claimed,Status status) {
        public Item {
            if(couponId<0 || campaignId<=0 || name==null || name.isBlank() || name.codePointCount(0,name.length())>80
                    || thresholdCents<0 || discountCents<=0 || endsAt<=startsAt || total<1 || claimed<0 || claimed>total || status==null) {
                throw new IllegalArgumentException("Invalid coupon item.");
            }
        }
    }
    public CouponPage {
        if(!Set.of("AVAILABLE","OWNED").contains(kind) || limit<1 || limit>100 || offset<0 || items.size()>limit || (hasMore && items.size()!=limit)) {
            throw new IllegalArgumentException("Invalid coupon page.");
        }
        items=List.copyOf(items);
        long previous=0;
        for(Item item:items) {
            long id=kind.equals("OWNED") ? item.couponId() : item.campaignId();
            if(id<=previous || (kind.equals("OWNED") && !Set.of(Status.USABLE,Status.NOT_STARTED,Status.EXPIRED,Status.THRESHOLD_NOT_MET,Status.USED,Status.EMPTY_SELECTION).contains(item.status()))) {
                throw new IllegalArgumentException("Invalid coupon order/status.");
            }
            if(kind.equals("AVAILABLE") && (!Set.of(Status.CAN_CLAIM,Status.ALREADY_CLAIMED,Status.PAUSED,Status.NOT_STARTED,Status.EXPIRED,Status.SOLD_OUT).contains(item.status())
                    || (item.status()==Status.ALREADY_CLAIMED)!=(item.couponId()>0))) throw new IllegalArgumentException("Invalid claim status/ownership.");
            previous=id;
        }
    }
    public static CouponPage parse(JsonObject body,String kind,int limit,int offset) throws ProtocolException {
        try {
            JsonFields.fields(body,"kind","items","limit","offset","hasMore");
            List<Item> items=new ArrayList<>();
            var array=body.getAsJsonArray("items");
            if(array.size()>100) throw new IllegalArgumentException("Too many coupons.");
            for(var element:array) items.add(parseItem(element.getAsJsonObject()));
            var result=new CouponPage(JsonFields.text(body,"kind"),items,Math.toIntExact(JsonFields.number(body,"limit")),
                    Math.toIntExact(JsonFields.number(body,"offset")),JsonFields.bool(body,"hasMore"));
            if(!result.kind.equals(kind) || result.limit!=limit || result.offset!=offset) throw new IllegalArgumentException("Pagination mismatch.");
            return result;
        } catch(RuntimeException exception) { throw new ProtocolException("Invalid coupon response.",exception); }
    }
    public static Item parseItem(JsonObject value) {
        JsonFields.fields(value,"couponId","campaignId","name","thresholdCents","discountCents","startsAt","endsAt","total","claimed","status");
        return new Item(JsonFields.number(value,"couponId"),JsonFields.number(value,"campaignId"),JsonFields.text(value,"name"),
                JsonFields.number(value,"thresholdCents"),JsonFields.number(value,"discountCents"),JsonFields.number(value,"startsAt"),
                JsonFields.number(value,"endsAt"),Math.toIntExact(JsonFields.number(value,"total")),Math.toIntExact(JsonFields.number(value,"claimed")),
                Status.valueOf(JsonFields.text(value,"status")));
    }
}
