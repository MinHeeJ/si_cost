package com.sicost.states;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.DeveloperAggregateRevision;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.states.DeveloperStateDtos.*;
import com.sicost.states.DeveloperStateDtos.Calendar;
import com.sicost.states.DeveloperStateDtos.Period;

@Service
public class DeveloperStateService {
    private static final LocalDate LAST=LocalDate.of(9999,12,31);
    private static final DateTimeFormatter MONTH=DateTimeFormatter.ofPattern("uuuuMM");
    private final DeveloperStateRepository repository;
    private final DeveloperAggregateRevision revisions;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public DeveloperStateService(DeveloperStateRepository repository,DeveloperAggregateRevision revisions,
                                 ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.revisions=revisions;this.history=history;this.clock=clock;
    }
    private DeveloperStateMapper mapper(){return repository.mapper();}
    private LocalDate today(){return PeopleInputPolicy.today(clock);}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"));}
    public States get(String id) {
        DeveloperStateInput.id(id);
        recalculate(id,false);
        return repository.read(()->{
            repository.actor();Developer developer=required(id);List<Stored> rows=mapper().states(id);Calendar calendar=calendar();
            List<State> views=rows.stream().sorted(Comparator.comparing(Stored::startDate).thenComparingInt(Stored::sn).reversed())
                .map(row->new State(row.sn(),row.state(),row.startDate(),row.endDate(),row.reason(),row.regUserId(),
                    Objects.requireNonNullElse(mapper().actorName(row.regUserId()),row.regUserId()),actions(row,rows,calendar))).toList();
            return new States(views,developer.hireDate(),revisions.revision(id,false));
        });
    }
    public Created create(String id,Create input) {
        DeveloperStateInput.id(id);
        return repository.write(()->{
            String actor=repository.actor();lock(id,input.expectedRevision());Developer developer=required(id);
            List<Stored> before=mapper().states(id),after=new ArrayList<>(before);
            LocalDate start=input.startDate();
            if(developer.hireDate()!=null && start.isBefore(developer.hireDate()))throw InputValidator.invalid("startDate");
            if(developer.retireDate()!=null && today().isAfter(developer.retireDate()))throw new ApiException(409,"RETIRED_DEVELOPER","퇴사 다음 날부터 근무 상태를 등록할 수 없습니다.");
            int sn=PeopleInputPolicy.nextSerial(mapper().greatestSerial(id));
            Integer link=null;LocalDate original=null;
            List<Assignment> assignments=mapper().assignments(id);
            if(input.state().equals("WORK")) {
                Stored leave=before.stream().filter(r->r.state().equals("LEAVE") && covers(r,today())).findFirst().orElseThrow(()->invalid("휴직 중인 사람만 재직으로 등록할 수 있습니다."));
                if(before.stream().anyMatch(r->r.state().equals("WORK") && Objects.equals(r.closeLeaveSn(),leave.sn())))throw invalid("이미 복귀가 연결된 휴직입니다.");
                if(!start.isAfter(leave.startDate()))throw invalid("복귀일은 휴직 시작일 다음 날부터 입력해 주세요.");
                if(leave.endDate()!=null && start.isAfter(leave.endDate().equals(LAST)?LAST:leave.endDate().plusDays(1)))
                    throw invalid("휴직 종료일 다음 날보다 늦습니다. 종료일 수정을 쓰세요.");
                link=leave.sn();original=leave.endDate();replace(after,leave.end(start.minusDays(1)));
            } else if(input.state().equals("RETIRE")) {
                if(before.stream().anyMatch(r->r.state().equals("RETIRE")))throw invalid("이미 등록된 퇴사 이력이 있습니다.");
                List<Assignment> blocking=assignments.stream().filter(a->a.endDate().isAfter(start)).toList();
                if(!blocking.isEmpty())throw new ApiException(409,"DEVELOPER_IN_USE","퇴사일 이후 투입이 있습니다. 먼저 조기 철수해 주세요.","startDate",Map.of("projects",blocking,"referenceCount",blocking.size()));
                for(Stored leave:before)if(leave.state().equals("LEAVE") && covers(leave,start)) {
                    link=leave.sn();original=leave.endDate();replace(after,leave.end(start.minusDays(1)));
                }
            }
            Stored added=new Stored(sn,input.state(),start,input.endDate(),input.reason(),link,original,actor,now(),null,null);
            after.add(added);
            LocalDate retire=retireDate(after);
            validate(developer,after,retire);
            changedMonths(before,after,calendar(),false);
            persistChanges(id,before,after,actor);
            one(mapper().insert(new Mutation(id,added,actor,now())));
            audit(id,added,"NEW","근무 상태 등록",null,summary(added),input.reason(),false);
            derive(id,developer,after,assignments,retire,false,actor);
            repository.committed();
            List<Warning> warnings=new ArrayList<>();
            if(input.state().equals("LEAVE")) {
                List<Assignment> overlaps=assignments.stream().filter(a->!a.endDate().isBefore(start) && !a.startDate().isAfter(end(input.endDate()))).toList();
                if(!overlaps.isEmpty())warnings.add(new Warning("state","겹치는 ACT 투입 "+overlaps.size()+"건: "+overlaps.stream().map(a->a.startDate()+" ~ "+a.endDate()).distinct().reduce((a,b)->a+", "+b).orElse("")+". 휴직은 저장했습니다."));
            }
            return new Created(sn,List.copyOf(warnings));
        });
    }
    public Saved putEnd(String id,int sn,End input) {
        DeveloperStateInput.id(id);DeveloperStateInput.sn(sn);
        return repository.write(()->{
            String actor=repository.actor();lock(id,input.expectedRevision());Developer developer=required(id);
            List<Stored> before=mapper().states(id);Stored row=find(before,sn);
            if(!editable(row,before))throw invalid("현재 또는 예정 휴직의 종료일만 수정할 수 있습니다.");
            if(input.endDate()!=null && input.endDate().isBefore(row.startDate()))throw InputValidator.invalid("endDate");
            List<Stored> after=new ArrayList<>(before);replace(after,row.end(input.endDate()));
            validate(developer,after,retireDate(after));
            if(Objects.equals(row.endDate(),input.endDate()))return new Saved(false);
            changedMonths(before,after,calendar(),false);
            persistChanges(id,before,after,actor);
            derive(id,developer,after,mapper().assignments(id),retireDate(after),false,actor);
            repository.committed();return new Saved(true);
        });
    }
    public Saved cancel(String id,int sn,Cancel input) {
        DeveloperStateInput.id(id);DeveloperStateInput.sn(sn);
        return repository.write(()->{
            String actor=repository.actor();lock(id,input.expectedRevision());Developer developer=required(id);
            List<Stored> before=mapper().states(id);Stored target=find(before,sn);
            if(!Set.of("WORK","RETIRE").contains(target.state()))throw invalid("재직 또는 퇴사 이력만 취소할 수 있습니다.");
            if(target.state().equals("RETIRE") && input.reason()==null)throw InputValidator.invalid("reason");
            Calendar calendar=calendar();checkMonths(new Period(target.startDate(),target.startDate()),calendar,target.state().equals("RETIRE"));
            List<Stored> after=new ArrayList<>(before);after.remove(target);
            if(target.closeLeaveSn()!=null) {
                Stored leave=find(after,target.closeLeaveSn());
                LocalDate closedEnd=target.startDate().minusDays(1);
                if(Objects.equals(leave.endDate(),closedEnd))replace(after,leave.end(target.originalLeaveEndDate()));
                for(Stored other:new ArrayList<>(after))if(Objects.equals(other.closeLeaveSn(),target.closeLeaveSn()) && Objects.equals(other.originalLeaveEndDate(),closedEnd))
                    replace(after,other.original(target.originalLeaveEndDate()));
                LocalDate retire=retireDate(after);
                Stored restored=find(after,leave.sn());
                if(retire!=null && end(restored.endDate()).isAfter(retire.minusDays(1))) {
                    Stored retirement=after.stream().filter(r->r.state().equals("RETIRE")).findFirst().orElseThrow();
                    if(retirement.closeLeaveSn()==null)replace(after,retirement.closure(restored.sn(),restored.endDate()));
                    replace(after,restored.end(retire.minusDays(1)));
                }
            }
            LocalDate retire=retireDate(after);validate(developer,after,retire);
            changedMonths(before,after,calendar,false);
            persistChanges(id,before,after,actor);
            one(mapper().delete(id,sn));
            String restored=target.closeLeaveSn()==null?"연결 휴직 없음":"휴직 종료일 "+Objects.toString(find(after,target.closeLeaveSn()).endDate(),"미정");
            audit(id,target,"UPD",target.state().equals("WORK")?"재직 취소":"퇴사 취소",summary(target),restored,input.reason(),false);
            derive(id,developer,after,mapper().assignments(id),retire,target.state().equals("RETIRE"),actor);
            repository.committed();return new Saved(true);
        });
    }
    private void lock(String id,String expected){revisions.requireExpected(expected,revisions.revision(id,true));}
    private Developer required(String id){Developer d=mapper().developer(id);if(d==null)throw new ApiException(404,"NOT_FOUND","개발자를 찾을 수 없습니다.");return d;}
    private Stored find(List<Stored> rows,int sn){return rows.stream().filter(r->r.sn()==sn).findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","근무 상태 이력을 찾을 수 없습니다."));}
    private void replace(List<Stored> rows,Stored row){for(int i=0;i<rows.size();i++)if(rows.get(i).sn()==row.sn()){rows.set(i,row);return;}throw invalid("연결 휴직을 찾을 수 없습니다.");}
    private LocalDate end(LocalDate value){return value==null?LAST:value;}
    private boolean covers(Stored row,LocalDate date){return !row.startDate().isAfter(date) && !end(row.endDate()).isBefore(date);}
    private LocalDate retireDate(List<Stored> rows){return rows.stream().filter(r->r.state().equals("RETIRE")).map(Stored::startDate).findFirst().orElse(null);}
    private ApiException invalid(String message){return new ApiException(409,"INVALID_STATE_HISTORY",message);}
    private boolean editable(Stored row,List<Stored> rows){return row.state().equals("LEAVE") && !end(row.endDate()).isBefore(today()) && rows.stream().noneMatch(r->r.state().equals("WORK") && Objects.equals(r.closeLeaveSn(),row.sn()));}
    private List<String> actions(Stored row,List<Stored> rows,Calendar calendar){
        if(editable(row,rows))return List.of("EDIT_END");
        if(Set.of("WORK","RETIRE").contains(row.state()) && !closed(row.startDate(),calendar))return List.of("CANCEL");
        return List.of();
    }
    private void validate(Developer developer,List<Stored> rows,LocalDate retire) {
        List<Stored> leaves=rows.stream().filter(r->r.state().equals("LEAVE")).sorted(Comparator.comparing(Stored::startDate)).toList();
        if(rows.stream().filter(r->r.state().equals("RETIRE")).count()>1)throw invalid("퇴사 이력은 한 건만 등록할 수 있습니다.");
        for(Stored row:rows) {
            if(developer.hireDate()!=null && row.startDate().isBefore(developer.hireDate()))throw invalid("입사일 이전의 상태가 있습니다.");
            if(!Set.of("LEAVE","WORK","RETIRE").contains(row.state()))throw invalid("근무 상태를 확인해 주세요.");
            if(!row.state().equals("LEAVE") && row.endDate()!=null)throw invalid("휴직만 종료일을 가질 수 있습니다.");
            if(row.endDate()!=null && row.endDate().isBefore(row.startDate()))throw invalid("종료일은 시작일 이전일 수 없습니다.");
            if(retire!=null && !row.state().equals("RETIRE") && (!row.startDate().isBefore(retire) || row.state().equals("LEAVE") && (row.endDate()==null || !row.endDate().isBefore(retire))))throw invalid("휴직과 복귀는 퇴사 전날까지여야 합니다.");
            if(row.state().equals("LEAVE") && row.closeLeaveSn()!=null)throw invalid("휴직은 닫기 연결을 가질 수 없습니다.");
            if(row.closeLeaveSn()!=null && rows.stream().noneMatch(r->r.sn()==row.closeLeaveSn() && r.state().equals("LEAVE")))throw invalid("같은 개발자의 휴직만 연결할 수 있습니다.");
            if(row.state().equals("WORK")) {
                Stored leave=rows.stream().filter(r->Objects.equals(r.sn(),row.closeLeaveSn()) && r.state().equals("LEAVE")).findFirst().orElseThrow(()->invalid("복귀에 연결된 휴직이 없습니다."));
                if(!row.startDate().isAfter(leave.startDate()) || !Objects.equals(leave.endDate(),row.startDate().minusDays(1)))throw invalid("복귀일은 연결 휴직 종료 다음 날이어야 합니다.");
                if(rows.stream().filter(r->r.state().equals("WORK") && Objects.equals(r.closeLeaveSn(),leave.sn())).count()>1)throw invalid("휴직에는 복귀를 한 번만 연결할 수 있습니다.");
                if(leaves.stream().anyMatch(r->r.sn()!=leave.sn() && !r.startDate().isBefore(row.startDate()) && !r.startDate().isAfter(row.startDate())))throw invalid("새 휴직은 복귀 다음 날부터 등록해 주세요.");
            }
        }
        for(int i=1;i<leaves.size();i++)if(!leaves.get(i).startDate().isAfter(end(leaves.get(i-1).endDate())))throw invalid("휴직 기간이 겹칩니다.");
    }
    private Calendar calendar(){return new Calendar(mapper().cashBaseMonth(today().format(MONTH)),mapper().closedMonths());}
    private boolean closed(LocalDate date,Calendar calendar){String month=date.format(MONTH);return calendar.cashBaseMonth()!=null && month.compareTo(calendar.cashBaseMonth())<0 || calendar.closedMonths().contains(month);}
    private void changedMonths(List<Stored> before,List<Stored> after,Calendar calendar,boolean retirementCancel) {
        Map<Integer,Stored> a=new HashMap<>(),b=new HashMap<>();before.forEach(r->a.put(r.sn(),r));after.forEach(r->b.put(r.sn(),r));
        Set<Integer> keys=new HashSet<>(a.keySet());keys.addAll(b.keySet());
        for(int key:keys) {
            Stored old=a.get(key),next=b.get(key),row=old==null?next:old;
            if(old==null || next==null)checkMonths(new Period(row.startDate(),row.state().equals("LEAVE")?end(row.endDate()):row.startDate()),calendar,retirementCancel);
            else if(row.state().equals("LEAVE") && !Objects.equals(old.endDate(),next.endDate())) {
                LocalDate left=end(old.endDate()),right=end(next.endDate());
                LocalDate min=left.isBefore(right)?left:right,max=left.isAfter(right)?left:right;
                if(min.isBefore(LAST))checkMonths(new Period(min.plusDays(1),max),calendar,false);
            }
        }
    }
    private void checkMonths(Period period,Calendar calendar,boolean retirementCancel) {
        String from=period.start().format(MONTH),to=period.end().format(MONTH);
        Set<String> blocking=new TreeSet<>();
        if(calendar.cashBaseMonth()!=null && from.compareTo(calendar.cashBaseMonth())<0)blocking.add(from);
        calendar.closedMonths().stream().filter(m->m.compareTo(from)>=0 && m.compareTo(to)<=0).forEach(blocking::add);
        if(!blocking.isEmpty()) {
            String month=from.substring(0,4)+"-"+from.substring(4);
            String message=retirementCancel?"퇴사한 달("+month+")이 마감되어 취소할 수 없습니다. 다시 쓰려면 새로 등록하세요.":"변경 구간에 마감된 달이 있어 저장할 수 없습니다.";
            throw new ApiException(409,"CLOSED_MONTH",message,null,Map.of("blockingMonths",blocking.stream().map(m->m.substring(0,4)+"-"+m.substring(4)).toList()));
        }
    }
    private void persistChanges(String id,List<Stored> before,List<Stored> after,String actor) {
        for(Stored old:before)for(Stored next:after)if(old.sn()==next.sn() && !old.equals(next)) {
            one(mapper().update(new Mutation(id,next,actor,now())));
            if(!Objects.equals(old.endDate(),next.endDate()))audit(id,next,"UPD","END_DT",Objects.toString(old.endDate(),null),Objects.toString(next.endDate(),null),null,false);
            if(!Objects.equals(old.originalLeaveEndDate(),next.originalLeaveEndDate()))audit(id,next,"UPD","ORGNL_LEAVE_END_DT",Objects.toString(old.originalLeaveEndDate(),null),Objects.toString(next.originalLeaveEndDate(),null),null,false);
            if(!Objects.equals(old.closeLeaveSn(),next.closeLeaveSn()))audit(id,next,"UPD","CLOSE_LEAVE_SN",Objects.toString(old.closeLeaveSn(),null),Objects.toString(next.closeLeaveSn(),null),null,false);
        }
    }
    private String summary(Stored row){return row.state()+" "+row.startDate()+" ~ "+Objects.toString(row.endDate(),"미정");}
    private void audit(String id,Stored row,String type,String item,String before,String after,String reason,boolean system) {
        append(new ChangeHistoryWriter.Change("TB_HR_DEV_STAT",id+":"+row.sn(),type,item,before,after,reason),system);
    }
    private void append(ChangeHistoryWriter.Change change,boolean system){if(system)history.appendAsSystem(change);else history.append(change);}
    private void one(int affected){if(affected!=1)throw new IllegalStateException("근무 상태를 저장하지 못했습니다.");}
    private void derive(String id,Developer old,List<Stored> rows,List<Assignment> assignments,LocalDate retire,boolean resume,String actor) {
        LocalDate date=today();boolean system=actor.equals("SYSTEM");
        String status;
        if(retire!=null && retire.isBefore(date))status="RETIRE";
        else if(rows.stream().anyMatch(r->r.state().equals("LEAVE") && covers(r,date)))status="LEAVE";
        else {
            LocalDate finalEnd=assignments.stream().filter(a->!a.startDate().isAfter(date) && !a.endDate().isBefore(date)).map(Assignment::endDate).max(LocalDate::compareTo).orElse(null);
            status=finalEnd==null?"IDLE":!finalEnd.isAfter(YearMonth.from(date).plusMonths(2).atEndOfMonth())?"WTHDR":"INPUT";
        }
        String use=status.equals("RETIRE")?"N":resume?"Y":old.useYn();
        if(Objects.equals(old.retireDate(),retire) && old.status().equals(status) && old.useYn().equals(use))return;
        one(mapper().updateDeveloper(new DeveloperMutation(id,retire,status,use,actor,now())));
        append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,"UPD","RETIRE_DT",Objects.toString(old.retireDate(),null),Objects.toString(retire,null),null),system);
        append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,"UPD","WORK_STAT_CD",old.status(),status,null),system);
        append(new ChangeHistoryWriter.Change("TB_HR_DEV",id,use.equals("Y")?"RESUME":"STOP","USE_YN",old.useYn(),use,null),system);
    }
    public void recalculate(String id,boolean system) {
        repository.write(()->{
            String actor=system?"SYSTEM":repository.actor();revisions.snapshot(id,true);
            Developer developer=required(id);List<Stored> rows=mapper().states(id);
            derive(id,developer,rows,mapper().assignments(id),developer.retireDate(),false,actor);return null;
        });
    }
    public void recalculateAll(){for(String id:mapper().developerIds())recalculate(id,true);}
}
