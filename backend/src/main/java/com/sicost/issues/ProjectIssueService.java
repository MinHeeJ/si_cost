package com.sicost.issues;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.ProjectProgressPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.issues.ProjectIssueDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.*;

@Service
public class ProjectIssueService {
    private final ProjectIssueRepository repository;
    private final WriteCoordinator writes;
    private final ProjectProgressAggregateRevision revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public ProjectIssueService(ProjectIssueRepository repository, WriteCoordinator writes,
        ProjectProgressAggregateRevision revisions, CurrentUserProvider users, ChangeHistoryWriter audit, Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.audit=audit;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page getProjectIssues(String id,Filter filter) {
        repository.required(id);users.currentUser();
        if (filter.importance()!=null && !repository.activeCode("IMPR",filter.importance())) throw invalid("importance");
        LocalDate today=today(clock);
        List<Issue> all=repository.issues(id).stream().map(r->issue(r,today)).toList();
        List<Issue> selected=all.stream().filter(r->filter.importance()==null || r.importance().equals(filter.importance()))
            .filter(r->switch(filter.status()) {
                case "OPEN_ACTIVE" -> Set.of("OPEN","ACT").contains(r.status());
                case "OVERDUE" -> r.overdue();
                case "CLOSED" -> r.status().equals("CLOSE");
                default -> true;
            }).sorted(Comparator.comparing((Issue i)->!i.importance().equals("HIGH"))
                .thenComparing(Issue::dueDate).thenComparingLong(Issue::issueId)).toList();
        long offset=((long)filter.page()-1)*filter.size();
        return new Page(selected.stream().skip(offset).limit(filter.size()).toList(),selected.size(),filter.page(),filter.size(),
            (int)all.stream().filter(i->!i.status().equals("CLOSE")).count(),today);
    }
    @Transactional
    public Result createProjectIssue(String id,Input input) {return save(id,null,input);}
    @Transactional
    public Result putProjectIssue(String id,long issueId,Input input) {return save(id,issueId,input);}
    private Result save(String id,Long issueId,Input input) {
        Row[] before=new Row[1];Long[] saved={issueId};
        var changes=new ArrayList<ChangeHistoryWriter.Change>();
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        var result=writes.write(List.of("PROJECT:"+id),input.expectedRevision(),()->revisions.snapshot(id),()-> {
            Project p=repository.required(id);
            ProjectProgressPolicy.requireEditable(input.year(),p.status(),p.endDate(),clock);
            if (issueId!=null) before[0]=requiredIssue(id,issueId);
            if (!repository.activeCode("IMPR",input.importance())) throw invalid("importance");
            if (!repository.activeCode("ISSUE_STAT",input.status())) throw invalid("status");
            if (issueId!=null) changes.addAll(changes(before[0],input,issueId,"UPD"));
        },()->issueId==null || !changes.isEmpty(),()-> {
            Mutation mutation=new Mutation(id,issueId,input,users.userId(),now);
            if (issueId==null) {
                saved[0]=repository.insert(mutation);
                changes.addAll(changes(null,input,saved[0],"NEW"));
            } else repository.update(mutation);
            changes.forEach(audit::append);
        });
        Object progress=repository.progress(id,input.year());
        return new Result(id,result.changed(),result.changed()?"저장했습니다.":result.message(),result.revision(),progress,null,
            issue(requiredIssue(id,saved[0]),today(clock)));
    }
    private Row requiredIssue(String id,long issueId) {
        return repository.issues(id).stream().filter(i->i.issueId()==issueId).findFirst()
            .orElseThrow(()->new ApiException(404,"NOT_FOUND","프로젝트에 속한 이슈를 찾을 수 없습니다."));
    }
    private static List<ChangeHistoryWriter.Change> changes(Row old,Input next,long id,String type) {
        String[] names={"ISSUE_TTL","IMPR_CD","DDLN_DT","ISSUE_STAT_CD","ACTN_CN"};
        String[] before=old==null?new String[5]:new String[]{old.title(),old.importance(),old.dueDate().toString(),old.status(),old.action()};
        String[] after={next.title(),next.importance(),next.dueDate().toString(),next.status(),next.action()};
        var result=new ArrayList<ChangeHistoryWriter.Change>();
        for(int i=0;i<names.length;i++) if(!Objects.equals(before[i],after[i]))
            result.add(new ChangeHistoryWriter.Change("TB_PJ_ISSUE",Long.toString(id),type,names[i],before[i],after[i],null));
        return result;
    }
    static Issue issue(Row r,LocalDate today) {
        return new Issue(r.issueId(),r.projectId(),r.projectName(),r.title(),r.importance(),r.importanceName(),r.dueDate(),r.status(),r.statusName(),
            r.action(),!r.status().equals("CLOSE") && r.dueDate().isBefore(today),new Actor(r.regUserId(),r.regUserName()),time(r.regDttm()),
            r.modUserId()==null?null:new Actor(r.modUserId(),r.modUserName()),time(r.modDttm()));
    }
    private static OffsetDateTime time(LocalDateTime value) {return value==null?null:value.atZone(TimeConfiguration.SEOUL).toOffsetDateTime();}
}
