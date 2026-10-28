package cn.edu.jlu.wechatmerchant.server.application;

import cn.edu.jlu.wechatmerchant.protocol.Messages;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.SQLException;
import java.util.Set;

public final class ProductQueryHandler {
    private static final Gson GSON = new Gson();
    private final ProductCatalog catalog;

    public ProductQueryHandler(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    public JsonObject handle(Messages.Request request) {
        final int limit;
        final int offset;
        try {
            if (!Set.of("limit", "offset").containsAll(request.body().keySet())) {
                throw new IllegalArgumentException("Unsupported product query field.");
            }
            limit = integer(request.body(), "limit", 20);
            offset = integer(request.body(), "offset", 0);
            if (limit < 1 || limit > 100 || offset < 0) {
                throw new IllegalArgumentException("Invalid pagination.");
            }
        } catch (IllegalArgumentException exception) {
            return Messages.response(request, "INVALID_ARGUMENT", "分页参数无效。", new JsonObject());
        }
        try {
            return Messages.response(request, "OK", "ok", GSON.toJsonTree(catalog.list(limit, offset)).getAsJsonObject());
        } catch (SQLException exception) {
            System.err.println("Product query failed, database code: " + exception.getErrorCode());
            return Messages.response(request, "INTERNAL_ERROR", "商品查询暂不可用，请稍后重试。", new JsonObject());
        }
    }

    private static int integer(JsonObject object, String name, int defaultValue) {
        if (!object.has(name)) {
            return defaultValue;
        }
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer.");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Expected integer.", exception);
        }
    }
}
