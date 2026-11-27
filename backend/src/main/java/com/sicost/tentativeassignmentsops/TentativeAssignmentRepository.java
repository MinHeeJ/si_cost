package com.sicost.tentativeassignmentsops;

import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.history.ChangeHistoryWriter;
import java.util.List;
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
