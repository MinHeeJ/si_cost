package com.sicost.pm;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.pm.ProjectPmDtos.*;

@Service
public class ProjectPmService {
    private final ProjectPmRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ProjectPmService(ProjectPmRepository repository,WriteCoordinator writes,RevisionService revisions,
                            CurrentUserProvider users,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProjectPmData getProjectPm(String id) {
        users.currentUser();ProjectPmInput.id(id);repository.required(id);
        return view(repository.scopes(id),revisions.revision(repository.snapshot(id)));
    }
    @Transactional
    public SaveProjectPmResult saveProjectPm(String id,SaveProjectPmRequest input) {
        return mutate(id,input.userId(),input.expectedRevision(),false,input.replaceConfirmed());
    }
    @Transactional
    public SaveProjectPmResult deleteProjectPm(String id,DeleteProjectPmRequest input) {
        return mutate(id,input.userId(),input.expectedRevision(),true,false);
    }
    private SaveProjectPmResult mutate(String id,String requestedUser,String expected,boolean removing,boolean confirmed) {
        ProjectPmInput.id(id);String userId=ProjectInputPolicy.text(requestedUser,30,"userId");
        ProjectInputPolicy.expectedRevision(expected);String actor=users.userId();repository.required(id);
        List<Scope> discovered=repository.scopes(id);
        Set<String> keys=new TreeSet<>();keys.add("PROJECT:"+id);keys.add("USER:"+userId);
        discovered.forEach(row -> keys.add("USER:"+row.userId()));
        List<Scope> before=new ArrayList<>();
        var result=writes.write(keys,expected,() -> repository.snapshot(id),() -> {
            List<Scope> current=repository.scopes(id);
            if (!identities(current).equals(identities(discovered)))
                throw new ApiException(409,"STALE_WRITE","담당 연결이 변경되었습니다. 다시 조회해 주세요.");
            before.addAll(current);
            ProjectSource project=repository.required(id);
            if (!removing) {
                if (!Set.of("ING","PRE").contains(project.status()) || project.endDate().isBefore(ProjectInputPolicy.today(clock)))
                    throw new ApiException(409,"INVALID_PROJECT_STATUS","진행 또는 수주예정 프로젝트만 지정할 수 있습니다.");
                if (!repository.eligible(userId))
                    throw new ApiException(409,"PM_QUALIFICATION_REQUIRED","사용 중인 PM 자격 사용자를 선택해 주세요.","userId",null);
                if ((current.size()>1 || current.stream().anyMatch(row -> !row.userId().equals(userId))) && !confirmed)
                    throw new ApiException(409,"PM_REPLACEMENT_REQUIRED","기존 PM 전체를 확인한 뒤 교체해 주세요.","replaceConfirmed",null);
            }
        },() -> removing ? before.stream().anyMatch(row -> row.userId().equals(userId))
            : before.size()!=1 || !before.get(0).userId().equals(userId),() -> {
            int historySn=ProjectInputPolicy.nextSerial(repository.greatestHistorySn(id));
            int scopeSn=removing?0:ProjectInputPolicy.nextSerial(repository.greatestScopeSn(userId));
            LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
            for (Scope row:before) {
                if (!removing || row.userId().equals(userId)) {
                    repository.delete(new ScopeWrite(row.userId(),row.scopeSn(),id,actor,now));
                    audit(row.userId(),row.scopeSn(),id,"DEL");
                }
            }
            if (!removing) {
                repository.insert(new ScopeWrite(userId,scopeSn,id,actor,now));
                audit(userId,scopeSn,id,"NEW");
            }
            repository.history(new PmHistory(id,historySn,summary(before),summary(repository.scopes(id)),actor,now));
        });
        return new SaveProjectPmResult(view(repository.scopes(id),result.revision()),result.revision(),result.changed());
    }
    private List<String> identities(List<Scope> rows) { return rows.stream().map(r -> r.userId()+":"+r.scopeSn()).sorted().toList(); }
    private ProjectPmData view(List<Scope> rows,String revision) {
        return new ProjectPmData(rows.stream().map(r -> new ProjectPm(r.userId(),r.name(),r.scopeSn())).toList(),revision);
    }
    private void audit(String userId,int scopeSn,String id,String type) {
        String value="PROJ:"+id+";PART:NULL";
        history.append(new ChangeHistoryWriter.Change("TB_SY_USER_SCOPE",userId+":"+scopeSn,type,"PM",
            type.equals("DEL")?value:null,type.equals("NEW")?value:null,null));
    }
    private String summary(List<Scope> rows) {
        if (rows.isEmpty()) return null;
        String value=String.join(", ",rows.stream().map(r -> r.name()+" ("+r.userId()+")").toList());
        return value.codePointCount(0,value.length())<=500?value:"PM "+rows.size()+"명; 연결 revision="+revisions.revision(identities(rows));
    }
}
