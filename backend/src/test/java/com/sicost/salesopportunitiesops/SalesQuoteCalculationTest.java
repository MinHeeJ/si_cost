package com.sicost.salesopportunitiesops;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

class SalesQuoteCalculationTest {
    final SalesQuoteCalculation calculation=new SalesQuoteCalculation();
    QuoteSources source(String target,List<PersonCost> people){return new QuoteSources("2026-07","2026-06",List.of(
        new Standard("QUOTE_APRV_RT",new BigDecimal("15")),new Standard("QUOTE_REAPRV_GAP",new BigDecimal("0.5")),new Standard("TGT_MRGN_RT",new BigDecimal(target))),
        List.of(new Code("GRADE","MD",null),new Code("JOB","BE","10000000")),people);}
    DraftNeed need(String job){return new DraftNeed(1,job,null,2,"2026-08",3,"0.25","10000000");}
    PersonCost person(String id,String job,String cost){return new PersonCost(id,job,"MD",new BigDecimal(cost));}
    @Test void distinctJobGradeCohortRoundHalfUpAndExactQuantity(){
        Quote q=calculation.calculate(List.of(need("BE")),source("20",List.of(person("a","BE","5000000"),person("b","BE","5010000"),person("c","FE","9000000"))));
        QuoteRow r=q.rows().get(0);assertThat(r.unitMonthlyCost()).isEqualTo("5010000");assertThat(r.cohortCount()).isEqualTo(2);assertThat(r.fallbackUsed()).isFalse();assertThat(r.quantityMm()).isEqualTo("1.50");
        assertThat(q.revenue()).isEqualTo("15000000");assertThat(q.cost()).isEqualTo("7515000");assertThat(r.targetRate()).isEqualTo("6270000");assertThat(q.targetAmount()).isEqualTo("10000000");assertThat(q.judgement()).isEqualTo("MET");
    }
    @Test void singletonJobUsesWholeGradeAndTarget100OnlyRemovesTargets(){
        Quote q=calculation.calculate(List.of(need("FE")),source("100",List.of(person("a","BE","5000000"),person("b","FE","7000000"))));
        assertThat(q.rows().get(0).cohort()).isEqualTo("GRADE");assertThat(q.rows().get(0).fallbackUsed()).isTrue();assertThat(q.rows().get(0).unitMonthlyCost()).isEqualTo("6000000");assertThat(q.minimumAmount()).isEqualTo("9000000");assertThat(q.targetAmount()).isNull();assertThat(q.rows().get(0).targetRate()).isNull();assertThat(q.targetUnavailableReason()).contains("100%");
    }
    @Test void rawFractionNotDisplayRateDeterminesApprovalBoundary(){
        DraftNeed n=new DraftNeed(1,"BE","MD",1,"2026-08",1,"1.00","1176470");
        Quote q=calculation.calculate(List.of(n),source("20",List.of(person("a","BE","1000000"))));
        assertThat(q.storedProfitRate()).isEqualTo("15.00");assertThat(q.judgement()).isEqualTo("APPROVAL_REQUIRED");
    }
    @Test void absentCohortNeverCreatesZeroAndIncompleteRowsAreExcluded(){
        Quote q=calculation.calculate(List.of(need("BE"),new DraftNeed(null,null,null,null,null,null,null,null)),source("20",List.of()));
        assertThat(q.cost()).isNull();assertThat(q.storedProfitRate()).isNull();assertThat(q.rows().get(0).unitMonthlyCost()).isNull();assertThat(q.rows().get(1).quantityMm()).isNull();assertThat(q.issues()).extracting(Issue::status).contains("UNKNOWN","INCOMPLETE");
    }
    @Test void missingInvalidStandardsAreNotReplacedWithInitialValues(){
        var s=source("101",List.of(person("a","BE","1000000")));Quote q=calculation.calculate(List.of(need("BE")),s);
        assertThat(q.targetMargin()).isNull();assertThat(q.targetUnavailableReason()).contains("미확인");assertThat(q.issues()).extracting(Issue::field).contains("TGT_MRGN_RT");
    }
}
