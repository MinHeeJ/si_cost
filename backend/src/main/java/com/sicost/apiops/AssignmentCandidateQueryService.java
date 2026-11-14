package com.sicost.apiops;

import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.StaffingReadDtos.*;

@Service
public class AssignmentCandidateQueryService {
    private final StaffingReadMapper mapper;
    private final AssignmentCandidateRepository revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public AssignmentCandidateQueryService(StaffingReadMapper mapper,AssignmentCandidateRepository revisions,CurrentUserProvider users,Clock clock){this.mapper=mapper;this.revisions=revisions;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CandidatePage list(StaffingQueryInput.Paging paging){
        users.currentUser();Map<String,String> tokens=new HashMap<>();
        var rows=new ArrayList<>(mapper.candidates(YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","")).stream().map(r->new Candidate(r.id(),r.opportunityId(),r.projectId(),r.projectName(),r.developerId(),r.developerName(),r.jobCode(),r.startDate(),r.endDate(),r.contractMm().toPlainString(),r.billingUnitPrice()==null?null:r.billingUnitPrice().toPlainString(),tokens.computeIfAbsent(r.opportunityId(),revisions::revision))).toList());
        Comparator<Candidate> order=switch(paging.sort()){case "projectId"->Comparator.comparing(Candidate::projectId);case "developerId"->Comparator.comparing(Candidate::developerId);default->Comparator.comparing(c->new BigInteger(c.id()));};
        if("DESC".equals(paging.direction()))order=order.reversed();rows.sort(order.thenComparing(c->new BigInteger(c.id())));
        return new CandidatePage(StaffingQueryInput.page(rows,paging),rows.size(),paging.page(),paging.size());
    }
}
