package com.sicost.costanalysis;

import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.projects.ProjectDtos.Project;
import com.sicost.projects.ProjectRepository;
import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.profitability.MonthlyCostDtos.*;

@Repository
public class ProjectCostRepository {
    public record FutureMonth(String personId,String month,BigDecimal mm) {}
    public record Snapshot(Project project,Sources sources,MonthlyCostsData costs,List<ProjectCostMapper.Expense> expenses,
        BigDecimal planMm,BigDecimal actualProgress,List<FutureMonth> future,Map<String,String> jobs,List<Price> prices) {}
    private final MonthlyCostRepository sources;
    private final MonthlyCostService costs;
    private final ProjectRepository projects;
    private final ProjectCostMapper mapper;
    private final AssignmentMonthlyService assignments;
    public ProjectCostRepository(MonthlyCostRepository sources,MonthlyCostService costs,ProjectRepository projects,
        ProjectCostMapper mapper,AssignmentMonthlyService assignments) {
        this.sources=sources;this.costs=costs;this.projects=projects;this.mapper=mapper;this.assignments=assignments;
    }
    public Snapshot load(String id,String month) {
        Project p=projects.required(id);
        Sources s=sources.loadRange(month);
        List<FutureMonth> future=new ArrayList<>();
        for(String key:mapper.assignmentIds(id)) {
            var a=assignments.required(key);
            if(!"ACT".equals(a.status()))continue;

            var bounded=a.changed(a.endDate().isAfter(p.endDate())?p.endDate():a.endDate(),a.contractMm(),a.price(),a.inputType(),a.extensionPossibility(),a.extensionCount(),a.earlyWithdrawal(),a.status());
            if(bounded.endDate().isBefore(bounded.startDate()))continue;
            for(var m:assignments.calculate(bounded,assignments.mapper().histories(key),p.status(),p.contractType()))
                if(m.month().replace("-","").compareTo(s.period().toMonth())>0)
                    future.add(new FutureMonth(a.developerId(),m.month(),new BigDecimal(m.monthlyMm())));
        }
        Map<String,String> jobs=new HashMap<>();
        s.assignments().stream().filter(a->id.equals(a.projectId())).map(Assignment::jobCode).filter(Objects::nonNull).distinct()
            .forEach(code->jobs.put(code,Objects.toString(mapper.jobName(code),code)));
        return new Snapshot(p,s,costs.calculate(s),mapper.expenses(id),mapper.planMm(id),
            mapper.actualProgress(new ProjectCostMapper.ProgressQuery(id,parseMonth(s.period().toMonth()).atEndOfMonth())),List.copyOf(future),jobs,mapper.prices(id));
    }
}
