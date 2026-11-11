package com.sicost.developersops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Service
public class DeveloperUseStatusService {
    private final DeveloperActionsRepository repository;
    private final DeveloperActionsMapper mapper;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final Clock clock;
    public DeveloperUseStatusService(DeveloperActionsRepository repository,DeveloperActionsMapper mapper,RevisionService revisions,
                                    ChangeHistoryWriter history,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.mapper=mapper;this.revisions=revisions;this.history=history;this.users=users;this.clock=clock;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean change(SelectionItem item,String use) {
        String actor=users.userId(); Base row=repository.required(item.id(),true);
        revisions.requireExpected(item.revision(),repository.revision(row,true));
        if(row.useYn().equals(use)) return false;
        LocalDate today=PeopleInputPolicy.today(clock);
        if(use.equals("N")) {
            var projects=mapper.blockingProjects(row.id(),today);
            if(!projects.isEmpty()) throw new ApiException(409,"DEVELOPER_IN_USE","현재 또는 예정된 투입이 있어 사용을 중지할 수 없습니다.",null,
                Map.of("projects",projects,"referenceCount",projects.size()));
        } else if(row.retireDate()!=null && row.retireDate().isBefore(today)) {
            throw new ApiException(409,"RETIRED_DEVELOPER","퇴사한 개발자는 다시 사용할 수 없습니다. 새로 등록하세요.");
        }
        if(mapper.status(new Mutation(row.id(),use,row.status(),actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
            throw new IllegalStateException("사용 상태를 저장하지 못했습니다.");
        history.append(new ChangeHistoryWriter.Change("TB_HR_DEV",row.id(),use.equals("N")?"STOP":"RESUME","USE_YN",row.useYn(),use,null));
        return true;
    }
}
