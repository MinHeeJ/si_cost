package com.sicost.me;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.InputValidator;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PreferenceValidator {
    public static final List<String> GLOBAL = List.of("FAV","RECENT","MENU_FOLD","PAGE_SIZE","DENSE","AMT_UNIT");
    private static final Map<String,List<String>> COLUMNS = Map.ofEntries(
            Map.entry("projectCost",List.of("projectName","contractType","status","originalContractAmount","contractAmount","planMm","actualMm","cumulativeRevenue","cumulativeCost","cumulativeProfit","cumulativeMargin","forecastMargin","monthlyMargin","targetMargin","targetGap","budgetAmount","budgetUsage","pricePerMm","costPerMm","profitPerMm","freeMm","freeLaborCost","alerts")),
            Map.entry("statusBoard",List.of("light","projectName","pm","currentStep","planProgress","actualProgress","costUsage","costProgressGap","openIssueCount","overdueIssueCount","lastUpdatedDate","elapsedDays")),
            Map.entry("issues",List.of("importance","title","projectName","action","registeredBy","dueDate","status","registeredAt","modifiedBy","modifiedAt")),
            Map.entry("projects",List.of("id","name","customerName","contractType","contractPeriod","contractAmount","personCount","partName","status","distribution")),
            Map.entry("codes",List.of("code","name","addVal1","addVal2","sortOrder","useYn")),
            Map.entry("valueCodes",List.of("code","name","codeVal","unitNm")),
            Map.entry("mappings",List.of("sourceText","groupId","code")),
            Map.entry("standards",List.of("key","name","value","unitNm","effectiveMonth","description")),
            Map.entry("history",List.of("occurredAt","target","groupId","entityKey","type","item","before","after","actor")),
            Map.entry("users",List.of("userId","name","email","roles","status","lastLoginAt")),
            Map.entry("clients",List.of("type","name","businessNo","ceo","industry","contactName","phone","taxEmail","account","projectCount","personCount","useYn")),
            Map.entry("developers",List.of("name","companyName","employeeType","grade","job","status","currentProjects","withdrawalDate","monthlyRate")),
            Map.entry("candidates",List.of("name","employeeType","job","grade","skills","homeAddress","desiredRate","availableDate","contactStatus","lastContactDate","source")),
            Map.entry("individualProfitability",List.of("name","companyName","position","employeeType","hireDate","utilization","mm","revenue","pay","allowance","companyInsurance","retirementReserve","personalCost","teamExpense","projectExpense","totalCost","profit","profitRate","selfInsurance","withholdingTax","incomeTax","netPay")));
    private static final Map<String,List<String>> REQUIRED = Map.ofEntries(
            Map.entry("projectCost",List.of("projectName")),
            Map.entry("statusBoard",List.of("projectName")),
            Map.entry("issues",List.of("title","projectName")),
            Map.entry("projects",List.of("id","name")),
            Map.entry("codes",List.of("code","name")),Map.entry("valueCodes",List.of("code","name")),
            Map.entry("mappings",List.of("sourceText","groupId","code")),Map.entry("standards",List.of("key","name","value")),Map.entry("history",List.of("occurredAt","target")),
            Map.entry("users",List.of("userId","name")),Map.entry("clients",List.of("name")),Map.entry("developers",List.of("name")),Map.entry("candidates",List.of("name")),
            Map.entry("individualProfitability",List.of("name","revenue","totalCost","profit")));
    private final PreferenceMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;
    public PreferenceValidator(PreferenceMapper mapper,ObjectMapper json,Clock clock) {this.mapper=mapper;this.json=json;this.clock=clock;}
    public void identity(String type,String key) {
        if (GLOBAL.contains(type)) {if (!"*".equals(key)) throw InputValidator.invalid("key");}
        else if (Set.of("COLS","PERIOD").contains(type)) {
            if (!mapper.screens().contains(key)) throw InputValidator.invalid("key");
            if (type.equals("COLS") && !Set.of("UI-01","UI-02","UI-03","UI-04","UI-15","UI-23","UI-27","UI-31","UI-33").contains(key)) throw InputValidator.invalid("key");
            if (type.equals("PERIOD") && periodType(key).equals("NONE")) throw InputValidator.invalid("type");
        } else throw InputValidator.invalid("type");
    }
    public String serialized(JsonNode value) {
        if(value==null||value.isNull()||value.toString().codePointCount(0,value.toString().length())>2000) throw InputValidator.invalid("value");
        return value.toString();
    }
    public void validate(String type,String key,JsonNode value) {
        identity(type,key);serialized(value);
        switch(type) {
            case "PAGE_SIZE" -> {if (!value.isIntegralNumber()||!value.canConvertToInt()||!Set.of(20,50,100).contains(value.intValue())) throw InputValidator.invalid("value");}
            case "MENU_FOLD" -> {if(!value.isBoolean()) throw InputValidator.invalid("value");}
            case "DENSE" -> oneOf(value,Set.of("넓게","좁게"));
            case "AMT_UNIT" -> oneOf(value,Set.of("원","천원","백만원"));
            case "FAV","RECENT" -> {
                List<String> ids=strings(value,"value");
                if(ids.size()>(type.equals("FAV")?10:5)||!mapper.screens().containsAll(ids)) throw InputValidator.invalid("value");
            }
            case "COLS" -> columns(key,value);
            case "PERIOD" -> period(key,value);
            default -> throw InputValidator.invalid("type");
        }
    }
    private void oneOf(JsonNode node,Set<String> choices) {if(!node.isTextual()||!choices.contains(node.textValue()))throw InputValidator.invalid("value");}
    public static void fields(JsonNode node,Set<String> allowed) {
        if(node==null||!node.isObject())throw InputValidator.invalid("value");
        node.fieldNames().forEachRemaining(f->{if(!allowed.contains(f))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",f,null);});
    }
    private List<String> strings(JsonNode array,String field) {
        if(array==null||!array.isArray())throw InputValidator.invalid(field);
        var result=new ArrayList<String>();
        for(JsonNode n:array) {if(!n.isTextual()||result.contains(n.textValue()))throw InputValidator.invalid(field);result.add(n.textValue());}
        return result;
    }
    private Set<String> columnViews(String key) {
        return switch(key) {
            case "UI-01" -> Set.of("projects");
            case "UI-02" -> Set.of("developers");
            case "UI-03" -> Set.of("individualProfitability");
            case "UI-04" -> Set.of("projectCost");
            case "UI-31" -> Set.of("statusBoard","issues");
            case "UI-15" -> Set.of("clients");
            case "UI-23" -> Set.of("users");
            case "UI-27" -> Set.of("codes","valueCodes","mappings","standards","history");
            case "UI-33" -> Set.of("candidates");
            default -> throw InputValidator.invalid("key");
        };
    }
    private void columns(String key,JsonNode value) {
        fields(value,columnViews(key));
        if(key.equals("UI-04")&&!value.has("projectCost") || key.equals("UI-31")&&value.isEmpty())throw InputValidator.invalid("value");
        if(key.equals("UI-03")&&!value.has("individualProfitability"))throw InputValidator.invalid("value");
        value.fields().forEachRemaining(entry->{
            var view=entry.getValue();fields(view,Set.of("order","hidden"));
            var order=strings(view.get("order"),"value.order");var hidden=strings(view.get("hidden"),"value.hidden");
            var all=COLUMNS.get(entry.getKey());
            if(order.size()!=all.size()||!new HashSet<>(order).equals(new HashSet<>(all)))throw InputValidator.invalid("value.order");
            if(!all.containsAll(hidden)||hidden.stream().anyMatch(REQUIRED.get(entry.getKey())::contains))throw InputValidator.invalid("value.hidden");
        });
    }
    public String periodType(String key) {
        if(Set.of("UI-01","UI-03","UI-05","UI-11","UI-12","UI-13","UI-17","UI-21","UI-28").contains(key))return "YEAR";
        if(Set.of("UI-04","UI-10","UI-14","UI-16","UI-19").contains(key))return "YEAR_MONTH";
        if(Set.of("UI-07","UI-08","UI-09","UI-18").contains(key))return "YEAR_CUM";
        return "NONE";
    }
    private YearMonth base() {
        String value=mapper.cashBaseMonth();
        if(value==null)throw new IllegalStateException("운영 시작월이 없습니다.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(value));
    }
    private YearMonth now() {return YearMonth.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
    private int minYear(String key,YearMonth base) {return base.getYear()-(Set.of("UI-07","UI-08","UI-09").contains(key)?1:0);}
    private String text(JsonNode node,String field) {if(node==null||!node.isTextual())throw InputValidator.invalid(field);return node.textValue();}
    private int year(JsonNode node) {String v=text(node,"value.year");if(!v.matches("(?!0000)[0-9]{4}"))throw InputValidator.invalid("value.year");return Integer.parseInt(v);}
    private YearMonth month(JsonNode node) {
        String v=text(node,"value.month");if(!v.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw InputValidator.invalid("value.month");return YearMonth.parse(v);
    }
    private void period(String key,JsonNode value) {
        String kind=periodType(key);fields(value,kind.equals("YEAR_CUM")?Set.of("type","year","month"):Set.of("type","value"));
        if(!kind.equals(text(value.get("type"),"value.type")))throw InputValidator.invalid("value.type");
        var base=base();var now=now();
        if(kind.equals("YEAR")) {int y=year(value.get("value"));int max=now.getYear()+(key.equals("UI-03")?0:1);if(y<base.getYear()||y>max)throw InputValidator.invalid("value");}
        else if(kind.equals("YEAR_MONTH")) {var m=month(value.get("value"));if(m.isBefore(base)||m.isAfter(now))throw InputValidator.invalid("value");}
        else if(kind.equals("YEAR_CUM")) {
            int y=year(value.get("year"));var m=month(value.get("month"));
            if(y<minYear(key,base)||y>now.getYear()||m.getYear()!=y||(y==now.getYear()&&m.isAfter(now)))throw InputValidator.invalid("value.month");
        } else throw InputValidator.invalid("type");
    }
    public JsonNode defaultValue(String type,String key) {
        return switch(type) {
            case "FAV","RECENT" -> json.createArrayNode();
            case "MENU_FOLD" -> NullNode.instance;
            case "PAGE_SIZE" -> IntNode.valueOf(50);
            case "DENSE" -> TextNode.valueOf("넓게");
            case "AMT_UNIT" -> TextNode.valueOf("천원");
            case "COLS" -> {var o=json.createObjectNode();COLUMNS.forEach((k,v)->{if(columnViews(key).contains(k)){var c=o.putObject(k);c.set("order",json.valueToTree(v));c.set("hidden",json.valueToTree(k.equals("individualProfitability")?List.of("selfInsurance","withholdingTax","incomeTax","netPay"):List.of()));}});yield o;}
            case "PERIOD" -> {
                String kind=periodType(key);var o=json.createObjectNode().put("type",kind);var now=now();var base=base();
                if(kind.equals("YEAR"))o.put("value",String.format("%04d",Math.max(base.getYear(),now.getYear())));
                else if(kind.equals("YEAR_MONTH"))o.put("value",(now.isBefore(base)?base:now).toString());
                else {int y=Math.max(minYear(key,base),now.getYear());o.put("year",String.format("%04d",y));o.put("month",YearMonth.of(y,now.getMonth()).toString());}
                yield o;
            }
            default -> throw InputValidator.invalid("type");
        };
    }
}
