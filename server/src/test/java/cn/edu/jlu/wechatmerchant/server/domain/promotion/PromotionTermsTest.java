package cn.edu.jlu.wechatmerchant.server.domain.promotion;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PromotionTermsTest {
    @ParameterizedTest
    @CsvSource({"0,10,0,1000", "1.00,9,100,900", "0.01,8.85,1,885", "20.25,0.01,2025,1",
            "92233720368547758.07,10.00,9223372036854775807,1000"})
    void parsesExactDecimalAmountsAndDiscounts(String reduction, String discount, long cents, int hundredths) {
        var terms = PromotionTerms.parse(reduction, discount);
        assertEquals(cents, terms.reductionCents()); assertEquals(hundredths, terms.discountHundredths());
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("-1", "9"), Arguments.of("1.001", "9"), Arguments.of("1e2", "9"),
                Arguments.of("NaN", "9"), Arguments.of("1,00", "9"), Arguments.of("", "9"), Arguments.of(null, "9"),
                Arguments.of("92233720368547758.08", "9"), Arguments.of("0", "0"), Arguments.of("0", "-1"),
                Arguments.of("0", "10.01"), Arguments.of("0", "0.001"), Arguments.of("0", "9.001"),
                Arguments.of("0", "9e0"), Arguments.of("0", "Infinity"), Arguments.of("0", ""), Arguments.of("0", null));
    }
    @ParameterizedTest @MethodSource("invalidInputs")
    void rejectsInvalidPrecisionZeroDiscountAndOverflowBeforeAnyCalculation(String reduction, String discount) {
        assertThrows(IllegalArgumentException.class, () -> PromotionTerms.parse(reduction, discount));
    }

    @Test void constructorEnforcesTheSameBoundsAndDisplayUsesZhe() {
        assertThrows(IllegalArgumentException.class, () -> new PromotionTerms(-1, 900));
        assertThrows(IllegalArgumentException.class, () -> new PromotionTerms(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PromotionTerms(0, 1001));
        assertEquals("8.85", PromotionTerms.parse(" 1.00 ", " 8.85 ").discountText());
        assertEquals("9", PromotionTerms.parse("0", "9.00").discountText());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"java.lang.Runtime", "UNIFORM;anything", "TIERED", "uniform"})
    void factoryCannotLoadUnregisteredOrCallerSuppliedClasses(String type) {
        assertThrows(IllegalArgumentException.class, () -> new DiscountStrategyFactory().create(type, 900));
    }

    @Test void allowlistedReflectionConstructsTheRealStrategyAndPreservesValidation() {
        var factory = new DiscountStrategyFactory();
        var strategy = factory.create("UNIFORM", 900);
        assertInstanceOf(UniformDiscountStrategy.class, strategy);
        assertEquals(BigInteger.valueOf(15), strategy.discountedTotal(5, 3));
        assertThrows(IllegalArgumentException.class, () -> factory.create("UNIFORM", 0));
        assertThrows(IllegalArgumentException.class, () -> strategy.discountedTotal(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> strategy.discountedTotal(1, 0));
    }
}
