package com.sicost.structure;

import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;
import static com.sicost.structure.ProfitStructureDtos.*;

@Service
public class ProfitStructureExportService {
    private final ProfitStructureService service;
    private final ExcelExportService excel;
    public ProfitStructureExportService(ProfitStructureService service,ExcelExportService excel) {this.service=service;this.excel=excel;}
    public Download create(Query query,HttpServletRequest request) {
        String unit=switch(query.amountUnit()){case "WON"->"원";case "MILLION_WON"->"백만원";default->"천원";};
        return excel.generateWorkbook(new Specification("전체수익구조",null,List.of(new Column("metric","항목",CellType.TEXT)),query.month()+" "+query.aggregation(),unit,"UI-07"),()->workbook(service.getProfitLossStructure(query),unit),request);
    }
    WorkbookData workbook(Data data,String unit) {
        List<Column> columns=new ArrayList<>();columns.add(new Column("metric","항목",CellType.TEXT));
        data.buckets().forEach(b->columns.add(new Column(b.key(),b.key()+" · "+(b.provisional()?"잠정/작업중":"확정/이관")+" · 자료 "+b.completeness().availableMonthCount()+"/"+b.monthCount()+"개월",CellType.NUMBER)));
        List<Sheet> sheets=new ArrayList<>();
        sheets.add(new Sheet("기간별 손익·참고선",columns,sink->data.numericRows().forEach(r->{List<Object> values=new ArrayList<>();values.add(r.label());r.bucketValues().forEach(v->values.add(number(v,r.isRate(),unit)));sink.accept(values);})));
        for(String kind:List.of("CONTRACT_TYPE","CUSTOMER","SALES_PART")) {
            Map<String,Group> identities=new LinkedHashMap<>();data.buckets().forEach(b->groups(b.composition(),kind).forEach(g->identities.put(identity(g),g)));
            sheets.add(new Sheet(kind.equals("CONTRACT_TYPE")?"유형 구성":kind.equals("CUSTOMER")?"고객사 구성":"영업 파트 구성",columns,sink->{
                for(var entry:identities.entrySet())for(String metric:List.of("매출","비중","기여이익률")) {
                    List<Object> values=new ArrayList<>();values.add(entry.getValue().groupName()+" · "+metric);
                    for(var bucket:data.buckets()) {
                        Group group=groups(bucket.composition(),kind).stream().filter(g->identity(g).equals(entry.getKey())).findFirst().orElse(null);
                        String value=group==null?null:metric.equals("매출")?group.salesAmt():metric.equals("비중")?group.revenueShare():group.contributionRate();values.add(number(value,!metric.equals("매출"),unit));
                    }sink.accept(values);
                }
            }));
        }
        sheets.add(new Sheet("계산 방법",List.of(new Column("metric","항목",CellType.TEXT),new Column("sources","원천",CellType.TEXT),new Column("formula","계산식·VAT 제외·참고 구분",CellType.TEXT)),sink->data.calculationMethods().forEach(m->sink.accept(List.of(m.metric(),String.join(", ",m.sources()),m.formula()+" · 부가세 제외"+(m.referenceOnly()?" · 참고액":""))))));
        List<Information> information=new ArrayList<>();information.add(new Information("조회 기준월",data.context().month()));information.add(new Information("기간단위",data.context().aggregation()));information.add(new Information("단위",unit));information.add(new Information("조회시각",data.context().snapshotAt().toString()));
        data.notices().forEach(n->information.add(new Information("미확인·이관 범위",n)));
        information.add(new Information("안내","영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다"));
        return new WorkbookData(sheets,information,data.numericRows().size(),data.context().month().replace("-","")+"_"+data.context().aggregation());
    }
    private String identity(Group group) {return Objects.toString(group.groupId(),"UNASSIGNED");}
    private List<Group> groups(Composition c,String kind) {return !c.visible()?List.of():kind.equals("CONTRACT_TYPE")?c.contractTypes():kind.equals("CUSTOMER")?c.customers():c.salesParts();}
    private BigDecimal number(String value,boolean rate,String unit) {return value==null?null:new BigDecimal(value).movePointLeft(rate||"원".equals(unit)?0:"천원".equals(unit)?3:6);}
}
