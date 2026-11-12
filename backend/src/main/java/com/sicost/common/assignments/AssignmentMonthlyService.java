package com.sicost.common.assignments;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.projectsops.ProjectAssignmentMonths;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AssignmentMonthlyService {
    public record Source(String id,String projectId,String developerId,String jobCode,LocalDate startDate,LocalDate endDate,
        BigDecimal contractMm,BigDecimal price,String inputType,String contractDeveloperId,String extensionPossibility,
        int extensionCount,String earlyWithdrawal,String status) {
        public Source changed(LocalDate end,BigDecimal mm,BigDecimal unit,String kind,String possibility,int count,String early,String state) {
            return new Source(id,projectId,developerId,jobCode,startDate,end,mm,unit,kind,contractDeveloperId,possibility,count,early,state);
        }
    }
    public record Month(String month,String projectId,String developerId,int inputDays,String contractMm,String monthlyMm,
        String appliedUnitPrice,String salesAmount,String fixed) {}
    public record StoredMonth(String month,int days,BigDecimal mm,BigDecimal price,BigDecimal sales,String fixed) {}
    public record MonthWrite(Source source,String month,Month value,String actor,LocalDateTime now) {}
    public record Projection(String id,BigDecimal mm,BigDecimal price,String actor,LocalDateTime now) {}
    private final AssignmentMonthlyMapper mapper;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public AssignmentMonthlyService(AssignmentMonthlyMapper mapper,CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock) {
        this.mapper=mapper;this.users=users;this.audit=audit;this.clock=clock;
    }
    public AssignmentMonthlyMapper mapper() {return mapper;}
    public Source required(String id) {
        Source row=mapper.source(id);if(row==null)throw new ApiException(404,"NOT_FOUND","투입 배정을 찾을 수 없습니다.");return row;
    }
    public YearMonth cashBase() {
        String value=mapper.cashBase();if(value==null)throw new IllegalStateException("운영 시작월이 없습니다.");return YearMonth.parse(DatabaseMonthCodec.toApi(value));
    }
    public boolean protectedMonth(String month,String fixed) {
        return "Y".equals(fixed)||YearMonth.parse(month).isBefore(cashBase())||"CLOSE".equals(mapper.closeStatus(db(month)));
    }
    public List<String> protectedMonths(Source source) {
        Map<String,StoredMonth> stored=new TreeMap<>();mapper.months(source.id()).forEach(m->stored.put(api(m.month()),m));
        Set<String> months=new TreeSet<>(stored.keySet());months.addAll(range(source.startDate(),source.endDate()));
        return months.stream().filter(m->protectedMonth(m,stored.containsKey(m)?stored.get(m).fixed():null)).toList();
    }
    public List<Month> calculate(Source source,List<ValueHistory> histories,String projectStatus,String contractType) {
        List<Month> result=new ArrayList<>();
        if("CNCL".equals(source.status()))return result;
        for(String key:range(source.startDate(),source.endDate())) {
            YearMonth month=YearMonth.parse(key);int days=ProjectAssignmentMonths.days(month,source.startDate(),source.endDate());
            BigDecimal mm=ProjectAssignmentMonths.value(histories,month,true,source.contractMm());
            BigDecimal price=ProjectAssignmentMonths.value(histories,month,false,source.price());
            if("TK".equals(contractType)||"IN".equals(contractType))price=null;
            if("FREE".equals(source.inputType()))price=BigDecimal.ZERO;
            BigDecimal actual=ProjectAssignmentMonths.mm(mm,days,month);
            boolean paid=Set.of("ING","END").contains(projectStatus)&&Set.of("MO","DP").contains(contractType)&&!"FREE".equals(source.inputType());
            BigDecimal sales=paid&&price!=null?price.multiply(actual).setScale(0,RoundingMode.DOWN):BigDecimal.ZERO;
            result.add(new Month(key,source.projectId(),source.developerId(),days,mm.setScale(2).toPlainString(),actual.toPlainString(),amount(price),amount(sales),"N"));
        }
        return result;
    }
    public List<Month> preview(Source source,List<ValueHistory> histories,String status,String type) {
        Map<String,Month> result=new TreeMap<>();calculate(source,histories,status,type).forEach(m->result.put(m.month(),m));
        for(StoredMonth stored:mapper.months(source.id())) {
            String key=api(stored.month());
            if(protectedMonth(key,stored.fixed()))result.put(key,view(source,stored,histories));
        }
        result.entrySet().removeIf(e->protectedMonth(e.getKey(),null)&&mapper.months(source.id()).stream().noneMatch(m->api(m.month()).equals(e.getKey())));
        return List.copyOf(result.values());
    }
    public Month view(Source source,StoredMonth row,List<ValueHistory> histories) {
        String key=api(row.month());BigDecimal mm=ProjectAssignmentMonths.value(histories,YearMonth.parse(key),true,source.contractMm());
        return new Month(key,source.projectId(),source.developerId(),row.days(),mm.setScale(2).toPlainString(),row.mm().setScale(2).toPlainString(),amount(row.price()),amount(row.sales()),row.fixed());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void synchronize(Source source,String status,String type) {
        List<ValueHistory> histories=mapper.histories(source.id());String actor=users.userId();LocalDateTime now=LocalDateTime.now(clock);
        Map<String,StoredMonth> stored=new TreeMap<>();mapper.months(source.id()).forEach(m->stored.put(api(m.month()),m));
        Map<String,Month> desired=new TreeMap<>();calculate(source,histories,status,type).forEach(m->desired.put(m.month(),m));
        Set<String> keys=new TreeSet<>(stored.keySet());keys.addAll(desired.keySet());
        for(String key:keys) {
            StoredMonth old=stored.get(key);if(protectedMonth(key,old==null?null:old.fixed()))continue;
            Month next=desired.get(key);if(next==null) {
                if(old!=null){mapper.deleteMonth(source.id(),db(key));audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",source.id(),"UPD","월자료:"+key,old.toString(),null,null));}
            } else if(old==null||!view(source,old,histories).equals(next)) {
                mapper.saveMonth(new MonthWrite(source,db(key),next,actor,now));
                audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",source.id(),"UPD","월자료:"+key,old==null?null:old.toString(),next.toString(),null));
            }
        }
        YearMonth display=YearMonth.from(ProjectInputPolicy.today(clock));if(display.isBefore(YearMonth.from(source.startDate())))display=YearMonth.from(source.startDate());
        BigDecimal mm=ProjectAssignmentMonths.value(histories,display,true,source.contractMm());BigDecimal price=ProjectAssignmentMonths.value(histories,display,false,source.price());
        if(!equal(mm,source.contractMm())||!equal(price,source.price()))mapper.project(new Projection(source.id(),mm,price,actor,now));
    }
    public static boolean equal(BigDecimal a,BigDecimal b) {return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    public static String amount(BigDecimal value) {return value==null?null:value.setScale(0).toPlainString();}
    public static String db(String month) {return DatabaseMonthCodec.toDatabase(month);}
    public static String api(String month) {return DatabaseMonthCodec.toApi(month);}
    public static List<String> range(LocalDate start,LocalDate end) {
        List<String> values=new ArrayList<>();for(YearMonth m=YearMonth.from(start);!m.isAfter(YearMonth.from(end));m=m.plusMonths(1))values.add(m.toString());return values;
    }
}
