package com.sicost.workforceoperationsops;

import com.sicost.apiops.StaffingQueryInput;
import com.sicost.apiops.StaffingReadDtos.CalendarPage;
import com.sicost.apiops.WorkforceCalendarService;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.forecast.SalesForecastRepository;
import com.sicost.forecast.SalesWorkforceProjectionService;
import com.sicost.workforceforecastsops.WorkforceForecastExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class WorkforceOperationsExportService {
    private final WorkforceCalendarService calendar;
    private final SalesForecastRepository repository;
    private final SalesWorkforceProjectionService projections;
    private final WorkforceForecastExportService forecastWorkbooks;
    private final ExcelExportService excel;
    private final Clock clock;
    private com.sicost.common.excel.ExportLabels labels;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLabels(com.sicost.common.excel.ExportLabels labels) { this.labels = labels; }

    public WorkforceOperationsExportService(WorkforceCalendarService calendar, SalesForecastRepository repository,
            SalesWorkforceProjectionService projections, WorkforceForecastExportService forecastWorkbooks,
            ExcelExportService excel, Clock clock) {
        this.calendar = calendar; this.repository = repository; this.projections = projections;
        this.forecastWorkbooks = forecastWorkbooks; this.excel = excel; this.clock = clock;
    }

    public Download export(WorkforceOperationsExportInput.Query query, HttpServletRequest request) {
        boolean supply = query.tab().equals("SUPPLY_DEMAND");
        String kind = supply ? "인력운영수요공급" : "인력운영달력";
        String conditions = supply ? "수요·공급 · 서버 현재월 M+1~M+6 · 전체 인력 · 달력 연도/필터 미적용"
            : calendarConditions(query);
        var spec = new Specification(kind, null, List.of(text("metric", "항목")), conditions, unit(query.amountUnit()), "UI-28");
        // The source and every sheet are consumed inside ExcelExportService's single repeatable-read snapshot.
        return excel.generateWorkbook(spec, () -> {
            LocalDate day = LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
            calendar.requireYear(query.year());
            if (!supply) return calendarWorkbook(calendar.listAll(query.calendarFilters()), query, day);
            YearMonth current = YearMonth.from(day);
            if (current.plusMonths(6).getYear() > 9999) throw StaffingQueryInput.invalid("year", "INVALID_PERIOD");
            var data = projections.calculate(repository.load(current), day, current.plusMonths(1), 6, true);
            return forecastWorkbooks.workbook(data, query.amountUnit());
        }, request);
    }

    private WorkbookData calendarWorkbook(CalendarPage data, WorkforceOperationsExportInput.Query query, LocalDate day) {
        var jobs = labels.labels("JOB"); var grades = labels.labels("GRADE");
        var employeeTypes = labels.labels("EMP_TYPE"); var extensions = labels.labels("EXT_PSBL");
        var tentativeStatuses = labels.labels("TENT_STAT");
        var columns = new ArrayList<Column>();
        columns.add(text("developerId", "개발자ID")); columns.add(text("name", "이름"));
        columns.add(text("jobCode", "직무")); columns.add(text("gradeCode", "등급"));
        columns.add(text("employeeType", "직원구분")); columns.add(text("lastWithdrawalDate", "마지막 철수일"));
        for (int month = 1; month <= 12; month++) {
            columns.add(text("status" + month, month + "월 상태"));
            columns.add(number("mm" + month, month + "월 실제 MM"));
        }
        var sheets = new ArrayList<Sheet>();
        sheets.add(new Sheet("인력 운영 달력", columns, sink -> data.items().forEach(person -> {
            var row = new ArrayList<Object>(Arrays.asList(person.developerId(), person.name(), com.sicost.common.excel.ExportLabels.name(jobs,person.jobCode()), com.sicost.common.excel.ExportLabels.name(grades,person.gradeCode()),
                com.sicost.common.excel.ExportLabels.name(employeeTypes,person.employeeType()), person.lastWithdrawalDate()));
            person.cells().forEach(cell -> { row.add(status(cell.status())); row.add(decimal(cell.actualMm())); });
            sink.accept(row);
        })));
        sheets.add(new Sheet("월별 합계", List.of(text("month", "월"), number("available", "비는인원"), number("remaining", "남는 MM")),
            sink -> data.monthTotals().forEach(m -> sink.accept(Arrays.asList(m.month(), m.availablePeople(), decimal(m.remainingMm()))))));
        sheets.add(new Sheet("요약", List.of(number("available", "현재 비는인원"), number("remaining", "현재 남는 MM"),
            number("leaving", "3개월 안 철수 예정"), number("extension", "연장 가능성 높음"), number("concurrent", "동시 투입"),
            number("over", "1.0 초과"), number("third", "M+3 남는 MM")), sink -> {
                var s = data.summary();
                sink.accept(Arrays.asList(s.availablePeople(), decimal(s.remainingMm()), s.leavingSoonPeople(), s.highExtensionPeople(),
                    s.concurrentPeople(), s.overAssignedPeople(), decimal(s.thirdMonthRemainingMm())));
            }));
        sheets.add(new Sheet("월별 상세", List.of(text("developerId", "개발자ID"), text("month", "월"), text("status", "상태"),
            number("count", "배정 건수"), text("over", "1.0 초과"), text("leaving", "철수 예정"), number("probability", "가배정 확률(%)")),
            sink -> data.items().forEach(p -> p.cells().forEach(c -> sink.accept(Arrays.asList(p.developerId(), c.month(), status(c.status()),
                c.assignmentCount(), c.overAssigned() ? "예" : "아니오", c.leavingSoon() ? "예" : "아니오", decimal(c.tentativeProbability())))))));
        sheets.add(new Sheet("프로젝트 근거", List.of(text("developerId", "개발자ID"), text("month", "월"), text("projectId", "프로젝트ID"),
            number("mm", "실제 MM"), text("end", "종료일"), text("extension", "연장 가능성"), text("pre", "미계약")),
            sink -> data.items().forEach(p -> p.cells().forEach(c -> c.projects().forEach(a -> sink.accept(Arrays.asList(p.developerId(), c.month(),
                a.projectId(), decimal(a.actualMm()), a.endDate(), com.sicost.common.excel.ExportLabels.name(extensions,a.extensionPossibility()), a.preContract() ? "예" : "아니오")))))));
        sheets.add(new Sheet("가배정 근거", List.of(text("developerId", "개발자ID"), text("month", "월"), text("tentativeId", "가배정ID"),
            text("opportunityId", "기회ID"), number("needSn", "필요순번"), text("start", "시작월"), text("status", "상태"),
            text("source", "원천 화면"), text("processed", "처리시각")),
            sink -> data.items().forEach(p -> p.cells().forEach(c -> c.tentativeAssignments().forEach(t -> sink.accept(Arrays.asList(
                p.developerId(), c.month(), t.id(), t.opportunityId(), t.needSn(), t.startMonth(), com.sicost.common.excel.ExportLabels.name(tentativeStatuses,t.status()), t.sourceScreenId(), t.processedAt())))))));
        var information = List.of(new Information("기간", data.year() + "-01 ~ " + data.year() + "-12"),
            new Information("기준일", day.toString()), new Information("기준월", YearMonth.from(day).toString()),
            new Information("단위", unit(query.amountUnit())), new Information("인력 단위", "명 / MM, 숫자셀은 화면 전체값"),
            new Information("필터", calendarConditions(query)), new Information("출력 범위", "달력 탭 · 필터 전체 / 모든 페이지"),
            new Information("원천", "개발자 · 근무 이력 · 투입 배정 · 가배정 · 영업 기회 · 필요 인력"),
            new Information("미확인", "과거월 비는인원/남는 MM 및 미확인 값은 빈셀입니다. 숫자 0과 구분합니다."));
        return new WorkbookData(sheets, information, data.total(), Integer.toString(data.year()));
    }

    private String calendarConditions(WorkforceOperationsExportInput.Query query) {
        var parts = new ArrayList<String>(); parts.add(query.year()+"년 달력");
        for (String field : List.of("jobCode", "gradeCode", "employeeType")) {
            String value = query.calendarFilters().getFirst(field);
            if (value != null) {
                String group = field.equals("jobCode") ? "JOB" : field.equals("gradeCode") ? "GRADE" : "EMP_TYPE";
                String title = field.equals("jobCode") ? "직무" : field.equals("gradeCode") ? "등급" : "직원구분";
                parts.add(title+"="+com.sicost.common.excel.ExportLabels.name(labels.labels(group),value));
            }
        }
        if ("true".equals(query.calendarFilters().getFirst("remainingOnly"))) parts.add("남는 인력만=예");
        return String.join(" · ",parts);
    }
    private static Column text(String id, String title) { return new Column(id, title, CellType.TEXT); }
    private static Column number(String id, String title) { return new Column(id, title, CellType.NUMBER); }
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    private static String unit(String value) { return switch (value) { case "WON" -> "원"; case "MILLION" -> "백만원"; default -> "천원"; }; }
    private static String status(String value) {
        return switch (value) {
            case "BEFORE_JOIN" -> "입사 전"; case "RETIRED" -> "퇴사"; case "LEAVE" -> "휴직";
            case "ASSIGNED" -> "투입중"; case "TENTATIVE" -> "가배정"; case "AVAILABLE" -> "비어있음";
            default -> "–";
        };
    }
}
