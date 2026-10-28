package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Reflection is limited to compiled server-owned classes, never caller-supplied class names. */
public final class DiscountStrategyFactory {
    private static final Map<String, Class<? extends DiscountStrategy>> ALLOWED =
            Map.of("UNIFORM", UniformDiscountStrategy.class);

    public DiscountStrategy create(String type, int discountHundredths) {
        Class<? extends DiscountStrategy> implementation = type == null ? null : ALLOWED.get(type);
        if (implementation == null) throw new IllegalArgumentException("不支持的折扣策略。");
        try {
            return implementation.getConstructor(int.class).newInstance(discountHundredths);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("折扣策略初始化失败。", exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("折扣策略装配失败。", exception);
        }
    }
}
