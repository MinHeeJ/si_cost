package com.sicost.withdrawalsops;

import com.sicost.apiops.*;
import com.sicost.assignmentsops.AssignmentActionsRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.WithdrawalDtos.*;

@Repository
public class NextPositionRepository {
    private final NextPositionMapper mapper;
    private final WithdrawalRepository withdrawals;
    private final AssignmentActionsRepository assignments;
    private final SettlementSourceLocks locks;
    private final ChangeHistoryWriter audit;
    private final com.sicost.common.assignments.AssignmentMonthlyService monthly;
    public NextPositionRepository(NextPositionMapper mapper,WithdrawalRepository withdrawals,AssignmentActionsRepository assignments,SettlementSourceLocks locks,ChangeHistoryWriter audit,com.sicost.common.assignments.AssignmentMonthlyService monthly){this.mapper=mapper;this.withdrawals=withdrawals;this.assignments=assignments;this.locks=locks;this.audit=audit;this.monthly=monthly;}
    public java.time.YearMonth cashBase(){return monthly.cashBase();}
    public Source lock(String id){
        Source before=required(id);Set<String> keys=new TreeSet<>(mapper.sourceKeys(before.developerId()));
        keys.addAll(List.of("ASGN:"+id,"PROJECT:"+before.projectId(),"DEV:"+before.developerId(),"RCMD_SOURCE:"+id,
            "code-group:JOB","code-group:GRADE","operating-standards:CASH_BASE_YM","operating-standards:SHORT_PERSON"));
        locks.lock(keys);mapper.lockOpportunities();mapper.lockNeeds();mapper.lockDeveloper(before.developerId());mapper.lockAssignment(id);
        Source row=required(id);if(!before.equals(row))throw new ApiException(409,"STALE_WRITE","철수 원천이 변경되었습니다. 다시 조회해 주세요.");return row;
    }
    public Source required(String id){Source row=mapper.source(id);if(row==null)throw new ApiException(404,"NOT_FOUND","철수 배정을 찾을 수 없습니다.");return row;}
    public void requireRevision(String id,String expected){
        if(!Objects.equals(expected,assignments.revision(id)))throw new ApiException(409,"STALE_WRITE","철수 원천이 변경되었습니다. 다시 조회하거나 다시 추천해 주세요.","expectedRevision",null);
    }
    public void requireActive(Source source){if(!"ACT".equals(assignments.required(source.assignmentId()).status()))throw new ApiException(409,"INVALID_STATE","취소 배정은 추천할 수 없습니다.");}
    public List<NextAssignment> next(Source source){return withdrawals.next(source);}
    public List<Leave> leaves(String id){return withdrawals.leaves(id);}
    public List<Position> positions(String id){return withdrawals.positions(id);}
    public String nextId(){
        String id;try{id=mapper.nextId();}catch(org.springframework.dao.DataAccessException e){throw exhausted();}
        if(id==null||!id.matches("[1-9][0-9]{0,11}"))throw exhausted();return id;
    }
    public void insert(com.sicost.workforcerecommendations.WorkforceRecommendationDtos.Row row,String sourceId){
        if(mapper.insert(row)!=1)throw new IllegalStateException("다음 자리 추천을 저장하지 못했습니다.");
        audit.appendRecommendation(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG",row.rcmdId()+":"+row.rank(),"NEW","sourceAssignmentId",null,sourceId,"철수 원천 배정"));
    }

    private ApiException exhausted(){return new ApiException(409,"ID_EXHAUSTED","추천 ID를 모두 사용했습니다.");}
}
