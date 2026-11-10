package com.sicost.common.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class InputValidatorTest {
    @Test void groupIdentifierBoundaryAndNormalization() {
        assertThat(InputValidator.groupId("  a_2 ")).isEqualTo("A_2");
        assertThat(InputValidator.groupId("a".repeat(30))).hasSize(30);
        assertThatThrownBy(() -> InputValidator.groupId("a".repeat(31))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InputValidator.groupId("1ABC")).isInstanceOf(ApiException.class);
    }
    @Test void codeIdentifierBoundaryAndNormalization() {
        assertThat(InputValidator.code(" 1_a ")).isEqualTo("1_A");
        assertThat(InputValidator.code("a".repeat(20))).hasSize(20);
        assertThatThrownBy(() -> InputValidator.code("a".repeat(21))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InputValidator.code("A-1")).isInstanceOf(ApiException.class);
    }
    @Test void requiredNamesRejectBlankAndNeverTruncate() {
        for (int max : new int[]{50, 100}) {
            assertThat(InputValidator.text("가".repeat(max), max, "name")).hasSize(max);
            assertThatThrownBy(() -> InputValidator.text("가".repeat(max + 1), max, "name")).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> InputValidator.text("  ", 50, "name")).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @ValueSource(strings={"-1", "1e2", "NaN", "Infinity", "100000000000", "1.12345", "1원원", "+1", ".1"})
    void invalidDecimalsReturn400(String input) {
        assertThatThrownBy(() -> DecimalValidator.standard(input)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(400));
    }
    @Test void exactFourDecimalsAreNotRoundedOrLimitedByPercent() {
        assertThat(DecimalValidator.standard(" 99,999,999,999.9999 원 ").toPlainString()).isEqualTo("99999999999.9999");
        assertThat(DecimalValidator.standard("123.4500% ").toPlainString()).isEqualTo("123.4500");
        assertThat(DecimalValidator.standard("101%")).isEqualByComparingTo("101");
    }
    @Test void allowanceEmptyZeroAndStandardEmptyErrorAreDistinct() {
        assertThat(DecimalValidator.allowance("  ")).isEqualTo(BigDecimal.ZERO);
        assertThatThrownBy(() -> DecimalValidator.standard(" ")).isInstanceOf(ApiException.class);
    }
    @Test void jobAllowsFifteenDigitIntegersOnly() {
        assertThat(DecimalValidator.jobAmount("999,999,999,999,999원")).isEqualTo("999999999999999");
        assertThatThrownBy(() -> DecimalValidator.jobAmount("1000000000000000")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DecimalValidator.jobAmount("1.1")).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @ValueSource(strings={"2026-00", "2026-13", "0000-01", "2026-1", "10000-01"})
    void rejectsInvalidMonths(String value) { assertThatThrownBy(() -> PeriodValidator.month(value)).isInstanceOf(ApiException.class); }
    @ParameterizedTest @ValueSource(strings={"0001-01", "9999-12", "2026-02"})
    void databaseMonthRoundTrip(String month) { assertThat(DatabaseMonthCodec.toApi(DatabaseMonthCodec.toDatabase(month))).isEqualTo(month); }
    @Test void yesNoAndImmutablePrimaryKeys() {
        assertThat(InputValidator.yesNo(" Y ", "useYn")).isEqualTo("Y");
        assertThatThrownBy(() -> InputValidator.yesNo("true", "useYn")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InputValidator.immutable("A", "B", "code")).isInstanceOf(ApiException.class);
    }
    @Test void jsonHasExactLengthLimitAndBooleanType() {
        JsonValidator validator = new JsonValidator(new ObjectMapper());
        assertThat(validator.parse("\"" + "a".repeat(1998) + "\"").isTextual()).isTrue();
        assertThatThrownBy(() -> validator.parse("\"" + "a".repeat(1999) + "\"")).isInstanceOf(ApiException.class);
        assertThat(validator.booleanValue(validator.parse("true"))).isTrue();
        assertThatThrownBy(() -> validator.booleanValue(validator.parse("\"true\""))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.parse("{" )).isInstanceOf(ApiException.class);
    }
    @Test void paginationUsesOnlyApprovedSizes() {
        assertThat(InputValidator.size(null)).isEqualTo(50);
        for (int size : new int[]{20,50,100}) assertThat(InputValidator.size(size)).isEqualTo(size);
        assertThatThrownBy(() -> InputValidator.size(10)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InputValidator.page(0)).isInstanceOf(ApiException.class);
    }
}
