package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.InputValidator;
import com.sicost.projects.ProjectDtos.*;
import com.sicost.projects.ProjectInput;
import com.sicost.projects.ProjectQueryService;
import com.sicost.shared.SharedQueriesMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

/** UI-01 목록 API의 조건·정렬·배분표 표시를 재사용하는 전체 결과 다운로드. */
@Service
public class ProjectExportService {
    public static final List<String> COLUMNS = List.of("id", "name", "customerName", "contractType",
        "contractPeriod", "contractAmount", "personCount", "partName", "status", "distribution");
    private final ProjectQueryService projects;
    private final SharedQueriesMapper codes;
    private final ExcelExportService excel;

    public ProjectExportService(ProjectQueryService projects, SharedQueriesMapper codes, ExcelExportService excel) {
        this.projects = projects;
        this.codes = codes;
        this.excel = excel;
    }

    public static boolean handles(JsonNode body) {
        return body != null && "PROJECTS".equals(body.path("view").asText());
    }

    public Download create(JsonNode body, HttpServletRequest request) {
        body.fieldNames().forEachRemaining(key -> {
            if (!Set.of("view", "filters", "sort", "direction", "columns", "amountUnit").contains(key))
                throw MasterDataExportInput.unknown(key);
        });
        JsonNode filters = body.get("filters");
        if (filters == null || !filters.isObject()) throw InputValidator.invalid("filters");
        Map<String, String> query = new LinkedHashMap<>();
        filters.fieldNames().forEachRemaining(key -> {
            if (!Set.of("year", "contractType", "status", "partId", "q").contains(key))
                throw MasterDataExportInput.unknown("filters." + key);
            JsonNode value = filters.get(key);
            if (value.isNull()) return;
            if (!value.isTextual() && !value.isIntegralNumber()) throw InputValidator.invalid("filters." + key);
            if (!value.asText().isBlank()) query.put(key, value.asText());
        });
        for (String key : List.of("sort", "direction")) {
            JsonNode value = body.get(key);
            if (value != null && !value.isNull()) {
                if (!value.isTextual()) throw InputValidator.invalid(key);
                query.put(key, value.asText());
            }
        }
        if (body.hasNonNull("amountUnit") && !"원".equals(body.get("amountUnit").asText()))
            throw InputValidator.invalid("amountUnit");
        Filter filter = ProjectInput.filter(query);
        JsonNode selected = body.get("columns");
        if (selected == null || !selected.isArray() || selected.isEmpty()) throw InputValidator.invalid("columns");
        List<String> ids = new ArrayList<>();
        for (JsonNode id : selected) {
            if (!id.isTextual() || !COLUMNS.contains(id.asText()) || ids.contains(id.asText()))
                throw InputValidator.invalid("columns");
            ids.add(id.asText());
        }
        if (!ids.containsAll(List.of("id", "name"))) throw InputValidator.invalid("columns");
        var columns = ids.stream().map(id -> new Column(id, title(id),
            Set.of("contractAmount", "personCount").contains(id) ? CellType.NUMBER : CellType.TEXT)).toList();
        var spec = new Specification("프로젝트", null, columns, query.toString(), "원", "UI-01");
        return excel.generateWorkbook(spec, () -> {
            Map<String, String> types = labels("CTRT_TYPE"), statuses = labels("PROJ_STAT"), steps = labels("DSTRB_STEP");
            ProjectsPage first = projects.getProjects(page(filter, 1));
            var sheet = new Sheet("프로젝트", columns, sink -> {
                ProjectsPage data = first;
                for (int page = 1; ; page++) {
                    for (ProjectListItem row : data.items())
                        sink.accept(ids.stream().map(id -> cell(row, id, types, statuses, steps)).toList());
                    if ((long) page * 100 >= data.total() || data.items().isEmpty()) break;
                    data = projects.getProjects(page(filter, page + 1));
                }
            });
            return new WorkbookData(List.of(sheet), List.of(new Information("기간", first.year())),
                first.total(), Integer.toString(first.year()));
        }, request);
    }

    private static Filter page(Filter filter, int page) {
        return new Filter(filter.year(), filter.contractType(), filter.status(), filter.partId(), filter.q(),
            page, 100, filter.sort(), filter.direction());
    }
    private Map<String, String> labels(String group) {
        Map<String, String> result = new HashMap<>();
        codes.codes(group, true).forEach(code -> result.put(code.code(), code.name()));
        return result;
    }
    private static Object cell(ProjectListItem row, String id, Map<String, String> types,
                               Map<String, String> statuses, Map<String, String> steps) {
        return switch (id) {
            case "id" -> row.id();
            case "name" -> row.name();
            case "customerName" -> "IN".equals(row.contractType()) ? "(자체)" : row.customerName();
            case "contractType" -> types.getOrDefault(row.contractType(), "–");
            case "contractPeriod" -> row.startDate() + " ~ " + row.endDate();
            case "contractAmount" -> row.contractAmount() == null ? null : new BigDecimal(row.contractAmount());
            case "personCount" -> row.personCount();
            case "partName" -> "IN".equals(row.contractType()) ? "–" : Objects.toString(row.partName(), "미지정");
            case "status" -> statuses.getOrDefault(row.status(), "–");
            case "distribution" -> !"TK".equals(row.contractType()) ? "–" : row.distribution() == null ? "올리기"
                : steps.getOrDefault(row.distribution().selectedStep(), "–")
                    + (!"0".equals(row.distribution().contractDifference()) ? " 합계 경고" : "")
                    + (row.distribution().mismatchCount() > 0 ? " 불일치 " + row.distribution().mismatchCount() : "");
            default -> throw InputValidator.invalid("columns");
        };
    }
    private static String title(String id) {
        return switch (id) {
            case "id" -> "프로젝트ID";
            case "name" -> "프로젝트명";
            case "customerName" -> "고객사";
            case "contractType" -> "계약유형";
            case "contractPeriod" -> "계약 기간";
            case "contractAmount" -> "계약금액";
            case "personCount" -> "투입 인원";
            case "partName" -> "영업 파트";
            case "status" -> "상태";
            default -> "배분표";
        };
    }
}
