package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonObject;
import java.util.Set;

/** Strict helpers for the new coupon contracts; never coerce strings to numbers. */
public final class JsonFields {
    private JsonFields() { }
    public static void fields(JsonObject object,String... names) {
        if(!object.keySet().equals(Set.of(names))) throw new IllegalArgumentException("Unexpected fields.");
    }
    public static long number(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Integer required.");
        return value.getAsBigDecimal().longValueExact();
    }
    public static String text(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("String required.");
        return value.getAsString();
    }
    public static boolean bool(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Boolean required.");
        return value.getAsBoolean();
    }
    public static java.math.BigInteger big(JsonObject object,String key) {
        var value=object.get(key);
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Integer required.");
        var result=value.getAsBigDecimal().toBigIntegerExact();
        if(result.signum()<0 || result.bitLength()>128) throw new IllegalArgumentException("Invalid amount.");
        return result;
    }
}
