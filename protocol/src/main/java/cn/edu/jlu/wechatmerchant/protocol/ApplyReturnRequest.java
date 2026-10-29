package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;

public record ApplyReturnRequest(String returnKey,long orderId,String reason) {
    public ApplyReturnRequest {if(!validKey(returnKey) || orderId<=0 || !validText(reason))throw new IllegalArgumentException("Invalid return request.");}
    public static boolean validKey(String key){return key!=null && key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    public static boolean validText(String value){return value!=null && !value.isBlank() && value.codePointCount(0,value.length())<=500;}
    public static ApplyReturnRequest parse(JsonObject body){JsonFields.fields(body,"returnKey","orderId","reason");return new ApplyReturnRequest(JsonFields.text(body,"returnKey"),JsonFields.number(body,"orderId"),JsonFields.text(body,"reason"));}
    @Override public String toString(){return "ApplyReturnRequest[returnKey="+returnKey+",orderId="+orderId+",reason=<redacted>]";}
}
