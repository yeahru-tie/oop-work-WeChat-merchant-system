package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.*;
import com.google.gson.*;
import java.sql.SQLException;

public final class AfterSaleHandler {
    private static final Gson JSON=new GsonBuilder().serializeNulls().create();private final AfterSaleService service;private final IdentityHandler identities;
    public AfterSaleHandler(AfterSaleService service,IdentityHandler identities){this.service=service;this.identities=identities;}
    public boolean handles(int op){return op==Messages.APPLY_RETURN || op==Messages.QUERY_RETURNS;}
    public JsonObject handle(Messages.Request request){var denied=identities.authorizeBuyer(request);if(denied!=null)return denied;
        try{var body=request.body();Object result;if(request.operation()==Messages.APPLY_RETURN)result=service.apply(request.sessionToken(),ApplyReturnRequest.parse(body));
            else switch(JsonFields.text(body,"mode")){
                case "BY_KEY" -> {JsonFields.fields(body,"mode","returnKey");result=service.byKey(request.sessionToken(),JsonFields.text(body,"returnKey"));}
                case "HISTORY" -> {JsonFields.fields(body,"mode","orderId","limit","offset");result=service.history(request.sessionToken(),JsonFields.number(body,"orderId"),Math.toIntExact(JsonFields.number(body,"limit")),Math.toIntExact(JsonFields.number(body,"offset")));}
                default -> throw new IllegalArgumentException("Invalid query.");}
            return Messages.response(request,"OK","ok",JSON.toJsonTree(result).getAsJsonObject());
        }catch(SecurityException | AfterSaleService.Rejected ex){return error(request,ex.getMessage());}catch(IllegalArgumentException | ArithmeticException ex){return error(request,"INVALID_ARGUMENT");}catch(SQLException ex){System.err.println("After-sale operation failed, database code: "+ex.getErrorCode());return error(request,request.operation()==Messages.APPLY_RETURN?"RESULT_UNKNOWN":"INTERNAL_ERROR");}
    }
    private static JsonObject error(Messages.Request request,String code){return Messages.response(request,code,"售后操作未完成，请核对本次结果。",new JsonObject());}
}
