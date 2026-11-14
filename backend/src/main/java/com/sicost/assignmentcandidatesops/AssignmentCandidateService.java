package com.sicost.assignmentcandidatesops;

import com.sicost.assignments.AssignmentService;
import com.sicost.assignmentsops.AssignmentActionsDtos;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.TimeConfiguration;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;

@Service
public class AssignmentCandidateService {
    private final AssignmentCandidateRepository repository;
    private final AssignmentService assignments;
    private final CurrentUserProvider users;
    private final Clock clock;
    public AssignmentCandidateService(AssignmentCandidateRepository repository,AssignmentService assignments,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.assignments=assignments;this.users=users;this.clock=clock;
    }
    @Transactional
    public ConfirmationResult confirm(String id,Confirmation input) {
        String actor=users.currentUser().userId();
        if(!id.equals(input.sourceTentId()))throw new ApiException(400,"VALIDATION_ERROR","원천 후보가 일치하지 않습니다.","sourceTentId",null);
        var create=input.assignment();
        Origin row=repository.lock(id,create);
        if(!java.util.Objects.equals(row.projectId(),create.projectId())||!row.developerId().equals(create.developerId())||!row.jobCode().equals(create.jobCode()))
            throw new ApiException(400,"VALIDATION_ERROR","후보의 프로젝트·개발자·직무를 변경할 수 없습니다.","sourceTentId",null);
        if(!"NEW".equals(create.inputType())||create.contractDeveloperId()!=null)throw new ApiException(400,"VALIDATION_ERROR","후보는 신규 배정만 확정할 수 있습니다.","inputType",null);
        if("DONE".equals(row.status())) {
            String existing=repository.confirmed(id);
            if(existing==null)throw new ApiException(409,"INVALID_STATE","확정 배정 연결을 확인할 수 없습니다.");
            return new ConfirmationResult(false,repository.revision(row.opportunityId()),List.of(),row.opportunityId(),id,existing);
        }
        repository.requireRevision(input.expectedRevision(),row.opportunityId());
        requireCandidate(row);
        if(!"Y".equals(row.developerUseYn())||row.projectStatus()==null||!List.of("ING","PRE").contains(row.projectStatus())
            ||row.projectEndDate()==null||row.projectEndDate().isBefore(LocalDate.now(clock.withZone(TimeConfiguration.SEOUL))))
            throw new ApiException(409,"INVALID_STATE","중지 인력 또는 종료·실주 프로젝트는 배정할 수 없습니다.");
        AssignmentActionsDtos.Save saved;
        try{saved=assignments.create(create);}catch(ApiException e){throw AssignmentCandidateInput.validation(e);}
        repository.transition(row,new Mutation(id,row.opportunityId(),"DONE",actor,now()),saved.assignment().id());
        return new ConfirmationResult(true,repository.revision(row.opportunityId()),saved.warnings().stream().map(AssignmentActionsDtos.Warning::message).toList(),row.opportunityId(),id,saved.assignment().id());
    }
    @Transactional
    public DropResult drop(String id,String expectedRevision) {
        String actor=users.currentUser().userId();Origin row=repository.lock(id,null);
        repository.requireRevision(expectedRevision,row.opportunityId());
        if("DROP".equals(row.status()))return new DropResult(false,repository.revision(row.opportunityId()),List.of(),row.opportunityId(),id);
        requireCandidate(row);
        repository.transition(row,new Mutation(id,row.opportunityId(),"DROP",actor,now()),null);
        return new DropResult(true,repository.revision(row.opportunityId()),List.of(),row.opportunityId(),id);
    }
    private void requireCandidate(Origin row){if(!"CNVT".equals(row.status()))throw new ApiException(409,"INVALID_STATE","확정 대기 후보만 처리할 수 있습니다.");}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
