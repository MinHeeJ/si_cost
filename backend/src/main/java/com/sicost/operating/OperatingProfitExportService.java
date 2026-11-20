package com.sicost.operating;

import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;
import static com.sicost.operating.OperatingProfitDtos.*;

@Service
public class OperatingProfitExportService {
    private final OperatingProfitService service;
    private final ExcelExportService excel;
    public OperatingProfitExportService(OperatingProfitService service,ExcelExportService excel) {this.service=service;this.excel=excel;}
    public Download create(Query query,HttpServletRequest request) {
        String unit=switch(query.amountUnit()){case "WON"->"원";case "MILLION_WON"->"백만원";default->"천원";};
        return excel.generateWorkbook(new Specification("영업이익표",null,List.of(new Column("item","항목",CellType.TEXT)),query.month(),unit,"UI-08"),
            ()->workbook(service.getOperatingProfitLoss(query),unit),request);
    }
    WorkbookData workbook(Data data,String unit) {
        List<Column> columns=new ArrayList<>();columns.add(new Column("item","항목",CellType.TEXT));
        for(var month:data.months())columns.add(new Column(month.month(),month.month()+" · "+status(month.sourceKind()),CellType.NUMBER));
        columns.add(new Column("cumulative",data.cumulativeProvisional()?"누계 · 잠정":"누계",CellType.NUMBER));
        if(data.showPreviousYear()) {columns.add(new Column("previousYear","전년 누계",CellType.NUMBER));columns.add(new Column("change","전년 대비 (%/%p)",CellType.NUMBER));}
        Sheet statement=new Sheet("영업이익표",columns,sink->{
            for(var row:data.rows()) {
                List<Object> values=new ArrayList<>();values.add((row.depth()>0?"└":"")+row.label());
                row.monthlyValues().forEach(v->values.add(number(v,row.isRate(),unit)));
                values.add(number(row.cumulativeValue(),row.isRate(),unit));
                if(data.showPreviousYear()) {values.add(number(row.previousYearCumulativeValue(),row.isRate(),unit));var c=row.comparison();values.add(number(c==null?null:row.isRate()?c.percentagePointChange():c.changeRate(),true,unit));}
                sink.accept(values);
            }
        });
        Map<String, String> summaryLabels = Map.of(
            "salesAmt", "누계 매출",
            "grossPrftAmt", "누계 매출총이익",
            "oprtPrftAmt", "누계 영업이익",
            "currentMonthOperatingProfit", "기준월 영업이익"
        );
        Sheet summary = new Sheet("요약", List.of(
            new Column("metric", "항목", CellType.TEXT),
            new Column("amount", "금액", CellType.NUMBER),
            new Column("rate", "이익률(%)", CellType.NUMBER),
            new Column("comparisonBasis", "비교 기준", CellType.TEXT),
            new Column("comparisonValue", "비교 금액", CellType.NUMBER),
            new Column("changeRate", "증감률(%)", CellType.NUMBER)
        ), sink -> {
            for (var card : data.summary()) {
                boolean currentMonth = "currentMonthOperatingProfit".equals(card.metric());
                var comparison = currentMonth ? card.previous() : card.previousYear();
                sink.accept(Arrays.asList(
                    summaryLabels.get(card.metric()),
                    number(card.amount(), false, unit),
                    number(card.rate(), true, unit),
                    currentMonth ? "전월" : "전년 같은 기간",
                    number(comparison == null ? null : comparison.comparisonValue(), false, unit),
                    number(comparison == null ? null : comparison.changeRate(), true, unit)
                ));
            }
        });
        Map<String, String> displayNames = informationNames(data);
        List<Information> info=new ArrayList<>();info.add(new Information("조회 기준월",data.context().month()));info.add(new Information("기간단위","연 누계"));info.add(new Information("단위",unit));info.add(new Information("조회시각",data.context().snapshotAt().toString()));
        data.notices().forEach(n->info.add(new Information("미확인·이관 범위",readable(n, displayNames))));
        data.months().stream().filter(m->m.month().compareTo(data.context().month())<=0).forEach(m->{
            info.add(new Information(m.month(),status(m.sourceKind())+" · 자료 "+m.completeness().availableMonthCount()+"/1개월"));
            m.completeness().estimated().forEach(reason->info.add(new Information("예정 포함",m.month()+" "+readable(reason, displayNames))));
            m.completeness().issues().forEach(i->info.add(new Information("미확인 원천",i.month()+" "+readable(i.metric()+" "+i.reason(), displayNames))));
        });
        return new WorkbookData(List.of(statement,summary),info,data.rows().size(),data.context().month().replace("-",""));
    }
    private Map<String, String> informationNames(Data data) {
        Map<String, String> names = new HashMap<>();
        data.rows().forEach(row -> names.put(row.metric(), row.label()));
        names.putAll(Map.ofEntries(
            Map.entry("noprIncmAmt", "영업외수익"),
            Map.entry("noprExpsAmt", "영업외비용"),
            Map.entry("corpTaxAmt", "법인세(월할)"),
            Map.entry("netPrftAmt", "당기순이익"),
            Map.entry("grossProfitRate", "매출총이익률"),
            Map.entry("netProfitRate", "순이익률"),
            Map.entry("idleCostShare", "대기 원가 비중"),
            Map.entry("TB_PJ_TK_DSTRB", "턴키 배분표"),
            Map.entry("TB_CO_MON_COST", "월별 원가표"),
            Map.entry("TB_HR_MON_PAY", "월별 급여표"),
            Map.entry("TB_HR_DEV", "인력 정보"),
            Map.entry("TB_PJ_PROJ", "프로젝트 정보"),
            Map.entry("TB_PJ_ASGN", "투입 배정표"),
            Map.entry("TB_PJ_ASGN_MON", "월별 투입표"),
            Map.entry("TB_CO_MON_PL", "월별 손익표"),
            Map.entry("TB_BL_BILL", "청구 내역"),
            Map.entry("TB_FI_ITEM", "재무 입력 내역"),
            Map.entry("TB_CO_PREV_ADJ", "전월 조정 내역"),
            Map.entry("TB_PJ_DRCT_EXPS", "프로젝트 직접 경비"),
            Map.entry("TB_FI_CORP_TAX", "법인세 예상액")
        ));
        return names;
    }

    private String readable(String text, Map<String, String> names) {
        var tokens = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*").matcher(text);
        return tokens.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
            names.getOrDefault(match.group(), match.group().startsWith("TB_") ? "관련 원천" : match.group())
        ));
    }

    private BigDecimal number(String value,boolean rate,String unit) {return value==null?null:new BigDecimal(value).movePointLeft(rate||"원".equals(unit)?0:"천원".equals(unit)?3:6);}
    private String status(String kind) {return switch(kind){case "MIGRATED"->"이관";case "CLOSED_SNAPSHOT"->"확정";case "CALCULATED"->"작업중";case "FUTURE"->"–";default->"자료 미확인";};}
}
