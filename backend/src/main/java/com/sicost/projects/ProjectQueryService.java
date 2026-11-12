package com.sicost.projects;

import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.distribution.ProjectDistributionService;
import com.sicost.plans.ProjectPlanService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.projects.ProjectDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectQueryService {
    private final ProjectRepository repository;
    private final ProjectDistributionService distribution;
    private final ProjectPlanService plans;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectQueryService(ProjectRepository repository,ProjectDistributionService distribution,ProjectPlanService plans,
                               RevisionService revisions,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.distribution=distribution;this.plans=plans;this.revisions=revisions;this.users=users;this.clock=clock;
    }
    public ProjectsPage getProjects(Filter filter) {
        users.currentUser();LocalDate today=ProjectInputPolicy.today(clock);
        String month=YearMonth.from(today).format(DateTimeFormatter.ofPattern("uuuuMM"));
        int year=ProjectInputPolicy.year(filter.year()==null?today.getYear():filter.year(),ProjectRules.cashBase(repository.defaults(month)),clock);
        Query query=new Query(filter,today,month,LocalDate.of(year,1,1),LocalDate.of(year,12,31),(long)(filter.page()-1)*filter.size());
        var items=repository.list(query).stream().map(row -> new ProjectListItem(row.id(),row.name(),row.contractType(),row.startDate(),row.endDate(),
            row.contractAmount(),row.status(),row.customerName(),row.personCount(),row.partName(),"TK".equals(row.contractType())?distribution.badge(row.id()):null)).toList();
        return new ProjectsPage(items,repository.count(query),repository.unassignedCount(query),filter.page(),filter.size(),year);
    }
    public ProjectDetail getProject(String id,boolean includeCancelled) {
        ProjectInput.id(id);users.currentUser();
        Project project=repository.required(id);
        String effective=ProjectRules.effectiveStatus(project,ProjectInputPolicy.today(clock));
        String month=YearMonth.from(ProjectInputPolicy.today(clock)).format(DateTimeFormatter.ofPattern("uuuuMM"));
        return new ProjectDetail(project,revisions.revision(repository.snapshot(id)),effective,"LOSE".equals(effective),
            repository.parts(id,month),repository.pm(id),plans.getProjectPlans(id).items(),repository.assignments(id,includeCancelled),repository.history(id),
            repository.actualMmTotal(id),repository.expenseTotal(id));
    }
}
