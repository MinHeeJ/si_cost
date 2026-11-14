package com.sicost.projectcostsops;

import com.sicost.apiops.ProjectAnalysisInput;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class ProjectCostExportInput {
    private ProjectCostExportInput() {}
    public static final List<String> COLUMNS=List.of("projectName","contractType","status","originalContractAmount","contractAmount","planMm","actualMm","cumulativeRevenue","cumulativeCost","cumulativeProfit","cumulativeMargin","forecastMargin","monthlyMargin","targetMargin","targetGap","budgetAmount","budgetUsage","pricePerMm","costPerMm","profitPerMm","freeMm","freeLaborCost","alerts");
    private static final Set<String> FILTERS=Set.of("month","projectId","contractType","status","alertOnly");
    public record Export(ProjectAnalysisInput.Query query,List<String> order,List<String> hidden,String unit) {}
    public static ProjectAnalysisInput.Query summary(MultiValueMap<String,String> query) {
        return all(values(query,FILTERS));
    }
    public static Export export(MultiValueMap<String,String> query) {
        var allowed=new HashSet<>(FILTERS);allowed.addAll(Set.of("sort","direction","order","hidden","unit"));
        var values=values(query,allowed);
        List<String> order=query.containsKey("order")?columns(values.remove("order"),false):null;
        List<String> hidden=query.containsKey("hidden")?columns(values.remove("hidden"),true):null;
        String unit=values.remove("unit");
        if(unit!=null&&!Set.of("원","천원","백만원").contains(unit))throw ProjectInputPolicy.invalid("unit");
        return new Export(all(values),order,hidden,unit);
    }
    private static Map<String,String> values(MultiValueMap<String,String> query,Set<String> allowed) {
        var result=new LinkedHashMap<String,String>();
        query.forEach((key,entries)->{
            if(!allowed.contains(key)||entries.size()!=1)throw ProjectInputPolicy.invalid(key);
            String value=ProjectInputPolicy.optionalText(entries.get(0),Set.of("order","hidden").contains(key)?1000:"projectId".equals(key)?20:100,key);
            if(value!=null)result.put(key,value);
        });
        return result;
    }
    public static List<String> columns(String value,boolean hidden) {
        var list=value==null||value.isBlank()?List.<String>of():Arrays.stream(value.split(",",-1)).map(String::trim).toList();
        if(new HashSet<>(list).size()!=list.size()||!COLUMNS.containsAll(list)||(!hidden&&list.size()!=COLUMNS.size())||(hidden&&list.contains("projectName")))throw ProjectInputPolicy.invalid(hidden?"hidden":"order");
        return List.copyOf(list);
    }
    private static ProjectAnalysisInput.Query all(Map<String,String> values) {
        var query=new LinkedMultiValueMap<String,String>();values.forEach(query::add);
        var parsed=ProjectAnalysisInput.query(query,false);
        return new ProjectAnalysisInput.Query(parsed.filters(),1,Integer.MAX_VALUE,parsed.sort(),parsed.direction());
    }
}
