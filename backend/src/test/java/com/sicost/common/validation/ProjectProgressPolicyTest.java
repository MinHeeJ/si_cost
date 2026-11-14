package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProgressPolicyTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), ZoneOffset.UTC);
    final LocalDate today = LocalDate.of(2027, 1, 1);
    @Test void currentYearUsesSeoulAndFutureContractsAreNotDenied() {
        for (String status : new String[]{"ING", "PRE"}) {
            assertThatCode(() -> ProjectProgressPolicy.requireEditable(2027, status, today.plusYears(1), clock)).doesNotThrowAnyException();
            assertThatCode(() -> ProjectProgressPolicy.requireEditable(2027, status, today, clock)).doesNotThrowAnyException();
        }
    }
    @Test void otherYearIsConflictIncludingNextYear() {
        for (int year : new int[]{2026, 2028}) {
            assertThatThrownBy(() -> ProjectProgressPolicy.requireEditable(year, "ING", today, clock))
                .isInstanceOfSatisfying(ApiException.class, e -> { assertThat(e.status()).isEqualTo(409); assertThat(e.code()).isEqualTo("READ_ONLY_YEAR"); });
        }
    }
    @Test void invalidYearIsBadRequest() {
        for (Integer year : new Integer[]{null, 0, -1, 10000}) {
            assertThatThrownBy(() -> ProjectProgressPolicy.requireEditable(year, "ING", today, clock))
                .isInstanceOfSatisfying(ApiException.class, e -> { assertThat(e.status()).isEqualTo(400); assertThat(e.field()).isEqualTo("year"); });
        }
    }
    @Test void lostOrEndedProjectsStayReadOnly() {
        for (String status : new String[]{"LOSE", "END", "unknown"}) {
            assertThatThrownBy(() -> ProjectProgressPolicy.requireEditable(2027, status, today, clock))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PROJECT_STATUS"));
        }
    }
    @Test void expiredStoredIngAndPreAreEffectivelyEnded() {
        for (String status : new String[]{"ING", "PRE"}) {
            assertThatThrownBy(() -> ProjectProgressPolicy.requireEditable(2027, status, today.minusDays(1), clock))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PROJECT_STATUS"));
        }
    }
}
