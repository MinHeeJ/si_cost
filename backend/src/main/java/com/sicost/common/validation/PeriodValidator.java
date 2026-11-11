package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.time.YearMonth;

public final class PeriodValidator {
    private PeriodValidator() {}
    public static YearMonth month(String value) {
        if (value == null || !value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new ApiException(400, "INVALID_PERIOD", "실제 연월을 YYYY-MM 형식으로 입력해 주세요.", "month", null);
        }
        return YearMonth.parse(value);
    }
}
