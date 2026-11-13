package com.sicost.apiops;

import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.util.MultiValueMap;

public final class ProjectAnalysisInput {
    private ProjectAnalysisInput() {}
    public static final Set<String> COST_SORTS=Set.of("projectName","contractAmount","actualMm","cumulativeRevenue","cumulativeCost","cumulativeProfit","cumulativeMargin","budgetUsage");
    public static final Set<String> BOARD_SORTS=Set.of("light","projectName","planProgress","actualProgress","costUsage","openIssueCount","lastUpdatedDate");
    public record Query(Map<String,String> filters,int page,int size,String sort,String direction) {
        public String value(String key) {return filters.get(key);}
    }
    public static Query query(MultiValueMap<String,String> values,boolean board) {
        Set<String> allowed=board?Set.of("light","contractType","partId","page","size","sort","direction"):
            Set.of("month","projectId","contractType","status","alertOnly","page","size","sort","direction");
        var result=new HashMap<String,String>();
        values.forEach((key,entries)->{
            if(!allowed.contains(key)||entries.size()!=1)throw ProjectInputPolicy.invalid(key);
            String value=ProjectInputPolicy.text(entries.get(0),key.equals("partId")||key.equals("projectId")?20:100,key);
            if(value!=null)result.put(key,value);
        });
        choice(result,"contractType",Set.of("TK","MO","DP","IN"));
        choice(result,"direction",Set.of("ASC","DESC"));
        choice(result,"sort",board?BOARD_SORTS:COST_SORTS);
        if(board)choice(result,"light",Set.of("위험","주의","정상"));
        else {
            choice(result,"status",Set.of("ING","END"));choice(result,"alertOnly",Set.of("true","false"));
            if(result.containsKey("month"))month(result.get("month"));
        }
        int page=integer(result.getOrDefault("page","1"),"page");
        int size=integer(result.getOrDefault("size","50"),"size");
        if(page<1)throw ProjectInputPolicy.invalid("page");
        if(!Set.of(20,50,100).contains(size))throw ProjectInputPolicy.invalid("size");
        return new Query(Map.copyOf(result),page,size,result.get("sort"),result.getOrDefault("direction","ASC"));
    }
    public static YearMonth month(String value) {
        try {
            if(value==null||!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw ProjectInputPolicy.invalid("month");
            return YearMonth.parse(value);
        } catch(DateTimeException ex) {throw ProjectInputPolicy.invalid("month");}
    }
    private static void choice(Map<String,String> values,String key,Set<String> allowed) {
        if(values.containsKey(key)&&!allowed.contains(values.get(key)))throw ProjectInputPolicy.invalid(key);
    }
    private static int integer(String value,String field) {
        try {if(!value.matches("[0-9]{1,10}"))throw ProjectInputPolicy.invalid(field);return Integer.parseInt(value);}
        catch(NumberFormatException ex){throw ProjectInputPolicy.invalid(field);}
    }
}
