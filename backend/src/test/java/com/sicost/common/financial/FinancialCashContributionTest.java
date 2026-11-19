package com.sicost.common.financial;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.common.financial.FinancialCalculation.*;

class FinancialCashContributionTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    final String month="2026-07";
    ObjectNode snapshot() {
        var node=json.createObjectNode();
        for(String name:List.of("items","adjustments","cardMonths","cardPayments","transfers","loans","taxes","taxPayments"))node.putArray(name);
        return node;
    }
    Allocation unknownAllocation() { return allocate("2026",null,List.of(new TaxMonth(month,"WORK",null,null,null))); }
    @Test void noSourcesIsValidCashZeroEvenWhenMonthlyAllocationIsUnknown() {
        var result=contributions(snapshot(),month,unknownAllocation());
        assertThat(result.get("cashTaxPayment")).isEqualTo("0");assertThat(result.get("cashCardExpense")).isEqualTo("0");
        assertThat(result.get("verified")).isEqualTo(false);assertThat(result.get("cashIssues")).isEqualTo(List.of());
        assertThat(result.get("corporateTaxAllocation")).isNull();assertThat(result.get("cashVerified")).isEqualTo(true);
    }
    @Test void nonOperatingIncomeWithoutIoDateStaysInProfitOnly() {
        var source=snapshot();source.withArray("items").addObject().put("fin_cls_cd","NOPR_IN").put("fin_ym","202607").put("amt","-100").put("vat_amt","10").putNull("io_dt");
        var result=contributions(source,month,unknownAllocation());
        assertThat(result.get("nonOperatingIncome")).isEqualTo("-100");
        assertThat(result.get("nonOperatingIncomeVat")).isEqualTo("10");
        assertThat(result.get("cashMiscIncome")).isEqualTo("0");assertThat(result.get("cashFinancialExpense")).isEqualTo("0");
        assertThat(contributions(source,"2026-08",unknownAllocation()).get("cashMiscIncome")).isEqualTo("0");
    }
    @Test void explicitIoDateOverridesSourceMonth() {
        var source=snapshot();source.withArray("items").addObject().put("fin_cls_cd","NOPR_IN").put("fin_ym","202607").put("amt","100").put("vat_amt","0").put("io_dt","2026-08-01");
        assertThat(contributions(source,month,unknownAllocation()).get("cashMiscIncome")).isEqualTo("0");
        assertThat(contributions(source,"2026-08",unknownAllocation()).get("cashMiscIncome")).isEqualTo("100");
    }
    @Test void closedUnconfirmedTaxIsExcludedButKnownActualTaxRemainsKnown() {
        var source=snapshot();
        source.withArray("taxPayments").addObject().put("tax_yr","2026").put("pay_type_cd","MID").put("pay_yn","N").put("pay_due_dt","2026-07-31").put("pay_amt","100");
        source.withArray("taxPayments").addObject().put("tax_yr","2025").put("pay_type_cd","CNFM").put("pay_yn","Y").put("pay_due_dt","2026-03-31").put("pay_dt","2026-07-10").put("pay_amt","50");
        var result=contributions(source,month,unknownAllocation(),Set.of(month));
        assertThat(result.get("cashTaxPayment")).isEqualTo("50");assertThat(result.get("cashVerified")).isEqualTo(true);
    }
    @Test void closedUnknownPlDoesNotInvalidateActualCash() {
        var source=snapshot();source.withArray("taxPayments").addObject().put("pay_yn","N").put("pay_due_dt","2026-07-31").put("pay_type_cd","CNFM").putNull("pay_amt");
        var allocation=allocate("2026",null,List.of(new TaxMonth(month,"CLOSE",null,"Y","N")));
        var result=contributions(source,month,allocation);
        assertThat(allocation.verified()).isFalse();assertThat(result.get("cashTaxPayment")).isEqualTo("0");assertThat(result.get("cashVerified")).isEqualTo(true);
    }
    @Test void openUnknownFinalTaxIsNullNotZero() {
        var source=snapshot();source.withArray("taxPayments").addObject().put("tax_yr","2026").put("pay_type_cd","CNFM").put("pay_yn","N").put("pay_due_dt","2026-07-31").putNull("pay_amt");
        var result=contributions(source,month,unknownAllocation());
        assertThat(result.get("cashTaxPayment")).isNull();assertThat(result.get("cashVerified")).isEqualTo(false);
    }
    @Test void paidTaxWithUnknownAmountIsNeverEstimatedFromAnnual() {
        var source=snapshot();source.withArray("taxes").addObject().put("tax_yr","2026").put("est_tax_amt","100");
        source.withArray("taxPayments").addObject().put("tax_yr","2026").put("pay_type_cd","MID").put("pay_yn","Y").put("pay_dt","2026-07-01").putNull("pay_amt");
        assertThat(contributions(source,month,unknownAllocation()).get("cashTaxPayment")).isNull();
    }
    @Test void cardDetailsWithoutParentAreUnknownAndExplicitCashZeroIsValid() {
        var source=snapshot();source.withArray("cardPayments").addObject().put("pay_amt","100");
        var unknown=contributions(source,month,unknownAllocation());
        assertThat(unknown.get("cashCardExpense")).isNull();assertThat(unknown.get("cashVerified")).isEqualTo(false);
        source.withArray("cardMonths").addObject().put("cash_amt","0").put("card_sum_amt","100");
        var zero=contributions(source,month,unknownAllocation());assertThat(zero.get("cashCardExpense")).isEqualTo("0");assertThat(zero.get("cashVerified")).isEqualTo(true);
    }
    @Test void cardParentIsCountedOnceAndNullOverrideRemainsUnknown() {
        var source=snapshot();source.withArray("cardPayments").addObject().put("pay_amt","100");
        var parent=source.withArray("cardMonths").addObject().put("cash_amt","80");
        assertThat(contributions(source,month,unknownAllocation()).get("cashCardExpense")).isEqualTo("80");
        parent.putNull("cash_amt");assertThat(contributions(source,month,unknownAllocation()).get("cashCardExpense")).isNull();
    }
    @Test void allocationUsesDownTowardZeroAndFinalMonthRemainderIncludingNegativeValues() {
        var months=List.of(new TaxMonth("2026-07","WORK",null,null,null),new TaxMonth("2026-08","WORK",null,null,null),new TaxMonth("2026-09","WORK",null,null,null));
        assertThat(allocate("2026",new BigDecimal("10"),months).months()).extracting(AllocationMonth::amount).containsExactly("3","3","4");
        assertThat(allocate("2026",new BigDecimal("-10"),months).months()).extracting(AllocationMonth::amount).containsExactly("-3","-3","-4");
    }
    @Test void businessArithmeticDoesNotLoseFifteenDigitBoundaryOrNegativeZero() {
        assertThat(money(new BigDecimal("999999999999999"))).isEqualTo("999999999999999");
        assertThat(money(new BigDecimal("-0.00"))).isEqualTo("0");assertThat(money(null)).isNull();
    }
}
