package com.sicost.contacts;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CandidateRevisionService;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.contacts.CandidateContactDtos.*;

@Service
public class CandidateContactService {
    private final CandidateContactRepository repository;
    private final CandidateRevisionService snapshots;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public CandidateContactService(CandidateContactRepository repository,CandidateRevisionService snapshots,
            RevisionService revisions,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.snapshots=snapshots;this.revisions=revisions;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(String id,Query query) {
        repository.actor();required(id);
        long total=repository.mapper().count(id);
        return new Page(repository.mapper().list(new Selection(id,query.size(),(long)(query.page()-1)*query.size())),total,query.page(),query.size());
    }
    public Created create(String id,Create input) {
        return repository.write(()->{
            String actor=repository.actor();
            revisions.requireExpected(input.expectedRevision(),snapshots.revision(id,true));
            Candidate old=required(id);
            int sn=PeopleInputPolicy.nextSerial(repository.mapper().greatestSerial(id));
            LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
            Mutation mutation=new Mutation(id,sn,input.contactStatus(),input.memo(),now.toLocalDate(),actor,now);
            if(repository.mapper().insert(mutation)!=1 || repository.mapper().updateCandidate(mutation)!=1)
                throw new IllegalStateException("연락 기록을 저장하지 못했습니다.");
            history.append(new ChangeHistoryWriter.Change("TB_HR_CAND_CNTC",id+":"+sn,"NEW","연락 등록",null,
                    input.contactStatus()+" / "+now.toLocalDate()+" / "+input.memo(),null));
            history.append(new ChangeHistoryWriter.Change("TB_HR_CAND",id,"UPD","CNTC_STAT_CD",old.status(),input.contactStatus(),null));
            history.append(new ChangeHistoryWriter.Change("TB_HR_CAND",id,"UPD","LAST_CNTC_DT",
                    Objects.toString(old.lastContactDate(),null),now.toLocalDate().toString(),null));
            Contact contact=repository.mapper().contact(id,sn);
            if(contact==null)throw new IllegalStateException("저장한 연락 기록을 확인하지 못했습니다.");
            repository.committed();
            return new Created(contact,input.contactStatus(),now.toLocalDate());
        });
    }
    private Candidate required(String id) {
        Candidate row=repository.mapper().candidate(id);
        if(row==null)throw new ApiException(404,"NOT_FOUND","후보를 찾을 수 없습니다.");
        return row;
    }
}
