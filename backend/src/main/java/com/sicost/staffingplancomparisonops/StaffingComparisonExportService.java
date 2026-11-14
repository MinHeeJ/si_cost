package com.sicost.staffingplancomparisonops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;
import static com.sicost.staffingplancomparisonops.StaffingComparisonDtos.*;

@Service
public class StaffingComparisonExportService {
    private final StaffingComparisonRepository repository;
    private final ExcelExportService excel;
    private final Clock clock;
    public StaffingComparisonExportService(StaffingComparisonRepository repository, ExcelExportService excel, Clock clock) {
        this.repository=repository; this.excel=excel; this.clock=clock;
    }
    public Download export(Query input, HttpServletRequest request) {
        var columns=List.of(text("projectId","프로젝트 ID"),text("projectName","프로젝트"),text("jobCode","직무"),number("planMm","계획 MM"),number("actualMm","실제·예정 MM"),number("differenceMm","차이"));
        String unit=switch(input.amountUnit()) {case "WON" -> "원";case "MILLION" -> "백만원";default -> "천원";};
        return excel.generateWorkbook(new Specification("계획대비투입",null,columns,"프로젝트="+(input.projectId()==null?"전체":input.projectId())+" · 차이="+input.difference(),unit,"UI-11"),() -> {
            LocalDate today=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
            int year=input.year()==null?today.getYear():input.year();
            if (year<repository.cashBase().getYear() || year>Math.min(9999,today.getYear()+1))
                throw new ApiException(400,"INVALID_PERIOD","운영 시작연도부터 내년까지 조회할 수 있습니다.","year",null);
            var rows=repository.rows(new Query(year,input.projectId(),input.difference(),input.amountUnit()));
            BigDecimal plan=rows.stream().map(Row::planMm).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal actual=rows.stream().map(Row::actualMm).reduce(BigDecimal.ZERO,BigDecimal::add);
            var sheet=new Sheet("계획 대비 투입",columns,sink -> {
                for (Row row:rows) sink.accept(List.of(row.projectId(),row.projectName(),row.jobCode(),row.planMm(),row.actualMm(),row.differenceMm()));
                sink.accept(List.of("","필터 전체 합계","",plan,actual,actual.subtract(plan)));
            });
            return new WorkbookData(List.of(sheet),List.of(new Information("기간",year+"-01 ~ "+year+"-12"),
                new Information("기준일",today.toString()),new Information("단위",unit),new Information("MM 단위","MM (표시단위와 무관한 전체값)"),
                new Information("원천","TB_PJ_PROJ / TB_PJ_MNPW_PLAN / 05 월MM preview (보호월 저장값 우선)"),
                new Information("계산","연도계획=전체계획×연도 겹친 개월/계약 전체 개월 HALF_UP2; 차이=실제−계획"),
                new Information("미확인","원천 조회 실패 시 파일 미전송; 빈 목록은 합계 0")),rows.size(),String.format("%04d",year));
        },request);
    }
    private static Column text(String id,String title) { return new Column(id,title,CellType.TEXT); }
    private static Column number(String id,String title) { return new Column(id,title,CellType.NUMBER); }
}
