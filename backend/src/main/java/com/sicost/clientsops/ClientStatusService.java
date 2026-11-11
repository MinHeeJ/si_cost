package com.sicost.clientsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Service
public class ClientStatusService {
    private final ClientActionsRepository repository;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public ClientStatusService(ClientActionsRepository repository, RevisionService revisions, ChangeHistoryWriter history,
            CurrentUserProvider users, CommitInvalidation invalidation, Clock clock) {
        this.repository=repository; this.revisions=revisions; this.history=history;
        this.users=users; this.invalidation=invalidation; this.clock=clock;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public BulkItem change(Target target,String useYn) {
        String name="";
        try {
            String actor=users.userId();
            var row=repository.client(target.id(),true);
            if (row==null) throw ClientActionsService.missing();
            name=row.name();
            revisions.requireExpected(target.expectedRevision(),revisions.revision(row));
            if (row.useYn().equals(useYn)) return new BulkItem(row.id(),name,"noChange",null);
            if (useYn.equals("N")) {
                var references=repository.references(row.id(),row.type()).stream().filter(ref -> ref.count()>0).toList();
                if (!references.isEmpty()) throw new ApiException(409,"REFERENCE_IN_USE",
                    "연결된 프로젝트 또는 소속 개발자가 있어 사용을 중지할 수 없습니다.",null,Map.of("references",references));
            }
            if (repository.status(new StatusChange(row.id(),useYn,actor,LocalDateTime.now(clock)))!=1)
                throw new IllegalStateException("상태를 저장하지 못했습니다.");
            history.append(new ChangeHistoryWriter.Change("TB_BS_CLNT",row.id(),useYn.equals("N")?"STOP":"RESUME",
                "USE_YN",row.useYn(),useYn,null));
            invalidation.afterCommit("clients");
            return new BulkItem(row.id(),name,"applied",null);
        } catch (ApiException error) {
            Map<String,Object> details=new LinkedHashMap<>(); details.put("clientName",name);
            if (error.details() instanceof Map<?,?> original) original.forEach((key,value)->details.put(key.toString(),value));
            throw new ApiException(error.status(),error.code(),error.getMessage(),error.field(),details);
        } catch (RuntimeException error) {
            throw new ApiException(500,"INTERNAL_ERROR","거래처 상태와 이력을 저장하지 못했습니다.",null,Map.of("clientName",name));
        }
    }
}
