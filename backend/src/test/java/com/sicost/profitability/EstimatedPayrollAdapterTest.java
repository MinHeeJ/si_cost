package com.sicost.profitability;

import com.sicost.common.payroll.PayrollCalculation;
import com.sicost.shared.SharedQueryDtos.CodeLookup;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static org.assertj.core.api.Assertions.*;

class EstimatedPayrollAdapterTest {
    final EstimatedPayrollAdapter adapter = new EstimatedPayrollAdapter();
    static Sources estimate(Person person, String amount, List<CodeLookup> rates) {
        var base = sources(person,List.of(),List.of(),List.of());
        return new Sources(base.period(),base.actor(),base.asOfDate(),base.people(),
            List.of(new Price(person.personId(),MONTH,null,n(amount),n("4970000"))),base.states(),base.payroll(),base.assignments(),base.projectMonths(),
            base.projects(),base.projectParts(),base.turnkey(),base.storedCosts(),base.standards(),base.parts(),base.closes(),rates);
    }
    static List<CodeLookup> rates() {
        // Same small boundary inputs as B1ImportTest; not production defaults or OQ fixtures.
        return List.of(code("ALLOW_TEAM","10"),code("ALLOW_GROUP","20"),code("INS_RATE","10"),code("SEV_RATE","8.3333"));
    }
    static CodeLookup code(String code, String value) { return new CodeLookup(code,code,null,null,null,value,null); }
    @ParameterizedTest @ValueSource(strings={"FT","CT","FL","OS"})
    void estimateUsesExactlySharedB1DerivationAndNeverAddsPayrollRows(String type) {
        var base = person(type);
        var p = new Person(base.personId(),base.managementNo(),base.name(),type,null,null,null,base.jobCode(),"TEAM","팀장",
            "DRCT",base.personPartId(),base.personPartName(),null,null,"WORK","Y");
        var s = estimate(p,"100",rates());
        var result = adapter.resolve(s,p,MONTH);
        var expected = PayrollCalculation.calculate(new PayrollCalculation.Classification(type,"DRCT","TEAM"),
            new PayrollCalculation.Amounts(type.equals("OS")?BigDecimal.ZERO:n("100"),null,BigDecimal.ZERO,BigDecimal.ZERO,
                type.equals("OS")?n("100"):BigDecimal.ZERO),Map.of("ALLOW_TEAM",n("10"),"ALLOW_GROUP",n("20"),"INS_RATE",n("10"),"SEV_RATE",n("8.3333")));
        assertThat(result.source().estimated()).isTrue();
        assertThat(result.source().pay()).isEqualTo(money(type.equals("OS")?expected.vendorSupply():expected.pay()));
        assertThat(result.source().allowance()).isEqualTo(money(expected.allowance()));
        assertThat(result.source().companyInsurance()).isEqualTo(money(expected.companyInsurance()));
        assertThat(result.source().retirementReserve()).isEqualTo(money(expected.retirement()));
        assertThat(result.source().netPay()).isEqualTo(money(type.equals("OS")?expected.vendorSupply():expected.realPay()));
        assertThat(s.payroll()).isEmpty(); assertThat(result.issues()).isEmpty();
    }
    @Test void missingEffectivePriceIsUnknownButExplicitZeroWithValidRatesIsRealZero() {
        var p = person("FT");
        var s = estimate(p,null,rates());
        var unknown = adapter.resolve(s,p,MONTH);
        assertThat(unknown.labor()).isNull();
        assertThat(unknown.source().netPay()).isNull();
        assertThat(unknown.issues()).anyMatch(i -> i.month().equals(MONTH) && i.message().contains("급여 업로드"));
        assertThat(adapter.resolve(estimate(p,"0",rates()),p,MONTH).labor()).isZero();
        assertThat(adapter.resolve(estimate(p,"100",List.of()),p,MONTH).labor()).isNull();
        assertThat(adapter.resolve(estimate(p,"100",List.of(code("INS_RATE","-1"))),p,MONTH).labor()).isNull();
        var result = new MonthlyCostCalculation(adapter).calculate(s,p,MONTH);
        assertThat(result.rows().get(0).laborAllocated()).isNull();
        assertThat(result.rows().get(0).profit()).isNull();
    }
    @Test void uploadedZeroAndNullableReferencesOverrideCurrentPriceAndMissingRates() {
        var p = person("OS"); var base = estimate(p,"5000000",List.of());
        var stored = pay("FT","0");
        var s = new Sources(base.period(),base.actor(),base.asOfDate(),base.people(),base.prices(),base.states(),List.of(stored),base.assignments(),base.projectMonths(),
            base.projects(),base.projectParts(),base.turnkey(),base.storedCosts(),base.standards(),base.parts(),base.closes(),base.allowanceCodes());
        var result = adapter.resolve(s,p,MONTH);
        assertThat(result.source().employeeType()).isEqualTo("FT");
        assertThat(result.labor()).isZero(); assertThat(result.source().estimated()).isFalse();
        assertThat(result.source().selfInsurance()).isNull(); assertThat(result.source().incomeTax()).isNull();
        assertThat(result.source().netPay()).isNull();
        assertThat(s.payroll()).containsExactly(stored);
    }
    @ParameterizedTest @ValueSource(strings={"FT","CT","FL","OS"})
    void uploadedLaborUsesStoredComponentsNotDeductionsOrVat(String type) {
        var original = new Payroll(MONTH,"actual",n("100"),n("10"),n("20"),n("999"),n("30"),n("999"),n("999"),n("-999"),n("40"),type,"DRCT",null,null,null,null,null);
        var changed = new Payroll(MONTH,"actual",original.pay(),original.allowance(),original.companyInsurance(),BigDecimal.ZERO,original.retirementReserve(),
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,original.vendorSupply(),type,"DRCT",null,null,null,null,null);
        BigDecimal expected = switch(type) {case "FT" -> n("160");case "CT" -> n("130");case "FL" -> n("100");default -> n("40");};
        assertThat(adapter.uploaded(original).labor()).isEqualByComparingTo(expected);
        assertThat(adapter.uploaded(changed).labor()).isEqualByComparingTo(expected);
        if(type.equals("OS")) {
            assertThat(adapter.uploaded(original).source().pay()).isEqualTo("40");
            assertThat(adapter.uploaded(original).source().netPay()).isEqualTo("40");
        }
    }
    @Test void unknownVendorSupplyNeverFallsBackToCurrentPriceOrZero() {
        var p = pay("OS",null);
        assertThat(adapter.uploaded(p).labor()).isNull();
        assertThat(adapter.uploaded(p).issues()).isNotEmpty();
        assertThat(adapter.uploaded(pay("OS","0")).labor()).isZero();
    }
}
