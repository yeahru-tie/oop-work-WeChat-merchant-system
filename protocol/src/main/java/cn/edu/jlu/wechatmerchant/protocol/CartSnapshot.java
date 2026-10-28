package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Current cart contents, never a binding checkout quote. */
public record CartSnapshot(long cartVersion, List<Item> items) {
    public static final int MAX_ITEMS = 100;

    public record Item(long itemId, long productId, String name, long priceCents, int stock, int quantity, PromotionPrice promotion) {
        public Item(long itemId, long productId, String name, long priceCents, int stock, int quantity) {
            this(itemId, productId, name, priceCents, stock, quantity, null);
        }
        public Item {
            if (itemId <= 0 || productId <= 0 || name == null || name.isBlank()
                    || name.codePointCount(0, name.length()) > 120 || priceCents < 0 || stock < 0 || quantity <= 0) {
                throw new IllegalArgumentException("Invalid cart item.");
            }
            if (promotion != null && promotion.originalUnitCents() != priceCents) throw new IllegalArgumentException("Price mismatch.");
        }
        public BigInteger estimatedCents() {
            return BigInteger.valueOf(promotion == null ? priceCents : promotion.estimatedUnitCents()).multiply(BigInteger.valueOf(quantity));
        }
        public BigInteger subtotalCents() {
            return BigInteger.valueOf(priceCents).multiply(BigInteger.valueOf(quantity));
        }
    }

    public CartSnapshot {
        items = List.copyOf(items);
        if (cartVersion < 0 || items.size() > MAX_ITEMS) throw new IllegalArgumentException("Invalid cart.");
        long previous = 0;
        Set<Long> products = new HashSet<>();
        for (Item item : items) {
            if (item.itemId() <= previous || !products.add(item.productId())) {
                throw new IllegalArgumentException("Cart items must be ordered and products unique.");
            }
            previous = item.itemId();
        }
    }

    public BigInteger totalCents() {
        return items.stream().map(Item::subtotalCents).reduce(BigInteger.ZERO, BigInteger::add);
    }

    public BigInteger estimatedTotalCents() {
        return items.stream().map(Item::estimatedCents).reduce(BigInteger.ZERO, BigInteger::add);
    }

    public static CartSnapshot parse(JsonObject body) throws ProtocolException {
        try {
            if (!body.keySet().equals(Set.of("cartVersion", "items")) || !body.get("items").isJsonArray()
                    || body.getAsJsonArray("items").size() > MAX_ITEMS) throw new IllegalArgumentException("Invalid cart body.");
            List<Item> items = new ArrayList<>();
            for (JsonElement value : body.getAsJsonArray("items")) {
                JsonObject item = value.getAsJsonObject();
                if (!item.keySet().equals(Set.of("itemId", "productId", "name", "priceCents", "stock", "quantity"))
                        && !item.keySet().equals(Set.of("itemId", "productId", "name", "priceCents", "stock", "quantity", "promotion"))) {
                    throw new IllegalArgumentException("Invalid cart fields.");
                }
                JsonElement name = item.get("name");
                if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid name.");
                items.add(new Item(integer(item,"itemId"), integer(item,"productId"), name.getAsString(),
                        integer(item,"priceCents"), Math.toIntExact(integer(item,"stock")), Math.toIntExact(integer(item,"quantity")),
                        PromotionPrice.parse(item.get("promotion"), integer(item,"priceCents"))));
            }
            return new CartSnapshot(integer(body,"cartVersion"), items);
        } catch (IllegalArgumentException | ArithmeticException | IllegalStateException exception) {
            throw new ProtocolException("Invalid cart response.", exception);
        }
    }

    private static long integer(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Integer required.");
        }
        return value.getAsBigDecimal().longValueExact();
    }
}
