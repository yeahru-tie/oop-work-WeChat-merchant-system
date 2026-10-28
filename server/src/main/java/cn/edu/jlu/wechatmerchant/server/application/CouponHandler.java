package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.sql.SQLException;

public final class CouponHandler {
    private static final Gson GSON=new GsonBuilder().serializeNulls().create();
    private final CouponService coupons;
    private final IdentityHandler identities;
    public CouponHandler(CouponService coupons,IdentityHandler identities) { this.coupons=coupons; this.identities=identities; }
    public boolean handles(int operation) { return operation==Messages.LIST_COUPONS || operation==Messages.CLAIM_COUPON || operation==Messages.QUOTE_CART; }
    public JsonObject handle(Messages.Request request) {
        var denied=identities.authorizeBuyer(request); if(denied!=null)return denied;
        try {
            JsonObject body=request.body(); Object result;
            if(request.operation()==Messages.LIST_COUPONS) {
                if(body.has("itemIds"))JsonFields.fields(body,"kind","limit","offset","itemIds");else JsonFields.fields(body,"kind","limit","offset");
                result=coupons.list(request.sessionToken(),JsonFields.text(body,"kind"),Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")),body.has("itemIds")?CartSelection.parse(body.get("itemIds")):null);
            } else if(request.operation()==Messages.CLAIM_COUPON) {
                JsonFields.fields(body,"campaignId");
                result=coupons.claim(request.sessionToken(),JsonFields.number(body,"campaignId"));
            } else {
                if(body.has("itemIds"))JsonFields.fields(body,"cartVersion","couponId","itemIds");else JsonFields.fields(body,"cartVersion","couponId");
                Long coupon=body.get("couponId").isJsonNull()?null:JsonFields.number(body,"couponId");
                result=coupons.quote(request.sessionToken(),JsonFields.number(body,"cartVersion"),coupon,body.has("itemIds")?CartSelection.parse(body.get("itemIds")):null);
            }
            return Messages.response(request,"OK","ok",GSON.toJsonTree(result).getAsJsonObject());
        } catch(SecurityException ex) { return error(request,ex.getMessage()); }
        catch(CouponService.Rejected ex) { return error(request,ex.getMessage()); }
        catch(IllegalArgumentException | ArithmeticException ex) { return error(request,"INVALID_ARGUMENT"); }
        catch(SQLException ex) {
            System.err.println("Coupon request failed, database code: "+ex.getErrorCode());
            return error(request,ex.getErrorCode()==5 || ex.getErrorCode()==6 ? "BUSY_RETRY":"INTERNAL_ERROR");
        }
    }
    private static JsonObject error(Messages.Request request,String code) {
        return Messages.response(request,code,"优惠券操作未完成，请刷新核对。",new JsonObject());
    }
}
