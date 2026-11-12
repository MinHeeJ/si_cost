package com.sicost.assignments;

import com.sicost.assignmentsops.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;

@Service
public class AssignmentService {
    private final AssignmentRepository repository;
    private final AssignmentActionsService actions;
    private final AssignmentMonthlyService monthly;
    private final CurrentUserProvider users;
    private final Clock clock;
    public AssignmentService(AssignmentRepository repository,AssignmentActionsService actions,AssignmentMonthlyService monthly,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.actions=actions;this.monthly=monthly;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Assignment> list(AssignmentInput.Query query) {
        List<Assignment> rows=matching(query);
        return new Page<>(rows.stream().skip((long)(query.page()-1)*query.size()).limit(query.size()).toList(),rows.size(),query.page(),query.size());
    }
    /** Every row the list would show for these conditions and order, without paging (used by the excel download). */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Assignment> listAll(AssignmentInput.Query query) {return matching(query);}
    private List<Assignment> matching(AssignmentInput.Query query) {
        users.currentUser();ProjectInputPolicy.year(query.filters().year(),monthly.cashBase(),clock);
        return repository.ids(query.filters()).stream().map(id->actions.saved(id,query.filters().year(),false).assignment())
            .filter(row->query.filters().warning()==null||row.warnings().stream().anyMatch(w->w.code().equals(query.filters().warning())
                && (w.months().isEmpty()||w.months().stream().anyMatch(m->m.startsWith(query.filters().year()+"-")))))
            .sorted(order(query)).toList();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Assignment get(String id,int year) {
        users.currentUser();ProjectInputPolicy.year(year,monthly.cashBase(),clock);repository.required(id);
        if(!repository.ids(new Filter(year,null,null,null,null,true)).contains(id))throw new ApiException(404,"NOT_FOUND","선택 연도에 투입 배정이 없습니다.");
        return actions.saved(id,year,false).assignment();
    }
    @Transactional
    public Save create(Create input) {
        String actor=users.userId();ProjectInputPolicy.requireWritableYear(input.year(),monthly.cashBase(),clock);
        String id=repository.allocate();repository.lock(id,input);monthly.mapper().lockClose();
        Source prepared=actions.prepare(input);
        Source source=new Source(id,prepared.projectId(),prepared.developerId(),prepared.jobCode(),prepared.startDate(),prepared.endDate(),
            prepared.contractMm(),prepared.price(),prepared.inputType(),prepared.contractDeveloperId(),null,0,"N","ACT");
        List<String> protection=monthly.protectedMonths(source);
        repository.insert(source,actor,LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul")),protection,source.startDate().isBefore(ProjectInputPolicy.today(clock)));
        Project project=repository.project(source.projectId());
        String status="LOSE".equals(project.status())?"LOSE":project.endDate().isBefore(ProjectInputPolicy.today(clock))?"END":project.status();
        monthly.synchronize(source,status,project.contractType());
        Save result=actions.saved(id,input.year(),true);repository.invalidate(source);return result;
    }
    private Comparator<Assignment> order(AssignmentInput.Query query) {
        if(query.sort()==null)return Comparator.comparing(Assignment::projectName,Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Assignment::startDate).thenComparing(Assignment::id);
        Map<String,String> labels=new HashMap<>();repository.labels().forEach(c->labels.put(c.group()+":"+c.code(),c.name()));
        Comparator<Assignment> comparator=switch(query.sort()) {
            case "projectName" -> compare(Assignment::projectName,query.direction());
            case "developerName" -> compare(Assignment::developerName,query.direction());
            case "jobName" -> compare(Assignment::jobName,query.direction());
            case "startDate" -> compare(Assignment::startDate,query.direction());
            case "endDate" -> compare(Assignment::endDate,query.direction());
            case "billingUnitPrice" -> compare(a->decimal(a.billingUnitPrice()),query.direction());
            case "contractMm" -> compare(a->decimal(a.contractMm()),query.direction());
            case "yearMm" -> compare(a->decimal(a.yearMm()),query.direction());
            case "extensionPossibility" -> compare(a->a.extensionPossibility()==null?null:labels.getOrDefault("EXT_PSBL:"+a.extensionPossibility(),a.extensionPossibility()),query.direction());
            case "inputType" -> compare(a->labels.getOrDefault("INPUT_TYPE:"+a.inputType(),a.inputType()),query.direction());
            default -> throw ProjectInputPolicy.invalid("sort");
        };
        return comparator.thenComparing(Assignment::id);
    }
    private BigDecimal decimal(String value) {return value==null?null:new BigDecimal(value);}
    private <T extends Comparable<? super T>> Comparator<Assignment> compare(Function<Assignment,T> key,String direction) {
        Comparator<T> values="DESC".equals(direction)?Comparator.reverseOrder():Comparator.naturalOrder();
        return Comparator.comparing(key,Comparator.nullsLast(values));
    }
}
