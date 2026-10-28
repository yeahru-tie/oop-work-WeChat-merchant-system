package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.math.BigDecimal;

/** Reduction in cents; discount in hundredths of a Chinese zhe (900 means nine zhe). */
public record PromotionTerms(long reductionCents, int discountHundredths) {
    public PromotionTerms {
        if (reductionCents < 0) throw new IllegalArgumentException("每件直降金额不能为负数。");
        if (discountHundredths < 1 || discountHundredths > 1000) {
            throw new IllegalArgumentException("折扣须大于 0 且不超过 10 折，最多两位小数。");
        }
    }

    public static PromotionTerms parse(String reductionYuan, String discountZhe) {
        try {
            long reduction = decimal(reductionYuan, "每件直降金额").movePointRight(2).longValueExact();
            int discount = decimal(discountZhe, "折扣").movePointRight(2).intValueExact();
            return new PromotionTerms(reduction, discount);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("金额或折扣超出支持的精确范围。", exception);
        }
    }

    public String discountText() {
        return BigDecimal.valueOf(discountHundredths, 2).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal decimal(String text, String name) {
        if (text == null || !text.strip().matches("[0-9]{1,20}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException(name + "须为非负十进制数，最多两位小数。");
        }
        return new BigDecimal(text.strip());
    }
}
