package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.Set;

/** Read-only estimate. An unavailable selected coupon deliberately has no payable amount. */
public record QuoteSnapshot(CartSnapshot cart,java.util.List<Long> itemIds,Long couponId,String couponStatus,BigInteger couponReductionCents,
        BigInteger payableCents,long quotedAt,long validUntil,String digest) {
    public QuoteSnapshot(CartSnapshot cart,Long couponId,String couponStatus,BigInteger reduction,BigInteger payable,long quotedAt,long validUntil,String digest) {
        this(cart,CartSelection.all(cart),couponId,couponStatus,reduction,payable,quotedAt,validUntil,digest);
    }
    public QuoteSnapshot {
        itemIds=CartSelection.validate(itemIds);
        var selected=CartSelection.select(cart,itemIds);
        if(cart==null || couponReductionCents==null || quotedAt<0 || validUntil<=quotedAt || digest==null || !digest.matches("[0-9a-f]{64}")
                || couponId!=null && couponId<=0 || !Set.of("NONE","APPLIED","EXPIRED","NOT_STARTED","THRESHOLD_NOT_MET","NOT_FOUND","USED","EMPTY_CART","EMPTY_SELECTION").contains(couponStatus)
                || couponReductionCents.signum()<0 || couponReductionCents.compareTo(selected.estimatedTotalCents())>0
                || (couponId==null && !Set.of("NONE","EMPTY_SELECTION").contains(couponStatus)) || (couponId!=null && couponStatus.equals("NONE"))
                || (couponStatus.equals("NONE") && couponReductionCents.signum()!=0)) {
            throw new IllegalArgumentException("Invalid quote.");
        }
        boolean ready=couponStatus.equals("NONE") || couponStatus.equals("APPLIED");
        if(ready ? payableCents==null || !payableCents.equals(selected.estimatedTotalCents().subtract(couponReductionCents))
                : payableCents!=null || couponReductionCents.signum()!=0) throw new IllegalArgumentException("Inconsistent quote totals.");
        if(couponStatus.equals("EMPTY_SELECTION") && !itemIds.isEmpty())throw new IllegalArgumentException("Nonempty selection.");
        if(cart.items().stream().anyMatch(item -> item.promotion()==null)) throw new IllegalArgumentException("Missing authoritative promotion.");
    }
    public boolean ready() { return payableCents!=null; }
    public CartSnapshot selectedCart(){return CartSelection.select(cart,itemIds);}
    public static QuoteSnapshot parse(JsonObject body,long version,Long couponId) throws ProtocolException {
        return parse(body,version,couponId,null);
    }
    public static QuoteSnapshot parse(JsonObject body,long version,Long couponId,java.util.List<Long> expectedItems) throws ProtocolException {
        try {
            if(body.has("itemIds"))JsonFields.fields(body,"cart","itemIds","couponId","couponStatus","couponReductionCents","payableCents","quotedAt","validUntil","digest");
            else JsonFields.fields(body,"cart","couponId","couponStatus","couponReductionCents","payableCents","quotedAt","validUntil","digest");
            var cart=CartSnapshot.parse(body.getAsJsonObject("cart"));
            var value=new QuoteSnapshot(cart,body.has("itemIds")?CartSelection.parse(body.get("itemIds")):CartSelection.all(cart),body.get("couponId").isJsonNull()?null:JsonFields.number(body,"couponId"),
                    JsonFields.text(body,"couponStatus"),JsonFields.big(body,"couponReductionCents"),body.get("payableCents").isJsonNull()?null:JsonFields.big(body,"payableCents"),
                    JsonFields.number(body,"quotedAt"),JsonFields.number(body,"validUntil"),JsonFields.text(body,"digest"));
            if(value.cart.cartVersion()!=version || !java.util.Objects.equals(value.couponId,couponId)) throw new IllegalArgumentException("Quote correlation mismatch.");
            if(!value.itemIds.equals(expectedItems==null?CartSelection.all(cart):expectedItems))throw new IllegalArgumentException("Selection correlation mismatch.");
            return value;
        } catch(RuntimeException exception) { throw new ProtocolException("Invalid quote response.",exception); }
    }
}
