package com.sicost.budget;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.budget.ProjectBudgetCalculation.*;
import static org.assertj.core.api.Assertions.*;

class ProjectBudgetCalculationTest {
    @Test void previewTruncatesOnlyCalculatedMoneyAndSupportsRateEndpoints() {
        assertThat(preview(new BigDecimal("101"),new BigDecimal("20"))).isEqualByComparingTo("80");
        assertThat(preview(new BigDecimal("999999999999999"),BigDecimal.ZERO)).isEqualByComparingTo("999999999999999");
        assertThat(preview(new BigDecimal("101"),new BigDecimal("100"))).isEqualByComparingTo("0");
        assertThat(preview(new BigDecimal("101"),new BigDecimal("12.3456"))).isEqualByComparingTo("88");
    }
    @ParameterizedTest @ValueSource(strings={"-1","100.0001"})
    void invalidRateCannotProduceAutomaticZero(String rate) {
        assertThat(preview(new BigDecimal("101"),new BigDecimal(rate))).isNull();
        assertThatThrownBy(()->resolve("TK",new BigDecimal("101"),null,null,"AUTO",null,new BigDecimal(rate)))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("budgetAmount"));
    }
    @Test void absentRateAllowsExplicitManualZeroButNotAutomaticSave() {
        assertThat(preview(new BigDecimal("101"),null)).isNull();
        assertThat(resolve("TK",new BigDecimal("101"),null,null,"MANUAL",BigDecimal.ZERO,null)).isEqualTo(new Budget(BigDecimal.ZERO,"Y"));
        assertThatThrownBy(()->resolve("TK",new BigDecimal("101"),null,null,"AUTO",null,null)).isInstanceOf(ApiException.class);
    }
    @Test void manualIntentAtEqualAutomaticAmountIsAlwaysManualAndCannotRevert() {
        Budget manual=resolve("TK",new BigDecimal("101"),new Budget(new BigDecimal("80"),"N"),new BigDecimal("101"),"MANUAL",new BigDecimal("80"),new BigDecimal("20"));
        assertThat(manual.manual()).isEqualTo("Y");
        assertThatThrownBy(()->resolve("TK",new BigDecimal("101"),manual,new BigDecimal("101"),"AUTO",null,new BigDecimal("20")))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("budgetMode"));
    }
    @Test void manualBudgetSurvivesContractAmountAndStandardsChanges() {
        Budget previous=new Budget(new BigDecimal("80"),"Y");
        assertThat(resolve("TK",new BigDecimal("900"),previous,new BigDecimal("101"),null,null,new BigDecimal("30"))).isSameAs(previous);
    }
    @Test void automaticRecalculatesOnlyForActualContractAmountChange() {
        Budget previous=new Budget(new BigDecimal("80"),"N");
        assertThat(resolve("TK",new BigDecimal("101.00"),previous,new BigDecimal("101"),"AUTO",null,new BigDecimal("30"))).isSameAs(previous);
        assertThat(resolve("TK",new BigDecimal("200"),previous,new BigDecimal("101"),null,null,new BigDecimal("30"))).isEqualTo(new Budget(new BigDecimal("140"),"N"));
    }
    @Test void internalBudgetRemainsNullAndAutomatic() {
        assertThat(resolve("IN",BigDecimal.ZERO,null,null,"AUTO",null,null)).isEqualTo(new Budget(null,"N"));
        assertThatThrownBy(()->resolve("IN",BigDecimal.ZERO,null,null,"MANUAL",BigDecimal.ZERO,null)).isInstanceOf(ApiException.class);
    }
    @Test void manualAmountIsRequiredAndDirectFractionsNeverRounded() {
        assertThatThrownBy(()->resolve("TK",BigDecimal.TEN,null,null,"MANUAL",null,null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->resolve("TK",BigDecimal.TEN,null,null,"MANUAL",new BigDecimal("0.1"),null)).isInstanceOf(ApiException.class);
    }
    @Test void allowanceDefaultsAcceptOnlyValidWonIntegersWithoutInventingZero() {
        assertThat(allowanceDefault(null)).isNull();assertThat(allowanceDefault(new BigDecimal("-1"))).isNull();
        assertThat(allowanceDefault(new BigDecimal("1.25"))).isNull();assertThat(allowanceDefault(new BigDecimal("1000000000000000"))).isNull();
        assertThat(allowanceDefault(new BigDecimal("0.0000"))).isEqualByComparingTo("0");assertThat(allowanceDefault(new BigDecimal("15000.0000"))).isEqualByComparingTo("15000");
    }
}
