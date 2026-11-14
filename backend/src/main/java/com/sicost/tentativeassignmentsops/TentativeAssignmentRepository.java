package com.sicost.tentativeassignmentsops;

import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.history.ChangeHistoryWriter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

@Repository
public class TentativeAssignmentRepository {
    private final TentativeAssignmentMapper mapper;
    private final AssignmentCandidateRepository candidates;
    private final ChangeHistoryWriter history;
    private final CommitInvalidation invalidation;
    public TentativeAssignmentRepository(TentativeAssignmentMapper mapper, AssignmentCandidateRepository candidates,
                                        ChangeHistoryWriter history, CommitInvalidation invalidation) {
        this.mapper=mapper; this.candidates=candidates; this.history=history; this.invalidation=invalidation;
    }
    public State lock(String id) {
        candidates.lock(id, null);
        State row=mapper.state(id);
        if(row==null) throw new ApiException(404,"NOT_FOUND","가배정이 없습니다.");
        return row;
    }
    public String revision(String opportunityId) { return candidates.revision(opportunityId); }
    public void requireRevision(String expected, String opportunityId) { candidates.requireRevision(expected, opportunityId); }
    public void requireSingleActive(State row) {
        List<String> ids=mapper.activeIds(row.developerId());
        if(ids.size()>1) throw new ApiException(409,"INVALID_STATE","한 개발자의 활성 가배정이 여러 건입니다. 충돌 대상을 확인해 주세요.",
            "developerId",Map.of("tentativeIds",ids));
    }
    public void release(State before, Mutation mutation, String reason) {
        one(mapper.release(mutation));
        one(mapper.touchOpportunity(mutation));
        String cause="사용자 해제; sourceTentId="+before.id()+(reason==null||reason.isBlank()?"":"; "+reason);
        history.append(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",before.id(),"UPD","TENT_STAT_CD",before.status(),"REL",cause));
        history.append(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",before.id(),"UPD","PROC_DTTM",
            before.processedAt()==null?null:before.processedAt().toString(),mutation.now().toString(),cause));
        for(String key:List.of("OPP:"+before.opportunityId(),"TENT:"+before.id(),"sales-opportunities","assignment-candidates",
                "staffing-plan-comparison","workforce-forecasts","workforce-operations","candidates")) invalidation.afterCommit(key);
    }
    private void one(int count) { if(count!=1) throw new IllegalStateException("가배정 해제를 저장하지 못했습니다."); }
}
