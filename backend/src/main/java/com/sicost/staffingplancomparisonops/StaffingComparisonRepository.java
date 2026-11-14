package com.sicost.staffingplancomparisonops;

import com.sicost.common.assignments.AssignmentMonthlyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.staffingplancomparisonops.StaffingComparisonDtos.*;

@Repository
public class StaffingComparisonRepository {
    private final StaffingComparisonMapper mapper;
    private final AssignmentMonthlyService monthly;
    public StaffingComparisonRepository(StaffingComparisonMapper mapper, AssignmentMonthlyService monthly) {
        this.mapper=mapper; this.monthly=monthly;
    }
    public YearMonth cashBase() { return monthly.cashBase(); }
    public List<Row> rows(Query query) {
        List<Row> rows=new ArrayList<>();
        for (Project project:mapper.projects(query)) {
            Map<String,BigDecimal> plans=new TreeMap<>(),actual=new TreeMap<>();
            YearMonth start=YearMonth.from(project.startDate()),end=YearMonth.from(project.endDate());
            long months=ChronoUnit.MONTHS.between(start,end)+1;
            if (months<=0) throw new IllegalStateException("프로젝트 계약기간 원천을 확인해 주세요.");
            YearMonth first=start.isBefore(YearMonth.of(query.year(),1))?YearMonth.of(query.year(),1):start;
            YearMonth last=end.isAfter(YearMonth.of(query.year(),12))?YearMonth.of(query.year(),12):end;
            long overlap=ChronoUnit.MONTHS.between(first,last)+1;
            for (Plan plan:mapper.plans(project.id()))
                plans.put(plan.jobCode(),plan.planMm().multiply(BigDecimal.valueOf(overlap)).divide(BigDecimal.valueOf(months),2,RoundingMode.HALF_UP));
            for (String id:mapper.assignmentIds(project.id())) {
                var source=monthly.required(id);
                for (var month:monthly.preview(source,monthly.mapper().histories(id),project.status(),project.contractType()))
                    if (YearMonth.parse(month.month()).getYear()==query.year()) actual.merge(source.jobCode(),new BigDecimal(month.monthlyMm()),BigDecimal::add);
            }
            Set<String> jobs=new TreeSet<>(plans.keySet());jobs.addAll(actual.keySet());
            for (String job:jobs) {
                BigDecimal plan=plans.getOrDefault(job,BigDecimal.ZERO).setScale(2),used=actual.getOrDefault(job,BigDecimal.ZERO).setScale(2);
                BigDecimal difference=used.subtract(plan);
                if (plan.signum()==0 && used.signum()==0) continue;
                boolean include=switch(query.difference()) {case "SHORT" -> difference.signum()<0;case "EXCESS" -> difference.signum()>0;case "MATCH" -> difference.signum()==0;default -> true;};
                if (include) rows.add(new Row(project.id(),project.name(),job,plan,used,difference));
            }
        }
        rows.sort(Comparator.comparing(Row::differenceMm).thenComparing(Row::projectName).thenComparing(Row::jobCode).thenComparing(Row::projectId));
        return List.copyOf(rows);
    }
}
