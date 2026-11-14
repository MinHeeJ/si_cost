package com.sicost.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.invalid;

public final class ProjectProgressInput {
    private ProjectProgressInput() {}
    public static String id(String value) { return ProjectInputPolicy.text(value,20,"id"); }
    public static int serial(String value, int max, String field) {
        int result=integer(value,field); if (result<1 || result>max) throw invalid(field); return result;
    }
    public static void query(MultiValueMap<String,String> query, Set<String> allowed) {
        query.forEach((key,values)-> { if (!allowed.contains(key) || values.size()!=1) throw invalid(key); });
    }
    public static Integer year(MultiValueMap<String,String> query) {
        query(query,Set.of("year"));
        return query.containsKey("year") ? serial(query.getFirst("year"),9999,"year") : null;
    }
    public static HistoryQuery history(MultiValueMap<String,String> q) {
        query(q,Set.of("targetTable","targetKey","page","size"));
        String table=q.getFirst("targetTable");
        if (table!=null && !Set.of("TB_PJ_STEP","TB_PJ_WEEK_MEMO","TB_PJ_ISSUE").contains(table)) throw invalid("targetTable");
        int page=q.containsKey("page")?serial(q.getFirst("page"),Integer.MAX_VALUE,"page"):1;
        int size=q.containsKey("size")?integer(q.getFirst("size"),"size"):50;
        if (!Set.of(20,50,100).contains(size)) throw invalid("size");
        return new HistoryQuery(table,ProjectInputPolicy.optionalText(q.getFirst("targetKey"),100,"targetKey"),page,size);
    }
    public static Write write(JsonNode body) {
        check(body,Set.of("year","expectedRevision")); return new Write(year(body),revision(body));
    }
    public static Dates dates(JsonNode body, boolean preview) {
        check(body,preview?Set.of("year","steps"):Set.of("year","expectedRevision","steps"));
        JsonNode rows=body.get("steps");
        if (rows==null || !rows.isArray() || rows.isEmpty() || rows.size()>99) throw invalid("steps");
        List<EndDate> values=new ArrayList<>();
        for (JsonNode row:rows) {
            check(row,Set.of("stepSn","endDate"));
            int sn=number(row.get("stepSn"),"steps");
            if (sn<1 || sn>99) throw invalid("steps");
            values.add(new EndDate(sn,ProjectInputPolicy.date(text(row,"endDate"),"steps")));
        }
        return new Dates(year(body),preview?null:revision(body),List.copyOf(values));
    }
    public static MemoInput memo(JsonNode body) {
        check(body,Set.of("year","expectedRevision","thisWeek","nextWeek","request","actualProgress"));
        JsonNode actual=body.get("actualProgress");
        Integer progress=actual==null || actual.isNull()?null:number(actual,"actualProgress");
        if (progress!=null && (progress<0 || progress>100)) throw invalid("actualProgress");
        return new MemoInput(year(body),revision(body),optional(body,"thisWeek"),optional(body,"nextWeek"),
            optional(body,"request"),progress,body.has("actualProgress"));
    }
    private static String optional(JsonNode body,String key) {return ProjectInputPolicy.optionalText(text(body,key),200,key);}
    private static String revision(JsonNode body) {return ProjectInputPolicy.expectedRevision(text(body,"expectedRevision"));}
    private static int year(JsonNode body) {int year=number(body.get("year"),"year");if(year<1 || year>9999)throw invalid("year");return year;}
    private static int number(JsonNode value,String field) {
        if (value==null || !value.isIntegralNumber() || !value.canConvertToInt()) throw invalid(field);return value.intValue();
    }
    private static int integer(String value,String field) {
        if(value==null || !value.matches("[0-9]+"))throw invalid(field);
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw invalid(field);}
    }
    private static String text(JsonNode body,String key) {
        JsonNode v=body.get(key);if(v==null || v.isNull())return null;if(!v.isTextual())throw invalid(key);return v.textValue();
    }
    private static void check(JsonNode body,Set<String> allowed) {
        if(body==null || !body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid(k);});
    }
}
