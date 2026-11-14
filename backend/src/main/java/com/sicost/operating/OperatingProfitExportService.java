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
        Sheet summary=new Sheet("요약",List.of(new Column("metric","항목",CellType.TEXT),new Column("amount","금액",CellType.NUMBER),new Column("rate","이익률 (%)",CellType.NUMBER)),sink->{
            for(var card:data.summary())sink.accept(Arrays.asList(card.metric(),number(card.amount(),false,unit),number(card.rate(),true,unit)));
        });
        List<Information> info=new ArrayList<>();info.add(new Information("조회 기준월",data.context().month()));info.add(new Information("기간단위","YTD"));info.add(new Information("단위",unit));info.add(new Information("조회시각",data.context().snapshotAt().toString()));
        data.notices().forEach(n->info.add(new Information("미확인·이관 범위",n)));
        data.months().stream().filter(m->m.month().compareTo(data.context().month())<=0).forEach(m->{
            info.add(new Information(m.month(),status(m.sourceKind())+" · 자료 "+m.completeness().availableMonthCount()+"/1개월"));
            m.completeness().estimated().forEach(reason->info.add(new Information("예정 포함",m.month()+" "+reason)));
            m.completeness().issues().forEach(i->info.add(new Information("미확인 원천",i.month()+" "+i.metric()+" "+i.reason())));
        });
        return new WorkbookData(List.of(statement,summary),info,data.rows().size(),data.context().month().replace("-",""));
    }
    private BigDecimal number(String value,boolean rate,String unit) {return value==null?null:new BigDecimal(value).movePointLeft(rate||"원".equals(unit)?0:"천원".equals(unit)?3:6);}
    private String status(String kind) {return switch(kind){case "MIGRATED"->"이관";case "CLOSED_SNAPSHOT"->"확정";case "CALCULATED"->"작업중";case "FUTURE"->"–";default->"자료 미확인";};}
}
