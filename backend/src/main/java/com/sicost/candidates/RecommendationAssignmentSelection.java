package com.sicost.candidates;

import com.sicost.assignmentsops.AssignmentActionsDtos.*;
import com.sicost.assignmentsops.AssignmentActionsService;
import com.sicost.common.assignments.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.sicost.candidates.RecommendationActionDtos.*;

@Component
public class RecommendationAssignmentSelection implements RecommendationAssignmentHandler {
    private final RecommendationActionRepository repository;
    private final RecommendationActionMapper mapper;
    private final AssignmentActionsService assignments;
    private final ChangeHistoryWriter audit;
    private final CurrentUserProvider users;
    private final Clock clock;
    public RecommendationAssignmentSelection(RecommendationActionRepository repository,RecommendationActionMapper mapper,AssignmentActionsService assignments,ChangeHistoryWriter audit,CurrentUserProvider users,Clock clock){this.repository=repository;this.mapper=mapper;this.assignments=assignments;this.audit=audit;this.users=users;this.clock=clock;}
    @Transactional(propagation=Propagation.MANDATORY)
    public Save create(Create input,String assignmentId,Supplier<Save> ordinary){
        var ref=input.recommendationRef();Key key=new Key(ref.rcmdId(),ref.rank());Row before=repository.required(key);
        if(!Objects.equals(before.projectId(),input.projectId())||!before.developerId().equals(input.developerId()))return ordinary.get();
        Set<String> keys=new TreeSet<>(List.of("ASGN:"+assignmentId,"PROJECT:"+input.projectId(),"DEV:"+input.developerId()));
        if(input.contractDeveloperId()!=null)keys.add("DEV:"+input.contractDeveloperId());
        AssignmentMonthlyService.range(input.startDate(),input.endDate()).forEach(m->keys.add("PAY:"+AssignmentMonthlyService.db(m)));
        Row row=repository.lock(key,keys);Map<String,String> payload=payload(input);
        if(repository.replay(row,"ASGN",payload)){
            String id=repository.values(key).get("assignmentId");if(id==null)throw RecommendationActionRepository.taken();
            Save existing=assignments.saved(id,input.year(),false);return new Save(existing.assignment(),false,existing.warnings(),existing.protectedMonths());
        }
        if(!"AI".equals(row.type()))throw RecommendationActionInput.invalid("recommendationRef");
        var original=repository.values(key);var current=repository.current(row);
        repository.requireCurrent(row,new Revisions(original.getOrDefault("expectedRevision",current.expectedRevision()),original.getOrDefault("targetRevision",current.targetRevision()),original.getOrDefault("candidateRevision",current.candidateRevision())));
        if(!mapper.targetOpen(row))throw new ApiException(409,"INVALID_STATE","종료·중지한 프로젝트는 추천 배정할 수 없습니다.");
        repository.requireDomain(row);repository.storeOriginal(row);
        Save saved=ordinary.get();var value=saved.assignment();
        if(!value.developerId().equals(row.developerId())||!value.projectId().equals(row.projectId()))throw new IllegalStateException("추천 배정 원천이 일치하지 않습니다.");
        for(var field:payload.entrySet())audit.appendRecommendationAssignment(new ChangeHistoryWriter.Change("TB_PJ_ASGN",value.id(),"NEW",field.getKey(),null,field.getValue(),"추천으로 저장한 최종 입력값"));
        audit.appendRecommendationAssignment(new ChangeHistoryWriter.Change("TB_PJ_ASGN",value.id(),"NEW","billingUnitPrice",null,value.billingUnitPrice(),"추천 실제배정의 유효 최종 단가"));
        var choice=new Choice(key,"ASGN",null,users.currentUser().userId(),LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL));
        repository.select(row,choice,payload,Map.of("assignmentId",value.id()));return saved;
    }
    private Map<String,String> payload(Create input){
        Map<String,String> values=new TreeMap<>();values.put("year",String.valueOf(input.year()));values.put("projectId",input.projectId());values.put("developerId",input.developerId());values.put("jobCode",input.jobCode());values.put("startDate",input.startDate().toString());values.put("endDate",input.endDate().toString());values.put("contractMm",input.contractMm().stripTrailingZeros().toPlainString());values.put("billingUnitPrice",input.billingUnitPrice()==null?null:input.billingUnitPrice().strip().replace(",","").replace("원","").strip());values.put("inputType",input.inputType());values.put("contractDeveloperId",input.contractDeveloperId());return values;
    }
}
