package com.sicost.forecast;

import com.sicost.common.assignments.AssignmentMonthlyService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
import static com.sicost.forecast.SalesForecastSources.*;

@Repository
public class SalesForecastRepository {
    private final SalesForecastMapper mapper;
    private final AssignmentMonthlyService monthly;
    public SalesForecastRepository(SalesForecastMapper mapper, AssignmentMonthlyService monthly) {
        this.mapper = mapper; this.monthly = monthly;
    }
    public Snapshot load(YearMonth standardMonth) {
        var projects = mapper.projects();
        var byId = new LinkedHashMap<String, Project>();
        projects.forEach(p -> byId.put(p.id(), p));
        var assignments = new ArrayList<Assignment>();
        for (String id : mapper.assignmentIds()) {
            var source = monthly.required(id);
            var project = byId.get(source.projectId());
            if (project == null) throw new IllegalStateException("배정의 프로젝트 원천을 확인할 수 없습니다.");
            assignments.add(new Assignment(source, monthly.preview(source, monthly.mapper().histories(id), project.status(), project.type())));
        }
        var standards = new LinkedHashMap<String, BigDecimal>();
        mapper.standards(standardMonth.toString().replace("-", "")).forEach(s -> standards.put(s.code(), s.value()));
        return new Snapshot(projects, mapper.opportunities(), mapper.needs(), mapper.plans(), mapper.people(), mapper.leaves(),
            mapper.candidates(), standards, mapper.jobs(), assignments);
    }
}
