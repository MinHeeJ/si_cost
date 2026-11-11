package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;

public final class MasterDataExportInput {
    private MasterDataExportInput() {}
    public static final List<String> CLIENT_COLUMNS = List.of("type", "name", "businessNo", "ceo", "industry", "contactName", "phone", "taxEmail", "account", "projectCount", "personCount", "useYn");
    public static final List<String> PART_COLUMNS = List.of("name", "leaderName", "profitRate", "salesExpenseAmount", "effectiveMonth", "scheduledChange", "personCount", "projectCount", "useYn");
    public record Filters(String type, String useYn, String q) {}
    public record Export(String view, Filters filters, String sort, String direction, List<String> columns, String amountUnit) {}

    public static Export parse(JsonNode body) {
        allow(body, Set.of("view", "filters", "sort", "direction", "columns", "amountUnit"), "");
        String view = choice(body, "view", Set.of("CLIENTS", "PARTS"), null, "");
        JsonNode filters = body.get("filters");
        allow(filters, Set.of("type", "useYn", "q"), "filters.");
        String type = filters.has("type") ? choice(filters, "type", Set.of("CUST", "VNDR", "BOTH"), null, "filters.") : null;
        if (view.equals("PARTS") && type != null) throw invalid("filters.type", "영업 파트에는 거래처 구분 조건을 사용할 수 없습니다.");
        String useYn = filters.has("useYn") ? choice(filters, "useYn", Set.of("Y", "N"), null, "filters.") : null;
        String q = null;
        if (filters.has("q")) {
            JsonNode value = filters.get("q");
            if (!value.isTextual()) throw InputValidator.invalid("filters.q");
            String text = value.textValue().strip();
            if (text.codePointCount(0, text.length()) > 100) throw InputValidator.invalid("filters.q");
            if (!text.isEmpty()) q = text;
        }
        List<String> allowed = view.equals("CLIENTS") ? CLIENT_COLUMNS : PART_COLUMNS;
        JsonNode values = body.get("columns");
        if (values == null || !values.isArray() || values.isEmpty()) throw invalid("columns", "다운로드할 열을 선택해 주세요.");
        List<String> columns = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !allowed.contains(value.textValue()) || columns.contains(value.textValue()))
                throw invalid("columns", "허용된 열만 중복 없이 선택할 수 있습니다. 전체 계좌번호는 내려받을 수 없습니다.");
            columns.add(value.textValue());
        }
        if (!columns.contains("name")) throw invalid("columns", "상호 또는 파트명은 필수 열입니다.");
        if (view.equals("PARTS") && !columns.equals(PART_COLUMNS)) throw invalid("columns", "영업 파트는 고정된 9개 열 순서로 내려받습니다.");
        Set<String> sorts = new HashSet<>(allowed); sorts.remove("account"); sorts.remove("scheduledChange");
        return new Export(view, new Filters(type, useYn, q), choice(body, "sort", sorts, "name", ""),
                choice(body, "direction", Set.of("ASC", "DESC"), "ASC", ""), List.copyOf(columns),
                choice(body, "amountUnit", Set.of("원", "천원", "백만원"), "천원", ""));
    }
    static void allow(JsonNode node, Set<String> fields, String prefix) {
        if (node == null || !node.isObject()) throw InputValidator.invalid(prefix.isEmpty() ? "body" : "filters");
        node.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) throw unknown(prefix + field); });
    }
    static ApiException unknown(String field) { return new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", field, null); }
    private static String choice(JsonNode node, String field, Set<String> values, String fallback, String prefix) {
        if (!node.has(field) && fallback != null) return fallback;
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !values.contains(value.textValue())) throw InputValidator.invalid(prefix + field);
        return value.textValue();
    }
    private static ApiException invalid(String field, String message) { return new ApiException(400, "VALIDATION_ERROR", message, field, null); }
}
