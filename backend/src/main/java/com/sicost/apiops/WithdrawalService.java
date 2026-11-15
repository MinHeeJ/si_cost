package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.WithdrawalDtos.*;

@Service
public class WithdrawalService {
    private final WithdrawalRepository repository;
    private final AssignmentMonthlyService monthly;
    private final CurrentUserProvider users;
    private final Clock clock;
    public WithdrawalService(WithdrawalRepository repository,AssignmentMonthlyService monthly,CurrentUserProvider users,Clock clock){this.repository=repository;this.monthly=monthly;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data list(WithdrawalInput.Query query){
        return read(query,true);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data listAll(WithdrawalInput.Query query){
        return read(query,false);
    }
    private Data read(WithdrawalInput.Query query,boolean paged){
        users.currentUser();
        LocalDate today=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
        YearMonth base=monthly.cashBase();int year=query.year()==null?today.getYear():query.year();
        try { ProjectInputPolicy.year(year,base,clock); }
        catch (com.sicost.common.api.ApiException e) { throw StaffingQueryInput.invalid("year","VALIDATION_ERROR"); }
        if(query.job()!=null&&!repository.activeJob(query.job()))throw StaffingQueryInput.invalid("job","VALIDATION_ERROR");
        LocalDate end=YearMonth.from(today).plusMonths(2).atEndOfMonth();
        var sources=repository.sources(new Window(year,today,end));
        List<Row> selected=new ArrayList<>();Set<String> a=new HashSet<>(),b=new HashSet<>(),c=new HashSet<>();
        for(Source source:sources){
            boolean soon=period(source.withdrawalDate(),today,end).equals("SOON");
            boolean current=!source.withdrawalDate().isBefore(today);
            List<NextAssignment> next=current?repository.next(source):Collections.emptyList();
            boolean noNext=next.isEmpty(),nonH=!"H".equals(source.extensionPossibility());
            List<Leave> leaves=current?repository.leaves(source.developerId()):Collections.emptyList();
            boolean eligible=current&&WithdrawalPositionCalculator.eligible(source,today,leaves)&&!"LOSE".equals(source.projectStatus());
            int count=soon&&nonH&&noNext&&eligible?WithdrawalPositionCalculator.calculate(source,today,repository.positions(source.developerId()),leaves).size():0;
            if(soon){a.add(source.developerId());if(nonH&&noNext){b.add(source.developerId());if(count>0)c.add(source.developerId());}}
            if(source.withdrawalDate().getYear()!=year||!matches(source,query,today,end))continue;
            boolean writable=year>=today.getYear()&&current;
            boolean change=writable&&eligible&&!next.isEmpty()&&next.stream().allMatch(n->"TENTATIVE".equals(n.kind()));
            selected.add(new Row(source.assignmentId(),new Developer(source.developerId(),source.name(),source.managementNumber(),source.developerJob(),source.subJobCode(),source.gradeCode(),source.employeeTypeCode(),source.developerId()),
                source.projectId(),source.projectName(),source.jobCode(),source.withdrawalDate(),(int)ChronoUnit.DAYS.between(today,source.withdrawalDate()),
                source.extensionCount(),source.earlyWithdrawal(),source.extensionPossibility(),writable&&!"LOSE".equals(source.projectStatus()),
                next,count,writable&&eligible&&soon&&nonH&&noNext,change,repository.revision(source.assignmentId())));
        }
        selected.sort(Comparator.comparing(Row::withdrawalDate).thenComparing(r->r.developer().managementNumber()).thenComparing(Row::assignmentId));
        Guidance guidance=new Guidance(year==today.getYear(),a.size(),b.size(),c.size(),
            "3개월 안 철수 "+a.size()+"명 · 다음 배정 없는 "+b.size()+"명 중 "+c.size()+"명은 맞는 자리가 있습니다. 연도 경계의 다른 연도 자료는 연도를 바꿔 조회하세요.");
        return new Data(paged?selected.stream().skip((long)(query.page()-1)*query.size()).limit(query.size()).toList():List.copyOf(selected),year,query.page(),query.size(),selected.size(),guidance,today,base.toString());
    }
    static String period(LocalDate date,LocalDate today,LocalDate end){return date.isBefore(today)?"PAST":date.isAfter(end)?"LATER":"SOON";}
    private boolean matches(Source source,WithdrawalInput.Query q,LocalDate today,LocalDate end){
        return (q.period().equals("ALL")||q.period().equals(period(source.withdrawalDate(),today,end)))
            &&(q.extension().equals("ALL")||(q.extension().equals("UNSET")?source.extensionPossibility()==null:q.extension().equals(source.extensionPossibility())))
            &&(q.job()==null||q.job().equals(source.jobCode()));
    }
}
