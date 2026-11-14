package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

public final class ProjectProgressPolicy {
    private ProjectProgressPolicy() {}

    public static void requireEditable(Integer year, String storedStatus, LocalDate endDate, Clock clock) {
        if (year == null || year < 1 || year > 9999) throw ProjectInputPolicy.invalid("year");
        LocalDate today = ProjectInputPolicy.today(clock);
        if (year != today.getYear()) {
            throw new ApiException(409, "READ_ONLY_YEAR", "진행 자료는 현재연도에서만 편집할 수 있습니다.", "year", null);
        }
        if (endDate == null || endDate.isBefore(today) || !Set.of("ING", "PRE").contains(storedStatus)) {
            throw new ApiException(409, "INVALID_PROJECT_STATUS", "종료·실주 프로젝트의 진행 자료는 조회만 할 수 있습니다.");
        }
    }
}
