package com.sicost.imports;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import java.math.BigDecimal;

import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.imports.BusinessPlanImportDtos.*;

@Component
public class BusinessPlanImportDefinition {
    public static final List<String> HEADERS = List.of("연월", "매출 목표", "영업이익 목표");
    private static final BigDecimal MAX = new BigDecimal("999999999999999");
    private final ExcelImportService imports;
    public BusinessPlanImportDefinition(ExcelImportService imports) { this.imports = imports; }
    public String filename(MultipartFile file) {
        try { return imports.filename(file); } catch (ApiException e) { throw translated(e); }
    }
    public Parsed parse(MultipartFile file) {
        try {
            return imports.parse(file, new ImportDefinition(HEADERS, Set.copyOf(HEADERS), Set.of(),
                Set.of(), Map.of(), Set.of("매출 목표", "영업이익 목표"), Set.of("연월")));
        } catch (ApiException e) {
            throw translated(e);
        }
    }
    private ApiException translated(ApiException e) {
        return new ApiException(e.status(), e.status() == 413 ? "PAYLOAD_TOO_LARGE" : e.status() == 415
            ? "UNSUPPORTED_MEDIA_TYPE" : "VALIDATION_ERROR", e.getMessage(), "file", null);
    }
    public Checked check(Parsed parsed, int year, List<Stored> old, List<Close> closes) {
        List<Issue> issues = new ArrayList<>(parsed.fileIssues());
        List<Row> rows = new ArrayList<>();
        Map<Integer, List<Issue>> own = new LinkedHashMap<>();
        Map<String, List<SourceRow>> months = new LinkedHashMap<>();
        Set<String> blankMonths = new TreeSet<>();
        for (SourceRow source : parsed.rows()) {
            List<Issue> problems = new ArrayList<>(); own.put(source.number(), problems);
            for (String field : HEADERS) if (source.cells().get(field).error() != null)
                add(problems, source, field, "ERR", source.cells().get(field).error());
            String month = source.cells().get("연월").text();
            try {
                if (!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])") || YearMonth.parse(month).getYear() != year)
                    throw new IllegalArgumentException();
                months.computeIfAbsent(month, k -> new ArrayList<>()).add(source);
            } catch (RuntimeException e) { add(problems, source, "연월", "ERR", "대상연도의 YYYY-MM을 입력해 주세요."); month = null; }
            BigDecimal sales = amount(source, "매출 목표", false, problems);
            BigDecimal profit = amount(source, "영업이익 목표", true, problems);
            if (month != null && (source.cells().get("매출 목표").text().isBlank() || source.cells().get("영업이익 목표").text().isBlank())) blankMonths.add(month);
            rows.add(new Row(source.number(), month, sales, profit));
        }
        for (var entry : months.entrySet()) if (entry.getValue().size() > 1)
            for (SourceRow source : entry.getValue()) add(own.get(source.number()), source, "연월", "ERR", "연월이 중복되었습니다.");
        List<String> missing = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String month = YearMonth.of(year, m).toString(); if (!months.containsKey(month)) missing.add(month);
        }
        if (!missing.isEmpty() || parsed.rows().size() != 12) {
            for (SourceRow source : parsed.rows()) add(own.get(source.number()), source, "연월", "ERR",
                "대상연도 12개월이 각 1행 필요합니다. 누락: " + String.join(", ", missing));
        }
        int errors = 0, warnings = 0;
        for (List<Issue> problems : own.values()) {
            if (problems.stream().anyMatch(i -> "ERR".equals(i.severity()))) errors++;
            else if (!problems.isEmpty()) warnings++;
            issues.addAll(problems);
        }
        List<Change> changes = new ArrayList<>();
        for (Row row : rows) {
            if (row.month() == null || own.get(row.rowNumber()).stream().anyMatch(i -> "ERR".equals(i.severity()))) continue;
            Stored before = old.stream().filter(r -> row.month().replace("-", "").equals(r.month())).findFirst().orElse(null);
            Target after = new Target(row.sales().toPlainString(), row.profit().toPlainString());
            Target previous = before == null ? null : new Target(text(before.sales()), text(before.profit()));
            boolean changed = before == null || !equal(before.sales(), row.sales()) || !equal(before.profit(), row.profit());
            boolean closed = closes.stream().anyMatch(c -> row.month().replace("-", "").equals(c.month()) && "CLOSE".equals(c.status()));
            changes.add(new Change(row.month(), row.rowNumber(), closed, changed, previous, after));
        }
        List<Issue> ordered = new ArrayList<>();
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparing(Issue::columnName));
        for (Issue i : issues) ordered.add(new Issue(ordered.size() + 1, i.severity(), i.rowNumber(), i.columnName(), i.inputValue(), i.reason()));
        List<String> notices = blankMonths.isEmpty() ? Collections.emptyList() : List.of("목표가 빈 달: " + String.join(", ", blankMonths.stream().map(m -> Integer.parseInt(m.substring(5)) + "월").toList()));
        return new Checked(List.copyOf(rows), new Summary(rows.size(), rows.size() - errors - warnings, warnings, errors,
            parsed.fileIssues().size()), List.copyOf(ordered), notices, List.copyOf(blankMonths), List.copyOf(changes));
    }
    private BigDecimal amount(SourceRow source, String field, boolean signed, List<Issue> issues) {
        String value = source.cells().get(field).text().strip();
        if (value.isEmpty()) { add(issues, source, field, "WARN", "빈 목표는 0으로 반영합니다."); return BigDecimal.ZERO; }
        try {
            if (!value.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)")) throw new IllegalArgumentException();
            BigDecimal number = new BigDecimal(value.replace(",", ""));
            if ((!signed && number.signum() < 0) || number.abs().compareTo(MAX) > 0) throw new IllegalArgumentException();
            return number.setScale(0);
        } catch (RuntimeException e) { add(issues, source, field, "ERR", "허용 범위의 원 단위 정수를 입력해 주세요."); return null; }
    }
    private void add(List<Issue> issues, SourceRow row, String field, String severity, String reason) {
        issues.add(new Issue(0, severity, row.number(), field, excerpt(row.cells().get(field).text(), 200), excerpt(reason, 300)));
    }
    private String excerpt(String value, int max) { return value.codePointCount(0, value.length()) <= max ? value : value.substring(0, value.offsetByCodePoints(0, max)); }
    private boolean equal(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }
    private String text(BigDecimal value) { return value == null ? null : value.toPlainString(); }
}
