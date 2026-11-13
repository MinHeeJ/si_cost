package com.sicost.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.issues.ProjectIssueDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.*;

public final class ProjectIssueInput {
    private ProjectIssueInput() {}
    public static void query(MultiValueMap<String,String> query, Set<String> allowed) {
        query.forEach((key,values) -> { if (!allowed.contains(key) || values.size()!=1) throw invalid(key); });
    }
    public static Filter filter(MultiValueMap<String,String> q) {
        query(q,Set.of("status","importance","page","size"));
        String status=q.getFirst("status");
        if (status==null) status="OPEN_ACTIVE";
        if (!Set.of("OPEN_ACTIVE","OVERDUE","CLOSED","ALL").contains(status)) throw invalid("status");
        int page=integer(q.getFirst("page"),1,"page"), size=integer(q.getFirst("size"),50,"size");
        if (page<1) throw invalid("page");
        if (!Set.of(20,50,100).contains(size)) throw invalid("size");
        return new Filter(status,optionalText(q.getFirst("importance"),10,"importance"),page,size);
    }
    public static Input body(JsonNode b) {
        if (b==null || !b.isObject()) throw invalid("body");
        Set<String> allowed=Set.of("year","expectedRevision","title","dueDate","importance","status","action");
        b.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw invalid(k); });
        JsonNode y=b.get("year");
        if (y==null || !y.isIntegralNumber() || !y.canConvertToInt() || y.intValue()<1 || y.intValue()>9999) throw invalid("year");
        var date=ProjectInputPolicy.date(string(b,"dueDate"),"dueDate");
        if (date==null) throw invalid("dueDate");
        return new Input(y.intValue(),expectedRevision(string(b,"expectedRevision")),text(string(b,"title"),100,"title"),
            date,text(string(b,"importance"),10,"importance"),text(string(b,"status"),10,"status"),optionalText(string(b,"action"),500,"action"));
    }
    public static String id(String id) { return text(id,20,"id"); }
    public static long issueId(String id) {
        if (id==null || !id.matches("[1-9][0-9]{0,9}")) throw invalid("issueId");
        return Long.parseLong(id);
    }
    private static String string(JsonNode b,String key) {
        JsonNode n=b.get(key);
        if (n==null || n.isNull()) return null;
        if (!n.isTextual()) throw invalid(key);
        return n.textValue();
    }
    private static int integer(String value,int fallback,String key) {
        if (value==null) return fallback;
        if (!value.matches("[0-9]+")) throw invalid(key);
        try {return Integer.parseInt(value);} catch (NumberFormatException e) {throw invalid(key);}
    }
}
