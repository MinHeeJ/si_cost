package com.sicost.parts;

import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.parts.ProjectPartDtos.*;

@Repository
public class ProjectPartRepository {
    private final ProjectPartMapper mapper;
    private final ProjectBudgetRepository aggregates;
    private final MasterLockMapper locks;
    public ProjectPartRepository(ProjectPartMapper mapper,ProjectBudgetRepository aggregates,MasterLockMapper locks) {
        this.mapper=mapper;this.aggregates=aggregates;this.locks=locks;
    }
    public ProjectSource required(String id) {
        ProjectSource row=mapper.find(id);
        if (row==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return row;
    }
    public String snapshot(String id) { return aggregates.snapshot(id); }
    public YearMonth cashBase() {
        var value=aggregates.operatingValue("CASH_BASE_YM","999912");
        if (value==null) throw new IllegalStateException("운영 시작월이 없습니다.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(value.toBigIntegerExact().toString()));
    }
    public List<PartSource> parts(String id) { return mapper.parts(id); }
    public List<String> protectedMonths() { return mapper.protectedMonths(); }
    public String lastClose() { return mapper.lastClose(); }
    public PartReference reference(String id) { return mapper.reference(id); }
    public int greatestHistorySn(String id) { return aggregates.greatestHistorySn(id); }
    public void lockClose() { locks.lockClose(); }
    public void delete(String id,String month) { one(mapper.deleteReservation(id,month)); }
    public void insert(PartInsert input) { one(mapper.insert(input)); }
    public void history(PartHistory input) { one(mapper.insertHistory(input)); }
    private void one(int count) { if (count!=1) throw new IllegalStateException("파트 변경과 이력을 저장하지 못했습니다."); }
}
