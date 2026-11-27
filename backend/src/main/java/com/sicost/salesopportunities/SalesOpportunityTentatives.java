package com.sicost.salesopportunities;

import com.sicost.apiops.*;
import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.api.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;
import com.sicost.salesopportunitiesops.SalesCommandDtos.DraftNeed;

@Service
public class SalesOpportunityTentatives {
    private final TentativeSelectionService selections;
    private final AssignmentCandidateRepository revisions;
    public SalesOpportunityTentatives(TentativeSelectionService selections,AssignmentCandidateRepository revisions){this.selections=selections;this.revisions=revisions;}
    public List<Selection> resolve(List<Selection> input,List<DraftNeed> needs){
        if(input==null)return null;var byPerson=new LinkedHashMap<String,Selection>();
        for(Selection s:input){Integer sn=s.needSn();if(sn==null){if(s.needIndex()>needs.size())throw SalesOpportunityInput.invalid("needIndex");sn=needs.get(s.needIndex()-1).needSn();}
            int serial=sn;DraftNeed n=needs.stream().filter(v->v.needSn()==serial).findFirst().orElseThrow(()->SalesOpportunityInput.invalid("needSn"));
            YearMonth start=YearMonth.parse(s.startMonth()),begin=YearMonth.parse(n.startMonth()),end=begin.plusMonths(n.months()-1);
            if(start.isBefore(begin)||start.isAfter(end)||s.sourceScreenId().equals("UI-29")&&!start.equals(begin))throw SalesOpportunityInput.invalid("startMonth");
            byPerson.put(s.developerId(),new Selection(serial,null,s.developerId(),s.startMonth(),s.sourceScreenId()));
        }
        for(DraftNeed n:needs)if(byPerson.values().stream().filter(s->s.needSn().equals(n.needSn())).count()>n.count())throw new ApiException(400,"VALIDATION_ERROR","선택 인원을 줄이기 전에 해제 대상을 골라 주세요.","needs["+needs.indexOf(n)+"].count",null);
        return List.copyOf(byPerson.values());
    }
    public List<StoredTent> changes(List<StoredTent> old,List<DraftNeed> needs,List<Selection> selected,LocalDateTime now){
        var changes=new ArrayList<StoredTent>();
        for(StoredTent t:old){if(!t.status().equals("ACT"))continue;
            DraftNeed n=needs.stream().filter(v->v.needSn()==t.needSn()).findFirst().orElse(null);
            String status="ACT",start=t.startMonth();
            if(n==null||selected!=null&&selected.stream().noneMatch(s->s.developerId().equals(t.developerId())&&s.needSn()==t.needSn()))status="REL";
            if(n!=null){if(start.compareTo(n.startMonth())<0)start=n.startMonth();if(YearMonth.parse(start).isAfter(YearMonth.parse(n.startMonth()).plusMonths(n.months()-1)))status="REL";}
            if(!status.equals(t.status())||!start.equals(t.startMonth()))changes.add(new StoredTent(t.id(),t.opportunityId(),t.needSn(),t.developerId(),start,status,t.sourceScreenId(),status.equals("REL")?now:t.processedAt(),t.registeredBy(),t.registeredAt(),t.modifiedBy(),t.modifiedAt()));
        }
        if(selected==null)for(DraftNeed n:needs){long count=old.stream().filter(t->t.status().equals("ACT")&&t.needSn()==n.needSn()&&changes.stream().noneMatch(c->c.id().equals(t.id())&&c.status().equals("REL"))).count();if(count>n.count())throw new ApiException(400,"VALIDATION_ERROR","선택 인원을 줄이기 전에 해제 대상을 골라 주세요.","needs["+needs.indexOf(n)+"].count",null);}
        return changes;
    }
    public boolean additions(List<Selection> selected,List<StoredTent> old){return selected!=null&&selected.stream().anyMatch(s->old.stream().noneMatch(t->t.status().equals("ACT")&&t.needSn()==s.needSn()&&t.developerId().equals(s.developerId())));}
    public List<String> apply(String id,List<Selection> selected){
        var warnings=new ArrayList<String>();if(selected==null)return warnings;
        for(Selection s:selected){
            var result=selections.create(new TentativeSelectionInput.Create(id,s.needSn(),s.developerId(),YearMonth.parse(s.startMonth()),s.sourceScreenId(),revisions.revision(id),null,true));
            warnings.addAll(result.warnings());
        }return warnings;
    }
}
