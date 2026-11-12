package com.sicost.projects;

import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.projects.ProjectDtos.*;

@Repository
public class ProjectRepository {
    private final ProjectMapper mapper;
    private final ProjectBudgetRepository aggregate;
    public ProjectRepository(ProjectMapper mapper, ProjectBudgetRepository aggregate) { this.mapper=mapper; this.aggregate=aggregate; }
    public Project required(String id) {
        Project value=mapper.find(id);
        if (value==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return value;
    }
    public String snapshot(String id) { return aggregate.snapshot(id); }
    public List<ListRow> list(Query query) { return mapper.list(query); }
    public long count(Query query) { return mapper.count(query); }
    public long unassignedCount(Query query) { return mapper.unassignedCount(query); }
    public DefaultsRow defaults(String month) { return mapper.defaults(month); }
    public Reference customer(String id) { return mapper.customer(id); }
    public Reference part(String id) { return mapper.part(id); }
    public boolean activeType(String code) { return mapper.activeType(code); }
    public boolean hasBills(String id) { return mapper.hasBills(id); }
    public List<ProjectPart> parts(String id,String month) { return mapper.parts(id,month); }
    public List<ProjectPm> pm(String id) { return mapper.pm(id); }
    public List<ProjectAssignment> assignments(String id,boolean cancelled) { return mapper.assignments(id,cancelled); }
    public List<ProjectHistory> history(String id) { return mapper.history(id); }
    public String actualMmTotal(String id) { return mapper.actualMmTotal(id); }
    public String expenseTotal(String id) { return mapper.expenseTotal(id); }
    public List<PeriodUse> periodUses(PeriodCheck check) { return mapper.periodUses(check); }
    public List<String> protectedMonths(String id,String from,String to) { return mapper.protectedMonths(id,from,to); }
    public void lockClose() { mapper.lockClose(); }
    public int greatestHistorySn(String id) { return aggregate.greatestHistorySn(id); }
    public void update(Mutation mutation) { one(mapper.update(mutation)); }
    public void moveInitialPart(PartMove move) { one(mapper.moveInitialPart(move)); }
    public void insertHistory(HistoryWrite write) { one(mapper.insertHistory(write)); }
    private void one(int count) { if (count!=1) throw new IllegalStateException("프로젝트와 계약 이력을 저장하지 못했습니다."); }
}
