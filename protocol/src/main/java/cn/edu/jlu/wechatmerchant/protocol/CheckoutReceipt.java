package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.Set;

public record CheckoutReceipt(String checkoutKey,String state,OrderSnapshot order,String failureCode) {
    public CheckoutReceipt {
        if(checkoutKey==null || !checkoutKey.matches("[0-9a-f-]{36}") || !Set.of("SUCCESS","FAILED","NOT_FOUND","UNKNOWN").contains(state)
                || state.equals("SUCCESS")!=(order!=null) || state.equals("FAILED")!=(failureCode!=null)
                || order!=null && !checkoutKey.equals(order.checkoutKey()) || failureCode!=null && !failureCode.matches("[A-Z_]{1,64}"))throw new IllegalArgumentException("Invalid checkout result.");
    }
    public static CheckoutReceipt parse(JsonObject body,String key) throws ProtocolException {
        try {JsonFields.fields(body,"checkoutKey","state","order","failureCode");
            var result=new CheckoutReceipt(JsonFields.text(body,"checkoutKey"),JsonFields.text(body,"state"),body.get("order").isJsonNull()?null:OrderSnapshot.parse(body.getAsJsonObject("order")),body.get("failureCode").isJsonNull()?null:JsonFields.text(body,"failureCode"));
            if(!result.checkoutKey.equals(key))throw new IllegalArgumentException("Wrong checkout key.");return result;
        }catch(RuntimeException ex){throw new ProtocolException("Invalid checkout receipt.",ex);}
    }
}
