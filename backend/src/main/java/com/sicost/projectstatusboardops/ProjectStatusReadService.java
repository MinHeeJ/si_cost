package com.sicost.projectstatusboardops;

import com.sicost.apiops.ProjectAnalysisDtos.Actor;
import com.sicost.apiops.ProjectAnalysisService;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.shared.SharedQueriesService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.projectstatusboardops.ProjectStatusDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectStatusReadService {
    private final ProjectStatusIssueRepository repository;
    private final ProjectAnalysisService board;
    private final SharedQueriesService shared;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectStatusReadService(ProjectStatusIssueRepository repository,ProjectAnalysisService board,SharedQueriesService shared,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.board=board;this.shared=shared;this.users=users;this.clock=clock;
    }
    public Map<String,Object> summary(com.sicost.apiops.ProjectAnalysisInput.Query query) {
        return board.getProjectStatusBoard(query).summary();
    }
    public IssuesPage issues(ProjectStatusInput.Issues query) {
        users.currentUser();LocalDate day=ProjectInputPolicy.today(clock);
        if(query.importance()!=null&&shared.getCodes("IMPR",false).items().stream().noneMatch(c->query.importance().equals(c.code())))throw ProjectInputPolicy.invalid("importance");
        if(query.projectId()!=null) {
            String status=repository.projectStatus(query.projectId(),day);
            if(status==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
            if(!"ELIGIBLE".equals(status))throw new ApiException(409,"INVALID_PROJECT_STATUS","현황 대상 프로젝트가 아닙니다.");
        }
        var population=repository.eligibleIssues(day).stream().filter(i->query.projectId()==null||query.projectId().equals(i.projectId())).toList();
        int open=(int)population.stream().filter(i->!"CLOSE".equals(i.status())).count();
        var selected=population.stream().filter(i->query.importance()==null||query.importance().equals(i.importance())).filter(i->switch(query.status()) {
            case "OPEN_ACTIVE" -> Set.of("OPEN","ACT").contains(i.status());
            case "OVERDUE" -> !"CLOSE".equals(i.status())&&i.dueDate().isBefore(day);
            case "CLOSED" -> "CLOSE".equals(i.status());
            default -> true;
        }).sorted(Comparator.<IssueSource>comparingInt(i->"HIGH".equals(i.importance())?0:1).thenComparing(IssueSource::dueDate).thenComparingLong(IssueSource::issueId)).toList();
        long offset=((long)query.page()-1)*query.size();
        var page=selected.stream().skip(offset).limit(query.size()).map(i->new Issue(i.issueId(),i.projectId(),i.projectName(),i.title(),i.importance(),i.importanceName(),i.dueDate(),i.status(),i.statusName(),i.action(),
            !"CLOSE".equals(i.status())&&i.dueDate().isBefore(day),new Actor(i.registeredId(),i.registeredName()),i.registeredAt(),i.modifiedId()==null?null:new Actor(i.modifiedId(),i.modifiedName()),i.modifiedAt())).toList();
        return new IssuesPage(page,selected.size(),query.page(),query.size(),open,day);
    }
}
