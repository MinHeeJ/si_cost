package com.sicost.developers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.areasops.AddressService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import static com.sicost.developers.DeveloperInput.*;
import static com.sicost.developers.DeveloperQueryService.*;

@Service
public class DeveloperService {
    private final DeveloperRepository repository;
    private final DeveloperQueryService queries;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final MasterIdService ids;
    private final AddressService addresses;
    public DeveloperService(DeveloperRepository repository,DeveloperQueryService queries,RevisionService revisions,ChangeHistoryWriter history,MasterIdService ids,AddressService addresses){this.repository=repository;this.queries=queries;this.revisions=revisions;this.history=history;this.ids=ids;this.addresses=addresses;}
    private DeveloperMapper mapper(){return repository.mapper();}
    public JsonNode create(Save input) {
        try{return repository.write(()->createLocked(input));}
        catch(DuplicateKeyException e){if(input.managementNo()!=null&&mapper().managementExists(input.managementNo()))throw duplicate();throw e;}
    }
    private JsonNode createLocked(Save input) {
        String actor=repository.actor();Map<String,Object> row=new LinkedHashMap<>(input.values());validate(row,null);
        String number=input.managementNo()==null?ids.nextManagementNo():input.managementNo();repository.lockNumber(number);
        if(mapper().managementExists(number))throw duplicate();
        List<Map<String,String>> warnings=new ArrayList<>();
        if(row.get("hireDate")!=null)for(String existing:mapper().samePerson(row.get("name").toString(),date(row.get("hireDate"))))warnings.add(Map.of("field","name","message","동일한 이름과 입사일이 있습니다: "+existing));
        String id=ids.nextDeveloperId();row.put("id",id);row.put("managementNo",number);row.put("regUserId",actor);row.put("regDttm",now());one(mapper().insert(row));
        String start=month(row.get("hireDate")==null?queries.today():date(row.get("hireDate")));
        Map<String,Object> price=priceWrite(id,start,actor);price.put("end",null);price.put("rate",row.get("monthlyRate"));price.put("pay",row.get("monthlyPay"));one(mapper().insertPrice(price));
        replaceSkills(id,skills(row),actor);
        append("TB_HR_DEV",id,"NEW","등록",null,number+" "+row.get("name"));
        append("TB_HR_DEV_PRC",id+":"+start,"NEW","첫 가격",null,priceText(price));
        queries.synchronize(id,false);
        return queries.tree(Map.of("id",id,"managementNo",number,"warnings",warnings));
    }
    public JsonNode update(String id,Save input){return repository.write(()->updateLocked(id,input));}
    private JsonNode updateLocked(String id,Save input) {
        String actor=repository.actor();var snapshot=repository.snapshot(id,true);Map<String,Object> old=snapshot.developer();
        revisions.requireExpected(input.expectedRevision(),repository.revision(id,true));
        Map<String,Object> row=new LinkedHashMap<>(old);row.putAll(input.values());validate(row,old);
        if(row.get("hireDate")!=null&&old.get("retireDate")!=null&&date(row.get("hireDate")).isAfter(date(old.get("retireDate"))))throw InputValidator.invalid("hireDate");
        Map<String,Object> baseline=queries.editPrice(snapshot.prices());
        BigDecimal oldRate=baseline==null?null:amount(baseline.get("mon_uprc")),oldPay=baseline==null?null:amount(baseline.get("mon_pay_amt"));
        BigDecimal rate=input.values().containsKey("monthlyRate")?(BigDecimal)row.get("monthlyRate"):oldRate;
        BigDecimal pay=input.values().containsKey("monthlyPay")?(BigDecimal)row.get("monthlyPay"):oldPay;
        boolean priceChanged=!Objects.equals(rate,oldRate)||!Objects.equals(pay,oldPay);
        List<String> oldSkills=skillNames(snapshot.skills()),newSkills=input.values().containsKey("skills")?skills(row):oldSkills;
        boolean skillChanged=!newSkills.equals(oldSkills);
        List<String> fields=List.of("name","employeeType","companyId","gradeCode","jobCode","subJobCode","positionCode","directClass","partId","hireDate","homeAreaCode","homeAddress");
        List<String> changed=fields.stream().filter(k->!same(old.get(k),row.get(k))).toList();
        if(changed.isEmpty()&&!priceChanged&&!skillChanged)return queries.tree(Map.of("changed",false));
        if(priceChanged){mapper().lockClose();if(mapper().closed(month(queries.today())))throw new ApiException(409,"CLOSED_MONTH","현재월이 마감되어 금액을 변경할 수 없습니다.","monthlyRate",Map.of("blockingMonths",List.of(YearMonth.from(queries.today()).toString())));}
        row.put("modUserId",actor);row.put("modDttm",now());one(mapper().update(row));
        for(String field:changed)append("TB_HR_DEV",id,"UPD",field,Objects.toString(old.get(field),null),Objects.toString(row.get(field),null));
        if(skillChanged){replaceSkills(id,newSkills,actor);append("TB_HR_DEV",id,"UPD","보유 스킬",history.skillsJson(oldSkills),history.skillsJson(newSkills));}
        if(priceChanged)savePrice(id,snapshot.prices(),new PriceChange(rate,pay,oldRate,oldPay),actor);
        queries.synchronize(id,false);return queries.tree(Map.of("changed",true));
    }
    private record PriceChange(BigDecimal rate,BigDecimal pay,BigDecimal oldRate,BigDecimal oldPay) {}
    private void savePrice(String id,List<Map<String,Object>> rows,PriceChange change,String actor) {
        String current=month(queries.today());Map<String,Object> same=null,previous=null,next=null;
        for(var r:rows){String start=r.get("aply_bgn_ym").toString();int c=start.compareTo(current);if(c==0)same=r;if(c<0&&(previous==null||start.compareTo(previous.get("aply_bgn_ym").toString())>0))previous=r;if(c>0&&(next==null||start.compareTo(next.get("aply_bgn_ym").toString())<0))next=r;}
        Map<String,Object> write=priceWrite(id,current,actor);write.put("rate",change.rate());write.put("pay",change.pay());
        if(same!=null)one(mapper().updatePrice(write));
        else {
            if(previous!=null){Map<String,Object> close=priceWrite(id,previous.get("aply_bgn_ym").toString(),actor);String end=month(queries.today().withDayOfMonth(1).minusMonths(1));close.put("end",end);one(mapper().closePrice(close));append("TB_HR_DEV_PRC",id+":"+previous.get("aply_bgn_ym"),"UPD","종료월",Objects.toString(previous.get("aply_end_ym"),null),end);}
            write.put("end",next==null?null:YearMonth.parse(apiMonth(next.get("aply_bgn_ym"))).minusMonths(1).format(DateTimeFormatter.ofPattern("uuuuMM")));one(mapper().insertPrice(write));
        }
        append("TB_HR_DEV_PRC",id+":"+current,"UPD","월 단가",Objects.toString(change.oldRate(),null),Objects.toString(change.rate(),null));
        append("TB_HR_DEV_PRC",id+":"+current,"UPD","월 급여·지급액",Objects.toString(change.oldPay(),null),Objects.toString(change.pay(),null));
    }
    private boolean same(Object a,Object b){if(a==null||b==null)return a==b;return a.toString().equals(b.toString());}
    private LocalDateTime now(){return LocalDateTime.ofInstant(queriesClockInstant(),ZoneId.of("Asia/Seoul"));}
    private Instant queriesClockInstant(){return queries.instant();}
    private void validate(Map<String,Object> row,Map<String,Object> old) {
        Map<String,String> groups=Map.of("employeeType","EMP_TYPE","gradeCode","GRADE","jobCode","JOB","subJobCode","JOB","positionCode","JBPS","directClass","DRCT_CLS");
        for(var e:groups.entrySet()){String v=(String)row.get(e.getKey());if(v==null)continue;var code=mapper().code(e.getValue(),v);PeopleInputPolicy.selectableCode(v,old==null?null:(String)old.get(e.getKey()),code==null?null:new PeopleInputPolicy.CodeSelection(v,(String)code.get("useYn"),(String)code.get("delYn")),e.getKey());}
        if(Objects.equals(row.get("jobCode"),row.get("subJobCode")))row.put("subJobCode",null);
        String company=(String)row.get("companyId");if(company==null&&"OS".equals(row.get("employeeType")))throw InputValidator.invalid("companyId");
        if(company!=null){var c=mapper().company(company);boolean keep=old!=null&&Objects.equals(company,old.get("companyId"));if(c==null||(!keep&&(!"Y".equals(c.get("useYn"))||!Set.of("VNDR","BOTH").contains(c.get("type")))))throw InputValidator.invalid("companyId");}
        String part=(String)row.get("partId");var p=mapper().part(part);if(p==null||(!"Y".equals(p.get("useYn"))&&(old==null||!Objects.equals(part,old.get("partId")))))throw InputValidator.invalid("partId");
        var area=addresses.resolve((String)row.get("homeAreaCode"),old==null?null:(String)old.get("homeAreaCode"),"EMD");row.put("homeAreaCode",area.homeAreaCode());row.put("homeAddress",area.homeAddress());
    }
    @SuppressWarnings("unchecked") private List<String> skills(Map<String,Object> row){return (List<String>)row.getOrDefault("skills",List.of());}
    private void replaceSkills(String id,List<String> skills,String actor){mapper().deleteSkills(id);int order=0;for(String name:skills){Map<String,Object> write=new HashMap<>();write.put("id",id);write.put("name",name);write.put("order",++order);write.put("actor",actor);write.put("now",now());one(mapper().insertSkill(write));}}
    private Map<String,Object> priceWrite(String id,String month,String actor){Map<String,Object> r=new HashMap<>();r.put("id",id);r.put("month",month);r.put("actor",actor);r.put("now",now());return r;}
    private String priceText(Map<String,Object> price){return "월 단가="+Objects.toString(price.get("rate"),"NULL")+", 월 급여·지급액="+Objects.toString(price.get("pay"),"NULL");}
    private void append(String table,String key,String type,String item,String before,String after){history.append(new ChangeHistoryWriter.Change(table,key,type,item,before,after,null));}
    private ApiException duplicate(){return new ApiException(409,"DUPLICATE_MANAGEMENT_NO","이미 등록된 관리번호입니다.","managementNo",null);}
    private void one(int n){if(n!=1)throw new IllegalStateException("개발자 정보를 저장하지 못했습니다.");}
}
