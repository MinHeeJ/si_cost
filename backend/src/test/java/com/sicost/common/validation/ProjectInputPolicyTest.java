package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ProjectInputPolicyTest {
    final Clock clock = Clock.fixed(Instant.parse("2025-12-31T15:00:00Z"), ZoneOffset.UTC);

    @Test void textTrimsUnicodeSpaceAndCountsCodePointsWithoutTruncation() {
        assertThat(ProjectInputPolicy.text("\u00a0 프로젝트 \u2003",100,"name")).isEqualTo("프로젝트");
        assertThat(ProjectInputPolicy.optionalText("\u00a0\u2003",500,"feature")).isNull();
        assertThat(ProjectInputPolicy.optionalText(null,500,"feature")).isNull();
        assertThat(ProjectInputPolicy.text("😀".repeat(100),100,"name")).isEqualTo("😀".repeat(100));
        invalid(() -> ProjectInputPolicy.text("😀".repeat(101),100,"name"),"name");
        invalid(() -> ProjectInputPolicy.text(" \u00a0 ",100,"name"),"name");
        invalid(() -> ProjectInputPolicy.optionalText("가".repeat(501),500,"feature"),"feature");
    }
    @Test void amountsAcceptBothInclusiveBoundariesAndNeverUseFloatingPoint() {
        assertThat(ProjectInputPolicy.amount("0","contractAmount").toPlainString()).isEqualTo("0");
        assertThat(ProjectInputPolicy.amount("\u00a0999,999,999,999,999 원\u2003","contractAmount").toPlainString()).isEqualTo("999999999999999");
        assertThat(ProjectInputPolicy.expenseAmount("1원")).isEqualByComparingTo("1");
    }
    @ParameterizedTest @ValueSource(strings={"-1","+1","1.0","1e3","1E3","1000000000000000","1%","원1","1원원","NaN",""})
    void invalidAmountsUseProjectErrorWithoutRoundingOrClamping(String input) {
        invalid(() -> ProjectInputPolicy.amount(input,"contractAmount"),"contractAmount");
    }
    @Test void requiredAmountsAndPositiveExpensesRejectMissingAndZero() {
        invalid(() -> ProjectInputPolicy.amount(null,"budgetAmount"),"budgetAmount");
        invalid(() -> ProjectInputPolicy.expenseAmount("0"),"amount");
    }
    @Test void planMmKeepsExactTwoDigitsAndBlankBecomesZero() {
        assertThat(ProjectInputPolicy.planMm("","planMm").toPlainString()).isEqualTo("0.00");
        assertThat(ProjectInputPolicy.planMm("0","planMm").toPlainString()).isEqualTo("0.00");
        assertThat(ProjectInputPolicy.planMm("9999.99","planMm").toPlainString()).isEqualTo("9999.99");
        assertThat(ProjectInputPolicy.planMm("1.2","planMm").toPlainString()).isEqualTo("1.20");
    }
    @ParameterizedTest @ValueSource(strings={"1.001","10000","-1","+1","1e2",".1","1.","1,000","abc"})
    void invalidMmIsNotRounded(String input) { invalid(() -> ProjectInputPolicy.planMm(input,"planMm"),"planMm"); }
    @Test void calendarAndDateRangeAreStrictAndSeoulClockControlsToday() {
        assertThat(ProjectInputPolicy.today(clock).toString()).isEqualTo("2026-01-01");
        var day = ProjectInputPolicy.date("2024-02-29","startDate");
        assertThatCode(() -> ProjectInputPolicy.dateRange(day,day)).doesNotThrowAnyException();
        invalid(() -> ProjectInputPolicy.date("2025-02-29","startDate"),"startDate");
        invalid(() -> ProjectInputPolicy.dateRange(day,day.minusDays(1)),"endDate");
        invalid(() -> ProjectInputPolicy.date("0000-01-01","startDate"),"startDate");
        assertThat(ProjectInputPolicy.month("2026-01","month")).isEqualTo(YearMonth.of(2026,1));
        invalid(() -> ProjectInputPolicy.month("202601","month"),"month");
        invalid(() -> ProjectInputPolicy.month("2026-13","month"),"month");
    }
    @Test void queryYearAllowsPastButWritesRejectPastAndNeverAffectPmWithoutYear() {
        var base = YearMonth.of(2024,6);
        assertThat(ProjectInputPolicy.year(2024,base,clock)).isEqualTo(2024);
        assertThatCode(() -> ProjectInputPolicy.requireWritableYear(2026,base,clock)).doesNotThrowAnyException();
        assertThatCode(() -> ProjectInputPolicy.requireWritableYear(2027,base,clock)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ProjectInputPolicy.requireWritableYear(2025,base,clock)).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("READ_ONLY_YEAR");assertThat(e.field()).isEqualTo("year");
        });
    }
    @Test void yearIsRequiredAndConstrainedByDatabaseBaseAndCurrentSeoulYear() {
        var base = YearMonth.of(2024,6);
        invalid(() -> ProjectInputPolicy.year(null,base,clock),"year");
        invalid(() -> ProjectInputPolicy.year(2023,base,clock),"year");
        invalid(() -> ProjectInputPolicy.year(2028,base,clock),"year");
    }
    @Test void revisionIsRequiredAndLegacyInputErrorsStayUnchanged() {
        invalid(() -> ProjectInputPolicy.expectedRevision(" "),"expectedRevision");
        assertThatThrownBy(() -> InputValidator.text(" ",100,"name")).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("VALIDATION_ERROR"));
    }
    @Test void onlyComputedValuesAreRoundedAndResponseDecimalsAreStrings() {
        assertThat(ProjectInputPolicy.calculatedAmount(new BigDecimal("1.99")).toPlainString()).isEqualTo("1");
        assertThat(ProjectInputPolicy.calculatedAmount(new BigDecimal("-1.99")).toPlainString()).isEqualTo("-1");
        assertThat(ProjectInputPolicy.calculatedMm(new BigDecimal("1.235")).toPlainString()).isEqualTo("1.24");
        assertThat(ProjectInputPolicy.amountString(new BigDecimal("999999999999999"))).isEqualTo("999999999999999");
        assertThat(ProjectInputPolicy.mmString(new BigDecimal("0"))).isEqualTo("0.00");
        assertThat(ProjectInputPolicy.amountString(null)).isNull();
        assertThat(ProjectInputPolicy.mmString(null)).isNull();
    }
    @Test void serialUsesLargestPersistedOrDeletedAuditNumberAndStopsAtCapacity() {
        assertThat(ProjectInputPolicy.nextSerial(0)).isEqualTo(1);
        assertThat(ProjectInputPolicy.nextSerial(99998)).isEqualTo(99999);
        assertThatThrownBy(() -> ProjectInputPolicy.nextSerial(99999)).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("SERIAL_LIMIT");
        });
    }
    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,String field) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(400);assertThat(e.code()).isEqualTo("INVALID_INPUT");assertThat(e.field()).isEqualTo(field);
        });
    }
}
