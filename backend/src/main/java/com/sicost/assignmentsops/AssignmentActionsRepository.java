package com.sicost.assignmentsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.concurrent.RevisionService;
import org.springframework.stereotype.Repository;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;

@Repository
public class AssignmentActionsRepository {
    private final AssignmentActionsMapper mapper;
    private final AssignmentMonthlyService months;
    private final RevisionService revisions;
    public AssignmentActionsRepository(AssignmentActionsMapper mapper,AssignmentMonthlyService months,RevisionService revisions) {
        this.mapper=mapper;this.months=months;this.revisions=revisions;
    }
    public AssignmentActionsMapper mapper() {return mapper;}
    public Source required(String id) {return months.required(id);}
    public Project project(String id) {Project value=mapper.project(id);if(value==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return value;}
    public Developer developer(String id) {Developer value=mapper.developer(id);if(value==null)throw new ApiException(404,"NOT_FOUND","인력을 찾을 수 없습니다.");return value;}
    public String revision(String id) {required(id);return revisions.revision(mapper.snapshot(id));}
    public void one(int count) {if(count!=1)throw new IllegalStateException("투입 배정과 필수 이력을 저장하지 못했습니다.");}
}
