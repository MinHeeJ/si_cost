package com.sicost.assignmentsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.*;
import java.util.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.*;

public final class AssignmentActionsInput {
    private AssignmentActionsInput() {}
    public static void query(Map<String,String> query,Set<String> allowed) {for(String key:query.keySet())if(!allowed.contains(key))throw invalid(key);}
    public static void object(JsonNode node,Set<String> allowed) {
        if(node==null||!node.isObject())throw invalid("body");node.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid(k);});
    }
    public static String string(JsonNode node,String field,boolean required) {
        JsonNode value=node.get(field);if(value==null||value.isNull()){if(required)throw invalid(field);return null;}
        if(!value.isTextual())throw invalid(field);return value.textValue();
    }
    public static int year(JsonNode node) {if(!node.path("year").isIntegralNumber()||!node.path("year").canConvertToInt())throw invalid("year");return node.get("year").intValue();}
    public static int integer(String value,String field,int fallback) {
        if(value==null)return fallback;if(!value.matches("[0-9]{1,9}"))throw invalid(field);return Integer.parseInt(value);
    }
    public static int page(Map<String,String> q) {return AssignmentInputPolicy.page(integer(q.get("page"),"page",1));}
    public static int size(Map<String,String> q) {return AssignmentInputPolicy.size(integer(q.get("size"),"size",50));}
    public static String choice(String value,Set<String> allowed,String field) {
        String key=text(value,10,field);if(!allowed.contains(key))throw invalid(field);return key;
    }
    public static String warning(String value) {
        if(value==null)return null;
        String key=text(value,"UNCONTRACTED".length(),"warning");
        if(!Set.of("MM_OVER","UNCONTRACTED").contains(key))throw invalid("warning");return key;
    }
    public static String possibility(JsonNode node) {
        if(!node.has("extensionPossibility"))throw invalid("extensionPossibility");
        String value=string(node,"extensionPossibility",false);return value==null?null:choice(value,Set.of("H","M","L"),"extensionPossibility");
    }
    public static Create create(JsonNode n) {
        object(n,Set.of("year","projectId","developerId","jobCode","startDate","endDate","contractMm","billingUnitPrice","inputType","contractDeveloperId"));
        String kind=n.has("inputType")?choice(string(n,"inputType",true),Set.of("NEW","ADD","SUB","FREE"),"inputType"):"NEW";
        String contract=optionalText(string(n,"contractDeveloperId",false),20,"contractDeveloperId");
        if("SUB".equals(kind)&&contract==null||!"SUB".equals(kind)&&contract!=null)throw invalid("contractDeveloperId");
        var start=date(string(n,"startDate",true),"startDate");var end=date(string(n,"endDate",true),"endDate");dateRange(start,end);
        return new Create(year(n),text(string(n,"projectId",true),20,"projectId"),text(string(n,"developerId",true),20,"developerId"),text(string(n,"jobCode",true),10,"jobCode"),
            start,end,AssignmentInputPolicy.contractMm(string(n,"contractMm",false),true),string(n,"billingUnitPrice",false),kind,contract);
    }
    public static Change change(JsonNode n) {
        if(n==null||!n.isObject())throw invalid("change");String type=choice(string(n,"type",true),Set.of("EXT","EARLY","UPRC","RATE","EXTP","KIND"),"type");
        Set<String> allowed=new HashSet<>(Set.of("year","expectedRevision","type","reasonCode","memo"));
        allowed.addAll(switch(type){case "EXT"->Set.of("newEndDate");case "EARLY"->Set.of("withdrawalDate");case "UPRC"->Set.of("applyMonth","newUnitPrice");case "RATE"->Set.of("applyMonth","newContractMm");case "EXTP"->Set.of("applyDate","extensionPossibility");default->Set.of("applyDate","inputType");});object(n,allowed);
        return new Change(year(n),expectedRevision(string(n,"expectedRevision",true)),type,
            Set.of("EXTP","KIND").contains(type)?date(string(n,"applyDate",true),"applyDate"):null,
            Set.of("RATE","UPRC").contains(type)?month(string(n,"applyMonth",true),"applyMonth"):null,
            "EXT".equals(type)?date(string(n,"newEndDate",true),"newEndDate"):null,
            "EARLY".equals(type)?date(string(n,"withdrawalDate",true),"withdrawalDate"):null,
            "UPRC".equals(type)?AssignmentInputPolicy.paidUnitPrice(string(n,"newUnitPrice",true)):null,
            "RATE".equals(type)?AssignmentInputPolicy.contractMm(string(n,"newContractMm",true),false):null,
            "EXTP".equals(type)?possibility(n):null,"KIND".equals(type)?choice(string(n,"inputType",true),Set.of("NEW","ADD"),"inputType"):null,
            optionalText(string(n,"reasonCode",false),10,"reasonCode"),AssignmentInputPolicy.memo(string(n,"memo",false)));
    }
    public static PreviewRequest preview(JsonNode n) {
        if(n.has("id")||n.has("change")){object(n,Set.of("id","change"));return new PreviewRequest(null,text(string(n,"id",true),20,"id"),change(n.get("change")));}
        return new PreviewRequest(create(n),null,null);
    }
    public static List<String> ids(JsonNode n,String field) {
        if(!n.has(field))return List.of();if(!n.get(field).isArray())throw invalid(field);List<String> values=new ArrayList<>();
        for(JsonNode v:n.get(field)){if(!v.isTextual())throw invalid(field);String id=text(v.textValue(),20,field);if(values.contains(id))throw invalid(field);values.add(id);}return List.copyOf(values);
    }
    public static Filter filter(JsonNode n) {
        object(n,Set.of("year","projectId","jobCode","warning","q","includeCancelled"));
        if(n.has("includeCancelled")&&!n.get("includeCancelled").isBoolean())throw invalid("includeCancelled");
        return new Filter(year(n),optionalText(string(n,"projectId",false),20,"projectId"),optionalText(string(n,"jobCode",false),10,"jobCode"),
            warning(string(n,"warning",false)),optionalText(string(n,"q",false),100,"q"),n.path("includeCancelled").asBoolean(false));
    }
    public static SelectRequest selection(JsonNode n) {
        object(n,Set.of("year","scope","filters","ids","excludedIds"));Filter f=filter(n.get("filters"));int year=year(n);if(f.year()!=year)throw invalid("filters.year");
        return new SelectRequest(year,choice(string(n,"scope",true),Set.of("PAGE","ALL"),"scope"),f,ids(n,"ids"),ids(n,"excludedIds"));
    }
    public static Bulk bulk(JsonNode n) {
        object(n,Set.of("year","type","items","extensionPossibility","reasonCode","memo"));String type=choice(string(n,"type",true),Set.of("EXTP","CNCL"),"type");
        if(!n.path("items").isArray()||n.get("items").isEmpty())throw invalid("items");List<Item> items=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(JsonNode item:n.get("items")){object(item,Set.of("id","expectedRevision"));String id=text(string(item,"id",true),20,"id");if(!seen.add(id))throw invalid("items");items.add(new Item(id,expectedRevision(string(item,"expectedRevision",true))));}
        if("CNCL".equals(type)&&n.has("extensionPossibility"))throw invalid("extensionPossibility");
        return new Bulk(year(n),type,List.copyOf(items),"EXTP".equals(type)?possibility(n):null,optionalText(string(n,"reasonCode",false),10,"reasonCode"),AssignmentInputPolicy.memo(string(n,"memo",false)));
    }
    public static Create substitutionPreview(JsonNode n) {object(n,Set.of("assignment"));Create c=create(n.get("assignment"));if(!"SUB".equals(c.inputType()))throw invalid("inputType");return c;}
    public static Substitute substitute(JsonNode n) {
        object(n,Set.of("assignment","mode","previewToken","withdrawals"));Create c=create(n.get("assignment"));if(!"SUB".equals(c.inputType()))throw invalid("inputType");
        String mode=choice(string(n,"mode",true),Set.of("TOGETHER","ONLY"),"mode");if(!n.path("withdrawals").isArray())throw invalid("withdrawals");
        List<Withdrawal> values=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(JsonNode w:n.get("withdrawals")) {
            object(w,Set.of("assignmentId","expectedRevision","withdrawalDate","reasonCode","memo"));String id=text(string(w,"assignmentId",true),20,"assignmentId");if(!seen.add(id))throw invalid("withdrawals");
            values.add(new Withdrawal(id,expectedRevision(string(w,"expectedRevision",true)),date(string(w,"withdrawalDate",true),"withdrawalDate"),text(string(w,"reasonCode",true),10,"reasonCode"),AssignmentInputPolicy.memo(string(w,"memo",false))));
        }
        if("TOGETHER".equals(mode)?values.isEmpty():!values.isEmpty())throw invalid("withdrawals");
        return new Substitute(c,mode,text(string(n,"previewToken",true),200,"previewToken"),List.copyOf(values));
    }
}
