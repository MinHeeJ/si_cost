package com.sicost.plans;

import com.sicost.budget.ProjectBudgetDtos;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.plans.ProjectPlanDtos.*;

@Repository
public class ProjectPlanRepository {
    private final ProjectPlanMapper mapper;
    private final ProjectBudgetRepository projects;
    public ProjectPlanRepository(ProjectPlanMapper mapper, ProjectBudgetRepository projects) {
        this.mapper = mapper;
        this.projects = projects;
    }
    public ProjectBudgetDtos.Stored required(String id) { return projects.required(id); }
    public String snapshot(String id) { return projects.snapshot(id); }
    public List<StoredPlan> plans(String id) { return mapper.plans(id); }
    public List<String> activeJobs() { return mapper.activeJobs(); }
    public YearMonth cashBase() {
        BigDecimal value = projects.operatingValue("CASH_BASE_YM", "999912");
        if (value == null) throw new IllegalStateException("운영 시작월이 없습니다.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(value.toBigIntegerExact().toString()));
    }
    public void save(PlanMutation mutation, boolean existing) {
        int count = existing ? mapper.update(mutation) : mapper.insert(mutation);
        if (count != 1) throw new IllegalStateException("인원 계획을 저장하지 못했습니다.");
    }
}
