package com.sicost.apiops;

import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyMapper;
import com.sicost.profitability.MonthlyCostRepository;
import com.sicost.profitability.MonthlyCostService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

@Repository
public class ProjectAnalysisRepository {
    private final ProjectAnalysisMapper mapper;
    private final MonthlyCostRepository source;
    private final MonthlyCostService costs;
    private final AssignmentMonthlyService monthly;
    private final AssignmentMonthlyMapper assignments;
    public ProjectAnalysisRepository(ProjectAnalysisMapper mapper, MonthlyCostRepository source,
        MonthlyCostService costs, AssignmentMonthlyService monthly, AssignmentMonthlyMapper assignments) {
        this.mapper=mapper;this.source=source;this.costs=costs;this.monthly=monthly;this.assignments=assignments;
    }
    public Snapshot load(YearMonth month) {
        var sources=source.loadRange(month);
        var future=new ArrayList<ForecastMonth>();
        for (String id:mapper.assignmentIds()) {
            var assignment=assignments.source(id);
            var project=sources.projects().stream().filter(p->p.projectId().equals(assignment.projectId())).findFirst().orElse(null);
            if(project==null || !"TK".equals(project.contractType()) || !"ING".equals(project.status())) continue;
            var end=assignment.endDate().isAfter(project.toDate())?project.toDate():assignment.endDate();
            if(end.isBefore(assignment.startDate())) continue;
            var bounded=assignment.changed(end,assignment.contractMm(),assignment.price(),assignment.inputType(),
                assignment.extensionPossibility(),assignment.extensionCount(),assignment.earlyWithdrawal(),assignment.status());
            for(var value:monthly.calculate(bounded,assignments.histories(id),project.status(),project.contractType()))
                if(YearMonth.parse(value.month()).isAfter(month)) future.add(new ForecastMonth(project.projectId(),id,assignment.developerId(),value.month(),new BigDecimal(value.monthlyMm())));
        }
        return new Snapshot(sources,costs.calculate(sources),mapper.evidence(),mapper.expenses(),mapper.stages(),
            mapper.memos(),mapper.issues(),mapper.managers(),List.copyOf(future),mapper.templates());
    }
}
