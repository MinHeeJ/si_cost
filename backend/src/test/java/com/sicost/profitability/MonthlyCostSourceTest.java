package com.sicost.profitability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;

class MonthlyCostSourceTest {
    @Test void defaultsToSeoulYearAtUtcYearBoundaryAndStopsAtCurrentMonth() {
        var period = MonthlyCostRepository.period(null, "202507", Instant.parse("2025-12-31T15:00:00Z"));
        assertThat(period.year()).isEqualTo(2026);
        assertThat(period.fromMonth()).isEqualTo("202601");
        assertThat(period.toMonth()).isEqualTo("202601");
        assertThat(period.calculatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(period.noData()).isFalse();
    }
    @Test void pastYearEndsInDecemberAndIntersectsCashBase() {
        var period = MonthlyCostRepository.period(2025, "202507", Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(period.fromMonth()).isEqualTo("202507");
        assertThat(period.toMonth()).isEqualTo("202512");
    }
    @Test void noIntersectingMonthsAreExplicitlyAbsentRatherThanZeroSnapshots() {
        var period = MonthlyCostRepository.period(null, "202612", Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(period.noData()).isTrue();
        assertThat(period.fromMonth()).isNull();
        assertThat(period.toMonth()).isNull();
        var json = JsonMapper.builder().findAndAddModules().build()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL).valueToTree(period);
        assertThat(json.get("fromMonth").isNull()).isTrue();
        assertThat(json.get("toMonth").isNull()).isTrue();
        assertThat(json.get("year").isIntegralNumber()).isTrue();
    }
    @Test void futureAndPreOperatingYearsFailWithTheContractErrorWithoutSensitiveDetails() {
        for (int year : new int[]{2024, 2027}) {
            assertThatThrownBy(() -> MonthlyCostRepository.period(year, "202507", Instant.parse("2026-07-01T00:00:00Z")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.code()).isEqualTo("INVALID_PERIOD");
                    assertThat(e.field()).isEqualTo("year");
                    assertThat(e.details()).isNull();
                });
        }
    }
    @Test void employmentIsMonthInclusiveWithoutDailyProrationAndMissingHireStartsInSelectedYear() {
        LocalDate hire = LocalDate.of(2025, 2, 28), retire = LocalDate.of(2025, 7, 1);
        assertThat(employmentIncludes(hire, retire, "202501")).isFalse();
        assertThat(employmentIncludes(hire, retire, "202502")).isTrue();
        assertThat(employmentIncludes(hire, retire, "202507")).isTrue();
        assertThat(employmentIncludes(hire, retire, "202508")).isFalse();
        assertThat(employmentIncludes(null, retire, "202501")).isTrue();
        assertThat(employmentIncludes(null, null, "202501")).isTrue();
    }
    @Test void decimalPresentationIsExactAndNeverSilentlyRoundsStoredValues() {
        assertThat(money(new BigDecimal("4970000"))).isEqualTo("4970000");
        assertThat(mm(new BigDecimal("0.60"))).isEqualTo("0.60");
        assertThat(money(null)).isNull();
        assertThatThrownBy(() -> money(new BigDecimal("0.5"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> mm(new BigDecimal("0.601"))).isInstanceOf(ArithmeticException.class);
    }
    @Test void rangesRejectMalformedOrReversedMonthsAndUseLeapMonthEnd() {
        assertThat(new Range("202402", "202402").toDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThatThrownBy(() -> new Range("202403", "202402")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parseMonth("000001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parseMonth("202413")).isInstanceOf(DateTimeException.class);
        assertThatThrownBy(() -> parseMonth("2024-01")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void leaveUsesTheHistoricalIntervalIncludingOpenEndedAndPartialMonths() {
        var full = new WorkState(null, null, "LEAVE", LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
        var partial = new WorkState(null, null, "LEAVE", LocalDate.of(2024, 2, 2), LocalDate.of(2024, 2, 29));
        var open = new WorkState(null, null, "LEAVE", LocalDate.of(2024, 2, 1), null);
        assertThat(full.fullMonthLeave("202402")).isTrue();
        assertThat(full.fullMonthLeave("202403")).isFalse();
        assertThat(partial.fullMonthLeave("202402")).isFalse();
        assertThat(open.fullMonthLeave("202403")).isTrue();
        assertThat(partial.includes(LocalDate.of(2024, 2, 2))).isTrue();
    }
    @Test void publicRowAmountsAndPageCountersUseDifferentContractTypes() {
        Map<String, Class<?>> rowTypes = Arrays.stream(MonthlyCostRow.class.getRecordComponents())
            .collect(Collectors.toMap(java.lang.reflect.RecordComponent::getName, java.lang.reflect.RecordComponent::getType));
        for (String name : new String[]{"assignmentMm", "freeMm", "personalRevenue", "laborAllocated",
            "teamExpense", "projectExpense", "directExpense", "totalCost", "profit", "profitRate"})
            assertThat(rowTypes.get(name)).isEqualTo(String.class);
        Map<String, Class<?>> pageTypes = Arrays.stream(IndividualProfitabilityData.class.getRecordComponents())
            .collect(Collectors.toMap(java.lang.reflect.RecordComponent::getName, java.lang.reflect.RecordComponent::getType));
        assertThat(pageTypes.get("total")).isEqualTo(long.class);
        assertThat(pageTypes.get("page")).isEqualTo(int.class);
        assertThat(pageTypes.get("size")).isEqualTo(int.class);
        assertThat(pageTypes.get("unknownCount")).isEqualTo(int.class);
    }
}
