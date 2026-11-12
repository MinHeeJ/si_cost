package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AssignmentInputPolicy {
    private AssignmentInputPolicy() {}

    public static BigDecimal paidUnitPrice(String input) {
        BigDecimal value = ProjectInputPolicy.amount(input, "billingUnitPrice");
        if (value.signum() == 0) throw ProjectInputPolicy.invalid("billingUnitPrice");
        return value;
    }
    public static BigDecimal contractMm(String input, boolean allowCreateDefault) {
        String value = ProjectInputPolicy.optionalText(input, 4, "contractMm");
        if (value == null) {
            if (allowCreateDefault) return new BigDecimal("1.00");
            throw ProjectInputPolicy.invalid("contractMm");
        }
        if (!value.matches("(?:0\\.(?:0[1-9]|[1-9][0-9]?)|1(?:\\.0{1,2})?)")) {
            throw ProjectInputPolicy.invalid("contractMm");
        }
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
    public static String activeCode(String input, PeopleInputPolicy.CodeSelection stored, String field) {
        String value = ProjectInputPolicy.text(input, 10, field);
        if (stored == null || !value.equals(stored.code()) || !"Y".equals(stored.useYn()) || !"N".equals(stored.delYn())) {
            throw ProjectInputPolicy.invalid(field);
        }
        return value;
    }
    public static String memo(String input) { return ProjectInputPolicy.optionalText(input, 200, "memo"); }
    public static String correctionContent(String input) { return ProjectInputPolicy.text(input, 200, "correctContent"); }
    public static int page(int input) {
        try { return InputValidator.page(input); }
        catch (ApiException e) { throw ProjectInputPolicy.invalid("page"); }
    }
    public static int size(Integer input) {
        try { return InputValidator.size(input); }
        catch (ApiException e) { throw ProjectInputPolicy.invalid("size"); }
    }
}
