package com.sicost.common.validation;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class PeopleInputPolicy {
    private PeopleInputPolicy() {}

    public static LocalDate date(String input, String field) {
        String value = InputValidator.text(input, 10, field);
        if (!value.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw InputValidator.invalid(field);
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException e) { throw InputValidator.invalid(field); }
    }
    public static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)); }
    public static BigDecimal amount(String input, String field) {
        if (input == null || input.isBlank()) return null;
        try { return MasterDataValidator.salesExpenseAmount(input); }
        catch (ApiException e) { throw InputValidator.invalid(field); }
    }
    public static BigDecimal desiredRate(String input) {
        BigDecimal value = amount(input, "desiredRate");
        if (value == null || value.signum() <= 0) throw InputValidator.invalid("desiredRate");
        return value;
    }
    public static List<String> skills(List<String> input, boolean required) {
        if (input == null) {
            if (required) throw InputValidator.invalid("skills");
            return null;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : input) {
            if (item == null) throw InputValidator.invalid("skills");
            for (String part : item.split(",", -1)) {
                String skill = InputValidator.optionalText(part, 50, "skills");
                if (skill != null) values.add(skill);
            }
        }
        if (values.size() > 999 || (required && values.isEmpty())) throw InputValidator.invalid("skills");
        return List.copyOf(values);
    }
    public record CodeSelection(String code, String useYn, String delYn) {}
    public static String selectableCode(String input, String original, CodeSelection stored, String field) {
        String value = InputValidator.optionalText(input, 10, field);
        if (value == null) return null;
        if (stored == null || !Objects.equals(value, stored.code())
                || ((!"Y".equals(stored.useYn()) || !"N".equals(stored.delYn())) && !Objects.equals(value, original))) {
            throw InputValidator.invalid(field);
        }
        return value;
    }
    public static int nextSerial(int greatestUsed) {
        if (greatestUsed < 0) throw InputValidator.invalid("sn");
        if (greatestUsed >= 99999) throw new ApiException(409, "SERIAL_LIMIT", "사용 가능한 이력 번호를 모두 사용했습니다.");
        return greatestUsed + 1;
    }
}
