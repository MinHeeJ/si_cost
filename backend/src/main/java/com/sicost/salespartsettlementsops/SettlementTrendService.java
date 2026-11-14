package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.salespartsettlements.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SettlementTrendService {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cell(String month,String value,String employeeType,String source,List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Metric(String metric,List<Cell> months,String total,String remark) {}
    public record Person(String developerId,String name,String managementNo,List<Metric> metrics) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthTotal(String month,String personProfitShareAmount,String cardAmount,String partProfitAmount,List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Trend(String partId,String month,List<String> months,List<Person> items,Page pagination,
        List<MonthTotal> monthlyTotals,String periodPartProfitAmount,List<Issue> issues) {}
    private final SalesPartSettlementService reads;
    public SettlementTrendService(SalesPartSettlementService reads) {this.reads=reads;}
    public Trend get(String partId,SalesPartSettlementInput.Query query) {
        var tabs=reads.listEligibleParts(query.month());
        YearMonth selected=YearMonth.parse(tabs.month()),base=YearMonth.parse(tabs.baseMonth());
        List<String> months=new ArrayList<>();Map<String,Result> results=new LinkedHashMap<>();
        Map<String,Row> union=new HashMap<>();List<MonthTotal> totals=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        for(YearMonth ym=YearMonth.of(selected.getYear(),1);!ym.isAfter(selected);ym=ym.plusMonths(1)) {
            String month=ym.toString();months.add(month);
            if(ym.isBefore(base)) {totals.add(new MonthTotal(month,null,null,null,List.of()));continue;}
            Result result=reads.previewSettlement(partId,month);results.put(month,result);issues.addAll(result.issues());
            result.items().stream().filter(r->r.mm()!=null&&new BigDecimal(r.mm()).signum()>0).forEach(r->union.put(r.developerId(),r));
            totals.add(new MonthTotal(month,result.totals().personProfitShareAmount(),result.cardAmount(),result.totals().partProfitAmount(),result.issues()));
        }
        List<Person> all=new ArrayList<>();
        for(Row identity:union.values().stream().sorted(Comparator.comparing(Row::name).thenComparing(Row::developerId)).toList()) {
            List<Metric> metrics=new ArrayList<>();
            for(String metric:List.of("MM","UNIT_PRICE","COST","PROFIT","PROFIT_SHARE")) {
                List<Cell> cells=new ArrayList<>();BigDecimal sum=BigDecimal.ZERO;boolean unknown=false;List<String> problems=new ArrayList<>();
                for(String month:months) {
                    Result result=results.get(month);
                    Row row=result==null?null:result.items().stream().filter(r->r.developerId().equals(identity.developerId())).findFirst().orElse(null);
                    boolean valid=row!=null&&row.mm()!=null&&new BigDecimal(row.mm()).signum()>0;
                    String value=valid?value(row,metric):null;
                    boolean missing=result!=null&&((valid&&value==null)||(!valid&&result.issues().stream()
                        .anyMatch(issue->issue.blocking()&&(issue.developerId()==null||issue.developerId().equals(identity.developerId())))));
                    String source=result==null?"BEFORE_BASE":missing?"UNKNOWN":!valid?"NO_INPUT":result.status().equals("WORK")?"CALCULATED":result.status();
                    List<Issue> cellIssues=row==null?result==null?List.of():result.issues():row.issues();
                    cells.add(new Cell(month,value,row==null?null:row.employeeType(),source,cellIssues));
                    if(missing) {unknown=true;problems.add(month);} else if(value!=null)sum=sum.add(new BigDecimal(value));
                }
                metrics.add(new Metric(metric,List.copyOf(cells),unknown?null:sum.toPlainString(),problems.isEmpty()?null:"미확인 월: "+String.join(", ",problems)));
            }
            all.add(new Person(identity.developerId(),identity.name(),identity.managementNo(),List.copyOf(metrics)));
        }
        BigDecimal period=BigDecimal.ZERO;boolean unknown=false;
        for(MonthTotal total:totals)if(results.containsKey(total.month())) {if(total.partProfitAmount()==null)unknown=true;else period=period.add(new BigDecimal(total.partProfitAmount()));}
        long from=(long)(query.page()-1)*query.size();
        List<Person> page=from>=all.size()?List.of():all.subList((int)from,(int)Math.min(from+query.size(),all.size()));
        return new Trend(partId,tabs.month(),List.copyOf(months),List.copyOf(page),new Page(query.page(),query.size(),all.size()),List.copyOf(totals),unknown?null:period.toPlainString(),List.copyOf(issues));
    }
    private static String value(Row row,String metric) {return switch(metric) {case "MM"->row.mm();case "UNIT_PRICE"->row.unitPrice();case "COST"->row.costAmount();case "PROFIT"->row.profitAmount();default->row.profitShareAmount();};}
}
