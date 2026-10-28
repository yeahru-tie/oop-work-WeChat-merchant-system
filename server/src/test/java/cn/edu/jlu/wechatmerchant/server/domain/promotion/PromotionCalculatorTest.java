package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PromotionCalculatorTest {
    private final PromotionCalculator calculator = new PromotionCalculator();

    @ParameterizedTest
    @CsvSource({
        "10000,2,0,9,18000", "10000,2,10,9,16200", "1000,1,20,9,0",
        "5,3,0,9,15", "4,3,0,9,12", "1,7,0,5,7", "1,7,0,4.99,0",
        "1290,2,1,9,2142", "2590,1,2,10,2390", "1990,1,0,10,1990",
        "1000,2,0,8.85,1770", "100,3,0,0.01,0", "0,10,0,9,0", "5,3,0.01,9,12"
    })
    void confirmedRulesAndCentBoundariesAreExact(long unit, int quantity, String reduction, String discount, String expected) {
        var result = calculator.calculate(unit, quantity, PromotionTerms.parse(reduction, discount));
        assertEquals(new BigInteger(expected), result.estimatedTotalCents());
        assertEquals(result.originalTotalCents(), result.estimatedTotalCents().add(result.savingsTotalCents()));
        assertEquals(result.savingsTotalCents(), result.steps().stream().map(PriceBreakdown.Step::savingsCents)
                .reduce(BigInteger.ZERO, BigInteger::add));
    }

    @Test void decoratorsExplainReductionBeforeDiscountWithoutSubtractingTwice() {
        var result = calculator.calculate(10000, 2, PromotionTerms.parse("10", "9"));
        assertEquals(List.of(PriceBreakdown.Stage.DIRECT_REDUCTION, PriceBreakdown.Stage.UNIFORM_DISCOUNT),
                result.steps().stream().map(PriceBreakdown.Step::stage).toList());
        assertEquals(new BigInteger("2000"), result.steps().getFirst().savingsCents());
        assertEquals(new BigInteger("18000"), result.steps().getFirst().afterCents());
        assertEquals(new BigInteger("1800"), result.steps().getLast().savingsCents());
        assertEquals(new BigInteger("3800"), result.savingsTotalCents());
        assertThrows(UnsupportedOperationException.class, () -> result.steps().clear());
    }

    @Test void overReductionShowsOnlyTheActualReductionAndTenZheDoesNothing() {
        var free = calculator.calculate(1000, 2, PromotionTerms.parse("20", "10"));
        assertEquals(new BigInteger("2000"), free.savingsTotalCents());
        assertEquals(BigInteger.ZERO, free.estimatedTotalCents());
        var plain = calculator.calculate(1000, 2, PromotionTerms.parse("0", "10"));
        assertTrue(plain.steps().isEmpty());
        assertEquals(new BigInteger("2000"), plain.estimatedTotalCents());
    }

    @Test @Timeout(2)
    void maximumUnitAndQuantityStayExactWithoutIteratingBillionsOfTimes() {
        var result = calculator.calculate(Long.MAX_VALUE, Integer.MAX_VALUE, PromotionTerms.parse("0", "9"));
        BigInteger unit = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger rounded = unit.multiply(BigInteger.valueOf(9)).add(BigInteger.valueOf(5)).divide(BigInteger.TEN);
        assertEquals(rounded.multiply(BigInteger.valueOf(Integer.MAX_VALUE)), result.estimatedTotalCents());
        assertTrue(result.originalTotalCents().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertEquals(result.originalTotalCents(), result.estimatedTotalCents().add(result.savingsTotalCents()));
    }

    @Test void rejectsNegativePricesAndNonpositiveQuantities() {
        var terms = PromotionTerms.parse("1", "9");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(-1, 1, terms));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(100, 0, terms));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(100, -1, terms));
    }

    @Test void breakdownRejectsBrokenOrderGapsAndIncreasingAmounts() {
        var zero = BigInteger.ZERO;
        var hundred = BigInteger.valueOf(100);
        assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown.Step(PriceBreakdown.Stage.UNIFORM_DISCOUNT, zero, hundred));
        var gap = new PriceBreakdown.Step(PriceBreakdown.Stage.DIRECT_REDUCTION, BigInteger.ONE, zero);
        assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown(100, 1, List.of(gap)));
        var first = new PriceBreakdown.Step(PriceBreakdown.Stage.UNIFORM_DISCOUNT, hundred, hundred);
        var second = new PriceBreakdown.Step(PriceBreakdown.Stage.DIRECT_REDUCTION, hundred, zero);
        assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown(100, 1, List.of(first, second)));
        assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown(100, 1, List.of(first, first)));
    }

    @Test void sharedCalculatorHasNoCrossRequestPromotionState() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> calculator.calculate(10000, 2, PromotionTerms.parse("10", "9")));
            var b = pool.submit(() -> calculator.calculate(5, 3, PromotionTerms.parse("0", "9")));
            var c = pool.submit(() -> calculator.calculate(10000, 2, PromotionTerms.parse("0", "10")));
            assertEquals(new BigInteger("16200"), a.get(2, TimeUnit.SECONDS).estimatedTotalCents());
            assertEquals(new BigInteger("15"), b.get(2, TimeUnit.SECONDS).estimatedTotalCents());
            assertEquals(new BigInteger("20000"), c.get(2, TimeUnit.SECONDS).estimatedTotalCents());
        }
    }
}
