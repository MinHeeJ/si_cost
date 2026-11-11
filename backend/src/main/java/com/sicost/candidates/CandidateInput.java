package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.*;
import java.util.*;

public final class CandidateInput {
    private CandidateInput() {}
    public static final Set<String> SORTS=Set.of("name","employeeType","job","grade","desiredRate","availableDate","contactStatus","lastContactDate","source");
    private static final Set<String> FIELDS=Set.of("name","employeeType","companyId","phone","email","consent","jobCode","subJobCode","gradeCode","homeAreaCode","desiredRate","availableDate","sourceCode","memo","skills");
    public record Save(Map<String,Object> values,String expectedRevision,List<Warning> warnings) {}
    public record Warning(String field,String message) {}
    public record Query(Map<String,String> filters,String sort,String direction,int page,int size) {}
    public record Selection(Query query,java.time.LocalDate through,long offset) {}
    public static Save parse(JsonNode body,boolean editing) {
        if(body==null||!body.isObject())throw InputValidator.invalid("body");
        Set<String> allowed=new HashSet<>(FIELDS);if(editing)allowed.add("expectedRevision");
        body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw unknown(k);});
        Map<String,Object> values=new LinkedHashMap<>();
        for(String key:List.of("name","employeeType","jobCode","gradeCode","sourceCode"))values.put(key,text(body,key,key.equals("name")?50:10,true));
        MasterDataValidator.enumValue((String)values.get("employeeType"),Set.of("FL","OS"),"employeeType");
        for(String key:List.of("companyId","phone","email","subJobCode","homeAreaCode","memo"))values.put(key,text(body,key,switch(key){case "companyId","phone"->20;case "email"->100;case "memo"->500;default->10;},false));
        String phone=(String)values.get("phone"),email=(String)values.get("email");
        if(phone!=null&&!phone.matches("^[0-9][0-9-]{7,19}$"))throw InputValidator.invalid("phone");
        if(email!=null&&!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw InputValidator.invalid("email");
        if(body.has("consent")&&!body.get("consent").isBoolean())throw InputValidator.invalid("consent");
        boolean consent=body.path("consent").asBoolean(false);values.put("consent",consent);
        if((phone!=null||email!=null)&&!consent)throw new ApiException(400,"CONSENT_REQUIRED","연락처가 있으면 개인정보 수집·이용 동의를 확인해 주세요.","consent",null);
        values.put("desiredRate",PeopleInputPolicy.desiredRate(text(body,"desiredRate",200,true)));
        values.put("availableDate",PeopleInputPolicy.date(text(body,"availableDate",10,true),"availableDate"));
        if(!body.path("skills").isArray())throw InputValidator.invalid("skills");
        List<String> skills=new ArrayList<>();for(JsonNode skill:body.get("skills")){if(!skill.isTextual())throw InputValidator.invalid("skills");skills.add(skill.textValue());}
        values.put("skills",PeopleInputPolicy.skills(skills,true));
        if(Objects.equals(values.get("jobCode"),values.get("subJobCode")))values.put("subJobCode",null);
        List<Warning> warnings=new ArrayList<>();
        if("FL".equals(values.get("employeeType"))&&values.get("companyId")!=null){values.put("companyId",null);warnings.add(new Warning("companyId","프리랜서는 업체를 저장하지 않습니다."));}
        return new Save(Collections.unmodifiableMap(values),editing?text(body,"expectedRevision",200,true):null,List.copyOf(warnings));
    }
    private static String text(JsonNode body,String key,int max,boolean required) {
        JsonNode n=body.get(key);if(n==null||n.isNull()){if(required)throw InputValidator.invalid(key);return null;}
        if(!n.isTextual())throw InputValidator.invalid(key);
        return required?InputValidator.text(n.textValue(),max,key):InputValidator.optionalText(n.textValue(),max,key);
    }
    public static String id(String id){if(id==null||!id.matches("CAND-[0-9]{6,15}"))throw InputValidator.invalid("id");return id;}
    public static void allowQuery(Map<String,String> query,Set<String> allowed){query.keySet().forEach(k->{if(!allowed.contains(k))throw unknown(k);});}
    public static Query query(Map<String,String> input) {
        allowQuery(input,Set.of("jobCode","gradeCode","employeeType","contactStatus","availability","page","size","sort","direction"));
        Map<String,String> filters=new HashMap<>();
        for(String key:List.of("jobCode","gradeCode","employeeType","contactStatus","availability"))if(input.containsKey(key))filters.put(key,InputValidator.text(input.get(key),20,key));
        for(String key:List.of("jobCode","gradeCode"))if(filters.containsKey(key))InputValidator.text(filters.get(key),10,key);
        if(filters.containsKey("employeeType"))MasterDataValidator.enumValue(filters.get("employeeType"),Set.of("FL","OS"),"employeeType");
        if(filters.containsKey("contactStatus"))MasterDataValidator.enumValue(filters.get("contactStatus"),Set.of("BEFORE","DONE","ITVW","HOLD","CTRT"),"contactStatus");
        if(filters.containsKey("availability"))MasterDataValidator.enumValue(filters.get("availability"),Set.of("NEXT_MONTH","THREE_MONTHS"),"availability");
        String sort=input.get("sort"),direction=input.getOrDefault("direction","ASC");
        if(sort!=null&&!SORTS.contains(sort))throw InputValidator.invalid("sort");
        if(!Set.of("ASC","DESC").contains(direction))throw InputValidator.invalid("direction");
        return new Query(Map.copyOf(filters),sort,direction,InputValidator.page(number(input,"page",1)),InputValidator.size(number(input,"size",50)));
    }
    private static int number(Map<String,String> input,String key,int fallback){try{String v=input.get(key);if(v==null)return fallback;if(!v.matches("[0-9]+"))throw InputValidator.invalid(key);return Integer.parseInt(v);}catch(NumberFormatException e){throw InputValidator.invalid(key);}}
    private static ApiException unknown(String key){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);}
}
