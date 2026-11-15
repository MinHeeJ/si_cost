package com.sicost.apiops;

import com.sicost.assignmentsops.AssignmentActionsRepository;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.WithdrawalDtos.*;

@Repository
public class WithdrawalRepository {
    private final WithdrawalMapper mapper;
    private final AssignmentActionsRepository assignments;
    public WithdrawalRepository(WithdrawalMapper mapper,AssignmentActionsRepository assignments) {this.mapper=mapper;this.assignments=assignments;}
    public List<Source> sources(Window window){return mapper.sources(window);}
    public List<NextAssignment> next(Source row){return mapper.nextAssignments(new NextQuery(row.assignmentId(),row.developerId(),row.withdrawalDate()));}
    public List<Position> positions(String developerId){return mapper.positions(developerId);}
    public List<Leave> leaves(String developerId){return mapper.leaves(developerId);}
    public boolean activeJob(String job){return mapper.activeJob(job);}
    public String revision(String id){return assignments.revision(id);}
}
