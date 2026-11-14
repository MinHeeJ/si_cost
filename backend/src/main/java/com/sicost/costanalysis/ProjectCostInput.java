package com.sicost.costanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.costanalysis.ProjectCostDtos.*;

public final class ProjectCostInput {
    private ProjectCostInput() {}
    public static String id(String value) {return ProjectInputPolicy.text(value,20,"id");}
    public static String query(MultiValueMap<String,String> query,boolean simulation) {
        query.forEach((key,values)->{if(simulation||!key.equals("month")||values.size()!=1)throw ProjectInputPolicy.invalid(key);});
        String month=query.getFirst("month");
        if(month==null||month.isBlank())return null;
        return ProjectInputPolicy.month(month,"month").toString();
    }
    public static SimulationInput simulation(JsonNode body) {
        if(body==null||!body.isObject())throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!Set.of("month","n","c","m","r").contains(key))throw ProjectInputPolicy.invalid(key);});
        String month=null;
        if(body.has("month")) {
            if(!body.get("month").isTextual())throw ProjectInputPolicy.invalid("month");
            if(!body.get("month").asText().isBlank())month=ProjectInputPolicy.month(body.get("month").asText(),"month").toString();
        }
        return new SimulationInput(month,body.has("n")?integer(body.get("n"),"n",false):1,money(body.get("c"),"c"),
            body.has("m")?integer(body.get("m"),"m",true):null,body.has("r")?money(body.get("r"),"r"):null);
    }
    private static int integer(JsonNode value,String field,boolean positive) {
        if(value==null||!value.isIntegralNumber()||!value.canConvertToInt()||(positive&&value.intValue()<1))throw ProjectInputPolicy.invalid(field);
        return value.intValue();
    }
    private static String money(JsonNode value,String field) {
        if(value==null||!value.isTextual())throw ProjectInputPolicy.invalid(field);
        String text=value.asText().strip();
        if(!text.matches("(0|[1-9][0-9]{0,14})"))throw ProjectInputPolicy.invalid(field);
        return text;
    }
}
