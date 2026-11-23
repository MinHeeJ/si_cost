package com.sicost.forecast;

import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;
import static com.sicost.forecast.SalesForecastDtos.*;

@Service
public class SalesForecastExportService {
    private final SalesRevenueForecastService forecasts;
    private final ExcelExportService excel;
    private com.sicost.common.excel.ExportLabels labels;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLabels(com.sicost.common.excel.ExportLabels labels) { this.labels = labels; }
    public SalesForecastExportService(SalesRevenueForecastService forecasts,ExcelExportService excel) {this.forecasts=forecasts;this.excel=excel;}
    public Download export(String amountUnit,HttpServletRequest request) {
        var columns=List.of(text("month","월"),number("project","프로젝트 매출"),number("opportunity","확률반영 기회 매출"),number("total","합계"),number("allWon","모두 따면"));
        var specification=new Specification("영업예측",null,columns,"전체 · 다음 달부터 6개월 · 미충원 포함",unit(amountUnit),"UI-29");
        return excel.generateWorkbook(specification,() -> workbook(forecasts.forecast(),columns,amountUnit),request);
    }
    private WorkbookData workbook(Result result,List<Column> columns,String unit) {
        var jobs = labels.labels("JOB");
        List<Sheet> sheets=new ArrayList<>();
        sheets.add(new Sheet("월별 매출",columns,sink -> result.revenueMonths().forEach(m -> sink.accept(Arrays.asList(m.month(),decimal(m.projectRevenue()),decimal(m.opportunityRevenue()),decimal(m.totalRevenue()),decimal(m.allWonRevenue()))))));
        sheets.add(new Sheet("매출 원천",List.of(text("kind","원천"),text("id","ID"),text("name","이름"),text("month","월"),number("amount","매출"),number("allWon","모두 따면"),text("basis","근거")),
            sink -> result.revenueSources().forEach(r -> sink.accept(Arrays.asList("PROJECT".equals(r.sourceType())?"프로젝트":"기회",r.sourceId(),r.name(),r.month(),decimal(r.amount()),decimal(r.allWonAmount()),basis(r))))));
        sheets.add(new Sheet("수요공급",List.of(text("month","월"),number("supply","가용"),number("demand","필요"),number("unfilled","그중 미충원"),number("difference","차이"),text("status","상태")),
            sink -> result.workforce().totals().forEach(m -> sink.accept(Arrays.asList(m.month(),decimal(m.supply()),decimal(m.demand()),decimal(m.unfilledDemand()),decimal(m.difference()),com.sicost.common.excel.ExportLabels.workforceStatus(m.status()))))));
        sheets.add(new Sheet("채용 검토",List.of(text("job","직무"),text("first","첫부족월"),number("maximum","최대부족"),text("recommended","권장시작"),number("candidates","후보수")),
            sink -> result.workforce().hiring().forEach(h -> sink.accept(Arrays.asList(com.sicost.common.excel.ExportLabels.name(jobs,h.jobCode()),h.firstShortageMonth(),decimal(h.maximumShortage()),h.startNow()?"지금":h.recommendedStartMonth(),h.candidateCount())))));
        List<Information> info=new ArrayList<>();
        info.add(new Information("기준일",result.workforce().basisDate()));info.add(new Information("기준월",result.workforce().standardMonth()));
        info.add(new Information("기간",result.from()+" ~ "+result.revenueMonths().get(5).month()));
        info.add(new Information("표시단위",unit(unit)));info.add(new Information("숫자셀 금액단위","원 (표시단위와 무관한 전체 원값)"));
        info.add(new Information("원천","프로젝트 · 청구 · 영업 기회 · 투입 배정 · 인력 및 운영 기준"));
        info.add(new Information("실제 업무쓰기","없음 · 예측은 실제 손익/청구/미수에 저장하지 않음"));
        for(Issue issue:result.workforce().issues())info.add(new Information("미확인:"+issue.field(),issue.reason()));
        return new WorkbookData(List.copyOf(sheets),List.copyOf(info),result.revenueSources().size(),result.from().substring(0,4));
    }
    private static Column text(String id,String title) {return new Column(id,title,CellType.TEXT);}
    private static String basis(RevenueSource source) {
        if (!"PROJECT".equals(source.sourceType())) return "수주 확률 반영 매출을 계약 기간에 배분 · 끝자리는 마지막 달 · 모두 따면은 확률 100% 참고";
        if ("TK".equals(source.contractType())) return "등록 청구를 제외한 계약 잔액을 남은 계약 기간에 배분 · 끝자리는 마지막 달";
        return "해당 월 유상 투입 매출 · 철수일까지 반영 · 예상 연장은 제외" + (source.amount()==null?" · 적용 단가 미확인":"");
    }
    private static Column number(String id,String title) {return new Column(id,title,CellType.NUMBER);}
    private static BigDecimal decimal(String value) {return value==null?null:new BigDecimal(value);}
    private static String unit(String value) {return switch(value){case "WON" -> "원";case "MILLION" -> "백만원";default -> "천원";};}
}
