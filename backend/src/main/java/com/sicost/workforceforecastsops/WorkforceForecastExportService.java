package com.sicost.workforceforecastsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.forecast.SalesForecastDtos.Workforce;
import com.sicost.forecast.SalesForecastDtos.WorkforceMonth;
import com.sicost.forecast.SalesForecastRepository;
import com.sicost.forecast.SalesWorkforceProjectionService;
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
public class WorkforceForecastExportService {
    private final SalesForecastRepository repository;
    private final SalesWorkforceProjectionService projections;
    private final ExcelExportService excel;
    private final Clock clock;
    private com.sicost.common.excel.ExportLabels labels;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLabels(com.sicost.common.excel.ExportLabels labels) { this.labels = labels; }
    public WorkforceForecastExportService(SalesForecastRepository repository, SalesWorkforceProjectionService projections,
                                         ExcelExportService excel, Clock clock) {
        this.repository = repository; this.projections = projections; this.excel = excel; this.clock = clock;
    }
    public Download export(WorkforceForecastInput.Query query, HttpServletRequest request) {
        var columns = List.of(text("metric", "전체 사람수(MM)"));
        String conditions = query.from() + " ~ " + query.from().plusMonths(query.months() - 1)
            + " · 미충원 포함=" + (query.includeUnfilled()?"예":"아니오") + " · 전체 직무/전체 월";
        if (query.includeOpportunities() != null) conditions += " · " + opportunityFilter(query);
        return excel.generateWorkbook(new Specification("인력수요예측", null, columns, conditions, unit(query.amountUnit()), "UI-17"), () -> {
            LocalDate day = LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
            var snapshot = repository.load(YearMonth.from(day));
            Workforce data = projections.calculate(snapshot, day, query.from(), query.months(), query.includeUnfilled(), query.includeOpportunities(), query.minimumWinProbability());
            return workbook(data, query.amountUnit(), query);
        }, request);
    }
    public WorkbookData workbook(Workforce data, String amountUnit) {
        return workbook(data, amountUnit, null);
    }

    private WorkbookData workbook(Workforce data, String amountUnit, WorkforceForecastInput.Query query) {
        var jobs = labels.labels("JOB");
        List<Column> columns = new ArrayList<>(); columns.add(text("metric", "전체 사람수(MM)"));
        data.totals().forEach(m -> columns.add(number(m.month(), m.month())));
        List<Sheet> sheets = new ArrayList<>();
        sheets.add(new Sheet("월별 가용 vs 필요", columns, sink -> {
            sink.accept(metric("필요 인력", data.totals().stream().map(WorkforceMonth::demand).toList()));
            sink.accept(metric("가용 인력", data.totals().stream().map(WorkforceMonth::supply).toList()));
            sink.accept(metric("차이 가용−필요", data.totals().stream().map(WorkforceMonth::difference).toList()));
            sink.accept(metric("그중 미충원", data.totals().stream().map(WorkforceMonth::unfilledDemand).toList()));
        }));
        sheets.add(new Sheet("직무별 계산근거", List.of(text("job", "직무"), text("month", "월"), number("supply", "가용"),
            number("opportunity", "기회 필요"), number("unfilled", "미충원"), number("demand", "필요"), number("difference", "차이"),
            text("status", "판정"), text("basis", "계산근거")), sink -> data.jobDiagnostics().forEach(j -> {
                var m = j.month();
                sink.accept(Arrays.asList(com.sicost.common.excel.ExportLabels.name(jobs,j.jobCode()), m.month(), decimal(m.supply()), decimal(m.opportunityDemand()), decimal(m.unfilledDemand()),
                    decimal(m.demand()), decimal(m.difference()), com.sicost.common.excel.ExportLabels.workforceStatus(m.status()), "가용 인력 − 필요 인력 (기회 필요 및 미충원)"));
            })));
        sheets.add(new Sheet("채용 검토", List.of(text("job", "직무"), text("first", "첫부족월"), number("maximum", "최대부족"),
            text("recommended", "권장시작월"), text("now", "지금 시작"), number("candidates", "후보수")),
            sink -> data.hiring().forEach(h -> sink.accept(Arrays.asList(com.sicost.common.excel.ExportLabels.name(jobs,h.jobCode()), h.firstShortageMonth(), decimal(h.maximumShortage()),
                h.recommendedStartMonth(), h.startNow() ? "지금" : "", h.candidateCount())))));
        sheets.add(new Sheet("미확인", List.of(text("field", "항목"), number("needSn", "필요순번"), text("reason", "사유"), text("status", "상태")),
            sink -> data.issues().forEach(i -> sink.accept(Arrays.asList(i.field(), i.needSn(), i.reason(), i.status())))));
        List<Information> info = new ArrayList<>();
        info.add(new Information("기간", data.from() + " ~ " + YearMonth.parse(data.from()).plusMonths(data.months() - 1)));
        info.add(new Information("기준일", data.basisDate())); info.add(new Information("기준월", data.standardMonth()));
        info.add(new Information("단위", unit(amountUnit))); info.add(new Information("인력 단위", "명(MM), 숫자셀은 화면 전체값"));
        info.add(new Information("필터", "전체 직무 · 미충원 포함=" + (data.includeUnfilled()?"예":"아니오")));
        info.add(new Information("원천", "개발자 · 근무 이력 · 투입 배정 · 영업 기회 · 인원 계획 · 운영 기준"));
        String demandFormula = "기회 필요는 인원·월 MM·확률을 반영하고 미충원을 더합니다.";
        if (query != null && query.includeOpportunities() != null) {
            info.add(new Information("영업 기회 필터", opportunityFilter(query)));
            demandFormula = query.includeOpportunities()
                ? "조건에 맞는 기회의 인원 × 월 MM과 프로젝트 미충원을 더합니다."
                : "진행 중 프로젝트의 미충원을 합산합니다.";
        }
        info.add(new Information("계산 방법", "가용 인력에서 확정 투입과 예상 연장을 차감합니다. "
            + demandFormula + " 직무별 소수 첫째 자리 반올림 후 합산합니다."));
        info.add(new Information("인원 계획", "추가 투입하지 않으면 PM이 인원 계획을 실제에 맞게 줄입니다."));
        info.add(new Information("미확인", data.issues().isEmpty() ? "없음" : "미확인 시트의 항목별 사유 참조; 빈셀은 0이 아닙니다."));
        return new WorkbookData(sheets, info, data.jobDiagnostics().size(), data.from().substring(0, 4));
    }
    private String opportunityFilter(WorkforceForecastInput.Query query) {
        if (!query.includeOpportunities()) return "영업 기회 제외";
        if (query.minimumWinProbability() == null) return "영업 기회 전체";
        return "영업 기회 " + query.minimumWinProbability().stripTrailingZeros().toPlainString() + "% 이상";
    }

    private List<Object> metric(String label, List<String> values) {
        List<Object> row = new ArrayList<>(); row.add(label); values.forEach(v -> row.add(decimal(v))); return row;
    }
    private static Column text(String id, String title) { return new Column(id, title, CellType.TEXT); }
    private static Column number(String id, String title) { return new Column(id, title, CellType.NUMBER); }
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
    private static String unit(String value) { return switch (value) { case "WON" -> "원"; case "MILLION" -> "백만원"; default -> "천원"; }; }
}
