package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.areasops.AddressService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CandidateRevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.validation.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.candidates.CandidateInput.*;

@Service
public class CandidateService {
    private final CandidateRepository repository;
    private final CandidateQueryService queries;
    private final CandidateRevisionService revisions;
    private final ChangeHistoryWriter history;
    private final MasterIdService ids;
    private final AddressService addresses;
    public CandidateService(CandidateRepository repository,CandidateQueryService queries,CandidateRevisionService revisions,ChangeHistoryWriter history,MasterIdService ids,AddressService addresses){this.repository=repository;this.queries=queries;this.revisions=revisions;this.history=history;this.ids=ids;this.addresses=addresses;}
    public JsonNode create(Save input){return repository.write(()->{
        String actor=repository.actor();Map<String,Object> row=new LinkedHashMap<>(input.values());validate(row,null);metadata(row,null,actor);
        String id=ids.nextCandidateId();row.put("id",id);one(repository.mapper().insert(row));
        replaceSkills(row,skills(row));event(row,"BEFORE","등록");
        append(id,"NEW","등록",null,id);repository.committed();
        return queries.tree(Map.of("id",id,"warnings",input.warnings()));
    });}
    public JsonNode update(String id,Save input){return repository.write(()->{
        String actor=repository.actor();String actual=revisions.revision(id,true);
        if(!Objects.equals(actual,input.expectedRevision()))throw new ApiException(409,"STALE_WRITE","다른 작업에서 변경되었습니다. 다시 조회해 주세요.");
        Map<String,Object> old=queries.required(id),row=new LinkedHashMap<>(input.values());row.put("id",id);
        validate(row,old);metadata(row,old,actor);
        List<String> fields=List.of("name","employeeType","companyId","phone","email","consent","jobCode","subJobCode","gradeCode","homeAreaCode","homeAddress","desiredRate","availableDate","sourceCode","memo");
        List<String> changed=fields.stream().filter(k->!same(old.get(k),row.get(k))).toList();
        List<String> before=repository.mapper().skills(id),after=skills(row);boolean skillChanged=!before.equals(after);
        if(changed.isEmpty()&&!skillChanged)return queries.tree(Map.of("changed",false));
        int serial=PeopleInputPolicy.nextSerial(repository.mapper().greatestSerial(id));
        one(repository.mapper().update(row));
        if(skillChanged){replaceSkills(row,after);append(id,"UPD","보유 스킬",history.skillsJson(before),history.skillsJson(after));}
        for(String key:changed)append(id,"UPD",key.equals("phone")?"TELNO":key.equals("email")?"EMAIL_ADDR":key,Objects.toString(old.get(key),null),Objects.toString(row.get(key),null));
        row.put("sn",serial);event(row,old.get("contactStatus").toString(),"정보 수정");repository.committed();
        return queries.tree(Map.of("changed",true));
    });}
    private void validate(Map<String,Object> row,Map<String,Object> old) {
        for(var entry:Map.of("employeeType","EMP_TYPE","jobCode","JOB","subJobCode","JOB","gradeCode","GRADE","sourceCode","CAND_SRC").entrySet()){
            String value=(String)row.get(entry.getKey());
            if(value!=null)PeopleInputPolicy.selectableCode(value,old==null?null:(String)old.get(entry.getKey()),repository.mapper().code(entry.getValue(),value),entry.getKey());
        }
        if("OS".equals(row.get("employeeType"))){
            String company=(String)row.get("companyId");if(company==null)throw InputValidator.invalid("companyId");
            Map<String,Object> c=repository.mapper().company(company);boolean keep=old!=null&&Objects.equals(company,old.get("companyId"));
            if(c==null||(!keep&&(!"Y".equals(c.get("useYn"))||!Set.of("VNDR","BOTH").contains(c.get("type")))))throw InputValidator.invalid("companyId");
        }
        var address=addresses.resolve((String)row.get("homeAreaCode"),old==null?null:(String)old.get("homeAreaCode"),"SGG");
        row.put("homeAreaCode",address.homeAreaCode());row.put("homeAddress",address.homeAddress());
    }
    private void metadata(Map<String,Object> row,Map<String,Object> old,String actor){
        boolean consent=Boolean.TRUE.equals(row.get("consent")),kept=old!=null&&Boolean.TRUE.equals(old.get("consent"));
        row.put("consentYn",consent?"Y":"N");row.put("consentDate",!consent?null:kept?old.get("consentDate"):queries.today());
        row.put("consentUserId",!consent?null:kept?old.get("consentUserId"):actor);
        row.put("actor",actor);row.put("now",queries.now());row.put("today",queries.today());
    }
    private void replaceSkills(Map<String,Object> row,List<String> skills){
        String id=row.get("id").toString();repository.mapper().deleteSkills(id);int order=0;
        for(String name:skills){Map<String,Object> skill=new HashMap<>();skill.put("id",id);skill.put("name",name);skill.put("order",++order);skill.put("actor",row.get("actor"));skill.put("now",row.get("now"));one(repository.mapper().insertSkill(skill));}
    }
    private void event(Map<String,Object> row,String status,String memo){
        Map<String,Object> event=new HashMap<>();for(String key:List.of("id","actor","now","today"))event.put(key,row.get(key));
        event.put("sn",row.getOrDefault("sn",1));event.put("status",status);event.put("memo",memo);one(repository.mapper().insertContact(event));
        history.append(new ChangeHistoryWriter.Change("TB_HR_CAND_CNTC",row.get("id")+":"+event.get("sn"),"NEW",memo,null,memo,null));
    }
    @SuppressWarnings("unchecked") private List<String> skills(Map<String,Object> row){return (List<String>)row.get("skills");}
    private boolean same(Object a,Object b){return a==null||b==null?a==b:a.toString().equals(b.toString());}
    private void append(String id,String type,String field,String before,String after){history.append(new ChangeHistoryWriter.Change("TB_HR_CAND",id,type,field,before,after,null));}
    private void one(int count){if(count!=1)throw new IllegalStateException("후보 정보를 저장하지 못했습니다.");}
}
