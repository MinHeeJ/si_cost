package com.sicost.progress;

import com.sicost.profitability.MonthlyCostService;
import com.sicost.profitability.MonthlyCostDtos;
import com.sicost.shared.SharedQueriesService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.progress.ProjectProgressDtos.*;

@Service
public class ProjectProgressCostSource {
    private final MonthlyCostService monthly;
    private final SharedQueriesService shared;
    private final ProjectProgressMapper mapper;
    public ProjectProgressCostSource(MonthlyCostService monthly,SharedQueriesService shared,ProjectProgressMapper mapper) {
        this.monthly=monthly;this.shared=shared;this.mapper=mapper;
    }
    public Cost load(ProjectRow project,LocalDate today) {
        YearMonth start=YearMonth.from(project.startDate()),end=YearMonth.from(today);
        YearMonth base=YearMonth.parse(shared.getPeriodContext().cashBaseMonth());
        YearMonth from=start.isBefore(base)?base:start;
        List<MissingReason> issues=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
        boolean complete=true,revenue=true,estimated=false;
        if(start.isBefore(base)) {complete=false;revenue=false;issues.add(new MissingReason("BEFORE_OPERATION","운영 시작 전 원가 자료는 확인할 수 없습니다.",base.toString(),null,null,"cumulativeCost"));}
        for(int year=from.getYear();year<=end.getYear() && !from.isAfter(end);year++) {
            var source=monthly.loadYear(year);var costs=monthly.calculate(source);
            Set<String> expected=new HashSet<>();
            for(var a:source.assignments())if(project.projectId().equals(a.projectId()) && inRange(a.month(),from,end))expected.add(a.month()+":"+a.personId()+":"+a.assignmentId());
            Set<String> actual=new HashSet<>();
            for(var person:costs.items()) {
                var rows=person.rows().stream().filter(r->project.projectId().equals(r.projectId())&&inRange(r.month(),from,end)).toList();
                boolean related=!rows.isEmpty() || expected.stream().anyMatch(k->k.startsWith(person.month()+":"+person.personId()+":"));
                if(!related)continue;
                complete &= person.completeness().costComplete() && person.completeness().allocationComplete();
                revenue &= person.completeness().revenueComplete();estimated |= person.completeness().estimated();
                for(var i:person.completeness().issues())issues.add(new MissingReason(i.source(),i.message(),apiMonth(i.month()),i.personId(),null,"cumulativeCost"));
                for(var r:rows) {
                    actual.add(r.month()+":"+r.personId()+":"+r.assignmentId());
                    complete &= r.costComplete() && r.laborAllocated()!=null && r.directExpense()!=null;
                    revenue &= r.revenueComplete();estimated |= r.estimated();
                    if(r.laborAllocated()!=null && r.directExpense()!=null)total=total.add(new BigDecimal(r.laborAllocated())).add(new BigDecimal(r.directExpense()));
                }
            }
            if(!actual.containsAll(expected)){complete=false;issues.add(new MissingReason("MISSING_COST","투입에 대응하는 월별 원가를 확인할 수 없습니다.",null,null,null,"cumulativeCost"));}
        }
        if(!from.isAfter(end))total=total.add(mapper.additionalExpense(project.projectId(),db(from),db(end)));
        var completeness=new Completeness(complete,revenue,estimated,List.copyOf(issues),complete?0:1,from.toString(),end.toString(),start.isBefore(base)?base.toString():null);
        return new Cost(complete?total:null,completeness);
    }
    private static boolean inRange(String value,YearMonth from,YearMonth to){return value.compareTo(db(from))>=0 && value.compareTo(db(to))<=0;}
    private static String db(YearMonth value){return value.toString().replace("-","");}
    private static String apiMonth(String value){return value==null?null:MonthlyCostDtos.parseMonth(value).toString();}
}
