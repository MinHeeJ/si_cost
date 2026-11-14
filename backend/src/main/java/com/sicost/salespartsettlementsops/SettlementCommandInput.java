package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.util.Set;

public final class SettlementCommandInput {
    private SettlementCommandInput() {}
    public record Command(String month, BigDecimal cardAmount, String reason, String expectedRevision) {}
    public static Command parse(JsonNode body, boolean cancellation) {
        if (body == null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        Set<String> fields = cancellation ? Set.of("month", "reason", "expectedRevision") : Set.of("month", "cardAmount", "expectedRevision");
        body.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) throw ProjectInputPolicy.invalid(field); });
        String month = text(body, "month");
        if (!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")) throw ProjectInputPolicy.invalid("month");
        String revision = text(body, "expectedRevision");
        if (revision.isBlank()) throw ProjectInputPolicy.invalid("expectedRevision");
        String reason = cancellation ? text(body, "reason").strip() : null;
        if (cancellation && (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 500)) throw ProjectInputPolicy.invalid("reason");
        return new Command(month, cancellation ? null : card(text(body, "cardAmount")), reason, revision);
    }
    public static BigDecimal card(String input) {
        String value = input.strip();
        if (!value.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)?")) throw ProjectInputPolicy.invalid("cardAmount");
        value = value.replace(",", "");
        if (value.isEmpty()) return BigDecimal.ZERO;
        BigDecimal amount = new BigDecimal(value);
        if (amount.compareTo(new BigDecimal("999999999999999")) > 0) throw ProjectInputPolicy.invalid("cardAmount");
        return amount;
    }
    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
}
