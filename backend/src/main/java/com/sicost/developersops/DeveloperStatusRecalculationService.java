package com.sicost.developersops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.*;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Service
public class DeveloperStatusRecalculationService {
    private final DeveloperActionsMapper mapper;
    private final DeveloperActionsRepository repository;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public DeveloperStatusRecalculationService(DeveloperActionsMapper mapper,DeveloperActionsRepository repository,ChangeHistoryWriter history,Clock clock) {
        this.mapper=mapper;this.repository=repository;this.history=history;this.clock=clock;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void refresh() {
        for(String id:mapper.ids()) refreshOne(id,"SYSTEM");
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void refreshDeveloper(String id,String actor) { refreshOne(id,actor); }
    private void refreshOne(String id,String actor) {
        Base row=repository.required(id,true); repository.snapshot(row,true);
        LocalDate today=PeopleInputPolicy.today(clock);
        String status=mapper.calculatedStatus(id,today,YearMonth.from(today).plusMonths(2).atEndOfMonth());
        String use="RETIRE".equals(status)?"N":row.useYn();
        if(Objects.equals(status,row.status()) && Objects.equals(use,row.useYn())) return;
        if(mapper.recalculate(new Mutation(id,use,status,actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
            throw new IllegalStateException("근무 상태를 갱신하지 못했습니다.");
        append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,"UPD","WORK_STAT_CD",row.status(),status,null),actor);
        append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,"STOP","USE_YN",row.useYn(),use,null),actor);
    }
    private void append(ChangeHistoryWriter.Change change,String actor) {
        if(Objects.equals(change.before(),change.after())) return;
        if("SYSTEM".equals(actor)) history.appendAsSystem(change); else history.append(change);
    }
}
