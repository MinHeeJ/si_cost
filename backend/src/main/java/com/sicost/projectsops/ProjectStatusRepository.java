package com.sicost.projectsops;

import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.projectsops.ProjectStatusDtos.*;

@Repository
public class ProjectStatusRepository {
    final ProjectStatusMapper mapper;
    private final ProjectBudgetRepository projects;
    private final SharedQueriesMapper shared;
    public ProjectStatusRepository(ProjectStatusMapper mapper,ProjectBudgetRepository projects,SharedQueriesMapper shared) {
        this.mapper=mapper;this.projects=projects;this.shared=shared;
    }
    public Project required(String id) {
        Project p=mapper.project(id);if(p==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return p;
    }
    public String snapshot(String id) {return projects.snapshot(id);}
    public BigDecimal rate(String month) {return projects.operatingValue("TGT_MRGN_RT",month);}
    public int serial(String id) {return projects.greatestHistorySn(id);}
    public YearMonth cashBase() {
        String month=shared.cashBaseMonth();
        if(month==null)throw new IllegalStateException("운영 시작월이 없습니다.");return YearMonth.parse(DatabaseMonthCodec.toApi(month));
    }
    public boolean protectedMonth(YearMonth month) {
        if(month.isBefore(cashBase()))return true;
        String db=DatabaseMonthCodec.toDatabase(month.toString());
        return shared.monthCloses(db,db).stream().anyMatch(c->!"WORK".equals(c.status()));
    }
    public void one(int count) {if(count!=1)throw new IllegalStateException("프로젝트와 필수 이력을 저장하지 못했습니다.");}
}
