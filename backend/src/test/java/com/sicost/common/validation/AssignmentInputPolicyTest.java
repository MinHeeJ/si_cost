package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AssignmentInputPolicyTest {
    @Test void creationDefaultsOnlyAbsentOrBlankMmAndChangesRequireExplicitValue() {
        for (String value : new String[]{null,""," \u00a0 "}) {
            assertThat(AssignmentInputPolicy.contractMm(value,true).toPlainString()).isEqualTo("1.00");
            invalid(() -> AssignmentInputPolicy.contractMm(value,false),"contractMm");
        }
    }
    @ParameterizedTest @ValueSource(strings={"0.01","0.1","0.99","1","1.0","1.00"})
    void exactMmBoundsAreAcceptedWithoutRounding(String input) {
        assertThat(AssignmentInputPolicy.contractMm(input,false)).isEqualByComparingTo(input);
        assertThat(AssignmentInputPolicy.contractMm(input,false).scale()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"0","0.00","1.01","0.001","1.000",".1","1.","-0.1","+1","1e0","01","abc"})
    void invalidMmIsNotRoundedOrClamped(String input) { invalid(() -> AssignmentInputPolicy.contractMm(input,true),"contractMm"); }
    @Test void moneyNormalizationAndPositivePaidPriceReuseExistingExactDecimalPolicy() {
        assertThat(ProjectInputPolicy.amount("0","amount")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(AssignmentInputPolicy.paidUnitPrice(" 1 원 ").toPlainString()).isEqualTo("1");
        assertThat(AssignmentInputPolicy.paidUnitPrice("999,999,999,999,999원").toPlainString()).isEqualTo("999999999999999");
        invalid(() -> AssignmentInputPolicy.paidUnitPrice("0"),"billingUnitPrice");
        invalid(() -> AssignmentInputPolicy.paidUnitPrice(null),"billingUnitPrice");
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1e3","+1","NaN","1000000000000000","원1","1원원"})
    void malformedPaidPriceUsesNewErrorContract(String input) { invalid(() -> AssignmentInputPolicy.paidUnitPrice(input),"billingUnitPrice"); }
    @Test void activeCodeMatchesActualRecordWithoutChangingCaseOrTruncating() {
        var stored=new PeopleInputPolicy.CodeSelection("DEV","Y","N");
        assertThat(AssignmentInputPolicy.activeCode(" DEV ",stored,"jobCode")).isEqualTo("DEV");
        invalid(() -> AssignmentInputPolicy.activeCode("dev",stored,"jobCode"),"jobCode");
        invalid(() -> AssignmentInputPolicy.activeCode("ABCDEFGHIJK",new PeopleInputPolicy.CodeSelection("ABCDEFGHIJK","Y","N"),"jobCode"),"jobCode");
        invalid(() -> AssignmentInputPolicy.activeCode("DEV",null,"jobCode"),"jobCode");
    }
    @Test void stoppedAndDeletedCodesAreNeverNewSelections() {
        invalid(() -> AssignmentInputPolicy.activeCode("DEV",new PeopleInputPolicy.CodeSelection("DEV","N","N"),"jobCode"),"jobCode");
        invalid(() -> AssignmentInputPolicy.activeCode("DEV",new PeopleInputPolicy.CodeSelection("DEV","Y","Y"),"jobCode"),"jobCode");
        invalid(() -> AssignmentInputPolicy.activeCode("ETC",new PeopleInputPolicy.CodeSelection("ETC","N","N"),"reasonCode"),"reasonCode");
    }
    @Test void optionalMemoAndRequiredCorrectionHaveIndependentTwoHundredCharacterLimits() {
        assertThat(AssignmentInputPolicy.memo(" \u00a0 ")).isNull();
        assertThat(AssignmentInputPolicy.memo("가".repeat(200))).hasSize(200);
        assertThat(AssignmentInputPolicy.correctionContent(" 가 ")).isEqualTo("가");
        assertThat(AssignmentInputPolicy.correctionContent("😀".repeat(200))).isEqualTo("😀".repeat(200));
        invalid(() -> AssignmentInputPolicy.memo("가".repeat(201)),"memo");
        invalid(() -> AssignmentInputPolicy.correctionContent("가".repeat(201)),"correctContent");
        invalid(() -> AssignmentInputPolicy.correctionContent(" \u00a0 "),"correctContent");
    }
    @Test void pagingKeepsExistingDefaultsAndUsesInvalidInputForAssignmentErrors() {
        assertThat(AssignmentInputPolicy.size(null)).isEqualTo(50);
        for (int size : new int[]{20,50,100}) assertThat(AssignmentInputPolicy.size(size)).isEqualTo(size);
        assertThat(AssignmentInputPolicy.page(1)).isEqualTo(1);
        invalid(() -> AssignmentInputPolicy.size(30),"size");
        invalid(() -> AssignmentInputPolicy.page(0),"page");
        invalid(() -> AssignmentInputPolicy.page(-1),"page");
    }
    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String field) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(400);assertThat(e.code()).isEqualTo("INVALID_INPUT");assertThat(e.field()).isEqualTo(field);
        });
    }
}
