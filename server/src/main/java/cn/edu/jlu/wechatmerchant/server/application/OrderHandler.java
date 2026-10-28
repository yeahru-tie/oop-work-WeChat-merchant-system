package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.sql.SQLException;

public final class OrderHandler {
    private static final Gson GSON=new GsonBuilder().serializeNulls().create();
    private final OrderService orders;private final IdentityHandler identities;
    public OrderHandler(OrderService orders,IdentityHandler identities){this.orders=orders;this.identities=identities;}
    public boolean handles(int op){return op==Messages.CHECKOUT || op==Messages.QUERY_ORDERS;}
    public JsonObject handle(Messages.Request request){
        var denied=identities.authorizeBuyer(request);if(denied!=null)return denied;
        try{
            var body=request.body();Object result;
            if(request.operation()==Messages.CHECKOUT)result=orders.checkout(request.sessionToken(),CheckoutRequest.parse(body));
            else switch(JsonFields.text(body,"mode")){
                case "BY_KEY" -> {JsonFields.fields(body,"mode","checkoutKey");result=orders.byKey(request.sessionToken(),JsonFields.text(body,"checkoutKey"));}
                case "DETAIL" -> {JsonFields.fields(body,"mode","orderId");result=orders.detail(request.sessionToken(),JsonFields.number(body,"orderId"));}
                case "LIST" -> {JsonFields.fields(body,"mode","limit","offset");result=orders.list(request.sessionToken(),Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")));}
                default -> throw new IllegalArgumentException("Invalid order query.");
            }
            return Messages.response(request,"OK","ok",GSON.toJsonTree(result).getAsJsonObject());
        }catch(SecurityException | OrderService.Rejected ex){return error(request,ex.getMessage());}
        catch(IllegalArgumentException | ArithmeticException ex){return error(request,"INVALID_ARGUMENT");}
        catch(SQLException ex){System.err.println("Order operation failed, database code: "+ex.getErrorCode());return error(request,request.operation()==Messages.CHECKOUT?"RESULT_UNKNOWN":"INTERNAL_ERROR");}
    }
    private static JsonObject error(Messages.Request request,String code){return Messages.response(request,code,"订单操作未完成，请核对本次结果。",new JsonObject());}
}
