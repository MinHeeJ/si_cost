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
        Sheet summary=new Sheet("요약",List.of(new Column("metric","항목",CellType.TEXT),new Column("amount","금액",CellType.NUMBER),new Column("rate","순이익률 (%)",CellType.NUMBER),new Column("previous","전월 대비 (%)",CellType.NUMBER)),sink->{
            for(var card:data.summary())sink.accept(Arrays.asList(card.metric().equals("netPrftAmt")?"누계 순이익":"기준월 순이익",number(card.amount(),false,unit),number(card.rate(),true,unit),number(card.previous()==null?null:card.previous().changeRate(),true,unit)));
        });
        Sheet contribution=new Sheet("기준월 기여도",List.of(new Column("type","계약 유형",CellType.TEXT),new Column("sales","기준월 매출",CellType.NUMBER),new Column("labor","배분 노동비",CellType.NUMBER),new Column("profit","기여이익",CellType.NUMBER),new Column("share","양수 기여도 (%)",CellType.NUMBER),new Column("state","상태",CellType.TEXT)),sink->{
            if(data.contributions().visible())for(var group:data.contributions().contractTypes())sink.accept(Arrays.asList(group.groupName(),number(group.salesAmt(),false,unit),number(group.allocatedLaborCostAmt(),false,unit),number(group.contributionAmt(),false,unit),number(group.positiveContributionShare(),true,unit),group.loss()?"적자":group.showBar()?"양수 기여":"비율 없음"));
        });
        List<Information> info=new ArrayList<>();info.add(new Information("조회 기준월",data.context().month()));info.add(new Information("기여도 기준월",data.contributionMonth()));info.add(new Information("기간단위","YTD"));info.add(new Information("단위",unit));info.add(new Information("조회시각",data.context().snapshotAt().toString()));
        data.notices().forEach(n->info.add(new Information("미확인·이관 범위",n)));
        if(data.contributions().reason()!=null)info.add(new Information("기여도 원천",data.contributions().reason()));
        data.months().stream().filter(m->m.month().compareTo(data.context().month())<=0).forEach(m->{
            info.add(new Information(m.month(),status(m.sourceKind())+" · 자료 "+m.completeness().availableMonthCount()+"/1개월"));
            m.completeness().estimated().forEach(reason->info.add(new Information("예정 포함",m.month()+" "+reason)));
            m.completeness().issues().forEach(i->info.add(new Information("미확인 원천",i.month()+" "+i.metric()+" "+i.reason())));
        });
        return new WorkbookData(List.of(statement,summary,contribution),info,data.rows().size(),data.context().month().replace("-",""));
    }
    private BigDecimal number(String value,boolean rate,String unit) {return value==null?null:new BigDecimal(value).movePointLeft(rate||"원".equals(unit)?0:"천원".equals(unit)?3:6);}
    private String status(String kind) {return switch(kind){case "MIGRATED"->"이관";case "CLOSED_SNAPSHOT"->"확정";case "CALCULATED"->"작업중";case "FUTURE"->"–";default->"자료 미확인";};}
}
