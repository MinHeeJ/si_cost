package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.forecast.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.apiops.StaffingReadDtos.*;

@Service
public class WorkforceCalendarService {
    private final StaffingReadMapper mapper;
    private final SalesForecastRepository repository;
    private final AssignmentMonthlyService monthly;
    private final CurrentUserProvider users;
    private final Clock clock;
    public WorkforceCalendarService(StaffingReadMapper mapper,SalesForecastRepository repository,AssignmentMonthlyService monthly,CurrentUserProvider users,Clock clock){this.mapper=mapper;this.repository=repository;this.monthly=monthly;this.users=users;this.clock=clock;}
    private record Context(SalesForecastSources.Snapshot sources,List<TentativeSource> tentatives,LocalDate day){}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CalendarPage list(MultiValueMap<String,String> q){
        return load(q,false);
    }
    /** All filtered people from one snapshot, without the screen's pagination. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CalendarPage listAll(MultiValueMap<String,String> q){
        return load(q,true);
    }
    public void requireYear(int year){
        int current=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)).getYear();
        if(year<monthly.cashBase().getYear()||year>Math.min(9999,current+1))throw StaffingQueryInput.invalid("year","INVALID_PERIOD");
    }
    private CalendarPage load(MultiValueMap<String,String> q,boolean all){
        users.currentUser();LocalDate day=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));YearMonth current=YearMonth.from(day);
        int year=StaffingQueryInput.integer(q.getFirst("year"),day.getYear(),"year");requireYear(year);
        String job=StaffingQueryInput.text(q,"jobCode",10),grade=StaffingQueryInput.text(q,"gradeCode",10);
        String employee=StaffingQueryInput.choice(q,"employeeType",Set.of("FT","CT","FL","OS"),null);
        boolean remaining=Boolean.parseBoolean(StaffingQueryInput.choice(q,"remainingOnly",Set.of("true","false"),"false"));
        var paging=StaffingQueryInput.paging(q,Set.of("lastWithdrawalDate","name","developerId"),"lastWithdrawalDate");
        Context context=new Context(repository.load(current),mapper.tentatives(),day);
        Map<String,Person> people=new HashMap<>();var rows=new ArrayList<CalendarPerson>();
        for(Person person:mapper.people()){
            if(!"DRCT".equals(person.directClass())||job!=null&&!job.equals(person.jobCode())||grade!=null&&!grade.equals(person.gradeCode())||employee!=null&&!employee.equals(person.employeeType()))continue;
            boolean history=context.sources().assignments().stream().anyMatch(a->a.source().developerId().equals(person.id())&&a.months().stream().anyMatch(m->YearMonth.parse(m.month()).getYear()==year));
            boolean overlaps=(person.joinDate()==null||person.joinDate().getYear()<=year)&&(person.retireDate()==null||person.retireDate().getYear()>=year);
            if(!history&&(!"Y".equals(person.useYn())||!overlaps))continue;
            List<Cell> cells=new ArrayList<>();for(int m=1;m<=12;m++)cells.add(cell(person,YearMonth.of(year,m),context));
            if(remaining&&cells.stream().noneMatch(c->!YearMonth.parse(c.month()).isBefore(current)&&spare(person,c).signum()>0))continue;
            LocalDate last=context.sources().assignments().stream().filter(a->person.id().equals(a.source().developerId())).map(a->a.source().endDate()).max(Comparator.naturalOrder()).orElse(null);
            people.put(person.id(),person);rows.add(new CalendarPerson(person.id(),person.name(),person.jobCode(),person.gradeCode(),person.employeeType(),last,List.copyOf(cells)));
        }
        Comparator<CalendarPerson> order=switch(paging.sort()){
            case "name"->Comparator.comparing(CalendarPerson::name);case "developerId"->Comparator.comparing(CalendarPerson::developerId);
            default->Comparator.comparing(CalendarPerson::lastWithdrawalDate,"ASC".equals(paging.direction())?Comparator.nullsFirst(Comparator.naturalOrder()):Comparator.nullsLast(Comparator.reverseOrder()));
        };
        if(!paging.sort().equals("lastWithdrawalDate")&&paging.direction().equals("DESC"))order=order.reversed();
        rows.sort(order.thenComparing(CalendarPerson::name).thenComparing(CalendarPerson::developerId));
        var totals=new ArrayList<MonthTotal>();
        for(int m=1;m<=12;m++){
            YearMonth month=YearMonth.of(year,m);if(month.isBefore(current)){totals.add(new MonthTotal(month.toString(),null,null));continue;}
            int available=0;BigDecimal spare=BigDecimal.ZERO;
            for(var r:rows){Cell c=r.cells().get(m-1);if(c.status().equals("AVAILABLE"))available++;spare=spare.add(spare(people.get(r.developerId()),c));}
            totals.add(new MonthTotal(month.toString(),available,spare.setScale(2).toPlainString()));
        }
        int available=0,leaving=0,high=0,concurrent=0,over=0;BigDecimal spare=BigDecimal.ZERO,third=BigDecimal.ZERO;
        for(var r:rows){Person p=people.get(r.developerId());Cell c=cell(p,current,context);if(c.status().equals("AVAILABLE"))available++;spare=spare.add(spare(p,c));third=third.add(spare(p,cell(p,current.plusMonths(3),context)));if(c.assignmentCount()>=2)concurrent++;if(c.overAssigned())over++;
            if(r.lastWithdrawalDate()!=null&&!r.lastWithdrawalDate().isBefore(day)&&!r.lastWithdrawalDate().isAfter(current.plusMonths(2).atEndOfMonth())){leaving++;if(context.sources().assignments().stream().anyMatch(a->a.source().developerId().equals(p.id())&&a.source().endDate().equals(r.lastWithdrawalDate())&&"H".equals(a.source().extensionPossibility())))high++;}
        }
        return new CalendarPage(all?List.copyOf(rows):StaffingQueryInput.page(rows,paging),rows.size(),paging.page(),paging.size(),year,new Summary(available,spare.setScale(2).toPlainString(),leaving,high,concurrent,over,third.setScale(2).toPlainString()),List.copyOf(totals));
    }
    private BigDecimal spare(Person person,Cell cell){return !"Y".equals(person.useYn())||Set.of("BEFORE_JOIN","RETIRED","LEAVE").contains(cell.status())?BigDecimal.ZERO:BigDecimal.ONE.subtract(new BigDecimal(cell.actualMm())).max(BigDecimal.ZERO);}
    private Cell cell(Person person,YearMonth month,Context ctx){
        List<ProjectMm> projects=new ArrayList<>();BigDecimal mm=BigDecimal.ZERO;
        for(var a:ctx.sources().assignments())if(a.source().developerId().equals(person.id()))for(var m:a.months())if(m.month().equals(month.toString())&&new BigDecimal(m.monthlyMm()).signum()>0){
            boolean pre=ctx.sources().projects().stream().anyMatch(p->p.id().equals(a.source().projectId())&&p.status().equals("PRE"));
            projects.add(new ProjectMm(a.source().projectId(),m.monthlyMm(),a.source().endDate(),a.source().extensionPossibility(),pre));mm=mm.add(new BigDecimal(m.monthlyMm()));
        }
        var tents=ctx.tentatives().stream().filter(t->person.id().equals(t.developerId())&&month.compareTo(SalesWorkforceProjectionService.parse(t.startMonth()))>=0&&month.compareTo(SalesWorkforceProjectionService.parse(t.endMonth()))<=0).toList();
        YearMonth current=YearMonth.from(ctx.day());boolean past=month.isBefore(current);
        String status=person.joinDate()!=null&&YearMonth.from(person.joinDate()).isAfter(month)?"BEFORE_JOIN":person.retireDate()!=null&&YearMonth.from(person.retireDate()).isBefore(month)?"RETIRED":ctx.sources().leaves().stream().anyMatch(l->l.developerId().equals(person.id())&&!l.startDate().isAfter(month.atEndOfMonth())&&(l.endDate()==null||!l.endDate().isBefore(month.atDay(1))))?"LEAVE":mm.signum()>0?"ASSIGNED":!tents.isEmpty()?"TENTATIVE":past||!"Y".equals(person.useYn())?"PAST_EMPTY":"AVAILABLE";
        boolean soon=!past&&!projects.isEmpty()&&projects.stream().allMatch(p->!p.endDate().isBefore(ctx.day())&&!p.endDate().isAfter(current.plusMonths(2).atEndOfMonth()));
        return new Cell(month.toString(),status,mm.setScale(2).toPlainString(),projects.size(),mm.compareTo(BigDecimal.ONE)>0,soon,List.copyOf(projects),tents.stream().map(TentativeSource::view).toList(),tents.isEmpty()?null:tents.get(0).probability().toPlainString());
    }
}
