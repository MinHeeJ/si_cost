package com.sicost.history;

import com.sicost.assignmentsops.AssignmentActionsDtos.Filter;
import com.sicost.assignmentsops.AssignmentActionsDtos.CodeQuery;
import com.sicost.assignmentsops.AssignmentActionsRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.history.AssignmentHistoryDtos.*;

@Repository
public class AssignmentHistoryRepository {
    private final AssignmentHistoryMapper mapper;
    private final AssignmentActionsRepository assignments;
    private final AssignmentMonthlyService months;
    public AssignmentHistoryRepository(AssignmentHistoryMapper mapper,AssignmentActionsRepository assignments,AssignmentMonthlyService months) {
        this.mapper=mapper;this.assignments=assignments;this.months=months;
    }
    public AssignmentHistoryMapper mapper() {return mapper;}
    public AssignmentMonthlyService months() {return months;}
    public Source required(String id) {return assignments.required(id);}
    public String snapshot(String id) {required(id);return assignments.mapper().snapshot(id);}
    public void present(String id,int year) {
        if(!assignments.mapper().matchingIds(new Filter(year,null,null,null,null,true)).contains(id))throw ProjectInputPolicy.invalid("year");
    }
    public void reason(String code) {
        if(!assignments.mapper().active(new CodeQuery("ASGN_CHG_RSN",code)))throw ProjectInputPolicy.invalid("reasonCode");
    }
    public int serial(String id) {
        int max=assignments.mapper().serial(id);
        if(max>=99999)throw new ApiException(409,"STALE_WRITE","이력 번호가 소진되었습니다.","sn",Map.of("assignmentId",id));
        return max+1;
    }
    public Entry entry(String id,int sn) {
        Entry row=mapper.entry(id,sn);if(row==null)throw new ApiException(404,"NOT_FOUND","투입 변경 이력을 찾을 수 없습니다.");return row;
    }
    public void one(int count) {if(count!=1)throw new IllegalStateException("정정 이력을 저장하지 못했습니다.");}
}
