package com.sicost.cashflowops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.shared.SharedQueryDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.cashflowops.CashFlowDtos.*;
import static com.sicost.cashflowops.CashFlowContributions.*;

class CashFlowRulesTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    final YearMonth current=YearMonth.of(2026,11);
    JsonNode tree(String value)throws Exception{return json.readTree(value);}
    CashFlowRepository.Snapshot snapshot(List<Person> people,List<Payroll> payroll,List<CloseRow> closes) {
        return new CashFlowRepository.Snapshot(YearMonth.of(2026,1),people,payroll,List.of(new Standard("VAT_RT","202601",new BigDecimal("10")),new Standard("WTHLD_RT","202601",new BigDecimal("4"))),closes,List.of());
    }
    Payroll payroll(String month,String id,String type,String net,String withholding) {
        return new Payroll(month,id,new BigDecimal("1001"),new BigDecimal("100"),BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,new BigDecimal("2"),withholding==null?null:new BigDecimal(withholding),net==null?null:new BigDecimal(net),new BigDecimal("1001"),type,"DRCT",null,null,null,null,null);
    }
    Person person(String id,String type){return new Person(id,id,id,type,null,null,null,null,null,null,"DRCT",null,null,LocalDate.of(2026,1,1),null,"LEAVE","Y");}
    List<Source> sources(CashFlowRepository.Snapshot snapshot,YearMonth month,String financial) throws Exception{return calculate(snapshot,month,current,tree(financial),tree("{}"));}
    @Test void actualPayrollUsesStoredClassificationNetAndWithholdingNotCurrentPerson() throws Exception {
        var s=snapshot(List.of(person("001","FT")),List.of(payroll("202611","001","FL","0","36")),List.of());
        var rows=sources(s,current,"{}");assertThat(rows).hasSize(1);assertThat(rows.get(0).item()).isEqualTo(Item.CON_FL_PAY);assertThat(rows.get(0).amount()).isEqualTo("1065");assertThat(rows.get(0).employeeType()).isEqualTo("FL");assertThat(rows.get(0).referencePayrollMonth()).isNull();
    }
    @Test void futureUsesLatestPersonalReferenceAndDoesNotTurnLeaveIntoZero() throws Exception {
        var s=snapshot(List.of(person("001","FT")),List.of(payroll("202610","001","FT","9","0"),payroll("202611","001","FT","20","0")),List.of());
        var rows=sources(s,current.plusMonths(1),"{}");var row=rows.stream().filter(r->r.item()==Item.REG_PAY).findFirst().orElseThrow();assertThat(row.amount()).isEqualTo("20");assertThat(row.referencePayrollMonth()).isEqualTo("2026-11");assertThat(row.status()).isEqualTo("PLANNED");
    }
    @Test void futureActualUploadTakesPrecedenceOverReference() throws Exception {
        var s=snapshot(List.of(person("001","FT")),List.of(payroll("202611","001","FT","20","0"),payroll("202612","001","CT","30","5")),List.of());
        var row=sources(s,current.plusMonths(1),"{}").stream().filter(r->r.item()==Item.CON_FL_PAY).findFirst().orElseThrow();assertThat(row.amount()).isEqualTo("30");assertThat(row.status()).isEqualTo("ACTUAL");assertThat(row.referencePayrollMonth()).isNull();
    }
    @Test void missingReferenceIsUnknownAndExposesPersonWithoutFabricatingPayroll() throws Exception {
        var s=snapshot(List.of(person("001","FT")),List.of(),List.of());var rows=sources(s,current.plusMonths(1),"{}");assertThat(rows).hasSize(1);assertThat(rows.get(0).amount()).isNull();assertThat(rows.get(0).sourceId()).endsWith(":001");assertThat(rows.get(0).issues()).isNotEmpty();
    }
    @Test void requiredNullIsNotZero() throws Exception {var rows=sources(snapshot(List.of(),List.of(payroll("202611","001","FT",null,"0")),List.of()),current,"{}");assertThat(rows.get(0).status()).isEqualTo("UNKNOWN");assertThat(rows.get(0).amount()).isNull();}
    @Test void changedClassificationUsesPriorBaseAndEffectiveWithholdingRateNotHardcodedRate() throws Exception {
        var rows=sources(snapshot(List.of(person("001","FL")),List.of(payroll("202611","001","FT","20","0")),List.of()),current.plusMonths(1),"{}");
        var row=rows.stream().filter(r->r.item()==Item.CON_FL_PAY).findFirst().orElseThrow();assertThat(row.amount()).isEqualTo("961");assertThat(row.employeeType()).isEqualTo("FL");assertThat(row.referencePayrollMonth()).isEqualTo("2026-11");
    }
    @Test void closedOrCurrentMissingPayrollIsNotRepeated() throws Exception {
        var s=snapshot(List.of(person("001","FT")),List.of(payroll("202610","001","FT","20","0")),List.of(new CloseRow("202612","CLOSE")));
        assertThat(sources(s,current,"{}")).allMatch(r->r.item()==Item.EXPENSE);
        assertThat(sources(s,current.plusMonths(1),"{}")).isEmpty();
    }
    @Test void previousDecemberDeductionsAreAttributedToJanuaryExactlyOnce() throws Exception {
        var rows=sources(snapshot(List.of(),List.of(payroll("202512","001","FT","20","3")),List.of()),YearMonth.of(2026,1),"{}");assertThat(rows).hasSize(1);assertThat(rows.get(0).item()).isEqualTo(Item.EXPENSE);assertThat(rows.get(0).amount()).isEqualTo("16");assertThat(rows.get(0).sourceMonth()).isEqualTo("2025-12");assertThat(rows.get(0).attributionMonth()).isEqualTo("2026-01");
    }
    @Test void outsourcingVatIsTruncatedForEachPersonBeforeSum() throws Exception {
        var rows=sources(snapshot(List.of(),List.of(payroll("202611","001","OS",null,"0"),payroll("202611","002","OS",null,"0")),List.of()),current,"{}");assertThat(rows.stream().filter(r->r.item()==Item.OS_PAY).map(Source::amount)).containsExactly("1101","1101");
    }
    @Test void financialDatesAndFallbackDoNotCloneRepeatsOrDuplicateCardDetail() throws Exception {
        var rows=sources(snapshot(List.of(),List.of(),List.of()),current,"""
            {"items":[{"fin_ym":"202610","item_sn":1,"fin_cls_cd":"SGA","io_dt":"2026-11-03","amt":10,"vat_amt":1,"rpt_yn":"Y"},{"fin_ym":"202611","item_sn":2,"fin_cls_cd":"NOPR_IN","io_dt":null,"amt":20,"vat_amt":2}],"cardMonths":[{"pay_ym":"202611","cash_amt":0}],"cardPayments":[{"pay_ym":"202611","card_id":"card","pay_amt":999}]}
            """);assertThat(rows).hasSize(3);assertThat(rows.stream().filter(r->r.item()==Item.MISC).findFirst().orElseThrow().amount()).isEqualTo("22");assertThat(rows.stream().filter(r->r.sourceType().equals("TB_FI_CARD_MON")).findFirst().orElseThrow().amount()).isEqualTo("0");
    }
    @Test void missingCardParentWithDetailIsUnknownButBothAbsentIsValidZero() throws Exception {
        assertThat(sources(snapshot(List.of(),List.of(),List.of()),current,"{\"cardPayments\":[{\"pay_ym\":\"202611\",\"card_id\":\"001\"}]}").get(0).amount()).isNull();assertThat(sources(snapshot(List.of(),List.of(),List.of()),current,"{}")).isEmpty();
    }
    @Test void closedMonthExcludesUnpaidSchedulesButKnownPaymentDoesNotRequireAnnualAllocation() throws Exception {
        String financial="""
            {"taxPayments":[{"tax_yr":"2026","pay_type_cd":"MID","pay_yn":"N","pay_due_dt":"2026-11-01","pay_amt":30},{"tax_yr":"2025","pay_type_cd":"CNFM","pay_yn":"Y","pay_dt":"2026-11-02","pay_amt":40}]}
            """;
        var rows=sources(snapshot(List.of(),List.of(),List.of(new CloseRow("202611","CLOSE"))),current,financial);assertThat(rows).hasSize(1);assertThat(rows.get(0).amount()).isEqualTo("40");assertThat(rows.get(0).verified()).isTrue();
    }
    @Test void nullableMidIsZeroAndAutomaticFinalNeedsAnnualOnlyWhenUsed() throws Exception {
        var rows=sources(snapshot(List.of(),List.of(),List.of()),current,"""
            {"taxPayments":[{"tax_yr":"2026","pay_type_cd":"MID","pay_yn":"N","pay_due_dt":"2026-11-01","pay_amt":null},{"tax_yr":"2025","pay_type_cd":"CNFM","pay_yn":"N","pay_due_dt":"2026-11-02","pay_amt":null}]}
            """);assertThat(rows).hasSize(2);assertThat(rows.stream().filter(r->r.sourceId().equals("2026:MID")).findFirst().orElseThrow().amount()).isEqualTo("0");assertThat(rows.stream().filter(r->r.sourceId().equals("2025:CNFM")).findFirst().orElseThrow().amount()).isNull();
    }
    @Test void negativeLoanAndIndependentTransfersRemainSignedAndSeparate() throws Exception {
        var rows=sources(snapshot(List.of(),List.of(),List.of()),current,"""
            {"loans":[{"loan_sn":1,"loan_dt":"2026-11-01","io_cd":"OUT","amt":30}],"transfers":[{"xfer_sn":1,"xfer_dt":"2026-11-02","xfer_cls_cd":"SPCL","amt":20},{"xfer_sn":2,"xfer_dt":"2026-11-03","xfer_cls_cd":"EXP","amt":10}]}
            """);assertThat(rows.stream().filter(r->r.item()==Item.LOAN).findFirst().orElseThrow().amount()).isEqualTo("-30");assertThat(rows.stream().filter(r->r.item()==Item.UNPAID_LABOR).findFirst().orElseThrow().amount()).isEqualTo("20");assertThat(rows.stream().filter(r->r.item()==Item.EXPENSE).findFirst().orElseThrow().amount()).isEqualTo("10");
    }
}
