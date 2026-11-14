package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.apiops.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.MasterDataValidator;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.salespartsettlements.*;
import com.sicost.salespartsettlementsops.SettlementTrendService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.common.excel.ExcelExportService.*;

/** Additive dispatch from the existing export endpoint; reads share the workbook snapshot. */
@Service
public class SettlementExportService {
    public static final List<String> RATE_COLUMNS=List.of("name","employeeType","projectName","renewalDate","contractMm","unitPrice","monthlyLaborCost","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain");
    private final ExcelExportService excel;
    private final SalesPartSettlementService settlements;
    private final SettlementTrendService trends;
    private final RateRenegotiationService rates;
    private final Clock clock;
    public SettlementExportService(ExcelExportService excel,SalesPartSettlementService settlements,SettlementTrendService trends,RateRenegotiationService rates,Clock clock) {
        this.excel=excel;this.settlements=settlements;this.trends=trends;this.rates=rates;this.clock=clock;
    }
    public static boolean handles(JsonNode body) {
        return body!=null&&body.path("view").isTextual()&&Set.of("SALES_PART_SETTLEMENTS","RATE_RENEGOTIATIONS").contains(body.path("view").asText());
    }
    public Download create(JsonNode body,HttpServletRequest request) {
        boolean rate=body.path("view").asText().equals("RATE_RENEGOTIATIONS");
        MasterDataExportInput.allow(body,rate?Set.of("view","filters","sort","direction","columns","amountUnit"):Set.of("view","filters","sort","direction","amountUnit"),"");
        String unit=text(body,"amountUnit","천원");
        if(!Set.of("원","천원","백만원").contains(unit))throw ProjectInputPolicy.invalid("amountUnit");
        JsonNode filters=body.get("filters");
        MasterDataExportInput.allow(filters,rate?Set.of("renewalWindow","marginBelow","employeeType","projectId","increaseRate"):Set.of("partId","month","mode"),"filters.");
        var query=new LinkedMultiValueMap<String,String>();
        filters.fields().forEachRemaining(e->{if(!e.getValue().isNull()) {
            if(!e.getValue().isTextual())throw ProjectInputPolicy.invalid("filters."+e.getKey());
            query.add(e.getKey(),e.getValue().asText());
        }});
        for(String key:List.of("sort","direction"))if(body.has(key))query.add(key,text(body,key,null));
        String kind=rate?"단가재협상":"영업파트이익금",screen=rate?"UI-32":"UI-19";
        var spec=new Specification(kind,null,List.of(new Column("name","이름",CellType.TEXT)),body.toString(),unit,screen);
        if(rate) {
            var parsed=RateRenegotiationInput.query(query);List<String> columns=columns(body.get("columns"));
            return excel.generateWorkbook(spec,()->rateWorkbook(rates.export(parsed),columns,unit),request);
        }
        String part=text(filters,"partId",null),month=text(filters,"month",null),mode=text(filters,"mode",null);
        ProjectInputPolicy.text(part,20,"partId");
        if(month==null||!Set.of("MONTH","TREND").contains(Objects.toString(mode,"")))throw ProjectInputPolicy.invalid(month==null?"month":"mode");
        query.remove("partId");query.remove("mode");
        var parsed=SalesPartSettlementInput.query(query,true);
        return excel.generateWorkbook(spec,()->settlementWorkbook(part,parsed,mode,unit),request);
    }
    private WorkbookData rateWorkbook(RateRenegotiationDtos.Result data,List<String> ids,String unit) {
        var columns=ids.stream().map(id->new Column(id,rateTitle(id),Set.of("name","employeeType","projectName","renewalDate").contains(id)?CellType.TEXT:CellType.NUMBER)).toList();
        List<List<Object>> values=new ArrayList<>();
        for(var r:data.items())values.add(ids.stream().map(id->rateValue(r,id,unit)).toList());
        List<Object> footer=new ArrayList<>(Collections.nCopies(ids.size(),null));
        footer.set(ids.indexOf("name"),"합계");if(ids.contains("annualGain"))footer.set(ids.indexOf("annualGain"),amount(data.summary().annualGain(),unit));values.add(footer);
        var summary=data.summary();
        var info=new ArrayList<Information>(List.of(new Information("기준월",data.baseMonth()),new Information("단위",unit),new Information("적용 인상률",data.appliedIncreaseRate()),new Information("대상 인원",summary.personCount()),new Information("배정 건수",summary.assignmentCount()),new Information("대상 평균 이익률",number(summary.weightedMarginRate())),new Information("가정 연 증가 합",amount(summary.annualGain(),unit)),new Information("목표 연 증가 합",amount(summary.targetAnnualGain(),unit)),new Information("미평가 건수",data.unevaluatedCount())));
        for(var r:data.items())info.add(new Information("배정 "+r.assignmentId()+" 계산 근거","기준월 MM "+r.monthlyMm()+" / 계약 MM "+r.contractMm()+(r.planned()?" / 예정 투입":"")));
        data.unevaluatedAssignments().forEach(i->info.add(new Information("미평가 "+i.assignmentId(),i.message())));
        data.issues().forEach(i->info.add(new Information("계산 근거",i.message())));
        String day=LocalDate.now(clock.withZone(com.sicost.common.TimeConfiguration.SEOUL)).format(DateTimeFormatter.BASIC_ISO_DATE);
        return new WorkbookData(List.of(sheet("단가 재협상",columns,values),methods(true)),info,summary.assignmentCount(),day,"단가재협상_"+day+".xlsx");
    }
    private WorkbookData settlementWorkbook(String part,SalesPartSettlementInput.Query query,String mode,String unit) {
        var data=settlements.exportSettlement(part,query);List<Sheet> sheets=new ArrayList<>();List<Information> info=new ArrayList<>();
        info.add(new Information("파트",part));info.add(new Information("기준월",data.month()));info.add(new Information("단위",unit));
        info.add(new Information("상태",data.status()));info.add(new Information("배분율",number(data.standard().distributionRate())));
        info.add(new Information("공통관리비",amount(data.standard().salesExpenseAmount(),unit)));
        info.add(new Information("법인카드",amount(data.cardAmount(),unit)));info.add(new Information("파트 이익금",amount(data.summary().partProfitAmount(),unit)));
        long people;
        if(mode.equals("MONTH")) {
            var columns=new ArrayList<Column>();
            for(String id:List.of("name","managementNo","employeeType","projects","mm","unitPrice","payAmount","salesExpenseAmount","retirementAmount","costAmount","profitAmount","profitShareAmount","remark"))columns.add(new Column(id,settlementTitle(id),Set.of("name","managementNo","employeeType","projects","remark").contains(id)?CellType.TEXT:CellType.NUMBER));
            List<List<Object>> rows=new ArrayList<>();
            for(var r:data.items()) {
                rows.add(Arrays.asList(r.name(),r.managementNo(),r.employeeType(),r.legacyProjectText()==null?String.join(" · ",r.projectNames()):r.legacyProjectText(),number(r.mm()),amount(r.unitPrice(),unit),amount(r.payAmount(),unit),amount(r.salesExpenseAmount(),unit),amount(r.retirementAmount(),unit),amount(r.costAmount(),unit),amount(r.profitAmount(),unit),amount(r.profitShareAmount(),unit),r.remark()));
                info.add(new Information(r.developerId()+" 급여 근거",payBasis(r.basis().paySource())));
                info.add(new Information(r.developerId()+" 시작일/일수",Objects.toString(r.basis().firstInputDate(),"–")+" / "+Objects.toString(r.basis().payableDays(),"–")+"/"+Objects.toString(r.basis().calendarDays(),"–")));
                info.add(new Information(r.developerId()+" 유효 기본급여",amount(r.basis().basePayAmount(),unit)));
                info.add(new Information(r.developerId()+" TK 근거",String.join(" · ",r.basis().tkSources())));
            }
            var t=data.totals();rows.add(Arrays.asList("합계",null,null,null,number(t.mm()),amount(t.unitPrice(),unit),amount(t.payAmount(),unit),amount(t.salesExpenseAmount(),unit),amount(t.retirementAmount(),unit),amount(t.costAmount(),unit),amount(t.profitAmount(),unit),amount(t.personProfitShareAmount(),unit),null));
            sheets.add(sheet("이번 달",columns,rows));people=data.pagination().total();
        } else {
            String baseMonth=settlements.listEligibleParts(data.month()).baseMonth();
            var trend=trends.get(part,new SalesPartSettlementInput.Query(data.month(),1,Integer.MAX_VALUE,"name","ASC"));
            var columns=new ArrayList<Column>(List.of(new Column("name","이름/관리번호",CellType.TEXT),new Column("metric","항목",CellType.TEXT)));
            trend.months().forEach(m->columns.add(new Column(m,m,CellType.NUMBER)));columns.add(new Column("total","합계",CellType.NUMBER));columns.add(new Column("remark","비고",CellType.TEXT));
            List<List<Object>> rows=new ArrayList<>();
            for(var person:trend.items())for(var metric:person.metrics()) {
                var row=new ArrayList<Object>();row.add(person.name()+" / "+person.managementNo());row.add(metric.metric());
                metric.months().forEach(c->row.add(metric.metric().equals("MM")?number(c.value()):amount(c.value(),unit)));
                row.add(metric.metric().equals("MM")?number(metric.total()):amount(metric.total(),unit));row.add(metric.remark());rows.add(row);
            }
            for(String metric:List.of("인력 이익금 합","법인카드","파트 이익금")) {
                var row=new ArrayList<Object>();row.add(metric);row.add(null);BigDecimal total=BigDecimal.ZERO;boolean known=true;
                for(var m:trend.monthlyTotals()) {String v=metric.equals("인력 이익금 합")?m.personProfitShareAmount():metric.equals("법인카드")?m.cardAmount():m.partProfitAmount();row.add(amount(v,unit));if(v!=null)total=total.add(new BigDecimal(v));else if(m.month().compareTo(baseMonth)>=0)known=false;}
                row.add(metric.equals("파트 이익금")?amount(trend.periodPartProfitAmount(),unit):known?MasterDataValidator.amountInUnit(total,unit):null);row.add(null);rows.add(row);
            }
            for(var m:trend.monthlyTotals())if(m.month().compareTo(baseMonth)>=0) {
                var monthly=settlements.previewSettlement(part,m.month());
                info.add(new Information(m.month()+" 상태",monthly.status()));
                monthly.issues().forEach(i->info.add(new Information(m.month()+" 근거",i.message())));
                for(var r:monthly.items())info.add(new Information(m.month()+" "+r.developerId()+" 구분/급여 근거",r.employeeType()+" / "+payBasis(r.basis().paySource())+" / "+r.remark()));
            }
            sheets.add(sheet("기간 추이",columns,rows));people=trend.pagination().total();
        }
        data.issues().forEach(i->info.add(new Information("계산 근거 "+Objects.toString(i.developerId(),""),i.message())));
        sheets.add(methods(false));String ym=data.month().replace("-","");
        return new WorkbookData(sheets,info,people,ym,"영업파트이익금_"+part+"_"+ym+"_"+(mode.equals("MONTH")?"이번달":"추이")+".xlsx");
    }
    private static Sheet sheet(String name,List<Column> columns,List<List<Object>> rows) {return new Sheet(name,columns,sink->rows.forEach(sink));}
    private static Sheet methods(boolean rate) {
        List<List<Object>> rows=rate?List.of(List.of("현재 이익률","(기준월 매출R−노동비C)/R×100, 소수1자리 HALF_UP"),List.of("목표 단가","C/기준월MM m/(1−목표율/100), 만원 CEILING"),List.of("필요 인상률","max(0,(목표단가−현재단가U)/U×100)"),List.of("가정 연 증가","DOWN(U×가정율/100×계약MM k×12), 동일 계약MM 1년 가정"),List.of("목표 연 증가","DOWN(max(0,목표단가−U)×계약MM k×12)")):
            List.of(List.of("급여","첫 투입월 DOWN(유효 기본액×남은 달력일수/월일수), 나머지 월 업로드/유효 예정급여"),List.of("퇴직금","FT만 DOWN(파트 급여/12)"),List.of("비용 계","급여+월 공통관리비(OS는0)+퇴직금"),List.of("이익금","인력별 DOWN((단가−비용 계)×배분율/100), 음수도 0쪽 절사"),List.of("파트 이익금","인력 이익금 합−파트월 카드액 (월1회 차감)"));
        return sheet("계산 방법",List.of(new Column("metric","항목",CellType.TEXT),new Column("formula","산식·근거",CellType.TEXT)),rows);
    }
    private static String payBasis(String value) {return switch(Objects.toString(value,"UNKNOWN")) {
        case "START_MONTH_BASE"->"첫 투입월 유효 기본급여";case "ESTIMATED_BASE"->"급여 업로드 전·기본액";
        case "UPLOADED_PAY"->"업로드 월 급여";case "VENDOR_SUPPLY"->"업로드 외주 공급가액";
        case "START_MONTH_MISSING_ZERO"->"⚠ 월 급여 없음(시작 달 급여 0)";case "SNAPSHOT_UNKNOWN"->"확정 사본·당시 추가근거 미저장";
        default->"월 급여 미확인";
    };}
    private static String text(JsonNode body,String key,String fallback) {JsonNode v=body.get(key);if(v==null)return fallback;if(!v.isTextual())throw ProjectInputPolicy.invalid(key);return v.asText();}
    private static List<String> columns(JsonNode value) {
        if(value==null)throw ProjectInputPolicy.invalid("columns");
        if(!value.isArray())throw ProjectInputPolicy.invalid("columns");List<String> result=new ArrayList<>();
        for(var v:value) {if(!v.isTextual()||!RATE_COLUMNS.contains(v.asText())||result.contains(v.asText()))throw ProjectInputPolicy.invalid("columns");result.add(v.asText());}
        if(!result.containsAll(List.of("name","projectName")))throw ProjectInputPolicy.invalid("columns");return List.copyOf(result);
    }
    private static BigDecimal number(String v) {return v==null?null:new BigDecimal(v);}
    private static BigDecimal amount(String v,String unit) {return v==null?null:MasterDataValidator.amountInUnit(new BigDecimal(v),unit);}
    private static Object rateValue(RateRenegotiationDtos.Row r,String id,String unit) {return switch(id) {
        case "name"->r.name()+" / "+r.managementNo();case "employeeType"->r.employeeType();case "projectName"->r.projectName();case "renewalDate"->r.renewalDate();case "contractMm"->number(r.contractMm());
        case "unitPrice"->amount(r.unitPrice(),unit);case "monthlyLaborCost"->amount(r.monthlyLaborCost(),unit);case "marginRate"->number(r.marginRate());case "targetUnitPrice"->amount(r.targetUnitPrice(),unit);case "requiredIncreaseRate"->number(r.requiredIncreaseRate());default->amount(r.annualGain(),unit);
    };}
    private static String rateTitle(String id) {return switch(id) {case "name"->"이름/관리번호";case "employeeType"->"구분";case "projectName"->"프로젝트";case "renewalDate"->"갱신일";case "contractMm"->"계약 MM";case "unitPrice"->"현재 단가";case "monthlyLaborCost"->"월 노동비";case "marginRate"->"이익률";case "targetUnitPrice"->"목표 단가";case "requiredIncreaseRate"->"필요 인상률";default->"가정 연 증가";};}
    private static String settlementTitle(String id) {return switch(id) {case "name"->"이름";case "managementNo"->"관리번호";case "employeeType"->"구분";case "projects"->"프로젝트";case "mm"->"MM";case "unitPrice"->"단가";case "payAmount"->"급여";case "salesExpenseAmount"->"공통관리비";case "retirementAmount"->"퇴직금";case "costAmount"->"비용 계";case "profitAmount"->"이익";case "profitShareAmount"->"이익금";default->"비고";};}
}
