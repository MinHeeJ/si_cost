package com.sicost.tentativeassignmentsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

@Service
public class TentativeAssignmentService {
    private final TentativeAssignmentRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    public TentativeAssignmentService(TentativeAssignmentRepository repository, CurrentUserProvider users, Clock clock) {
        this.repository=repository; this.users=users; this.clock=clock;
    }
    @Transactional
    public Result release(String id, Release input) {
        String actor=users.currentUser().userId();
        State row=repository.lock(id);
        repository.requireRevision(input.expectedRevision(),row.opportunityId());
        if("REL".equals(row.status())) return new Result(false,repository.revision(row.opportunityId()),List.of(),row.opportunityId(),id);
        if(!"ACT".equals(row.status())||!"ING".equals(row.opportunityStatus()))
            throw new ApiException(409,"INVALID_STATE","진행 중인 기회의 활성 가배정만 해제할 수 있습니다.");
        repository.requireSingleActive(row);
        repository.release(row,new Mutation(id,row.opportunityId(),actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)),input.reason());
        return new Result(true,repository.revision(row.opportunityId()),List.of(),row.opportunityId(),id);
    }
}
