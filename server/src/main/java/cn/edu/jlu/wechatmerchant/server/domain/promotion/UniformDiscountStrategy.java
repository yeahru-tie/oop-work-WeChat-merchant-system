package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class UniformDiscountStrategy implements DiscountStrategy {
    private final BigDecimal rate;

    public UniformDiscountStrategy(int discountHundredths) {
        if (discountHundredths < 1 || discountHundredths > 1000) {
            throw new IllegalArgumentException("折扣须大于 0 且不超过 10 折。");
        }
        rate = BigDecimal.valueOf(discountHundredths, 3);
    }

    @Override public BigInteger discountedTotal(long unitCents, int quantity) {
        if (unitCents < 0 || quantity < 1) throw new IllegalArgumentException("Invalid discount input.");
        BigInteger roundedUnit = BigDecimal.valueOf(unitCents).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
        return roundedUnit.multiply(BigInteger.valueOf(quantity));
    }
}
