package com.sicost.net;

import com.sicost.common.excel.ExcelExportService;
import com.sicost.operating.OperatingProfitDtos.Query;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class NetProfitExportService {
    private final NetProfitService service;
    private final ExcelExportService excel;
    public NetProfitExportService(NetProfitService service,ExcelExportService excel) {this.service=service;this.excel=excel;}
    public Download create(Query query,HttpServletRequest request) {
        String unit=switch(query.amountUnit()){case "WON"->"원";case "MILLION_WON"->"백만원";default->"천원";};
        return excel.generateWorkbook(new Specification("순이익표",null,List.of(new Column("item","항목",CellType.TEXT)),query.month(),unit,"UI-09"),
            ()->workbook(service.getNetProfitLoss(query),unit),request);
    }
    WorkbookData workbook(NetProfitDtos.Data data,String unit) {
        List<Column> columns=new ArrayList<>();columns.add(new Column("item","항목",CellType.TEXT));
        for(var month:data.months())columns.add(new Column(month.month(),month.month()+" · "+status(month.sourceKind()),CellType.NUMBER));
        columns.add(new Column("cumulative",data.cumulativeProvisional()?"누계 · 잠정":"누계",CellType.NUMBER));
        if(data.showPreviousYear()) {columns.add(new Column("previousYear","전년 누계",CellType.NUMBER));columns.add(new Column("change","전년 대비 (%/%p)",CellType.NUMBER));}
        Sheet statement=new Sheet("순이익표",columns,sink->{
            for(var row:data.rows()) {
                List<Object> values=new ArrayList<>();values.add((row.depth()>0?"└":"")+row.label());
                row.monthlyValues().forEach(v->values.add(number(v,row.isRate(),unit)));
                values.add(number(row.cumulativeValue(),row.isRate(),unit));
                if(data.showPreviousYear()) {values.add(number(row.previousYearCumulativeValue(),row.isRate(),unit));var c=row.comparison();values.add(number(c==null?null:row.isRate()?c.percentagePointChange():c.changeRate(),true,unit));}
                sink.accept(values);
            }
        });
        Sheet summary=new Sheet("요약",List.of(
            new Column("metric","항목",CellType.TEXT),
            new Column("amount","금액",CellType.NUMBER),
            new Column("rate","순이익률(%)",CellType.NUMBER),
            new Column("comparisonBasis","비교 기준",CellType.TEXT),
            new Column("comparisonValue","비교 금액",CellType.NUMBER),
            new Column("changeRate","증감률(%)",CellType.NUMBER)
        ),sink->{
            for(var card:data.summary()) {
                boolean currentMonth="currentMonthNetProfit".equals(card.metric());
                var comparison=currentMonth?card.previous():card.previousYear();
                sink.accept(Arrays.asList(
                    currentMonth?"기준월 순이익":"누계 당기순이익",
                    number(card.amount(),false,unit),number(card.rate(),true,unit),
                    currentMonth?"전월":"전년 같은 기간",
                    number(comparison==null?null:comparison.comparisonValue(),false,unit),
                    number(comparison==null?null:comparison.changeRate(),true,unit)
                ));
            }
        });
        Sheet contribution=new Sheet("기준월 기여도",List.of(
            new Column("type","계약 유형",CellType.TEXT),
            new Column("sales","기준월 매출",CellType.NUMBER),
            new Column("labor","배분 원가",CellType.NUMBER),
            new Column("profit","유형별 매출총이익",CellType.NUMBER),
            new Column("share","기여도(%)",CellType.NUMBER),
            new Column("state","상태",CellType.TEXT)
        ),sink->{
            if(data.contributions().visible())for(var group:data.contributions().contractTypes())
                sink.accept(Arrays.asList(
                    group.groupName(),number(group.salesAmt(),false,unit),
                    number(group.allocatedLaborCostAmt(),false,unit),number(group.contributionAmt(),false,unit),
                    number(group.positiveContributionShare(),true,unit),
                    group.loss()?"적자":group.showBar()?"흑자":"비율 없음"
                ));
        });
        Map<String,String> displayNames=informationNames(data);
        List<Information> info=new ArrayList<>();info.add(new Information("조회 기준월",data.context().month()));info.add(new Information("기여도 기준월",data.contributionMonth()));info.add(new Information("기간단위","연 누계"));info.add(new Information("단위",unit));info.add(new Information("조회시각",data.context().snapshotAt().toString()));
        data.notices().forEach(n->info.add(new Information("미확인·이관 범위",readable(n,displayNames))));
        if(data.contributions().reason()!=null)info.add(new Information("기여도 원천",readable(data.contributions().reason(),displayNames)));
        data.months().stream().filter(m->m.month().compareTo(data.context().month())<=0).forEach(m->{
            info.add(new Information(m.month(),status(m.sourceKind())+" · 자료 "+m.completeness().availableMonthCount()+"/1개월"));
            m.completeness().estimated().forEach(reason->info.add(new Information("예정 포함",m.month()+" "+readable(reason,displayNames))));
            m.completeness().issues().forEach(i->info.add(new Information("미확인 원천",i.month()+" "+readable(i.metric()+" "+i.reason(),displayNames))));
        });
        return new WorkbookData(List.of(statement,summary,contribution),info,data.rows().size(),data.context().month().replace("-",""));
    }
    private Map<String,String> informationNames(NetProfitDtos.Data data) {
        Map<String,String> names=new HashMap<>(com.sicost.structure.ProfitStructureService.LABELS);
        data.rows().forEach(row->names.put(row.metric(),row.label()));
        names.putAll(Map.ofEntries(
            Map.entry("laborAllocated","배분 원가"),
            Map.entry("allocatedLaborCostAmt","배분 원가"),
            Map.entry("directExpense","직접 경비"),
            Map.entry("tkSales","턴키 매출"),
            Map.entry("directExtra","프로젝트 직접 경비"),
            Map.entry("rndExtra","연구개발 직접 경비"),
            Map.entry("TB_PJ_TK_DSTRB","턴키 배분표"),
            Map.entry("TB_CO_MON_COST","월별 원가표"),
            Map.entry("TB_HR_MON_PAY","월별 급여표"),
            Map.entry("TB_HR_DEV","인력 정보"),
            Map.entry("TB_PJ_PROJ","프로젝트 정보"),
            Map.entry("TB_PJ_ASGN","투입 배정표"),
            Map.entry("TB_PJ_ASGN_MON","월별 투입표"),
            Map.entry("TB_CO_MON_PL","월별 손익표"),
            Map.entry("TB_BL_BILL","청구 내역"),
            Map.entry("TB_FI_ITEM","재무 입력 내역"),
            Map.entry("TB_CO_PREV_ADJ","전월 조정 내역"),
            Map.entry("TB_PJ_DRCT_EXPS","프로젝트 직접 경비"),
            Map.entry("TB_FI_CORP_TAX","법인세 예상액")
        ));
        return names;
    }
    private String readable(String text,Map<String,String> names) {
        var tokens=java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*").matcher(text);
        return tokens.replaceAll(match->java.util.regex.Matcher.quoteReplacement(
            names.getOrDefault(match.group(),match.group().startsWith("TB_")?"관련 원천":match.group())
        ));
    }
    private BigDecimal number(String value,boolean rate,String unit) {return value==null?null:new BigDecimal(value).movePointLeft(rate||"원".equals(unit)?0:"천원".equals(unit)?3:6);}
    private String status(String kind) {return switch(kind){case "MIGRATED"->"이관";case "CLOSED_SNAPSHOT"->"확정";case "CALCULATED"->"작업중";case "FUTURE"->"–";default->"자료 미확인";};}
}
