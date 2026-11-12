package com.sicost.plans;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.plans.ProjectPlanDtos.*;

@Service
public class ProjectPlanService {
    private final ProjectPlanRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ProjectPlanService(ProjectPlanRepository repository, WriteCoordinator writes, RevisionService revisions,
                              CurrentUserProvider users, ChangeHistoryWriter history, Clock clock) {
        this.repository = repository;
        this.writes = writes;
        this.revisions = revisions;
        this.users = users;
        this.history = history;
        this.clock = clock;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectPlansData getProjectPlans(String id) {
        ProjectPlanInput.id(id);
        users.currentUser();
        repository.required(id);
        return view(id, revisions.revision(repository.snapshot(id)));
    }
    @Transactional
    public SaveProjectPlansResult saveProjectPlans(String id, SaveProjectPlansRequest supplied) {
        ProjectPlanInput.id(id);
        SaveProjectPlansRequest input = ProjectPlanInput.validated(supplied);
        String actor = users.userId();
        var changes = new ArrayList<PlanChange>();
        var result = writes.write(List.of("PROJECT:" + id, "operating-standards:CASH_BASE_YM"), input.expectedRevision(),
            () -> repository.snapshot(id),
            () -> {
                var project = repository.required(id);
                ProjectInputPolicy.requireWritableYear(input.year(), repository.cashBase(), clock);
                if ("LOSE".equals(project.status()) || "END".equals(project.status())
                        || project.endDate().isBefore(ProjectInputPolicy.today(clock))) {
                    throw new ApiException(409, "INVALID_PROJECT_STATUS", "종료 또는 실주 프로젝트의 계획은 변경할 수 없습니다.");
                }
                var before = new HashMap<String, StoredPlan>();
                repository.plans(id).forEach(row -> before.put(row.jobCode(), row));
                var active = new HashSet<>(repository.activeJobs());
                for (int index = 0; index < input.items().size(); index++) {
                    PlanInput item = input.items().get(index);
                    StoredPlan row = before.get(item.jobCode());
                    if (row == null && !active.contains(item.jobCode())) throw ProjectInputPolicy.invalid("items[" + index + "].jobCode");
                    BigDecimal old = row == null ? null : row.planMm();
                    BigDecimal next = new BigDecimal(item.planMm());
                    if (old == null || old.compareTo(next) != 0) changes.add(new PlanChange(item.jobCode(), old, next));
                }
            },
            () -> !changes.isEmpty(),
            () -> {
                LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL);
                for (PlanChange change : changes) {
                    repository.save(new PlanMutation(id, change.jobCode(), change.after(), actor, now), change.before() != null);
                    history.append(new ChangeHistoryWriter.Change("TB_PJ_MNPW_PLAN", id + ":" + change.jobCode(),
                        change.before() == null ? "NEW" : "UPD", "PLAN_MM", ProjectInputPolicy.mmString(change.before()),
                        ProjectInputPolicy.mmString(change.after()), null));
                }
            });
        return new SaveProjectPlansResult(view(id, result.revision()), result.revision(), result.changed());
    }
    private ProjectPlansData view(String id, String revision) {
        List<StoredPlan> rows = repository.plans(id);
        BigDecimal plan = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        var items = new ArrayList<ProjectPlan>();
        for (StoredPlan row : rows) {
            BigDecimal planned = row.planMm() == null ? BigDecimal.ZERO : row.planMm();
            plan = plan.add(planned);
            actual = actual.add(row.actualMm());
            items.add(new ProjectPlan(row.jobCode(), row.jobName(), row.useYn(), ProjectInputPolicy.mmString(row.planMm()),
                ProjectInputPolicy.mmString(row.actualMm()), ProjectInputPolicy.mmString(row.actualMm().subtract(planned)), row.source()));
        }
        return new ProjectPlansData(List.copyOf(items), ProjectInputPolicy.mmString(plan), ProjectInputPolicy.mmString(actual),
            ProjectInputPolicy.mmString(actual.subtract(plan)), revision);
    }
}
