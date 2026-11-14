package com.sicost.salesopportunities;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.salesopportunitiesops.SalesCommandInput;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;

public final class SalesOpportunityInput {
    private SalesOpportunityInput() {}
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 형식과 범위를 확인해 주세요.",field,null);}
    public static Query query(Map<String,String> q){
        Set<String> allowed=Set.of("status","includeStopped","result","page","size","sort","direction");
        q.keySet().forEach(k->{if(!allowed.contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 조건입니다.",k,null);});
        String status=option(q,"status",Set.of("ING","WIN","LOSE","STOP"),"ING"),result=option(q,"result",Set.of("WIN","LOSE","STOP"),null);
        int page=number(q,"page",1),size=number(q,"size",50);if(!Set.of(20,50,100).contains(size))throw invalid("size");
        String stopped=q.getOrDefault("includeStopped","false");if(!Set.of("true","false").contains(stopped))throw invalid("includeStopped");
        return new Query(status,Boolean.parseBoolean(stopped),result,page,size,option(q,"sort",Set.of("id","resultDate","amount","probability","startMonth"),null),option(q,"direction",Set.of("ASC","DESC"),null));
    }
    private static int number(Map<String,String> q,String k,int fallback){try{String s=q.get(k);if(s==null)return fallback;if(!s.matches("[1-9][0-9]*"))throw invalid(k);return Integer.parseInt(s);}catch(NumberFormatException e){throw invalid(k);}}
    private static String option(Map<String,String> q,String k,Set<String> choices,String fallback){String s=q.get(k);if(s==null)return fallback;if(!choices.contains(s))throw invalid(k);return s;}
    private static String text(JsonNode b,String k,int max,boolean required){JsonNode n=b.get(k);if(n==null||n.isNull()){if(required)throw invalid(k);return null;}if(!n.isTextual())throw invalid(k);String s=n.textValue().trim();if(s.codePointCount(0,s.length())>max||(required&&s.isBlank()))throw invalid(k);return s.isEmpty()&&!required?null:s;}
    private static Integer integer(JsonNode b,String k,boolean required){JsonNode n=b.get(k);if(n==null||n.isNull()){if(required)throw invalid(k);return null;}if(!n.isIntegralNumber()||!n.canConvertToInt()||n.intValue()<1||n.intValue()>999)throw invalid(k);return n.intValue();}
    private static String decimal(JsonNode b,String k,String pattern,boolean required){String s=text(b,k,40,required);if(s==null)return null;if(!s.matches("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]+)?"))throw invalid(k);s=s.replace(",","");if(!s.matches(pattern))throw invalid(k);return new BigDecimal(s).stripTrailingZeros().toPlainString();}
    public static YearMonth month(String s,String field){try{if(s==null||!s.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid(field);return YearMonth.parse(s);}catch(DateTimeException e){throw invalid(field);}}
    private static void period(String s,int months,String field){if(month(s,field).plusMonths(months-1).getYear()>9999)throw invalid(field);}
    public static Save save(JsonNode b,boolean update){
        var fields=new HashSet<>(Set.of("name","customerId","partId","stage","probability","manualProbability","amount","startMonth","contractType","months","nextTask","nextDue","workplace","memo","needs","tentativeSelections"));if(update)fields.add("expectedRevision");SalesCommandInput.fields(b,fields);
        String name=text(b,"name",100,true),customer=text(b,"customerId",20,true),part=text(b,"partId",20,true),stage=text(b,"stage",10,true);
        if(!Set.of("FIND","PROP","NEGO","PRE").contains(stage))throw invalid("stage");
        if(!b.has("manualProbability")||!b.get("manualProbability").isBoolean())throw invalid("manualProbability");
        String probability=decimal(b,"probability","(?:100(?:\\.0{1,2})?|(?:[0-9]|[1-9][0-9])(?:\\.[0-9]{1,2})?)",true),amount=decimal(b,"amount","[1-9][0-9]{0,14}",true);
        String start=text(b,"startMonth",7,true);Integer months=integer(b,"months",false);period(start,months==null?6:months,"startMonth");
        String type=text(b,"contractType",10,false);if(type!=null&&!Set.of("TK","MO","DP").contains(type))throw invalid("contractType");
        String task=text(b,"nextTask",200,false),due=text(b,"nextDue",10,false);LocalDate date=null;
        if(due!=null){try{if(!due.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid("nextDue");date=LocalDate.parse(due);}catch(DateTimeException e){throw invalid("nextDue");}if(task==null)throw invalid("nextDue");}
        JsonNode array=b.get("needs");if(array==null||!array.isArray()||array.isEmpty()||array.size()>999)throw invalid("needs");
        var needs=new ArrayList<com.sicost.salesopportunitiesops.SalesCommandDtos.DraftNeed>();var serials=new HashSet<Integer>();int row=0;
        for(JsonNode n:array){row++;try{
            SalesCommandInput.fields(n,Set.of("needSn","jobCode","gradeCode","count","startMonth","months","monthlyMm","expectedRate"));
            Integer sn=integer(n,"needSn",n.has("needSn"));if(sn!=null&&(!update||!serials.add(sn)))throw invalid("needSn");
            String startNeed=text(n,"startMonth",7,true);int duration=integer(n,"months",true);period(startNeed,duration,"startMonth");
            needs.add(new com.sicost.salesopportunitiesops.SalesCommandDtos.DraftNeed(sn,text(n,"jobCode",10,true),text(n,"gradeCode",10,false),integer(n,"count",true),startNeed,duration,decimal(n,"monthlyMm","(?:0\\.(?:0[1-9]|[1-9][0-9]?)|1(?:\\.0{1,2})?)",true),decimal(n,"expectedRate","[1-9][0-9]{0,14}",false)));
        }catch(ApiException e){throw new ApiException(400,"VALIDATION_ERROR",row+"번째 줄의 입력 칸을 확인해 주세요.","needs["+(row-1)+"]."+e.field(),Map.of("rowNumber",row));}}
        List<Selection> selections=null;
        if(b.has("tentativeSelections")){JsonNode a=b.get("tentativeSelections");if(!a.isArray()||a.size()>999)throw invalid("tentativeSelections");var values=new ArrayList<Selection>();for(JsonNode s:a){SalesCommandInput.fields(s,Set.of("needSn","needIndex","developerId","startMonth","sourceScreenId"));if(s.has("needSn")==s.has("needIndex"))throw invalid("needIndex");Integer sn=integer(s,"needSn",s.has("needSn")),index=integer(s,"needIndex",s.has("needIndex"));String sm=text(s,"startMonth",7,true);month(sm,"startMonth");String screen=text(s,"sourceScreenId",10,true);if(!Set.of("UI-29","UI-05","UI-30").contains(screen))throw invalid("sourceScreenId");values.add(new Selection(sn,index,text(s,"developerId",20,true),sm,screen));}selections=List.copyOf(values);}
        return new Save(name,customer,part,stage,probability,b.get("manualProbability").booleanValue(),amount,start,type,months,task,date,text(b,"workplace",200,false),text(b,"memo",500,false),List.copyOf(needs),selections,update?text(b,"expectedRevision",2000,true):null);
    }
}
