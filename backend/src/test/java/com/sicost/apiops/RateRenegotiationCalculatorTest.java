package com.sicost.apiops;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.apiops.RateRenegotiationCalculator.*;

class RateRenegotiationCalculatorTest {
    final RateRenegotiationCalculator calculator = new RateRenegotiationCalculator();
    static BigDecimal d(String text) { return text == null ? null : new BigDecimal(text); }
    static Amounts amounts(String labor, String monthly, String contract, String target) {
        return new Amounts(d("6000000"), d(labor), d("6000000"), d(monthly), d(contract), d(target), d("5"));
    }
    @Test void approvedExampleUsesAllocatedLaborAndRoundedTarget() {
        assertThat(calculator.calculate(amounts("5400000", "1.00", "1.00", "20")))
            .isEqualTo(new Metrics("10.0", "6750000", "12.5", "3600000", "9000000"));
    }
    @Test void monthlyDenominatorNeverReplacesContractAnnualMultiplier() {
        var result = calculator.calculate(amounts("5400000", "0.52", "1.00", "20"));
        assertThat(result.targetUnitPrice()).isEqualTo("12990000");
        assertThat(result.annualGain()).isEqualTo("3600000");
    }
    @ParameterizedTest @ValueSource(strings={"100", "101", "-1"})
    void invalidTargetOnlyNullsTargetMetrics(String target) {
        assertThat(calculator.calculate(amounts("5400000", "1", "1", target)))
            .isEqualTo(new Metrics("10.0", null, null, "3600000", null));
    }
    @Test void missingTargetPreservesCurrentAndHypotheticalMetrics() {
        assertThat(calculator.calculate(amounts("5400000", "1", "1", null)).annualGain()).isEqualTo("3600000");
    }
    @Test void zeroTargetValidAndLowerTargetDoesNotSuggestPriceCut() {
        assertThat(calculator.calculate(amounts("5400000", "1", "1", "0")))
            .isEqualTo(new Metrics("10.0", "5400000", "0.0", "3600000", "0"));
    }
    @Test void ceilingOccursOnlyAtFinalTenThousandBoundary() {
        assertThat(calculator.calculate(amounts("5400001", "1", "1", "20")).targetUnitPrice()).isEqualTo("6760000");
        assertThat(calculator.calculate(amounts("5400000", "1", "1", "20")).targetUnitPrice()).isEqualTo("6750000");
    }
    @Test void strictUnroundedMarginComparisonDoesNotUseDisplayRate() {
        assertThat(calculator.marginBelow(d("100000"), d("85001"), "15")).isTrue();
        assertThat(calculator.marginBelow(d("100000"), d("85000"), "15")).isFalse();
    }
    @Test void zeroAndMissingFormulaSourcesAreRejected() {
        assertThatThrownBy(() -> calculator.calculate(amounts(null, "1", "1", "20"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(amounts("1", "0", "1", "20"))).isInstanceOf(IllegalArgumentException.class);
    }
}
