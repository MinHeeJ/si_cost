package com.sicost.projectcostsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.apiops.ProjectAnalysisService;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.MasterDataValidator;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.me.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class ProjectCostExportService {
    private final ExcelExportService excel;
    private final ProjectAnalysisService costs;
    private final PreferenceService preferences;
    public ProjectCostExportService(ExcelExportService excel,ProjectAnalysisService costs,PreferenceService preferences) {
        this.excel=excel;this.costs=costs;this.preferences=preferences;
    }
    public Download create(ProjectCostExportInput.Export input,HttpServletRequest request) {
        var specification=new Specification("프로젝트 원가",null,List.of(new Column("projectName","프로젝트",CellType.TEXT)),
            new TreeMap<>(input.query().filters()).toString(),input.unit()==null?"천원":input.unit(),"UI-04");
        return excel.generateWorkbook(specification,()->workbook(input),request);
    }
    private WorkbookData workbook(ProjectCostExportInput.Export input) {
        List<String> order=ProjectCostExportInput.COLUMNS,hidden=Collections.emptyList();String unit="천원";
        for(var preference:preferences.getPreferences().items()) {
            if("COLS".equals(preference.type())&&"UI-04".equals(preference.key())) {
                JsonNode view=preference.value().path("projectCost");
                if(view.isObject()) {
                    order=ProjectCostExportInput.columns(csv(view.path("order")),false);
                    hidden=ProjectCostExportInput.columns(csv(view.path("hidden")),true);
                }
            }
            if("AMT_UNIT".equals(preference.type())&&preference.value().isTextual())unit=preference.value().asText();
        }
        if(input.order()!=null)order=input.order();if(input.hidden()!=null)hidden=input.hidden();if(input.unit()!=null)unit=input.unit();
        if(!Set.of("원","천원","백만원").contains(unit))throw ProjectInputPolicy.invalid("unit");
        String resolvedUnit=unit;Set<String> excluded=new HashSet<>(hidden);
        var columns=order.stream().filter(id->!excluded.contains(id)).map(id->new Column(id,title(id),text(id)?CellType.TEXT:CellType.NUMBER)).toList();
        var data=costs.getProjectCosts(input.query());
        var sheet=new Sheet("프로젝트 원가",columns,sink->{for(var row:data.items())sink.accept(columns.stream().map(c->cell(row,c.id(),resolvedUnit)).toList());});
        var completeness=(com.sicost.apiops.ProjectAnalysisDtos.Completeness)data.summary().get("completeness");
        return new WorkbookData(List.of(sheet),List.of(new Information("기준월",data.month()),new Information("단위",unit),
            new Information("미확인수",completeness.unknownProjectCount()),new Information("열순서",String.join(",",order)),new Information("숨김열",String.join(",",hidden)),
            new Information("정밀도 안내","금액/MM/율/건수는 숫자 셀입니다. Excel은 15자리 초과 금액을 반올림할 수 있습니다. 정확한 값은 API에서 확인하세요.")),data.total(),data.month().replace("-",""));
    }
    private static String csv(JsonNode value){var result=new ArrayList<String>();value.forEach(n->result.add(n.asText()));return String.join(",",result);}
    private static boolean text(String id){return Set.of("projectName","contractType","status","alerts").contains(id);}
    private static boolean amount(String id){return !text(id)&&!Set.of("planMm","actualMm","freeMm","cumulativeMargin","forecastMargin","monthlyMargin","targetMargin","targetGap","budgetUsage").contains(id);}
    private static Object cell(Map<String,Object> row,String id,String unit) {
        Object value=row.get(id);if(value==null)return null;
        if("alerts".equals(id))return ((List<?>)value).stream().map(a->Objects.toString(((Map<?,?>)a).get("message"),"")).collect(java.util.stream.Collectors.joining(", "));
        if(text(id))return value.toString();
        var number=new BigDecimal(value.toString());return amount(id)?MasterDataValidator.amountInUnit(number,unit):number;
    }
    private static String title(String id){return switch(id){
        case "projectName"->"프로젝트";case "contractType"->"유형";case "status"->"상태";case "originalContractAmount"->"원 계약금액";case "contractAmount"->"현재 계약금액";
        case "planMm"->"계획 MM";case "actualMm"->"실제 MM";case "cumulativeRevenue"->"누적 매출";case "cumulativeCost"->"누적 원가";case "cumulativeProfit"->"이익";
        case "cumulativeMargin"->"누적 마진율(%)";case "forecastMargin"->"예상 최종 마진율(%)";case "monthlyMargin"->"월간 마진율(%)";case "targetMargin"->"목표 마진율(%)";case "targetGap"->"목표 대비(%p)";case "budgetAmount"->"예산";case "budgetUsage"->"예산 소진율(%)";
        case "pricePerMm"->"MM당 단가";case "costPerMm"->"MM당 원가";case "profitPerMm"->"MM당 이익";case "freeMm"->"무상지원 MM";case "freeLaborCost"->"무상 원가";case "alerts"->"경보";default->throw ProjectInputPolicy.invalid("columns");};}
}
