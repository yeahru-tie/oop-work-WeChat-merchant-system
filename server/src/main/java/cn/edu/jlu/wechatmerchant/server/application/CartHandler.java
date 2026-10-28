package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.CartSnapshot;
import cn.edu.jlu.wechatmerchant.protocol.Messages;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.Set;

public final class CartHandler {
    private static final Gson GSON=new Gson();
    private final CartService carts;
    private final IdentityHandler identities;

    public CartHandler(CartService carts,IdentityHandler identities) { this.carts=carts; this.identities=identities; }
    public boolean handles(int operation) {
        return operation==Messages.LIST_CART || operation==Messages.ADD_TO_CART || operation==Messages.EDIT_CART;
    }

    public JsonObject handle(Messages.Request request) {
        var access=identities.requireBuyer(request);
        if (access.denial()!=null) return access.denial();
        long user=access.identity().userId();
        JsonObject body=request.body();
        try {
            CartSnapshot result;
            if (request.operation()==Messages.LIST_CART) {
                fields(body,Set.of());
                result=carts.list(user);
            } else if (request.operation()==Messages.ADD_TO_CART) {
                fields(body,Set.of("productId","quantity","cartVersion"));
                result=carts.add(user,positive(body,"productId"),quantity(body),version(body));
            } else {
                var action=body.get("action");
                if (action==null || !action.isJsonPrimitive() || !action.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Invalid action.");
                }
                boolean remove="REMOVE".equals(action.getAsString());
                if (!remove && !"SET".equals(action.getAsString())) throw new IllegalArgumentException("Unknown action.");
                fields(body,remove ? Set.of("itemId","action","cartVersion") : Set.of("itemId","action","cartVersion","quantity"));
                result=carts.edit(user,positive(body,"itemId"),remove ? null : quantity(body),version(body));
            }
            return Messages.response(request,"OK","ok",GSON.toJsonTree(result).getAsJsonObject());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return error(request,"INVALID_ARGUMENT");
        } catch (CartService.Rejected exception) {
            return error(request,exception.code());
        } catch (SQLException exception) {
            System.err.println("Cart query failed, database code: "+exception.getErrorCode());
            return error(request,(exception.getErrorCode()==5 || exception.getErrorCode()==6) ? "BUSY_RETRY" : "INTERNAL_ERROR");
        }
    }

    private static void fields(JsonObject body,Set<String> expected) {
        if (!body.keySet().equals(expected)) throw new IllegalArgumentException("Unexpected cart fields.");
    }
    private static long number(JsonObject body,String key) {
        var value=body.get(key);
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Integer required.");
        return value.getAsBigDecimal().longValueExact();
    }
    private static long positive(JsonObject body,String key) {
        long value=number(body,key);
        if (value<=0) throw new IllegalArgumentException("Positive integer required.");
        return value;
    }
    private static int quantity(JsonObject body) { return Math.toIntExact(positive(body,"quantity")); }
    private static long version(JsonObject body) {
        long value=number(body,"cartVersion");
        if (value<0) throw new IllegalArgumentException("Invalid version.");
        return value;
    }
    private static JsonObject error(Messages.Request request,String code) {
        String message=switch(code) {
            case "INVALID_ARGUMENT" -> "购物车参数无效。";
            case "CART_CHANGED" -> "购物车已变化，请刷新后重新操作。";
            case "OUT_OF_STOCK" -> "当前库存不足，请刷新后调整数量。";
            case "NOT_FOUND" -> "商品或购物车条目不存在。";
            case "CART_LIMIT" -> "购物车最多保存 100 种商品，请先移除部分条目。";
            case "BUSY_RETRY" -> "购物车服务繁忙，请先刷新核对。";
            default -> "购物车服务暂不可用，请先刷新核对。";
        };
        return Messages.response(request,code,message,new JsonObject());
    }
}
