package com.sicost.expenses;

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
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.expenses.ProjectExpenseDtos.*;

@Service
public class ProjectExpenseService {
    private final ProjectExpenseRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ProjectExpenseService(ProjectExpenseRepository repository,WriteCoordinator writes,RevisionService revisions,
                                 CurrentUserProvider users,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProjectExpensesData getProjectExpenses(String id) {
        id=ProjectExpenseInput.id(id);users.currentUser();
        return view(id,revisions.revision(repository.snapshot(id)));
    }
    public ExpenseResult createProjectExpense(String id,CreateProjectExpenseRequest input) {
        ProjectExpenseInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());
        YearMonth month=ProjectInputPolicy.month(input.expenseMonth(),"expenseMonth");
        String dbMonth=DatabaseMonthCodec.toDatabase(month.toString());
        String code=ProjectInputPolicy.text(input.itemCode(),10,"itemCode");
        BigDecimal amount=ProjectInputPolicy.expenseAmount(input.amount());
        String memo=ProjectInputPolicy.optionalText(input.memo(),500,"memo");
        String actor=users.userId();ProjectExpensesData[] value=new ProjectExpensesData[1];
        var result=writes.write(keys(id),input.expectedRevision(),()->repository.snapshot(id),
            ()->{
                writable(id,input.year(),month);
                if (!repository.activeItem(code)) throw ProjectInputPolicy.invalid("itemCode");
            },()->true,()->{
                int serial=ProjectInputPolicy.nextSerial(repository.greatestSerial(id,dbMonth));
                ExpenseKey key=new ExpenseKey(id,dbMonth,serial);
                repository.insert(new ExpenseWrite(key,code,amount,memo,actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)));
                audit(key,"NEW",month.toString(),code,amount,memo);
                value[0]=view(id,revisions.revision(repository.snapshot(id)));
            });
        return new ExpenseResult(value[0],result.revision(),result.changed());
    }
    public ExpenseResult deleteProjectExpense(ExpenseKey key,DeleteProjectExpenseRequest input) {
        ProjectExpenseInput.id(key.id());ProjectInputPolicy.expectedRevision(input.expectedRevision());
        YearMonth month=ProjectInputPolicy.month(DatabaseMonthCodec.toApi(key.month()),"expenseMonth");
        if (key.sn()<1 || key.sn()>99999) throw ProjectInputPolicy.invalid("expenseSn");
        users.currentUser();Stored[] before=new Stored[1];ProjectExpensesData[] value=new ProjectExpensesData[1];
        var result=writes.write(keys(key.id()),input.expectedRevision(),()->repository.snapshot(key.id()),
            ()->{ writable(key.id(),input.year(),month);before[0]=repository.required(key); },()->true,()->{
                repository.delete(key);
                audit(key,"DEL",month.toString(),before[0].itemCode(),before[0].amount(),before[0].memo());
                value[0]=view(key.id(),revisions.revision(repository.snapshot(key.id())));
            });
        return new ExpenseResult(value[0],result.revision(),result.changed());
    }
    private List<String> keys(String id) { return List.of("PROJECT:"+id,"operating-standards:CASH_BASE_YM"); }
    private void writable(String id,Integer year,YearMonth month) {
        ProjectState project=repository.required(id);
        repository.lockClose();
        String storedBase=repository.cashBaseMonth();
        if (storedBase==null) throw new IllegalStateException("운영 시작월이 없습니다.");
        YearMonth base=YearMonth.parse(DatabaseMonthCodec.toApi(storedBase));
        ProjectInputPolicy.requireWritableYear(year,base,clock);
        if ("IN".equals(project.contractType()) || "LOSE".equals(project.status()))
            throw new ApiException(409,"INVALID_PROJECT_STATUS","내부 또는 실주 프로젝트의 직접 경비는 변경할 수 없습니다.");
        if (month.isBefore(base) || repository.closed(DatabaseMonthCodec.toDatabase(month.toString())))
            throw new ApiException(409,"CLOSED_MONTH","마감·이관 월의 직접 경비는 변경할 수 없습니다.","expenseMonth",
                Map.of("blockingMonths",List.of(month.toString())));
    }
    private ProjectExpensesData view(String id,String revision) {
        List<Stored> rows=repository.list(id);
        var items=rows.stream().map(row->new ProjectExpense(DatabaseMonthCodec.toApi(row.month()),row.sn(),row.itemCode(),row.itemName(),
            ProjectInputPolicy.amountString(row.amount()),row.memo(),row.registeredAt().toString(),revision)).toList();
        return new ProjectExpensesData(items,ProjectInputPolicy.amountString(rows.stream().map(Stored::amount).reduce(BigDecimal.ZERO,BigDecimal::add)),revision);
    }
    private void audit(ExpenseKey key,String type,String month,String code,BigDecimal amount,String memo) {
        String target=key.id()+":"+key.month()+":"+key.sn();
        append(target,type,"월",month);append(target,type,"항목",code);
        append(target,type,"원 금액",ProjectInputPolicy.amountString(amount));
        append(target,type,"메모",memo);
    }
    private void append(String key,String type,String item,String value) {
        history.append(new ChangeHistoryWriter.Change("TB_PJ_DRCT_EXPS",key,type,item,"DEL".equals(type)?value:null,"NEW".equals(type)?value:null,null));
    }
}
