package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import java.time.*;
import java.util.*;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

public final class SalesCommandInput {
    private SalesCommandInput() {}
    public static ApiException invalid(String field) { return new ApiException(400,"VALIDATION_ERROR","입력 형식과 범위를 확인해 주세요.",field,null); }
    public static String id(String id) { if(id==null||!id.matches("O-[0-9]{4,18}"))throw invalid("id");return id; }
    public static void fields(JsonNode body,Set<String> allowed) {
        if(body==null||!body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력입니다.",k,null);});
    }
    public static void noQuery(Map<String,String> query) { if(!query.isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 조건입니다.",query.keySet().iterator().next(),null); }
    static String text(JsonNode b,String key,int max,boolean required) {
        JsonNode n=b.get(key);if(n==null||n.isNull()){if(required)throw invalid(key);return null;}
        if(!n.isTextual())throw invalid(key);String s=n.textValue();
        if(s.codePointCount(0,s.length())>max||(required&&s.isBlank()))throw invalid(key);return s;
    }
    static String revision(JsonNode b) {return text(b,"expectedRevision",2000,true);}
    static String choice(JsonNode b,String key,Set<String> values) {String s=text(b,key,20,true);if(!values.contains(s))throw invalid(key);return s;}
    static Boolean bool(JsonNode b,String key) {JsonNode n=b.get(key);if(n==null)return null;if(!n.isBoolean())throw invalid(key);return n.booleanValue();}
    static Integer integer(JsonNode b,String key) {JsonNode n=b.get(key);if(n==null)return null;if(!n.isIntegralNumber()||!n.canConvertToInt()||n.intValue()<1||n.intValue()>999)throw invalid(key);return n.intValue();}
    static String decimal(JsonNode b,String key,String regex) {String s=text(b,key,30,false);if(b.has(key)&&s==null)throw invalid(key);if(s!=null&&!s.matches(regex))throw invalid(key);return s;}
    public static Preview preview(JsonNode b) {
        fields(b,Set.of("needs"));if(!b.has("needs")||!b.get("needs").isArray()||b.get("needs").size()>999)throw invalid("needs");
        var rows=new ArrayList<DraftNeed>();for(JsonNode row:b.get("needs"))rows.add(need(row));return new Preview(List.copyOf(rows));
    }
    static DraftNeed need(JsonNode b) {
        fields(b,Set.of("needSn","jobCode","gradeCode","count","startMonth","months","monthlyMm","expectedRate"));
        String job=text(b,"jobCode",10,false),grade=text(b,"gradeCode",10,false),month=text(b,"startMonth",7,false);
        if(b.has("jobCode")&&(job==null||job.isEmpty()))throw invalid("jobCode");
        if(grade!=null&&grade.isEmpty())throw invalid("gradeCode");
        if(b.has("startMonth")){if(month==null)throw invalid("startMonth");month(month);}
        Integer months=integer(b,"months");if(month!=null&&months!=null&&YearMonth.parse(month).plusMonths(months-1).getYear()>9999)throw invalid("months");
        String rate=text(b,"expectedRate",15,false);if(rate!=null&&!rate.matches("[1-9][0-9]{0,14}"))throw invalid("expectedRate");
        return new DraftNeed(integer(b,"needSn"),job,grade,integer(b,"count"),month,months,
            decimal(b,"monthlyMm","(?:0\\.(?:0[1-9]|[1-9][0-9]?)|1(?:\\.0{1,2})?)"),rate);
    }
    static void month(String value) {try{if(!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("startMonth");YearMonth.parse(value);}catch(DateTimeException e){throw invalid("startMonth");}}
    public static Stage stage(JsonNode b) {
        fields(b,Set.of("expectedRevision","stage","probability","manualProbability"));
        String p=decimal(b,"probability","(?:100(?:\\.0{1,2})?|(?:[0-9]|[1-9][0-9])(?:\\.[0-9]{1,2})?)");
        Boolean manual=bool(b,"manualProbability");if(Boolean.TRUE.equals(manual)&&p==null)throw invalid("probability");
        return new Stage(revision(b),choice(b,"stage",Set.of("FIND","PROP","NEGO","PRE")),p,manual);
    }
    public static Approval approval(JsonNode b){fields(b,Set.of("expectedRevision","action","reason"));return new Approval(revision(b),choice(b,"action",Set.of("OK","REJ")),text(b,"reason",300,false));}
    public static Loss loss(JsonNode b){fields(b,Set.of("expectedRevision","reasonCode","memo"));return new Loss(revision(b),text(b,"reasonCode",10,true),text(b,"memo",500,false));}
    public static Revision revisionRequest(JsonNode b){fields(b,Set.of("expectedRevision"));return new Revision(revision(b));}
    static LocalDate date(JsonNode b,String key){String s=text(b,key,10,true);try{if(!s.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid(key);return LocalDate.parse(s);}catch(DateTimeException e){throw invalid(key);}}
    public static Win win(JsonNode b){
        fields(b,Set.of("expectedRevision","contractAmount","contractType","startDate","endDate","projectStatus"));
        String amount=text(b,"contractAmount",15,true);if(!amount.matches("[1-9][0-9]{0,14}"))throw invalid("contractAmount");
        LocalDate start=date(b,"startDate"),end=date(b,"endDate");if(!end.isAfter(start))throw invalid("endDate");
        return new Win(revision(b),amount,choice(b,"contractType",Set.of("TK","MO","DP")),start,end,choice(b,"projectStatus",Set.of("ING","PRE")));
    }
}
