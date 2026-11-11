package com.sicost.developers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.*;
import java.util.*;

public final class DeveloperInput {
    private DeveloperInput() {}
    public static final Set<String> SORTS=Set.of("name","companyName","employeeType","grade","job","status","withdrawalDate","monthlyRate");
    private static final Set<String> FIELDS=Set.of("name","employeeType","companyId","gradeCode","jobCode","subJobCode","positionCode","directClass","partId","hireDate","monthlyRate","monthlyPay","skills","homeAreaCode");
    public record Save(Map<String,Object> values,String managementNo,String expectedRevision) {}
    public record Query(Map<String,String> filters,String sort,String direction,int page,int size) {}
    public record Selection(Query query,String month,java.time.LocalDate today,long offset) {}
    public static Save parse(JsonNode body,boolean edit) {
        if(body==null||!body.isObject())throw InputValidator.invalid("body");
        Set<String> allowed=new HashSet<>(FIELDS);allowed.add(edit?"expectedRevision":"managementNo");
        body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw unknown(k);});
        Map<String,Object> values=new LinkedHashMap<>();
        for(String key:List.of("name","employeeType","companyId","jobCode","directClass","partId")) {
            if(!body.has(key))throw InputValidator.invalid(key);
        }
        for(String key:FIELDS) {
            if(!body.has(key))continue;
            JsonNode node=body.get(key);
            if(key.equals("skills")) {
                if(node.isNull()){values.put(key,List.of());continue;}
                if(!node.isArray())throw InputValidator.invalid(key);
                List<String> skills=new ArrayList<>();for(JsonNode v:node){if(!v.isTextual())throw InputValidator.invalid(key);skills.add(v.textValue());}
                values.put(key,PeopleInputPolicy.skills(skills,false));continue;
            }
            boolean required=Set.of("name","employeeType","jobCode","directClass","partId").contains(key);
            int max=key.equals("name")?50:key.equals("companyId")||key.equals("partId")?20: key.startsWith("monthly")?100:10;
            String text=text(body,key,max,required);
            Object value=text;
            if(key.startsWith("monthly"))value=PeopleInputPolicy.amount(text,key);
            if(key.equals("hireDate")&&text!=null)value=PeopleInputPolicy.date(text,key);
            if(key.equals("employeeType"))MasterDataValidator.enumValue(text,Set.of("FT","CT","FL","OS"),key);
            if(key.equals("directClass"))MasterDataValidator.enumValue(text,Set.of("DRCT","INDRCT"),key);
            values.put(key,value);
        }
        if(!edit){values.putIfAbsent("positionCode","MEMBER");values.putIfAbsent("skills",List.of());}
        String number=edit?null:text(body,"managementNo",20,false);
        if(number!=null&&!number.matches("[A-Za-z0-9-]{1,20}"))throw InputValidator.invalid("managementNo");
        return new Save(Collections.unmodifiableMap(values),number,edit?text(body,"expectedRevision",200,true):null);
    }
    private static String text(JsonNode body,String key,int max,boolean required) {
        JsonNode n=body.get(key);if(n==null||n.isNull()){if(required)throw InputValidator.invalid(key);return null;}
        if(!n.isTextual())throw InputValidator.invalid(key);
        return required?InputValidator.text(n.textValue(),max,key):InputValidator.optionalText(n.textValue(),max,key);
    }
    public static void allowQuery(Map<String,String> query,Set<String> allowed){query.keySet().forEach(k->{if(!allowed.contains(k))throw unknown(k);});}
    public static Query query(Map<String,String> input) {
        allowQuery(input,Set.of("employeeType","jobCode","directClass","workStatus","q","includeStopped","page","size","sort","direction"));
        Map<String,String> f=new HashMap<>();
        for(String k:List.of("employeeType","jobCode","directClass","workStatus"))if(input.containsKey(k))f.put(k,InputValidator.text(input.get(k),10,k));
        if(f.containsKey("employeeType"))MasterDataValidator.enumValue(f.get("employeeType"),Set.of("FT","CT","FL","OS"),"employeeType");
        if(f.containsKey("directClass"))MasterDataValidator.enumValue(f.get("directClass"),Set.of("DRCT","INDRCT"),"directClass");
        if(f.containsKey("workStatus"))MasterDataValidator.enumValue(f.get("workStatus"),Set.of("INPUT","WTHDR","IDLE","LEAVE","RETIRE"),"workStatus");
        String q=MasterDataValidator.search(input.get("q"));if(q!=null)f.put("q",q);
        String stopped=input.getOrDefault("includeStopped","false");if(!Set.of("true","false").contains(stopped))throw InputValidator.invalid("includeStopped");
        f.put("includeStopped",stopped);
        String sort=input.get("sort"),direction=input.getOrDefault("direction","ASC");
        if(sort!=null&&!SORTS.contains(sort))throw InputValidator.invalid("sort");
        if(!Set.of("ASC","DESC").contains(direction))throw InputValidator.invalid("direction");
        return new Query(f,sort,direction,InputValidator.page(number(input,"page",1)),InputValidator.size(number(input,"size",50)));
    }
    private static int number(Map<String,String> q,String key,int fallback){try{String v=q.get(key);if(v==null)return fallback;if(!v.matches("[0-9]+"))throw InputValidator.invalid(key);return Integer.parseInt(v);}catch(NumberFormatException e){throw InputValidator.invalid(key);}}
    public static ApiException unknown(String field){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
}
