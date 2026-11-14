package com.sicost.assignmentcandidatesops;

import com.sicost.assignments.AssignmentMapper;
import com.sicost.assignmentsops.AssignmentActionsDtos.Create;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;

@Repository
public class AssignmentCandidateRepository {
    private final AssignmentCandidateMapper mapper;
    private final SettlementSourceLocks locks;
    private final AssignmentMapper assignments;
    private final RevisionService revisions;
    private final ChangeHistoryWriter audit;
    private final CommitInvalidation invalidation;
    public AssignmentCandidateRepository(AssignmentCandidateMapper mapper,SettlementSourceLocks locks,AssignmentMapper assignments,
        RevisionService revisions,ChangeHistoryWriter audit,CommitInvalidation invalidation) {
        this.mapper=mapper;this.locks=locks;this.assignments=assignments;this.revisions=revisions;this.audit=audit;this.invalidation=invalidation;
    }
    public Origin required(String id) {
        Origin row=mapper.origin(id);if(row==null)throw new ApiException(404,"NOT_FOUND","배정 후보가 없습니다.");return row;
    }
    public Origin lock(String id,Create input) {
        Origin before=required(id);
        Set<String> keys=new TreeSet<>(List.of("TENT:"+id,"OPP:"+before.opportunityId(),"DEV:"+before.developerId(),
            "code-group:JOB","code-group:INPUT_TYPE","operating-standards:CASH_BASE_YM","operating-standards:MM_OVER"));
        if(before.projectId()!=null)keys.add("PROJECT:"+before.projectId());
        if(input!=null)AssignmentMonthlyService.range(input.startDate(),input.endDate()).forEach(m->keys.add("PAY:"+AssignmentMonthlyService.db(m)));
        locks.lock(keys);
        mapper.lockOpportunity(before.opportunityId());
        assignments.lockDevelopers(List.of(before.developerId()));
        if(before.projectId()!=null)assignments.lockProject(before.projectId());
        mapper.lockTentative(id);
        Origin row=required(id);
        if(!Objects.equals(before.opportunityId(),row.opportunityId())||!Objects.equals(before.projectId(),row.projectId())
            ||!Objects.equals(before.developerId(),row.developerId())||!Objects.equals(before.jobCode(),row.jobCode()))
            throw new ApiException(409,"STALE_WRITE","후보 원천이 변경되었습니다. 다시 조회해 주세요.");
        return row;
    }
    public String revision(String opportunityId){return revisions.revision(mapper.snapshot(opportunityId));}
    public void requireRevision(String expected,String opportunityId){revisions.requireExpected(expected,revision(opportunityId));}
    public String confirmed(String id){return mapper.confirmedAssignment(id);}
    public void transition(Origin origin,Mutation mutation,String assignmentId) {
        one(mapper.transition(mutation));one(mapper.touchOpportunity(mutation));
        field(origin.id(),"TENT_STAT_CD",origin.status(),mutation.status());
        field(origin.id(),"PROC_DTTM",origin.processedAt()==null?null:origin.processedAt().toString(),mutation.now().toString());
        if(assignmentId!=null){field(origin.id(),"sourceTentId",null,origin.id());field(origin.id(),"assignmentId",null,assignmentId);}
        for(String key:List.of("OPP:"+origin.opportunityId(),"TENT:"+origin.id(),"assignment-candidates","sales-opportunities","staffing-plan-comparison","workforce-forecasts","workforce-operations","candidates","assignments"))invalidation.afterCommit(key);
    }
    private void field(String id,String field,String before,String after){audit.append(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",id,"UPD",field,before,after,"sourceTentId="+id));}
    private void one(int count){if(count!=1)throw new IllegalStateException("배정 후보 변경을 저장하지 못했습니다.");}
}
