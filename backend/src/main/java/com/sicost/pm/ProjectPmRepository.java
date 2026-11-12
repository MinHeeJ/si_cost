package com.sicost.pm;

import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.pm.ProjectPmDtos.*;

@Repository
public class ProjectPmRepository {
    private final ProjectPmMapper mapper;
    private final ProjectBudgetRepository aggregates;
    public ProjectPmRepository(ProjectPmMapper mapper,ProjectBudgetRepository aggregates) {
        this.mapper=mapper;this.aggregates=aggregates;
    }
    public ProjectSource required(String id) {
        ProjectSource row=mapper.find(id);
        if (row==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return row;
    }
    public String snapshot(String id) { return aggregates.snapshot(id); }
    public List<Scope> scopes(String id) { return mapper.scopes(id); }
    public boolean eligible(String userId) { return mapper.eligible(userId); }
    public int greatestScopeSn(String userId) { return mapper.greatestScopeSn(userId); }
    public int greatestHistorySn(String id) { return aggregates.greatestHistorySn(id); }
    public void insert(ScopeWrite input) { one(mapper.insertScope(input)); }
    public void delete(ScopeWrite input) { one(mapper.deleteScope(input)); }
    public void history(PmHistory input) { one(mapper.insertHistory(input)); }
    private void one(int count) { if (count!=1) throw new IllegalStateException("PM 연결과 이력을 저장하지 못했습니다."); }
}
