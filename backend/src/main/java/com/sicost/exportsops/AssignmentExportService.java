package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignments.AssignmentInput;
import com.sicost.assignments.AssignmentRepository;
import com.sicost.assignments.AssignmentService;
import com.sicost.assignmentsops.AssignmentActionsDtos.Assignment;
import com.sicost.assignmentsops.AssignmentActionsDtos.Warning;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.excel.ExcelExportService.CellType;
import com.sicost.common.excel.ExcelExportService.Column;
import com.sicost.common.excel.ExcelExportService.Download;
import com.sicost.common.excel.ExcelExportService.Specification;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 투입 배정(UI-12) 목록 엑셀 다운로드.
 * 조회는 목록 API와 같은 입력 검증·같은 서비스(필터·경고 조건·정렬)를 쓰고, 페이지 제한 없이 조건에 맞는 전체를 파일에 쓴다.
 */
@Service
public class AssignmentExportService {
    public static final List<String> COLUMNS = List.of("warnings", "projectName", "developerName", "jobName", "startDate", "endDate",
        "billingUnitPrice", "contractMm", "yearMm", "extensionPossibility", "inputType");
    private static final Set<String> FIELDS = Set.of("view", "filters", "sort", "direction", "columns", "amountUnit");
    private static final Set<String> FILTERS = Set.of("year", "projectId", "jobCode", "warning", "q", "includeCancelled");
    private final AssignmentService assignments;
    private final AssignmentRepository repository;
    private final ExcelExportService excel;

    public AssignmentExportService(AssignmentService assignments, AssignmentRepository repository, ExcelExportService excel) {
        this.assignments = assignments; this.repository = repository; this.excel = excel;
    }

    public static boolean handles(JsonNode body) {
        JsonNode view = body == null ? null : body.get("view");
        return view != null && view.isTextual() && view.textValue().equals("ASSIGNMENTS");
    }

    public Download create(JsonNode body, HttpServletRequest request) {
        body.fieldNames().forEachRemaining(field -> { if (!FIELDS.contains(field)) throw MasterDataExportInput.unknown(field); });
        JsonNode filterNode = body.get("filters");
        if (filterNode == null || !filterNode.isObject()) throw InputValidator.invalid("filters");
        Map<String, String> query = new LinkedHashMap<>();
        filterNode.fieldNames().forEachRemaining(field -> {
            if (!FILTERS.contains(field)) throw MasterDataExportInput.unknown("filters." + field);
            JsonNode value = filterNode.get(field);
            if (value.isNull()) return;
            if (!value.isTextual() && !value.isBoolean() && !value.isInt()) throw InputValidator.invalid("filters." + field);
            String text = value.asText();
            if (!text.isBlank()) query.put(field, text);
        });
        String sort = text(body, "sort"), direction = text(body, "direction"), unit = text(body, "amountUnit");
        if (sort != null) query.put("sort", sort);
        query.put("direction", direction == null ? "ASC" : direction);
        if (unit == null) unit = "천원";
        if (!Set.of("원", "천원", "백만원").contains(unit)) throw InputValidator.invalid("amountUnit");
        JsonNode values = body.get("columns");
        List<String> columns = new ArrayList<>();
        if (values == null || !values.isArray() || values.isEmpty()) throw InputValidator.invalid("columns");
        for (JsonNode value : values) {
            if (!value.isTextual() || !COLUMNS.contains(value.textValue()) || columns.contains(value.textValue())) throw InputValidator.invalid("columns");
            columns.add(value.textValue());
        }
        // Same validation and rules as GET /api/assignments, with the page size irrelevant (all rows are read).
        AssignmentInput.Query list = AssignmentInput.list(query);
        List<Assignment> rows = assignments.listAll(list);
        Map<String, String> inputTypes = labels("INPUT_TYPE"), possibilities = labels("EXT_PSBL");
        String year = String.valueOf(list.filters().year()), amountUnit = unit;
        List<Column> specColumns = columns.stream().map(id -> new Column(id, title(id, amountUnit), numeric(id) ? CellType.NUMBER : CellType.TEXT)).toList();
        String conditions = "year=" + year + query.entrySet().stream().filter(e -> !e.getKey().equals("year")).sorted(Map.Entry.comparingByKey())
            .map(e -> "; " + e.getKey() + "=" + e.getValue()).collect(Collectors.joining());
        Specification specification = new Specification("투입배정", null, specColumns, conditions, unit, "UI-12");
        return excel.generate(specification, sink -> {
            for (Assignment row : rows) {
                List<Object> cells = new ArrayList<>();
                for (String id : columns) cells.add(cell(id, row, amountUnit, inputTypes, possibilities));
                sink.accept(cells);
            }
        }, request);
    }

    private Map<String, String> labels(String group) {
        Map<String, String> result = new HashMap<>();
        repository.labels().stream().filter(code -> code.group().equals(group)).forEach(code -> result.put(code.code(), code.name()));
        return result;
    }
    private static boolean numeric(String id) { return id.equals("billingUnitPrice") || id.equals("contractMm") || id.equals("yearMm"); }
    private static String title(String id, String unit) {
        return switch (id) {
            case "warnings" -> "경고";
            case "projectName" -> "프로젝트";
            case "developerName" -> "개발자";
            case "jobName" -> "직무";
            case "startDate" -> "시작일";
            case "endDate" -> "종료일";
            case "billingUnitPrice" -> "청구 단가(" + unit + ")";
            case "contractMm" -> "계약 MM";
            case "yearMm" -> "연 MM";
            case "extensionPossibility" -> "연장 가능성";
            case "inputType" -> "투입 구분";
            default -> id;
        };
    }
    private static Object cell(String id, Assignment row, String unit, Map<String, String> inputTypes, Map<String, String> possibilities) {
        return switch (id) {
            case "warnings" -> row.warnings().stream().map(Warning::message).collect(Collectors.joining(" / "));
            case "projectName" -> row.projectName();
            case "developerName" -> row.developerName();
            case "jobName" -> row.jobName();
            case "startDate" -> row.startDate() == null ? null : row.startDate().toString();
            case "endDate" -> row.endDate() == null ? null : row.endDate().toString();
            case "billingUnitPrice" -> row.billingUnitPrice() == null ? null : MasterDataValidator.amountInUnit(new BigDecimal(row.billingUnitPrice()), unit).stripTrailingZeros();
            case "contractMm" -> row.contractMm() == null ? null : new BigDecimal(row.contractMm());
            case "yearMm" -> row.yearMm() == null ? null : new BigDecimal(row.yearMm());
            case "extensionPossibility" -> row.extensionPossibility() == null ? null : possibilities.getOrDefault(row.extensionPossibility(), row.extensionPossibility());
            case "inputType" -> inputTypes.getOrDefault(row.inputType(), row.inputType());
            default -> null;
        };
    }
    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw InputValidator.invalid(field);
        return value.textValue();
    }
}
