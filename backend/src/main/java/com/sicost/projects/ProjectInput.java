package com.sicost.projects;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.util.*;
import static com.sicost.projects.ProjectDtos.*;

public final class ProjectInput {
    private ProjectInput() {}
    public static final Set<String> SORTS = Set.of("id","name","customerName","contractType","startDate","contractAmount","personCount","partName","status");
    private static final Set<String> FIELDS = Set.of("name","customerId","startDate","endDate","workplace","feature","estimateAmount",
        "contractAmount","budgetAmount","teamExpenseAmount","projectExpenseAmount","budgetMode","year");
    public static void query(Map<String,String> query, Set<String> allowed) {
        query.keySet().stream().filter(k -> !allowed.contains(k)).sorted().findFirst().ifPresent(k -> { throw ProjectInputPolicy.invalid(k); });
    }
    public static String id(String id) { return ProjectInputPolicy.text(id,20,"id"); }
    public static Filter filter(Map<String,String> query) {
        query(query,Set.of("year","contractType","status","partId","q","page","size","sort","direction"));
        Integer year = query.containsKey("year") ? integer(query.get("year"),"year") : null;
        int page = integer(query.getOrDefault("page","1"),"page"), size = integer(query.getOrDefault("size","50"),"size");
        if (page < 1) throw ProjectInputPolicy.invalid("page");
        if (!Set.of(20,50,100).contains(size)) throw ProjectInputPolicy.invalid("size");
        return new Filter(year,choice(query.get("contractType"),Set.of("TK","MO","DP","IN"),"contractType",null),
            choice(query.get("status"),Set.of("ING","PRE","END","LOSE"),"status",null),
            ProjectInputPolicy.optionalText(query.get("partId"),20,"partId"),ProjectInputPolicy.optionalText(query.get("q"),100,"q"),
            page,size,choice(query.get("sort"),SORTS,"sort","startDate"),choice(query.get("direction"),Set.of("ASC","DESC"),"direction","DESC"));
    }
    public static boolean includeCancelled(Map<String,String> query) {
        query(query,Set.of("includeCancelled"));
        String value=query.getOrDefault("includeCancelled","false");
        if (!Set.of("true","false").contains(value)) throw ProjectInputPolicy.invalid("includeCancelled");
        return Boolean.parseBoolean(value);
    }
    public static CreateProjectRequest create(JsonNode body) {
        check(body,Set.of("contractType","initialStatus","partId"));
        String type=ProjectInputPolicy.text(text(body,"contractType"),10,"contractType");
        return new CreateProjectRequest(year(body),choice(type,Set.of("TK","MO","DP","IN"),"contractType",null),
            choice(text(body,"initialStatus"),Set.of("ING","PRE"),"initialStatus","ING"),optional(body,"partId",20),fields(body));
    }

    public static PutProjectRequest update(JsonNode body) {
        check(body,Set.of("expectedRevision"));
        return new PutProjectRequest(year(body),ProjectInputPolicy.expectedRevision(text(body,"expectedRevision")),fields(body));
    }
    private static Fields fields(JsonNode body) {
        var start=ProjectInputPolicy.date(text(body,"startDate"),"startDate");
        var end=ProjectInputPolicy.date(text(body,"endDate"),"endDate");
        ProjectInputPolicy.dateRange(start,end);
        return new Fields(ProjectInputPolicy.text(text(body,"name"),100,"name"),optional(body,"customerId",20),start,end,
            optional(body,"workplace",200),optional(body,"feature",500),amount(body,"estimateAmount"),amount(body,"contractAmount"),
            amount(body,"budgetAmount"),amount(body,"teamExpenseAmount"),amount(body,"projectExpenseAmount"),
            choice(text(body,"budgetMode"),Set.of("AUTO","MANUAL"),"budgetMode",null));
    }
    private static void check(JsonNode body,Set<String> extra) {
        if (body==null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key -> { if (!FIELDS.contains(key) && !extra.contains(key)) throw ProjectInputPolicy.invalid(key); });
    }
    private static int year(JsonNode body) {
        JsonNode value=body.get("year");
        if (value==null || !value.isIntegralNumber() || !value.canConvertToInt()) throw ProjectInputPolicy.invalid("year");
        return value.intValue();
    }
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);
        if (value==null || value.isNull()) return null;
        if (!value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
    private static String optional(JsonNode body,String field,int max) { return ProjectInputPolicy.optionalText(text(body,field),max,field); }
    private static BigDecimal amount(JsonNode body,String field) {
        String value=text(body,field);
        return value==null || value.replaceAll("[\\s\\p{Z}]", "").isEmpty() ? null : ProjectInputPolicy.amount(value,field);
    }
    private static int integer(String value,String field) {
        if (value==null || !value.matches("[0-9]+")) throw ProjectInputPolicy.invalid(field);
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw ProjectInputPolicy.invalid(field); }
    }
    private static String choice(String value,Set<String> allowed,String field,String fallback) {
        if (value==null) return fallback;
        if (!allowed.contains(value)) throw ProjectInputPolicy.invalid(field);
        return value;
    }
}
