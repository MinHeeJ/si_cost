package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Supplier;

public final class ProjectInputPolicy {
    private ProjectInputPolicy() {}

    public static String text(String input, int max, String field) {
        return legacy(() -> InputValidator.text(strip(input), max, field), field);
    }
    public static String optionalText(String input, int max, String field) {
        String value = strip(input);
        return value == null || value.isEmpty() ? null : text(value, max, field);
    }
    public static BigDecimal amount(String input, String field) {
        return legacy(() -> MasterDataValidator.salesExpenseAmount(input), field);
    }
    public static BigDecimal expenseAmount(String input) {
        BigDecimal value = amount(input, "amount");
        if (value.signum() == 0) throw invalid("amount");
        return value;
    }
    public static BigDecimal planMm(String input, String field) {
        String value = strip(input);
        if (value == null || value.isEmpty()) return new BigDecimal("0.00");
        if (!value.matches("[0-9]{1,4}(?:\\.[0-9]{1,2})?")) throw invalid(field);
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
    public static LocalDate date(String input, String field) {
        return legacy(() -> PeopleInputPolicy.date(strip(input), field), field);
    }
    public static void dateRange(LocalDate start, LocalDate end) {
        if (start == null) throw invalid("startDate");
        if (end == null || start.isAfter(end)) throw invalid("endDate");
    }
    public static YearMonth month(String input, String field) {
        return legacy(() -> MasterDataValidator.month(strip(input), field), field);
    }
    public static LocalDate today(Clock clock) { return PeopleInputPolicy.today(clock); }
    public static int year(Integer input, YearMonth cashBaseMonth, Clock clock) {
        if (input == null || cashBaseMonth == null || input < cashBaseMonth.getYear()
                || input > today(clock).getYear() + 1) throw invalid("year");
        return input;
    }
    public static void requireWritableYear(Integer input, YearMonth cashBaseMonth, Clock clock) {
        int year = year(input, cashBaseMonth, clock);
        if (year < today(clock).getYear()) {
            throw new ApiException(409, "READ_ONLY_YEAR", "지난 연도는 조회만 할 수 있습니다.", "year", null);
        }
    }
    public static String expectedRevision(String input) { return text(input, 200, "expectedRevision"); }
    public static BigDecimal calculatedAmount(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
    public static BigDecimal calculatedMm(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    public static String amountString(BigDecimal value) { return MasterDataValidator.decimalString(value); }
    public static String mmString(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
    public static int nextSerial(int greatestPersistedOrAudited) {
        if (greatestPersistedOrAudited < 0) throw invalid("sn");
        return PeopleInputPolicy.nextSerial(greatestPersistedOrAudited);
    }
    public static ApiException invalid(String field) {
        return new ApiException(400, "INVALID_INPUT", "입력 항목의 형식과 길이를 확인해 주세요.", field, null);
    }
    private static <T> T legacy(Supplier<T> value, String field) {
        try { return value.get(); }
        catch (ApiException e) { throw invalid(field); }
    }
    private static String strip(String input) {
        return input == null ? null : input.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }
}
