package com.sicost.expenses;

import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import com.sicost.shared.SharedQueriesMapper;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.expenses.ProjectExpenseDtos.*;

@Repository
public class ProjectExpenseRepository {
    private final ProjectExpenseMapper mapper;
    private final ProjectBudgetRepository projects;
    private final SharedQueriesMapper shared;
    public ProjectExpenseRepository(ProjectExpenseMapper mapper,ProjectBudgetRepository projects,SharedQueriesMapper shared) {
        this.mapper=mapper;this.projects=projects;this.shared=shared;
    }
    public String cashBaseMonth() { return shared.cashBaseMonth(); }
    public boolean closed(String month) { return shared.monthCloses(month,month).stream().anyMatch(row->"CLOSE".equals(row.status())); }
    public String snapshot(String id) { return projects.snapshot(id); }
    public ProjectState required(String id) {
        ProjectState state=mapper.project(id);
        if (state==null) throw missing();
        return state;
    }
    public List<Stored> list(String id) { return mapper.list(id); }
    public Stored required(ExpenseKey key) {
        Stored row=mapper.find(key);
        if (row==null) throw missing();
        return row;
    }
    public boolean activeItem(String code) { return mapper.activeItem(code); }
    public int greatestSerial(String id,String month) { return mapper.greatestSerial(id,month); }
    public void lockClose() { mapper.lockClose(); }
    public void insert(ExpenseWrite write) { one(mapper.insert(write)); }
    public void delete(ExpenseKey key) { one(mapper.delete(key)); }
    private void one(int count) { if (count!=1) throw new IllegalStateException("경비를 저장하지 못했습니다."); }
    private ApiException missing() { return new ApiException(404,"NOT_FOUND","프로젝트 또는 직접 경비를 찾을 수 없습니다."); }
}
