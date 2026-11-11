package com.sicost.developers;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.areasops.AddressService;
import com.sicost.areasops.AddressDtos.AreaPath;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.PeopleInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import static com.sicost.developers.DeveloperInput.*;

@Service
@EnableScheduling
public class DeveloperQueryService {
    private final DeveloperRepository repository;
    private final RevisionService revisions;
    private final AddressService addresses;
    private final ObjectMapper json;
    private final Clock clock;
    private final ChangeHistoryWriter history;
    public DeveloperQueryService(DeveloperRepository repository,RevisionService revisions,AddressService addresses,ObjectMapper json,Clock clock,ChangeHistoryWriter history) {
        this.repository=repository;this.revisions=revisions;this.addresses=addresses;this.json=json;this.clock=clock;this.history=history;
    }
    public LocalDate today(){return PeopleInputPolicy.today(clock);}
    public Instant instant(){return clock.instant();}
    public static String month(LocalDate date){return YearMonth.from(date).format(DateTimeFormatter.ofPattern("uuuuMM"));}
    public static LocalDate date(Object value){if(value==null)return null;if(value instanceof LocalDate d)return d;if(value instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(value.toString());}
    public static String apiMonth(Object value){if(value==null)return null;String s=value.toString();return s.substring(0,4)+"-"+s.substring(4,6);}
    public static BigDecimal amount(Object value){return value==null?null:new BigDecimal(value.toString());}
    public static List<String> skillNames(List<Map<String,Object>> rows){return rows.stream().sorted(Comparator.comparing(r->new BigDecimal(r.get("sort_ord").toString()))).map(r->r.get("skill_nm").toString()).toList();}
    public JsonNode list(Query q) {
        if(q.filters().containsKey("jobCode")&&repository.mapper().code("JOB",q.filters().get("jobCode"))==null)throw com.sicost.common.validation.InputValidator.invalid("jobCode");
        refresh(false);
        return repository.read(()->{
            repository.actor();DeveloperMapper m=repository.mapper();
            List<JsonNode> items=m.list(new Selection(q,month(today()),today(),(long)(q.page()-1)*q.size())).stream().map(r->view(repository.snapshot(r.get("id").toString(),false),false)).toList();
            return tree(Map.of("items",items,"total",m.count(q),"page",q.page(),"size",q.size()));
        });
    }
    public JsonNode get(String id){refresh(false);return repository.read(()->{repository.actor();return view(repository.snapshot(id,false),true);});}
    @EventListener(ApplicationReadyEvent.class)
    public void startup(){refresh(true);}
    @Scheduled(cron="0 0 0 * * *",zone="Asia/Seoul")
    public void midnight(){refresh(true);}
    public void refresh(boolean system){repository.recalculate(()->{for(String id:repository.mapper().ids())synchronize(id,system);});}
    public void synchronize(String id,boolean system) {
        Map<String,Object> row=repository.required(id,true);LocalDate d=today();
        String status=calculate(row,repository.mapper().states(id,true),repository.mapper().assignments(id,d,month(d)),d);
        String use="RETIRE".equals(status)?"N":Objects.toString(row.get("useYn"),"Y");
        if(Objects.equals(status,row.get("status"))&&Objects.equals(use,row.get("useYn")))return;
        String actor=system?"SYSTEM":repository.actor();
        Map<String,Object> changed=new LinkedHashMap<>(row);changed.put("status",status);changed.put("useYn",use);changed.put("modUserId",actor);changed.put("modDttm",LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
        repository.mapper().updateStatus(changed);
        for(String field:List.of("status","useYn"))if(!Objects.equals(row.get(field),changed.get(field))) {
            var entry=new ChangeHistoryWriter.Change("TB_HR_DEV",id,field.equals("useYn")?"STOP":"UPD",field.equals("useYn")?"사용 여부":"근무 상태",Objects.toString(row.get(field),null),changed.get(field).toString(),null);
            if(system)history.appendAsSystem(entry);else history.append(entry);
        }
    }
    public static String calculate(Map<String,Object> row,List<Map<String,Object>> states,List<Map<String,Object>> assignments,LocalDate today) {
        LocalDate retire=date(row.get("retireDate"));if(retire!=null&&retire.isBefore(today))return "RETIRE";
        if(states.stream().anyMatch(s->"LEAVE".equals(s.get("work_stat_cd"))&&!date(s.get("bgn_dt")).isAfter(today)&&(s.get("end_dt")==null||!date(s.get("end_dt")).isBefore(today))))return "LEAVE";
        LocalDate end=assignments.stream().map(a->date(a.get("endDate"))).max(Comparator.naturalOrder()).orElse(null);
        if(end==null)return "IDLE";
        return !end.isAfter(YearMonth.from(today).plusMonths(2).atEndOfMonth())?"WTHDR":"INPUT";
    }
    public Map<String,Object> editPrice(List<Map<String,Object>> rows) {
        String month=month(today());Map<String,Object> current=null,future=null;
        for(Map<String,Object> row:rows){String start=row.get("aply_bgn_ym").toString(),end=Objects.toString(row.get("aply_end_ym"),null);
            if(start.compareTo(month)<=0&&(end==null||end.compareTo(month)>=0))current=row;
            if(start.compareTo(month)>0&&(future==null||start.compareTo(future.get("aply_bgn_ym").toString())<0))future=row;
        }
        return current!=null?current:future;
    }
    private Map<String,Object> price(Map<String,Object> row){Map<String,Object> p=new LinkedHashMap<>();p.put("effectiveMonth",apiMonth(row.get("aply_bgn_ym")));p.put("endMonth",apiMonth(row.get("aply_end_ym")));p.put("monthlyRate",money(row.get("mon_uprc")));p.put("monthlyPay",money(row.get("mon_pay_amt")));return p;}
    private String money(Object value){return value==null?null:amount(value).toPlainString();}
    private JsonNode view(DeveloperRepository.Snapshot snapshot,boolean detail) {
        Map<String,Object> row=snapshot.developer();String id=row.get("id").toString();DeveloperMapper m=repository.mapper();
        Map<String,Object> view=new LinkedHashMap<>();
        for(String k:List.of("id","managementNo","name","companyId","employeeType","status","useYn"))view.put(k,row.get(k));
        Map<String,Object> labels=m.labels(id);for(String k:List.of("companyName","grade","job"))view.put(k,labels.get(k));
        LocalDate retire=date(row.get("retireDate"));view.put("retireDate",retire);view.put("retirementScheduled",retire!=null&&!retire.isBefore(today()));
        List<Map<String,Object>> assignments=m.assignments(id,today(),month(today()));Map<String,Map<String,String>> projects=new LinkedHashMap<>();BigDecimal mm=BigDecimal.ZERO;LocalDate end=null;
        for(var a:assignments){projects.putIfAbsent(a.get("projectId").toString(),Map.of("id",a.get("projectId").toString(),"name",a.get("projectName").toString()));mm=mm.add(amount(a.get("mm")));LocalDate e=date(a.get("endDate"));if(end==null||e.isAfter(end))end=e;}
        view.put("currentProjects",new ArrayList<>(projects.values()));view.put("currentProjectLabel",projects.isEmpty()?null:projects.values().iterator().next().get("name")+(projects.size()>1?" 외 "+(projects.size()-1)+"건":""));view.put("withdrawalDate",end);view.put("monthlyMm",mm.setScale(2).toPlainString());
        String limit=m.mmOver(month(today()));if(limit==null)throw new IllegalStateException("MM 초과 기준값이 없습니다.");view.put("mmOver",limit);
        view.put("warnings",mm.compareTo(new BigDecimal(limit))>0?List.of(Map.of("field","monthlyMm","message","이번 달 MM이 기준값 "+limit+"을 초과했습니다.")):List.of());
        Map<String,Object> edit=editPrice(snapshot.prices());boolean current=edit!=null&&edit.get("aply_bgn_ym").toString().compareTo(month(today()))<=0;
        view.put("monthlyRate",current?money(edit.get("mon_uprc")):null);view.put("revision",repository.revision(id,false));
        if(detail){for(String k:List.of("gradeCode","jobCode","subJobCode","positionCode","directClass","partId","homeAreaCode","homeAddress"))view.put(k,row.get(k));view.put("hireDate",date(row.get("hireDate")));view.put("monthlyPay",current?money(edit.get("mon_pay_amt")):null);view.put("skills",skillNames(snapshot.skills()));view.put("addressPath",row.get("homeAreaCode")==null?new AreaPath(List.of(),null):addresses.getPath(row.get("homeAreaCode").toString()));view.put("priceHistory",snapshot.prices().stream().sorted(Comparator.comparing((Map<String,Object> p)->p.get("aply_bgn_ym").toString()).reversed()).map(this::price).toList());view.put("editPrice",edit==null?null:price(edit));}
        return tree(view);
    }
    public JsonNode tree(Object value){
        if(value==null)return NullNode.instance;
        if(value instanceof JsonNode n)return n;
        if(value instanceof Map<?,?> map){ObjectNode node=json.createObjectNode();map.forEach((k,v)->node.set(k.toString(),tree(v)));return node;}
        if(value instanceof Collection<?> list){ArrayNode node=json.createArrayNode();list.forEach(v->node.add(tree(v)));return node;}
        return json.valueToTree(value);
    }
}
