package com.sicost.common.validation;

import java.math.BigDecimal;

public final class DecimalValidator {
    private DecimalValidator() {}
    public static BigDecimal decimal(String input, boolean emptyAsZero, String field) {
        String value = clean(input);
        if (value.isEmpty() && emptyAsZero) return BigDecimal.ZERO;
        if (!value.matches("[0-9]{1,11}(\\.[0-9]{1,4})?")) throw InputValidator.invalid(field);
        return new BigDecimal(value);
    }
    public static BigDecimal allowance(String input) { return decimal(input, true, "codeVal"); }
    public static BigDecimal standard(String input) { return decimal(input, false, "value"); }
    public static String jobAmount(String input) {
        String value = clean(input);
        if (!value.matches("[0-9]{1,15}")) throw InputValidator.invalid("addVal2");
        return new BigDecimal(value).toPlainString();
    }
    private static String clean(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\s\\p{Z},]", "").replaceFirst("[원%]$", "");
    }
}
