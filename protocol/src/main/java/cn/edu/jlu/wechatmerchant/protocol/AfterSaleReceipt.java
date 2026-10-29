package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.Set;

public record AfterSaleReceipt(String returnKey,String state,AfterSaleSnapshot application,String failureCode) {
    public AfterSaleReceipt {if(!ApplyReturnRequest.validKey(returnKey) || state==null || !Set.of("SUCCESS","FAILED","NOT_FOUND","UNKNOWN").contains(state) || state.equals("SUCCESS")!=(application!=null) || state.equals("FAILED")!=(failureCode!=null) || application!=null && !returnKey.equals(application.returnKey()) || failureCode!=null && !failureCode.matches("[A-Z_]{1,64}"))throw new IllegalArgumentException("Invalid after-sale receipt.");}
    public static AfterSaleReceipt parse(JsonObject body,String key) throws ProtocolException {
        try{JsonFields.fields(body,"returnKey","state","application","failureCode");var result=new AfterSaleReceipt(JsonFields.text(body,"returnKey"),JsonFields.text(body,"state"),body.get("application").isJsonNull()?null:AfterSaleSnapshot.parse(body.getAsJsonObject("application")),body.get("failureCode").isJsonNull()?null:JsonFields.text(body,"failureCode"));if(!key.equals(result.returnKey))throw new IllegalArgumentException("Wrong request key.");return result;}
        catch(RuntimeException ex){throw new ProtocolException("Invalid after-sale receipt.",ex);}
    }
}
