package cn.edu.jlu.wechatmerchant.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Validated display snapshot, not a checkout authorization. All amounts are cents. */
public record PromotionPrice(long configurationVersion, long originalUnitCents, long reductionCents,
        int discountHundredths, long afterReductionCents, long estimatedUnitCents) {
    public PromotionPrice {
        if (configurationVersion < 0 || originalUnitCents < 0 || reductionCents < 0
                || discountHundredths < 1 || discountHundredths > 1000
                || afterReductionCents != originalUnitCents - Math.min(originalUnitCents, reductionCents)
                || estimatedUnitCents != BigDecimal.valueOf(afterReductionCents)
                        .multiply(BigDecimal.valueOf(discountHundredths, 3)).setScale(0, RoundingMode.HALF_UP).longValueExact()) {
            throw new IllegalArgumentException("Invalid promotion price.");
        }
    }

    public static PromotionPrice parse(JsonElement value, long priceCents) {
        if (value == null || value.isJsonNull()) return null; // Older original-price replies.
        JsonObject body = value.getAsJsonObject();
        if (!body.keySet().equals(Set.of("configurationVersion", "originalUnitCents", "reductionCents",
                "discountHundredths", "afterReductionCents", "estimatedUnitCents"))) {
            throw new IllegalArgumentException("Invalid promotion fields.");
        }
        PromotionPrice result = new PromotionPrice(number(body,"configurationVersion"), number(body,"originalUnitCents"),
                number(body,"reductionCents"), Math.toIntExact(number(body,"discountHundredths")),
                number(body,"afterReductionCents"), number(body,"estimatedUnitCents"));
        if (result.originalUnitCents != priceCents) throw new IllegalArgumentException("Price mismatch.");
        return result;
    }

    private static long number(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Integer required.");
        }
        return value.getAsBigDecimal().longValueExact();
    }
}
