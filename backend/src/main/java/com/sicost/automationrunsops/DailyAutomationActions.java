package com.sicost.automationrunsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.projectsops.ProjectAssignmentMonths;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Service
public class DailyAutomationActions {
    private final DailyAutomationMapper mapper;
    private final DailyAutomationRepository repository;
    private final DeveloperQueryService developers;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public DailyAutomationActions(DailyAutomationMapper mapper,DailyAutomationRepository repository,DeveloperQueryService developers,ChangeHistoryWriter history,Clock clock) {
        this.mapper=mapper;this.repository=repository;this.developers=developers;this.history=history;this.clock=clock;
    }
    public List<String> keys(int stage,LocalDate date) {
        return switch(stage) {
            case 1,2 -> mapper.developerIds();
            case 3 -> mapper.projectIds(date);
            case 4 -> date.getDayOfMonth()==1?mapper.assignmentIds(date).stream().map(Item::id).toList():List.of();
            default -> throw new IllegalArgumentException("자동 처리 단계를 확인해 주세요.");
        };
    }
    public void process(int stage,String id,LocalDate date) {
        if(stage<=2) {
            repository.item(List.of("DEV:"+id),()->{developers.synchronize(id,true);return null;});return;
        }
        if(stage==3) {
            repository.item(List.of("PROJECT:"+id),()->{
                Project row=mapper.project(id);
                if(row==null||!row.state().equals("ING")||!row.endDate().isBefore(date))return null;
                int serial=mapper.projectSerial(id)+1;if(serial>99999)throw new IllegalStateException("프로젝트 이력 번호 범위를 초과했습니다.");
                var write=new ProjectEnd(id,serial,row.state(),date,now());
                one(mapper.endProject(write));one(mapper.projectEvent(write));
                audit("TB_PJ_PROJ",id,"PROJ_STAT_CD",row.state(),"END");return null;
            });return;
        }
        if(stage!=4)throw new IllegalArgumentException("자동 처리 단계를 확인해 주세요.");
        Item found=mapper.assignmentIdentity(id);if(found==null)return;
        repository.item(List.of("ASGN:"+id,"DEV:"+found.developerId(),"PROJECT:"+found.projectId()),()->{
            Assignment row=mapper.assignment(id);
            if(row==null||!row.projectId().equals(found.projectId())||!row.developerId().equals(found.developerId()))throw new IllegalStateException("투입 소속이 변경되었습니다.");
            if(!eligible(row,date)||date.getDayOfMonth()!=1||mapper.protectedMonth(db(date)))return null;
            var values=mapper.valueHistory(id);
            if(values.stream().noneMatch(h->h.type().equals("INPUT")&&!h.date().isAfter(date)))throw new IllegalStateException("원본 투입 이력이 없습니다.");
            BigDecimal mm=ProjectAssignmentMonths.value(values,YearMonth.from(date),true,row.mm());
            BigDecimal price=ProjectAssignmentMonths.value(values,YearMonth.from(date),false,row.price());
            if(equal(mm,row.mm())&&equal(price,row.price()))return null;
            one(mapper.pointer(new Pointer(id,mm,price,now())));
            audit("TB_PJ_ASGN",id,"CTRT_MM",row.mm(),mm);audit("TB_PJ_ASGN",id,"BILL_UPRC",row.price(),price);return null;
        });
    }
    public static boolean eligible(Assignment row,LocalDate date) {
        return row.state().equals("ACT")&&!Set.of("LOSE","END").contains(row.projectState())&&!row.startDate().isAfter(date)&&!row.endDate().isBefore(date);
    }
    private boolean equal(BigDecimal a,BigDecimal b) { return a==null?b==null:b!=null&&a.compareTo(b)==0; }
    private void audit(String table,String id,String field,Object before,Object after) {
        if(before instanceof BigDecimal a&&after instanceof BigDecimal b&&a.compareTo(b)==0||Objects.equals(before,after))return;
        history.appendAsSystem(new ChangeHistoryWriter.Change(table,id,"UPD",field,before==null?null:before.toString(),after==null?null:after.toString(),null));
    }
    private static void one(int affected) { if(affected!=1)throw new IllegalStateException("자동 처리 항목을 저장하지 못했습니다."); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL); }
    static String db(LocalDate date) { return YearMonth.from(date).toString().replace("-",""); }
}
