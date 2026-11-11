package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;

/** UI-02 개발자 목록·UI-33 외부 인력 후보 목록 다운로드 요청의 입력 검증. 조회 조건은 각 목록 API와 같은 규칙을 쓴다. */
public final class PeopleExportInput {
    private PeopleExportInput() {}
    public static final Set<String> VIEWS = Set.of("DEVELOPERS", "CANDIDATES");
    public static final List<String> DEVELOPER_COLUMNS = List.of("name", "companyName", "employeeType", "grade", "job", "status", "currentProjects", "withdrawalDate", "monthlyRate");
    public static final List<String> CANDIDATE_COLUMNS = List.of("name", "employeeType", "job", "grade", "skills", "homeAddress", "desiredRate", "availableDate", "contactStatus", "lastContactDate", "source");
    private static final Set<String> DEVELOPER_FILTERS = Set.of("employeeType", "jobCode", "directClass", "workStatus", "q", "includeStopped");
    private static final Set<String> CANDIDATE_FILTERS = Set.of("jobCode", "gradeCode", "employeeType", "contactStatus", "availability");

    public record Export(String view, Map<String, String> filters, String sort, String direction, List<String> columns, String amountUnit) {}

    public static boolean handles(JsonNode body) {
        JsonNode view = body == null ? null : body.get("view");
        return view != null && view.isTextual() && VIEWS.contains(view.textValue());
    }

    /** 목록 API가 받는 쿼리 문자열과 같은 모양의 값으로 바꾼다. 빈 문자열과 null은 "조건 없음"이다. */
    public static Export parse(JsonNode body) {
        if (body == null || !body.isObject()) throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("view", "filters", "sort", "direction", "columns", "amountUnit").contains(field)) throw MasterDataExportInput.unknown(field);
        });
        String view = body.get("view").textValue();
        boolean developers = view.equals("DEVELOPERS");
        JsonNode filterNode = body.get("filters");
        if (filterNode == null || !filterNode.isObject()) throw InputValidator.invalid("filters");
        Set<String> allowedFilters = developers ? DEVELOPER_FILTERS : CANDIDATE_FILTERS;
        Map<String, String> filters = new LinkedHashMap<>();
        filterNode.fieldNames().forEachRemaining(field -> {
            if (!allowedFilters.contains(field)) throw MasterDataExportInput.unknown("filters." + field);
            JsonNode value = filterNode.get(field);
            if (value.isNull()) return;
            if (!value.isTextual() && !value.isBoolean()) throw InputValidator.invalid("filters." + field);
            String text = value.asText();
            if (!text.isBlank()) filters.put(field, text);
        });
        String sort = optionalText(body, "sort");
        String direction = optionalText(body, "direction");
        String unit = optionalText(body, "amountUnit");
        if (unit == null) unit = "천원";
        if (!Set.of("원", "천원", "백만원").contains(unit)) throw InputValidator.invalid("amountUnit");
        List<String> allowed = developers ? DEVELOPER_COLUMNS : CANDIDATE_COLUMNS;
        JsonNode values = body.get("columns");
        if (values == null || !values.isArray() || values.isEmpty()) throw new ApiException(400, "VALIDATION_ERROR", "다운로드할 열을 선택해 주세요.", "columns", null);
        List<String> columns = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || !allowed.contains(value.textValue()) || columns.contains(value.textValue()))
                throw new ApiException(400, "VALIDATION_ERROR", "허용된 열만 중복 없이 선택할 수 있습니다.", "columns", null);
            columns.add(value.textValue());
        }
        if (!columns.contains("name")) throw new ApiException(400, "VALIDATION_ERROR", "이름은 필수 열입니다.", "columns", null);
        return new Export(view, Map.copyOf(filters), sort, direction == null ? "ASC" : direction, List.copyOf(columns), unit);
    }
    private static String optionalText(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw InputValidator.invalid(field);
        return value.textValue();
    }
}
