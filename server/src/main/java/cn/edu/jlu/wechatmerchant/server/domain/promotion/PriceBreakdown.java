package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable money trail. Every step starts where the preceding step ended. */
public record PriceBreakdown(long originalUnitCents, int quantity, List<Step> steps) {
    public enum Stage { DIRECT_REDUCTION, UNIFORM_DISCOUNT }

    public record Step(Stage stage, BigInteger beforeCents, BigInteger afterCents) {
        public Step {
            Objects.requireNonNull(stage);
            Objects.requireNonNull(beforeCents);
            Objects.requireNonNull(afterCents);
            if (afterCents.signum() < 0 || beforeCents.compareTo(afterCents) < 0) {
                throw new IllegalArgumentException("Invalid promotion amount trail.");
            }
        }
        public BigInteger savingsCents() { return beforeCents.subtract(afterCents); }
    }

    public PriceBreakdown {
        if (originalUnitCents < 0 || quantity < 1) throw new IllegalArgumentException("单价不能为负数，数量须为正整数。");
        steps = List.copyOf(steps);
        BigInteger current = BigInteger.valueOf(originalUnitCents).multiply(BigInteger.valueOf(quantity));
        int previousStage = -1;
        for (Step step : steps) {
            if (step.stage().ordinal() <= previousStage || !current.equals(step.beforeCents())) {
                throw new IllegalArgumentException("Promotion steps must be ordered, unique and continuous.");
            }
            previousStage = step.stage().ordinal();
            current = step.afterCents();
        }
    }

    public BigInteger originalTotalCents() {
        return BigInteger.valueOf(originalUnitCents).multiply(BigInteger.valueOf(quantity));
    }
    public BigInteger estimatedTotalCents() {
        return steps.isEmpty() ? originalTotalCents() : steps.getLast().afterCents();
    }
    public BigInteger savingsTotalCents() { return originalTotalCents().subtract(estimatedTotalCents()); }

    PriceBreakdown append(Stage stage, BigInteger amount) {
        var trail = new ArrayList<>(steps);
        trail.add(new Step(stage, estimatedTotalCents(), amount));
        return new PriceBreakdown(originalUnitCents, quantity, trail);
    }
}
