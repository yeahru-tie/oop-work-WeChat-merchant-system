package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.math.BigInteger;

/** Quantity is explicit so the engine need not iterate once per purchased unit. */
public interface DiscountStrategy {
    BigInteger discountedTotal(long unitCents, int quantity);
}
