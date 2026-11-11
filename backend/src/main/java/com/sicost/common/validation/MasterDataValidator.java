package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;

public final class MasterDataValidator {
    private MasterDataValidator() {}

    public static String email(String input, String field) {
        String value = InputValidator.text(input, 100, field);
        if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw InputValidator.invalid(field);
        return value;
    }
    public static BigDecimal profitRate(String input) {
        String value = InputValidator.text(input, 100, "profitRate");
        if (!value.matches("[0-9]+(\\.[0-9]{1,2})?")) throw InputValidator.invalid("profitRate");
        BigDecimal result = new BigDecimal(value);
        if (result.compareTo(new BigDecimal("100")) > 0) throw InputValidator.invalid("profitRate");
        return result;
    }
    public static BigDecimal salesExpenseAmount(String input) {
        if (input == null) throw InputValidator.invalid("salesExpenseAmount");
        String value = input.replaceAll("[\\s\\p{Z},]", "").replaceFirst("원$", "");
        if (!value.matches("[0-9]+")) throw InputValidator.invalid("salesExpenseAmount");
        BigDecimal result = new BigDecimal(value);
        if (result.compareTo(new BigDecimal("999999999999999")) > 0) throw InputValidator.invalid("salesExpenseAmount");
        return result;
    }
    public static YearMonth month(String input, String field) {
        try { return PeriodValidator.month(input); }
        catch (ApiException e) { throw InputValidator.invalid(field); }
    }
    public static String enumValue(String input, Set<String> allowed, String field) {
        String value = InputValidator.text(input, 30, field);
        if (!allowed.contains(value)) throw InputValidator.invalid(field);
        return value;
    }
    public static String search(String input) { return InputValidator.optionalText(input, 100, "q"); }
    public static String decimalString(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
    public static BigDecimal amountInUnit(BigDecimal won, String unit) {
        int scale = switch (unit) {
            case "원" -> 0;
            case "천원" -> 3;
            case "백만원" -> 6;
            default -> throw InputValidator.invalid("amountUnit");
        };
        return won.movePointLeft(scale);
    }
}
