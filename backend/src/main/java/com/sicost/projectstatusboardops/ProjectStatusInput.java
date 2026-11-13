package com.sicost.projectstatusboardops;

import com.sicost.apiops.ProjectAnalysisInput;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class ProjectStatusInput {
    private ProjectStatusInput() {}
    public static final List<String> COLUMNS=List.of("light","projectName","pm","currentStep","planProgress","actualProgress","costUsage","costProgressGap","openIssueCount","overdueIssueCount","lastUpdatedDate","elapsedDays");
    public record Issues(String status,String importance,String projectId,int page,int size) {}
    public record Export(ProjectAnalysisInput.Query query,List<String> order,List<String> hidden,String unit) {}
    public static Map<String,String> values(MultiValueMap<String,String> query,Set<String> allowed) {
        var result=new LinkedHashMap<String,String>();
        query.forEach((key,entries)->{
            if(!allowed.contains(key)||entries.size()!=1)throw ProjectInputPolicy.invalid(key);
            String value=ProjectInputPolicy.optionalText(entries.get(0),key.equals("order")||key.equals("hidden")?1000:key.equals("projectId")||key.equals("partId")?20:100,key);
            if(value!=null)result.put(key,value);
        });
        return result;
    }
    public static Issues issues(MultiValueMap<String,String> query) {
        var v=values(query,Set.of("status","importance","projectId","page","size"));
        String status=v.getOrDefault("status","OPEN_ACTIVE");
        if(!Set.of("OPEN_ACTIVE","OVERDUE","CLOSED","ALL").contains(status))throw ProjectInputPolicy.invalid("status");
        String importance=ProjectInputPolicy.optionalText(v.get("importance"),10,"importance");
        int page=integer(v.getOrDefault("page","1"),"page"),size=integer(v.getOrDefault("size","50"),"size");
        if(!Set.of(20,50,100).contains(size))throw ProjectInputPolicy.invalid("size");
        return new Issues(status,importance,v.get("projectId"),page,size);
    }
    public static ProjectAnalysisInput.Query summary(MultiValueMap<String,String> query) {
        return board(values(query,Set.of("light","contractType","partId")));
    }
    public static Export export(MultiValueMap<String,String> query) {
        var v=values(query,Set.of("light","contractType","partId","sort","direction","order","hidden","unit"));
        List<String> order=query.containsKey("order")?columns(v.get("order"),false):null;
        List<String> hidden=query.containsKey("hidden")?columns(v.get("hidden"),true):null;
        String unit=v.get("unit");
        if(unit!=null&&!Set.of("원","천원","백만원").contains(unit))throw ProjectInputPolicy.invalid("unit");
        var filters=new LinkedHashMap<>(v);filters.keySet().removeAll(Set.of("order","hidden","unit"));
        return new Export(board(filters),order,hidden,unit);
    }
    public static List<String> columns(String value,boolean hidden) {
        List<String> list=value==null||value.isBlank()?Collections.emptyList():Arrays.stream(value.split(",",-1)).map(String::trim).toList();
        String field=hidden?"hidden":"order";
        if(new HashSet<>(list).size()!=list.size()||!COLUMNS.containsAll(list)||(!hidden&&list.size()!=COLUMNS.size())||(hidden&&list.contains("projectName")))throw ProjectInputPolicy.invalid(field);
        return List.copyOf(list);
    }
    private static ProjectAnalysisInput.Query board(Map<String,String> values) {
        var input=new LinkedMultiValueMap<String,String>();values.forEach(input::add);
        var parsed=ProjectAnalysisInput.query(input,true);
        return new ProjectAnalysisInput.Query(parsed.filters(),1,Integer.MAX_VALUE,parsed.sort(),parsed.direction());
    }
    private static int integer(String value,String field) {
        try {if(!value.matches("[0-9]{1,10}"))throw ProjectInputPolicy.invalid(field);int result=Integer.parseInt(value);if(result<1)throw ProjectInputPolicy.invalid(field);return result;}
        catch(NumberFormatException ex){throw ProjectInputPolicy.invalid(field);}
    }
}
