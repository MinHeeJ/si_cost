package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MasterDataValidatorTest {
    @Test void requiredOptionalAndTrimDoNotTruncate() {
        assertThat(InputValidator.text("  이름  ",2,"name")).isEqualTo("이름");
        assertThat(InputValidator.optionalText(" \n ",20,"phone")).isNull();
        assertThatThrownBy(() -> InputValidator.text(" \n ",20,"name")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InputValidator.text("이름",1,"name")).isInstanceOf(ApiException.class);
    }
    @Test void emailAllowsExactly100CharactersAndTrims() {
        String email = "a".repeat(94)+"@b.com";
        assertThat(MasterDataValidator.email(" "+email+" ","email")).hasSize(100).isEqualTo(email);
    }
    @ParameterizedTest @ValueSource(strings={"a b@c.com","a\nb@c.com","abc","a@b","@b.com","a@@b.com"})
    void emailRejectsInvalidSyntax(String value) { assert400(() -> MasterDataValidator.email(value,"email")); }
    @Test void emailRejects101Characters() { assert400(() -> MasterDataValidator.email("a".repeat(95)+"@b.com","email")); }
    @ParameterizedTest @ValueSource(strings={"0","100","40.25","40.00","000100.00"})
    void rateAllowsInclusiveBoundariesWithoutRounding(String value) {
        assertThat(MasterDataValidator.profitRate(value)).isEqualByComparingTo(new BigDecimal(value));
    }
    @ParameterizedTest @ValueSource(strings={"-0","+1","1e1","NaN","Infinity","100.01","1.001","1 0","1%"})
    void rateRejectsInvalidOrLossyInput(String value) { assert400(() -> MasterDataValidator.profitRate(value)); }
    @Test void expenseNormalizesWonAndKeepsFifteenDigits() {
        assertThat(MasterDataValidator.salesExpenseAmount(" 1,000원 ").toPlainString()).isEqualTo("1000");
        assertThat(MasterDataValidator.salesExpenseAmount("0")).isEqualByComparingTo("0");
        assertThat(MasterDataValidator.salesExpenseAmount("999999999999999").toPlainString()).isEqualTo("999999999999999");
    }
    @ParameterizedTest @ValueSource(strings={"1000000000000000","1.5","-1","+1","1e3","원","원1","1원원"})
    void expenseRejectsInvalidOrExcessInput(String value) { assert400(() -> MasterDataValidator.salesExpenseAmount(value)); }
    @Test void exactUnitDisplayNeverMutatesTheStoredAmount() {
        BigDecimal won=new BigDecimal("1234567");
        assertThat(MasterDataValidator.amountInUnit(won,"원").toPlainString()).isEqualTo("1234567");
        assertThat(MasterDataValidator.amountInUnit(won,"천원").toPlainString()).isEqualTo("1234.567");
        assertThat(MasterDataValidator.amountInUnit(won,"백만원").toPlainString()).isEqualTo("1.234567");
        assertThat(won.toPlainString()).isEqualTo("1234567");
        assertThat(MasterDataValidator.decimalString(new BigDecimal("40.00"))).isEqualTo("40");
    }
    @Test void monthsUseRealCalendarArithmeticAndSupportedBounds() {
        assertThat(MasterDataValidator.month("0001-01","effectiveMonth").toString()).isEqualTo("0001-01");
        assertThat(MasterDataValidator.month("9999-12","effectiveMonth").toString()).isEqualTo("9999-12");
        assertThat(MasterDataValidator.month("2026-12","effectiveMonth").plusMonths(1).toString()).isEqualTo("2027-01");
        assertThat(MasterDataValidator.month("2026-01","effectiveMonth").minusMonths(1).toString()).isEqualTo("2025-12");
        assert400(() -> MasterDataValidator.month("2026-00","effectiveMonth"));
        assert400(() -> MasterDataValidator.month("2026-13","effectiveMonth"));
    }
    @Test void searchIsCaseSensitiveAndOptional() {
        assertThat(MasterDataValidator.search("  Abc  ")).isEqualTo("Abc");
        assertThat(MasterDataValidator.search("  ")).isNull();
        assert400(() -> MasterDataValidator.search("a".repeat(101)));
    }
    void assert400(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(400));
    }
}
