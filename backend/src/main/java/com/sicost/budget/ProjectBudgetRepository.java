package com.sicost.budget;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
import static com.sicost.budget.ProjectBudgetDtos.*;

@Repository
public class ProjectBudgetRepository {
    private final ProjectBudgetMapper mapper;
    public ProjectBudgetRepository(ProjectBudgetMapper mapper) { this.mapper=mapper; }
    public Stored required(String id) {
        Stored stored=mapper.find(id);
        if (stored==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return stored;
    }
    public String snapshot(String id) {
        String snapshot=mapper.snapshot(id);
        if (snapshot==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return snapshot;
    }
    public BigDecimal operatingValue(String key,String month) { return mapper.operatingValue(key,month); }
    public int greatestHistorySn(String id) { return mapper.greatestHistorySn(id); }
    public void update(Mutation mutation,BudgetHistory history) {
        one(mapper.update(mutation));one(mapper.insertHistory(history));
    }
    private void one(int count) {
        if (count!=1) throw new IllegalStateException("프로젝트 예산과 이력을 저장하지 못했습니다.");
    }
}
