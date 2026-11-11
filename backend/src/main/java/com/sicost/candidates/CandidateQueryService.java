package com.sicost.candidates;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.areasops.AddressService;
import com.sicost.areasops.AddressDtos.AreaPath;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CandidateRevisionService;
import com.sicost.common.validation.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.candidates.CandidateInput.*;

@Service
public class CandidateQueryService {
    private final CandidateRepository repository;
    private final CandidateRevisionService revisions;
    private final AddressService addresses;
    private final ObjectMapper json;
    private final Clock clock;
    public CandidateQueryService(CandidateRepository repository,CandidateRevisionService revisions,AddressService addresses,ObjectMapper json,Clock clock){this.repository=repository;this.revisions=revisions;this.addresses=addresses;this.json=json;this.clock=clock;}
    public LocalDate today(){return PeopleInputPolicy.today(clock);}
    public LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),com.sicost.common.TimeConfiguration.SEOUL);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public JsonNode list(Query query) {
        repository.actor();
        for(String key:List.of("jobCode","gradeCode"))if(query.filters().containsKey(key)&&repository.mapper().code(key.equals("jobCode")?"JOB":"GRADE",query.filters().get(key))==null)throw InputValidator.invalid(key);
        String availability=query.filters().get("availability");
        LocalDate through=availability==null?null:YearMonth.from(today()).plusMonths(availability.equals("NEXT_MONTH")?1:3).atEndOfMonth();
        Selection selection=new Selection(query,through,(long)(query.page()-1)*query.size());
        List<JsonNode> items=repository.mapper().list(selection).stream().map(row->view(row,false)).toList();
        return tree(Map.of("items",items,"total",repository.mapper().count(selection),"page",query.page(),"size",query.size()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public JsonNode get(String id){repository.actor();return view(required(id),true);}
    public Map<String,Object> required(String id){Map<String,Object> row=repository.mapper().find(id);if(row==null)throw new ApiException(404,"NOT_FOUND","후보를 찾을 수 없습니다.");return row;}
    private JsonNode view(Map<String,Object> row,boolean detail) {
        String id=row.get("id").toString();Map<String,Object> labels=repository.mapper().labels(id),result=new LinkedHashMap<>();
        for(String key:List.of("id","name","employeeType","homeAddress","contactStatus"))result.put(key,row.get(key));
        for(String key:List.of("companyName","job","grade","source"))result.put(key,labels.get(key));
        result.put("desiredRate",row.get("desiredRate").toString());
        for(String key:List.of("availableDate","lastContactDate"))result.put(key,date(row.get(key)));
        result.put("skills",repository.mapper().skills(id));result.put("revision",revisions.revision(id,false));
        if(detail){
            for(String key:List.of("companyId","phone","email","consent","jobCode","subJobCode","gradeCode","homeAreaCode","sourceCode","memo","consentUserId"))result.put(key,row.get(key));
            result.put("consentDate",date(row.get("consentDate")));result.put("consentUserName",labels.get("consentUserName"));
            result.put("addressPath",row.get("homeAreaCode")==null?new AreaPath(List.of(),null):addresses.getPath(row.get("homeAreaCode").toString()));
        }
        return tree(result);
    }
    private String date(Object value){return value==null?null:value.toString();}
    public JsonNode tree(Object value){
        if(value==null)return NullNode.instance;
        if(value instanceof JsonNode node)return node;
        if(value instanceof Map<?,?> map){ObjectNode node=json.createObjectNode();map.forEach((k,v)->node.set(k.toString(),tree(v)));return node;}
        if(value instanceof Collection<?> rows){ArrayNode node=json.createArrayNode();rows.forEach(v->node.add(tree(v)));return node;}
        return json.valueToTree(value);
    }
}
