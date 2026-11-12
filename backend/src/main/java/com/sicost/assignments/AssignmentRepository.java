package com.sicost.assignments;

import com.sicost.assignmentsops.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;

@Repository
public class AssignmentRepository {
    private final AssignmentActionsRepository assignments;
    private final AssignmentMapper mapper;
    private final AggregateLockMapper locks;
    private final MasterIdService ids;
    private final ChangeHistoryWriter audit;
    private final CommitInvalidation invalidation;
    public AssignmentRepository(AssignmentActionsRepository assignments,AssignmentMapper mapper,AggregateLockMapper locks,
                                MasterIdService ids,ChangeHistoryWriter audit,CommitInvalidation invalidation) {
        this.assignments=assignments;this.mapper=mapper;this.locks=locks;this.ids=ids;this.audit=audit;this.invalidation=invalidation;
    }
    public List<String> ids(Filter filter) {return assignments.mapper().matchingIds(filter);}
    public List<AssignmentMapper.CodeLabel> labels() {return mapper.labels();}
    public Source required(String id) {return assignments.required(id);}
    public Project project(String id) {return assignments.project(id);}
    public String allocate() {return ids.nextAssignmentId();}
    public void lock(String id,Create input) {
        Set<String> keys=new TreeSet<>(List.of("ASGN:"+id,"PROJECT:"+input.projectId(),"DEV:"+input.developerId(),
            "operating-standards:CASH_BASE_YM","operating-standards:MM_OVER","code-group:JOB","code-group:INPUT_TYPE"));
        if(input.contractDeveloperId()!=null)keys.add("DEV:"+input.contractDeveloperId());
        AssignmentMonthlyService.range(input.startDate(),input.endDate()).forEach(m->keys.add("PAY:"+AssignmentMonthlyService.db(m)));
        keys.forEach(locks::lock);
        List<String> developers=new ArrayList<>(List.of(input.developerId()));
        if(input.contractDeveloperId()!=null)developers.add(input.contractDeveloperId());
        mapper.lockDevelopers(developers.stream().distinct().sorted().toList());
        mapper.lockProject(input.projectId());
    }
    public void insert(Source source,String actor,LocalDateTime now,List<String> protection,boolean retroactive) {
        assignments.one(assignments.mapper().insert(new Write(source,actor,now)));
        String summary=source.projectId()+"/"+source.developerId()+"/"+source.jobCode()+"/"+source.startDate()+"/"+source.endDate();
        assignments.one(assignments.mapper().event(new Event(source.id(),1,"INPUT",source.startDate(),null,summary,null,null,
            source.price(),source.contractMm(),retroactive?"Y":"N",protection.isEmpty()?"N":"Y",actor,now)));
        audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",source.id(),"NEW",null,null,summary,null));
    }
    public void invalidate(Source source) {
        for(String key:List.of("ASGN:"+source.id(),"PROJECT:"+source.projectId(),"DEV:"+source.developerId(),"assignments","developers","projects"))invalidation.afterCommit(key);
    }
}
