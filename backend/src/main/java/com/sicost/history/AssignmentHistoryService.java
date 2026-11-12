package com.sicost.history;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.AssignmentInputPolicy;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.history.AssignmentHistoryDtos.*;

@Service
public class AssignmentHistoryService {
    private final AssignmentHistoryRepository repository;
    private final WriteCoordinator writes;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public AssignmentHistoryService(AssignmentHistoryRepository repository,WriteCoordinator writes,CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock) {
        this.repository=repository;this.writes=writes;this.users=users;this.audit=audit;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page getAssignmentHistory(Query query,int year) {
        users.currentUser();repository.required(query.id());ProjectInputPolicy.year(year,repository.months().cashBase(),clock);
        repository.present(query.id(),year);
        return new Page(repository.mapper().list(query),repository.mapper().count(query),query.page(),query.size());
    }
    @Transactional
    public Result createAssignmentHistoryCorrection(String id,int sn,Correction input) {
        AssignmentHistoryInput.id(id);AssignmentHistoryInput.serial(sn);
        String content=AssignmentInputPolicy.correctionContent(input.correctContent());
        String reason=ProjectInputPolicy.text(input.reasonCode(),10,"reasonCode");
        ProjectInputPolicy.expectedRevision(input.expectedRevision());String actor=users.userId();
        var discovered=repository.required(id);List<String> keys=new ArrayList<>(List.of("ASGN:"+id,"PROJECT:"+discovered.projectId(),
            "DEV:"+discovered.developerId(),"operating-standards:CASH_BASE_YM","code-group:ASGN_CHG_RSN"));
        if(discovered.contractDeveloperId()!=null)keys.add("DEV:"+discovered.contractDeveloperId());
        Entry[] original=new Entry[1];int[] serial=new int[1];
        var result=writes.write(keys,input.expectedRevision(),()->repository.snapshot(id),()->{
            repository.months().mapper().lockClose();var current=repository.required(id);
            if(!current.equals(discovered))throw new ApiException(409,"STALE_WRITE","투입 배정을 다시 조회해 주세요.");
            ProjectInputPolicy.requireWritableYear(input.year(),repository.months().cashBase(),clock);repository.present(id,input.year());
            repository.reason(reason);original[0]=repository.entry(id,sn);
            if(Set.of("INPUT","CRCT").contains(original[0].type())||!"N".equals(original[0].corrected()))throw ProjectInputPolicy.invalid("sn");
            serial[0]=repository.serial(id);
        },()->true,()->{
            LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
            String before=summary(original[0]);
            repository.one(repository.mapper().mark(id,sn));
            repository.one(repository.mapper().append(new CorrectionWrite(id,serial[0],sn,now.toLocalDate(),before,content,reason,actor,now)));
            audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",id,"UPD","이력 정정:"+sn,before,content,reason));
        });
        return new Result(repository.entry(id,sn),repository.entry(id,serial[0]),result.revision(),true);
    }
    static String summary(Entry row) {
        String text="#"+row.sequence()+" "+row.type()+" "+row.applyDate()+" "+Objects.toString(row.before(),"–")+" → "+Objects.toString(row.after(),"–");
        return text.codePointCount(0,text.length())<=200?text:text.substring(0,text.offsetByCodePoints(0,199))+"…";
    }
}
