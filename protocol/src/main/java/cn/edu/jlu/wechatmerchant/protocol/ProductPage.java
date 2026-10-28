package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Immutable read model for the 1001/2001 contract; contains no database or UI code. */
public record ProductPage(List<Item> items, int limit, int offset, boolean hasMore) {
    public record Item(long productId, String name, long priceCents, int stock, String description, PromotionPrice promotion) {
        public Item(long productId, String name, long priceCents, int stock, String description) {
            this(productId, name, priceCents, stock, description, null);
        }
        public Item {
            if (productId <= 0 || name == null || name.isBlank() || length(name) > 120
                    || priceCents < 0 || stock < 0 || description == null || length(description) > 500) {
                throw new IllegalArgumentException("Invalid product fields.");
            }
            if (promotion != null && promotion.originalUnitCents() != priceCents) throw new IllegalArgumentException("Price mismatch.");
        }
    }

    public ProductPage {
        items = List.copyOf(items);
        if (limit < 1 || limit > 100 || offset < 0 || items.size() > limit || (hasMore && items.size() != limit)) {
            throw new IllegalArgumentException("Invalid product page.");
        }
        long previousId = 0;
        for (Item item : items) {
            if (item.productId() <= previousId) {
                throw new IllegalArgumentException("Product IDs must be unique and ascending.");
            }
            previousId = item.productId();
        }
    }

    public static ProductPage parse(JsonObject body, int expectedLimit, int expectedOffset) throws ProtocolException {
        try {
            int limit = Math.toIntExact(integer(body, "limit"));
            int offset = Math.toIntExact(integer(body, "offset"));
            JsonElement more = body.get("hasMore");
            JsonElement values = body.get("items");
            if (limit != expectedLimit || offset != expectedOffset || more == null || !more.isJsonPrimitive()
                    || !more.getAsJsonPrimitive().isBoolean() || values == null || !values.isJsonArray()
                    || values.getAsJsonArray().size() > 100) {
                throw new IllegalArgumentException("Invalid product pagination fields.");
            }
            List<Item> items = new ArrayList<>();
            for (JsonElement value : values.getAsJsonArray()) {
                if (!value.isJsonObject()) {
                    throw new IllegalArgumentException("Product must be an object.");
                }
                JsonObject item = value.getAsJsonObject();
                items.add(new Item(integer(item, "productId"), text(item, "name"), integer(item, "priceCents"),
                        Math.toIntExact(integer(item, "stock")), text(item, "description"),
                        PromotionPrice.parse(item.get("promotion"), integer(item,"priceCents"))));
            }
            return new ProductPage(items, limit, offset, more.getAsBoolean());
        } catch (IllegalArgumentException | ArithmeticException | IllegalStateException exception) {
            throw new ProtocolException("Invalid product response body.", exception);
        }
    }

    private static long integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Integer required: " + key);
        }
        return value.getAsBigDecimal().longValueExact();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("String required: " + key);
        }
        return value.getAsString();
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
