package com.sicost.common.persistence;

import com.sicost.common.validation.PeriodValidator;

public final class DatabaseMonthCodec {
    private DatabaseMonthCodec() {}
    public static String toDatabase(String value) {
        PeriodValidator.month(value);
        return value.replace("-", "");
    }
    public static String toApi(String value) {
        if (value == null || !value.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid stored month");
        String result = value.substring(0, 4) + "-" + value.substring(4);
        PeriodValidator.month(result);
        return result;
    }
}
