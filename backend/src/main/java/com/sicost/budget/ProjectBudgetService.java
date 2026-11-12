package com.sicost.budget;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.budget.ProjectBudgetDtos.*;

@Service
public class ProjectBudgetService {
    private final ProjectBudgetRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ProjectBudgetService(ProjectBudgetRepository repository,WriteCoordinator writes,RevisionService revisions,
                                CurrentUserProvider users,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProjectBudgetData getProjectBudget(String id) {
        users.currentUser();ProjectBudgetInput.id(id);
        Stored stored=repository.required(id);
        return view(stored,revisions.revision(repository.snapshot(id)));
    }
    @Transactional
    public SaveProjectBudgetResult saveProjectBudget(String id,SaveProjectBudgetRequest input) {
        ProjectBudgetInput.id(id);
        String actor=users.userId();
        ProjectInputPolicy.expectedRevision(input.expectedRevision());
        BigDecimal amount=ProjectInputPolicy.amount(input.budgetAmount()==null?null:input.budgetAmount().toPlainString(),"budgetAmount");
        Stored[] before=new Stored[1];
        var result=writes.write(List.of("PROJECT:"+id,"operating-standards:CASH_BASE_YM","operating-standards:TGT_MRGN_RT"),input.expectedRevision(),
            () -> repository.snapshot(id),
            () -> {
                before[0]=repository.required(id);
                ProjectInputPolicy.requireWritableYear(input.year(),cashBase(),clock);
                if ("LOSE".equals(before[0].status()) || "END".equals(before[0].status()) || before[0].endDate().isBefore(ProjectInputPolicy.today(clock)))
                    throw new ApiException(409,"INVALID_PROJECT_STATUS","종료 또는 실주 프로젝트의 예산은 변경할 수 없습니다.");
                if ("IN".equals(before[0].contractType())) throw ProjectInputPolicy.invalid("budgetAmount");
            },
            () -> !"Y".equals(before[0].budgetManual()) || before[0].budgetAmount()==null || amount.compareTo(before[0].budgetAmount())!=0,
            () -> {
                LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
                int serial=ProjectInputPolicy.nextSerial(repository.greatestHistorySn(id));
                String oldValue=historyValue(before[0].budgetAmount(),before[0].budgetManual());
                String newValue=historyValue(amount,"Y");
                repository.update(new Mutation(id,amount,actor,now),new BudgetHistory(id,serial,oldValue,newValue,actor,now));
                history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",id,"UPD","예산",oldValue,newValue,null));
            });
        ProjectBudgetData value=view(repository.required(id),result.revision());
        return new SaveProjectBudgetResult(value,result.revision(),result.changed());
    }
    private ProjectBudgetData view(Stored stored,String revision) {
        YearMonth month=YearMonth.from(ProjectInputPolicy.today(clock));
        BigDecimal rate=ProjectBudgetCalculation.validRate(repository.operatingValue("TGT_MRGN_RT",DatabaseMonthCodec.toDatabase(month.toString())));
        BigDecimal preview="IN".equals(stored.contractType())?null:ProjectBudgetCalculation.preview(stored.contractAmount(),rate);
        return new ProjectBudgetData(ProjectInputPolicy.amountString(stored.budgetAmount()),stored.budgetManual(),revision,
            ProjectInputPolicy.amountString(preview),ProjectInputPolicy.amountString(rate),month.toString());
    }
    private YearMonth cashBase() {
        BigDecimal value=repository.operatingValue("CASH_BASE_YM","999912");
        if (value==null) throw new IllegalStateException("운영 시작월이 없습니다.");
        try { return YearMonth.parse(DatabaseMonthCodec.toApi(value.toBigIntegerExact().toString())); }
        catch (RuntimeException e) { throw new IllegalStateException("운영 시작월을 확인해 주세요."); }
    }
    private String historyValue(BigDecimal amount,String manual) {
        return "budgetAmount="+(amount==null?"NULL":ProjectInputPolicy.amountString(amount))+",budgetManual="+manual;
    }
}
