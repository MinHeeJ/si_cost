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
    /** 빈 칸 문구: 받침에 맞는 조사, 칸 이름을 모르면 main 공통 문구(2026-11-19 205 완료 점검 ①). */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter='|', value={
        "projectId|프로젝트를 입력해 주세요.",
        "customerId|고객사를 입력해 주세요.",
        "partId|영업 파트를 입력해 주세요.",
        "name|이름을 입력해 주세요.",
        "jobCode|직무를 입력해 주세요.",
        "reason|사유를 입력해 주세요.",
        "memo|메모를 입력해 주세요.",
        "startMonth|시작월을 입력해 주세요.",
        "monthlyMm|월 MM을 입력해 주세요.",
        "needs[0].count|인원을 입력해 주세요.",
        "developerId|개발자를 골라 주세요",
        "거래처명|거래처명을 입력해 주세요.",
        "operationId|입력 항목의 형식과 길이를 확인해 주세요.",
        "key|입력 항목의 형식과 길이를 확인해 주세요.",
        "value|입력 항목의 형식과 길이를 확인해 주세요."})
    void blankTextMessageUsesParticleOrMainFallback(String field, String expected) {
        assertThatThrownBy(() -> InputValidator.text("  ", 50, field))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getMessage()).isEqualTo(expected).doesNotContain("을(를)");
                assertThat(e.field()).isEqualTo(field);
            });
    }
    @Test void particleFollowsFinalSoundIncludingLatinAndDigits() {
        assertThat(InputValidator.objectParticle("프로젝트")).isEqualTo("를");
        assertThat(InputValidator.objectParticle("시작월")).isEqualTo("을");
        assertThat(InputValidator.objectParticle("월 MM")).isEqualTo("을");
        assertThat(InputValidator.objectParticle("ID")).isEqualTo("를");
        assertThat(InputValidator.objectParticle("항목1")).isEqualTo("을");
        assertThat(InputValidator.objectParticle("항목2")).isEqualTo("를");
    }
    @Test void invalidMessageKeepsMainFallbackForUnknownFields() {
        assertThat(InputValidator.invalid("operationId").getMessage()).isEqualTo("입력 항목의 형식과 길이를 확인해 주세요.");
        assertThat(InputValidator.invalid(null).getMessage()).isEqualTo("입력 항목의 형식과 길이를 확인해 주세요.");
        assertThat(InputValidator.invalid("memo").getMessage()).isEqualTo("메모 칸의 형식과 길이를 확인해 주세요.");
        assertThatThrownBy(() -> InputValidator.text("가".repeat(51), 50, "projectId"))
            .hasMessage("프로젝트 칸의 형식과 길이를 확인해 주세요.");
    }
    @Test void labelStillNamesUnknownFieldsForRowMessages() {
        assertThat(InputValidator.label("needs[0].count")).isEqualTo("인원");
        assertThat(InputValidator.label("operationId")).isEqualTo("입력 항목");
        assertThat(InputValidator.label(null)).isEqualTo("입력");
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
