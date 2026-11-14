package com.sicost.projectstatusboardops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.apiops.ProjectAnalysisService;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.me.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class ProjectStatusExportService {
    private final ExcelExportService excel;
    private final ProjectAnalysisService board;
    private final PreferenceService preferences;
    public ProjectStatusExportService(ExcelExportService excel,ProjectAnalysisService board,PreferenceService preferences){this.excel=excel;this.board=board;this.preferences=preferences;}
    public Download create(ProjectStatusInput.Export input,HttpServletRequest request) {
        var specification=new Specification("프로젝트현황",null,List.of(new Column("projectName","프로젝트",CellType.TEXT)),input.query().filters().toString(),input.unit()==null?"천원":input.unit(),"UI-31");
        return excel.generateWorkbook(specification,()->workbook(input),request);
    }
    private WorkbookData workbook(ProjectStatusInput.Export input) {
        List<String> order=ProjectStatusInput.COLUMNS,hidden=Collections.emptyList();String unit="천원";
        for(var preference:preferences.getPreferences().items()) {
            if("COLS".equals(preference.type())&&"UI-31".equals(preference.key())) {
                JsonNode view=preference.value().path("statusBoard");
                if(view.isObject()) {
                    order=ProjectStatusInput.columns(csv(view.path("order")),false);
                    hidden=ProjectStatusInput.columns(csv(view.path("hidden")),true);
                }
            }
            if("AMT_UNIT".equals(preference.type())&&preference.value().isTextual())unit=preference.value().asText();
        }
        if(input.order()!=null)order=input.order();if(input.hidden()!=null)hidden=input.hidden();if(input.unit()!=null)unit=input.unit();
        if(!Set.of("원","천원","백만원").contains(unit))throw com.sicost.common.validation.ProjectInputPolicy.invalid("unit");
        Set<String> excluded=new HashSet<>(hidden);
        var columns=order.stream().filter(id->!excluded.contains(id)).map(id->new Column(id,title(id),numeric(id)?CellType.NUMBER:CellType.TEXT)).toList();
        var data=board.getProjectStatusBoard(input.query());
        var sheet=new Sheet("프로젝트현황",columns,sink->{for(var row:data.items())sink.accept(columns.stream().map(c->cell(row,c.id())).toList());});
        return new WorkbookData(List.of(sheet),List.of(new Information("기준일",data.baseDate()),new Information("원가 기준월",data.currentMonth()),new Information("단위",unit),
            new Information("미확인수",data.summary().get("unknownCostProjectCount")),new Information("열순서",String.join(",",order)),new Information("숨김열",String.join(",",hidden))),data.total(),data.baseDate().toString().replace("-",""));
    }
    private static String csv(JsonNode value){var strings=new ArrayList<String>();value.forEach(n->strings.add(n.asText()));return String.join(",",strings);}
    private static boolean numeric(String id){return Set.of("planProgress","actualProgress","costUsage","costProgressGap","openIssueCount","overdueIssueCount","elapsedDays").contains(id);}
    private static Object cell(Map<String,Object> row,String id) {
        Object value=row.get(id);if(value==null)return null;
        if(numeric(id))return new BigDecimal(value.toString());
        if("currentStep".equals(id))return ((Map<?,?>)value).get("stepName");
        if("pm".equals(id))return ((List<?>)value).stream().map(a->a instanceof com.sicost.apiops.ProjectAnalysisDtos.Actor actor?actor.userName():((Map<?,?>)a).get("userName").toString()).collect(java.util.stream.Collectors.joining(", "));
        return value.toString();
    }
    private static String title(String id){return switch(id){case "light"->"신호등";case "projectName"->"프로젝트";case "pm"->"PM";case "currentStep"->"현재 단계";case "planProgress"->"계획 진척률(%)";case "actualProgress"->"실적 진척률(%)";case "costUsage"->"원가 사용률(%)";case "costProgressGap"->"진척 대비 원가(%p)";case "openIssueCount"->"열린 이슈";case "overdueIssueCount"->"기한 지난 이슈";case "lastUpdatedDate"->"마지막 업데이트";default->"경과일";};}
}
