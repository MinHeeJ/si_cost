package com.sicost.common.payroll;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PayrollCalculationTest {
    static BigDecimal n(String value) { return new BigDecimal(value); }
    final Map<String,BigDecimal> rates = Map.of("ALLOW_TEAM",n("10"),"ALLOW_GROUP",n("20"),"INS_RATE",n("10"),"SEV_RATE",n("8.3333"));
    @Test void explicitZeroInsuranceWinsAndFtRetirementIncludesAllowance() {
        var result = PayrollCalculation.calculate(new PayrollCalculation.Classification("FT","DRCT","TEAM"),
            new PayrollCalculation.Amounts(n("100"),BigDecimal.ZERO,n("200"),n("1"),null),rates);
        assertThat(result.companyInsurance()).isZero();
        assertThat(result.allowance()).isEqualByComparingTo("10");
        assertThat(result.retirement()).isEqualByComparingTo("9");
        assertThat(result.realPay()).isEqualByComparingTo("-91");
    }
    @Test void contractWorkerDerivesInsuranceWithholdingAndNetButNotRetirement() {
        var result = PayrollCalculation.calculate(new PayrollCalculation.Classification("CT","DRCT","GROUP"),
            new PayrollCalculation.Amounts(n("100"),null,n("2"),n("3"),null),rates);
        assertThat(result.companyInsurance()).isEqualByComparingTo("10");
        assertThat(result.withholding()).isEqualByComparingTo("3");
        assertThat(result.retirement()).isZero();
        assertThat(result.realPay()).isEqualByComparingTo("112");
    }
    @Test void freelancerAndVendorDoNotRequireInsuranceAllowanceOrRetirementRates() {
        var fl = PayrollCalculation.calculate(new PayrollCalculation.Classification("FL","DRCT","TEAM"),
            new PayrollCalculation.Amounts(n("100"),null,null,n("1"),null),Map.of());
        assertThat(fl.withholding()).isEqualByComparingTo("3"); assertThat(fl.realPay()).isEqualByComparingTo("96");
        assertThat(fl.allowance()).isZero(); assertThat(fl.companyInsurance()).isZero();
        var os = PayrollCalculation.calculate(new PayrollCalculation.Classification("OS","DRCT","TEAM"),
            new PayrollCalculation.Amounts(null,null,null,null,n("100")),Map.of());
        assertThat(os.pay()).isZero(); assertThat(os.realPay()).isNull(); assertThat(os.vendorSupply()).isEqualByComparingTo("100");
    }
    @Test void missingRatesInvalidAllowanceAndOverflowAreErrorsRatherThanFallbacks() {
        var person = new PayrollCalculation.Classification("FT","DRCT","TEAM");
        var input = new PayrollCalculation.Amounts(n("100"),null,BigDecimal.ZERO,BigDecimal.ZERO,null);
        assertThatThrownBy(() -> PayrollCalculation.calculate(person,input,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayrollCalculation.calculate(person,input,Map.of("ALLOW_TEAM",n("1.5"))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("원 단위");
        assertThatThrownBy(() -> PayrollCalculation.calculate(person,new PayrollCalculation.Amounts(n("999999999999999"),null,BigDecimal.ZERO,BigDecimal.ZERO,null),rates))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NUMERIC(15)");
    }
}
