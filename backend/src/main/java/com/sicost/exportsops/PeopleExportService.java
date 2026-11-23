package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.candidates.CandidateInput;
import com.sicost.candidates.CandidateQueryService;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.excel.ExcelExportService.CellType;
import com.sicost.common.excel.ExcelExportService.Column;
import com.sicost.common.excel.ExcelExportService.Download;
import com.sicost.common.excel.ExcelExportService.Specification;
import com.sicost.common.validation.MasterDataValidator;
import com.sicost.developers.DeveloperInput;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.shared.SharedQueriesMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 개발자 목록(UI-02)·외부 인력 후보 목록(UI-33) 엑셀 다운로드.
 * 조회는 각 목록 API와 같은 서비스를 같은 조건으로 불러 필터·정렬 의미를 하나로 유지하고,
 * 페이지 제한 없이 조건에 맞는 전체를 묶음 단위로 읽어 파일에 쓴다. 접근 기록(DNLD)과 파일명은 공통 엑셀 서비스가 맡는다.
 */
@Service
public class PeopleExportService {
    /** 묶음 크기. 목록 API의 페이지 크기 제한(20/50/100)과 무관한 내부 조회 단위다. */
    private static final int CHUNK = 500;
    private final DeveloperQueryService developers;
    private final CandidateQueryService candidates;
    private final SharedQueriesMapper codes;
    private final ExcelExportService excel;
    private com.sicost.permission.PermissionProjectionService projection;
    @org.springframework.beans.factory.annotation.Autowired
    public void setProjection(com.sicost.permission.PermissionProjectionService projection) {this.projection=projection;}

    public PeopleExportService(DeveloperQueryService developers, CandidateQueryService candidates, SharedQueriesMapper codes, ExcelExportService excel) {
        this.developers = developers; this.candidates = candidates; this.codes = codes; this.excel = excel;
    }

    public Download create(JsonNode body, HttpServletRequest request) {
        PeopleExportInput.Export input = PeopleExportInput.parse(body);
        boolean dev = input.view().equals("DEVELOPERS");
        List<String> selected = new ArrayList<>(input.columns());
        if (!dev && selected.contains("employeeType") && !selected.contains("companyName"))
            selected.add(selected.indexOf("employeeType") + 1, "companyName");
        final List<String> allowedColumns=!dev && projection!=null?projection.candidateColumns(selected):selected;
        Map<String, String> query = new HashMap<>(input.filters());
        if (input.sort() != null) query.put("sort", input.sort());
        query.put("direction", input.direction());
        // 목록 API와 같은 검증을 한 번 통과시킨다. 조건 오류는 다운로드도 같은 400이다.
        DeveloperInput.Query devQuery = dev ? DeveloperInput.query(query) : null;
        CandidateInput.Query candQuery = dev ? null : CandidateInput.query(query);
        Map<String, String> employeeTypes = labels("EMP_TYPE"), workStatuses = labels("WORK_STAT"), contactStatuses = labels("CNTC_STAT");
        String unit = input.amountUnit();
        List<Column> columns = allowedColumns.stream().map(id -> new Column(id, title(id, dev, unit), numeric(id) ? CellType.NUMBER : CellType.TEXT)).toList();
        String conditions = input.filters().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> condition(e.getKey(), e.getValue())).collect(Collectors.joining("; "))
            + "; 정렬=" + (input.sort() == null ? "기본" : title(input.sort(), dev, unit))
            + " " + ("DESC".equals(input.direction()) ? "내림차순" : "오름차순");
        Specification specification = new Specification(dev ? "개발자" : "외부인력후보", null, columns, conditions, unit, dev ? "UI-02" : "UI-33");
        return excel.generate(specification, sink -> {
            for (int page = 1; ; page++) {
                JsonNode result = dev
                    ? developers.list(new DeveloperInput.Query(devQuery.filters(), devQuery.sort(), devQuery.direction(), page, CHUNK))
                    : candidates.list(new CandidateInput.Query(candQuery.filters(), candQuery.sort(), candQuery.direction(), page, CHUNK));
                JsonNode items = result.get("items");
                for (JsonNode item : items) {
                    List<Object> values = new ArrayList<>();
                    for (String id : allowedColumns) values.add(id.equals("companyName") && dev
                        ? Objects.toString(text(item.get(id)), "자사") : cell(id, item, unit, employeeTypes, workStatuses, contactStatuses));
                    sink.accept(values);
                }
                if (items.size() < CHUNK || (long) page * CHUNK >= result.get("total").asLong()) break;
            }
        }, request);
    }

    private String condition(String key, String value) {
        String group = switch (key) {
            case "employeeType" -> "EMP_TYPE";
            case "jobCode" -> "JOB";
            case "gradeCode" -> "GRADE";
            case "directClass" -> "DRCT_CLS";
            case "workStatus" -> "WORK_STAT";
            case "contactStatus" -> "CNTC_STAT";
            default -> null;
        };
        String name = switch (key) {
            case "employeeType" -> "구분"; case "jobCode" -> "직무"; case "gradeCode" -> "등급";
            case "directClass" -> "직접/간접"; case "workStatus" -> "근무 상태"; case "contactStatus" -> "연락 상태";
            case "q" -> "검색어"; case "includeStopped" -> "중지 포함"; case "activeOnly" -> "사용 인력만";
            case "availability" -> "투입 가능"; case "availableUntil" -> "투입 가능일";
            default -> "조건";
        };
        String display = group != null ? labels(group).getOrDefault(value, "–") : switch (value) {
            case "NEXT_MONTH" -> "다음 달까지"; case "THREE_MONTHS" -> "3개월 안";
            case "true" -> "예"; case "false" -> "아니요"; default -> value;
        };
        return name + "=" + display;
    }
    private Map<String, String> labels(String group) {
        Map<String, String> result = new HashMap<>();
        codes.codes(group, true).forEach(code -> result.put(code.code(), code.name()));
        return result;
    }
    private static boolean numeric(String id) { return id.equals("monthlyRate") || id.equals("desiredRate"); }
    private static String title(String id, boolean dev, String unit) {
        return switch (id) {
            case "name" -> "이름";
            case "companyName" -> "소속";
            case "employeeType" -> "직원구분";
            case "grade" -> "등급";
            case "job" -> dev ? "주 직무" : "직무";
            case "status" -> "상태";
            case "currentProjects" -> "현재 프로젝트";
            case "withdrawalDate" -> "철수 예정일";
            case "monthlyRate" -> "월 단가(" + unit + ")";
            case "skills" -> "기술";
            case "homeAddress" -> "거주지";
            case "desiredRate" -> "희망단가(" + unit + ")";
            case "availableDate" -> "가능일";
            case "contactStatus" -> "연락 상태";
            case "lastContactDate" -> "최근 연락일";
            case "source" -> "출처";
            default -> id;
        };
    }
    private static Object cell(String id, JsonNode item, String unit, Map<String, String> employeeTypes, Map<String, String> workStatuses, Map<String, String> contactStatuses) {
        return switch (id) {
            case "employeeType" -> label(employeeTypes, item.get("employeeType"));
            case "status" -> label(workStatuses, item.get("status"));
            case "contactStatus" -> label(contactStatuses, item.get("contactStatus"));
            case "currentProjects" -> text(item.get("currentProjectLabel"));
            case "skills" -> {
                JsonNode skills = item.get("skills");
                List<String> names = new ArrayList<>();
                if (skills != null && skills.isArray()) skills.forEach(skill -> names.add(skill.asText()));
                yield String.join(", ", names);
            }
            case "monthlyRate", "desiredRate" -> {
                String value = text(item.get(id));
                yield value == null ? null : MasterDataValidator.amountInUnit(new BigDecimal(value), unit).stripTrailingZeros();
            }
            default -> text(item.get(id));
        };
    }
    private static String text(JsonNode node) { return node == null || node.isNull() ? null : node.asText(); }
    private static String label(Map<String, String> labels, JsonNode code) {
        String value = text(code);
        return value == null ? null : labels.getOrDefault(value, value);
    }
}
