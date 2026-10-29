package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.*;

/** Historical values only; no current stock, product or activity lookup. Coupon stays at order level. */
public record OrderSnapshot(long orderId,String checkoutKey,long createdAt,String status,List<Item> items,Coupon coupon,
        BigInteger originalCents,BigInteger productReductionCents,BigInteger couponReductionCents,BigInteger paidCents) {
    public record Item(long cartItemId,long productId,String name,long priceCents,int quantity,PromotionPrice promotion) {
        public Item {
            if(cartItemId<=0 || productId<=0 || name==null || name.isBlank() || name.codePointCount(0,name.length())>120 || priceCents<0 || quantity<=0
                    || promotion==null || promotion.originalUnitCents()!=priceCents)throw new IllegalArgumentException("Invalid historical item.");
        }
        public BigInteger originalCents(){return BigInteger.valueOf(priceCents).multiply(BigInteger.valueOf(quantity));}
        public BigInteger estimatedCents(){return BigInteger.valueOf(promotion.estimatedUnitCents()).multiply(BigInteger.valueOf(quantity));}
    }
    public record Coupon(long couponId,long campaignId,String name,long thresholdCents,long discountCents,long startsAt,long endsAt) {
        public Coupon {
            if(couponId<=0 || campaignId<=0 || name==null || name.isBlank() || name.codePointCount(0,name.length())>80 || thresholdCents<0 || discountCents<=0 || startsAt>=endsAt)throw new IllegalArgumentException("Invalid historical coupon.");
        }
    }
    public OrderSnapshot {
        if(orderId<=0 || createdAt<0 || checkoutKey==null || !checkoutKey.matches("[0-9a-f-]{36}") || !validStatus(status)
                || items==null || items.isEmpty() || items.size()>100)throw new IllegalArgumentException("Invalid order.");
        items=List.copyOf(items);long previous=0;var products=new HashSet<Long>();
        for(var item:items){if(item.cartItemId()<=previous || !products.add(item.productId()))throw new IllegalArgumentException("Invalid order item ordering.");previous=item.cartItemId();}
        BigInteger original=items.stream().map(Item::originalCents).reduce(BigInteger.ZERO,BigInteger::add);
        BigInteger subtotal=items.stream().map(Item::estimatedCents).reduce(BigInteger.ZERO,BigInteger::add);
        BigInteger reduction=coupon==null?BigInteger.ZERO:subtotal.min(BigInteger.valueOf(coupon.discountCents()));
        if(!original.equals(originalCents) || !original.subtract(subtotal).equals(productReductionCents) || !reduction.equals(couponReductionCents)
                || !subtotal.subtract(reduction).equals(paidCents) || coupon!=null && (subtotal.compareTo(BigInteger.valueOf(coupon.thresholdCents()))<0 || createdAt<coupon.startsAt() || createdAt>=coupon.endsAt()))throw new IllegalArgumentException("Inconsistent historical totals.");
    }
    public static boolean validStatus(String status){return status!=null && Set.of("COMPLETED","RETURN_REQUESTED","REFUNDED").contains(status);}
    public static OrderSnapshot parse(JsonObject body) throws ProtocolException {
        try {
            JsonFields.fields(body,"orderId","checkoutKey","createdAt","status","items","coupon","originalCents","productReductionCents","couponReductionCents","paidCents");
            var array=body.getAsJsonArray("items");if(array.size()>100)throw new IllegalArgumentException("Too many order items.");
            var items=new ArrayList<Item>();for(var element:array){var i=element.getAsJsonObject();JsonFields.fields(i,"cartItemId","productId","name","priceCents","quantity","promotion");
                items.add(new Item(JsonFields.number(i,"cartItemId"),JsonFields.number(i,"productId"),JsonFields.text(i,"name"),JsonFields.number(i,"priceCents"),Math.toIntExact(JsonFields.number(i,"quantity")),PromotionPrice.parse(i.get("promotion"),JsonFields.number(i,"priceCents"))));}
            return new OrderSnapshot(JsonFields.number(body,"orderId"),JsonFields.text(body,"checkoutKey"),JsonFields.number(body,"createdAt"),JsonFields.text(body,"status"),items,
                    body.get("coupon").isJsonNull()?null:parseCoupon(body.getAsJsonObject("coupon")),JsonFields.big(body,"originalCents"),JsonFields.big(body,"productReductionCents"),JsonFields.big(body,"couponReductionCents"),JsonFields.big(body,"paidCents"));
        }catch(RuntimeException ex){throw new ProtocolException("Invalid historical order.",ex);}
    }
    public static Coupon parseCoupon(JsonObject body){JsonFields.fields(body,"couponId","campaignId","name","thresholdCents","discountCents","startsAt","endsAt");return new Coupon(JsonFields.number(body,"couponId"),JsonFields.number(body,"campaignId"),JsonFields.text(body,"name"),JsonFields.number(body,"thresholdCents"),JsonFields.number(body,"discountCents"),JsonFields.number(body,"startsAt"),JsonFields.number(body,"endsAt"));}
}
