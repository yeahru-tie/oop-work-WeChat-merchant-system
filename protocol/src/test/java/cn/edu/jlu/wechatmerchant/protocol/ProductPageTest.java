package cn.edu.jlu.wechatmerchant.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductPageTest {
    @Test
    void parsesChineseAndExactIntegerBeyondDoublePrecisionIntoImmutableItems() throws Exception {
        JsonObject body = page();
        item(body).addProperty("priceCents", 9007199254740993L);
        item(body).addProperty("productId", Long.MAX_VALUE);
        item(body).addProperty("stock", Integer.MAX_VALUE);
        ProductPage page = ProductPage.parse(body, 20, 0);
        assertEquals("演示商品", page.items().getFirst().name());
        assertEquals(9007199254740993L, page.items().getFirst().priceCents());
        assertEquals(Long.MAX_VALUE, page.items().getFirst().productId());
        assertEquals(Integer.MAX_VALUE, page.items().getFirst().stock());
        assertEquals("", page.items().getFirst().description());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        item(body).addProperty("name", "已更改原 JSON");
        assertEquals("演示商品", page.items().getFirst().name());
    }

    @Test
    void acceptsAnExplicitEmptyPageAndAFullPageWithMore() throws Exception {
        JsonObject body = page();
        body.getAsJsonArray("items").remove(0);
        assertTrue(ProductPage.parse(body, 20, 0).items().isEmpty());
        body = page();
        body.addProperty("limit", 1);
        body.addProperty("hasMore", true);
        assertTrue(ProductPage.parse(body, 1, 0).hasMore());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-name", "null-description", "numeric-name", "string-price", "fraction-price",
            "negative-price", "overflow-price", "negative-stock", "overflow-stock", "zero-id", "blank-name",
            "long-name", "long-description", "duplicate-id", "unsorted-id", "missing-items", "null-item",
            "wrong-limit", "wrong-offset", "string-more", "impossible-more", "overfull-page"})
    void rejectsMalformedOrInconsistentProductPages(String scenario) {
        JsonObject body = page();
        JsonObject product = item(body);
        switch (scenario) {
            case "missing-name" -> product.remove("name");
            case "null-description" -> product.add("description", null);
            case "numeric-name" -> product.addProperty("name", 10);
            case "string-price" -> product.addProperty("priceCents", "1290");
            case "fraction-price" -> product.addProperty("priceCents", 12.9);
            case "negative-price" -> product.addProperty("priceCents", -1);
            case "overflow-price" -> product.add("priceCents", JsonParser.parseString("9223372036854775808"));
            case "negative-stock" -> product.addProperty("stock", -1);
            case "overflow-stock" -> product.addProperty("stock", 2147483648L);
            case "zero-id" -> product.addProperty("productId", 0);
            case "blank-name" -> product.addProperty("name", " \t");
            case "long-name" -> product.addProperty("name", "名".repeat(121));
            case "long-description" -> product.addProperty("description", "描".repeat(501));
            case "duplicate-id" -> body.getAsJsonArray("items").add(product.deepCopy());
            case "unsorted-id" -> {
                JsonObject next = product.deepCopy();
                next.addProperty("productId", 999);
                body.getAsJsonArray("items").add(next);
            }
            case "missing-items" -> body.remove("items");
            case "null-item" -> body.getAsJsonArray("items").add((JsonObject) null);
            case "wrong-limit" -> body.addProperty("limit", 19);
            case "wrong-offset" -> body.addProperty("offset", 20);
            case "string-more" -> body.addProperty("hasMore", "false");
            case "impossible-more" -> body.addProperty("hasMore", true);
            case "overfull-page" -> {
                for (int id = 1002; id <= 1021; id++) {
                    JsonObject next = product.deepCopy();
                    next.addProperty("productId", id);
                    body.getAsJsonArray("items").add(next);
                }
            }
            default -> fail("Unknown case");
        }
        assertThrows(ProtocolException.class, () -> ProductPage.parse(body, 20, 0));
    }

    private static JsonObject page() {
        return JsonParser.parseString("""
                {"items":[{"productId":1001,"name":"演示商品","priceCents":1290,"stock":20,"description":""}],
                 "limit":20,"offset":0,"hasMore":false}
                """).getAsJsonObject();
    }

    private static JsonObject item(JsonObject body) {
        return body.getAsJsonArray("items").get(0).getAsJsonObject();
    }
}
