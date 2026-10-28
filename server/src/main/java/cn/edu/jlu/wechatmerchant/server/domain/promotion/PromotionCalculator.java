package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Pure calculation shared by the preview and subsequent application services. */
public final class PromotionCalculator {
    private final DiscountStrategyFactory strategies = new DiscountStrategyFactory();

    public PriceBreakdown calculate(long unitCents, int quantity, PromotionTerms terms) {
        Objects.requireNonNull(terms);
        Calculation chain = (unit, count) -> new PriceBreakdown(unit, count, List.of());
        if (terms.reductionCents() > 0) chain = new ReductionDecorator(chain, terms.reductionCents());
        if (terms.discountHundredths() < 1000) {
            chain = new DiscountDecorator(chain, strategies.create("UNIFORM", terms.discountHundredths()));
        }
        return chain.calculate(unitCents, quantity);
    }

    @FunctionalInterface private interface Calculation {
        PriceBreakdown calculate(long unitCents, int quantity);
    }

    private record ReductionDecorator(Calculation inner, long reductionCents) implements Calculation {
        @Override public PriceBreakdown calculate(long unitCents, int quantity) {
            PriceBreakdown before = inner.calculate(unitCents, quantity);
            long reducedUnit = unitCents - Math.min(unitCents, reductionCents);
            return before.append(PriceBreakdown.Stage.DIRECT_REDUCTION,
                    BigInteger.valueOf(reducedUnit).multiply(BigInteger.valueOf(quantity)));
        }
    }

    private record DiscountDecorator(Calculation inner, DiscountStrategy strategy) implements Calculation {
        @Override public PriceBreakdown calculate(long unitCents, int quantity) {
            PriceBreakdown before = inner.calculate(unitCents, quantity);
            long baseUnit = before.estimatedTotalCents().divide(BigInteger.valueOf(quantity)).longValueExact();
            return before.append(PriceBreakdown.Stage.UNIFORM_DISCOUNT, strategy.discountedTotal(baseUnit, quantity));
        }
    }
}
