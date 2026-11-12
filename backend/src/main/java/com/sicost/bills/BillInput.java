package com.sicost.bills;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import static com.sicost.bills.BillDtos.*;

public final class BillInput {
    public static final BigDecimal MAX=new BigDecimal("999999999999999");
    public static final Set<String> SORTS=Set.of("id","projectName","order","billingDate","supplyAmount","vatAmount","totalAmount","receiptAmount","unpaidAmount","dueDate","status","dunning");
    private BillInput() {}
    public static String id(String value) {
        if(value==null||!value.matches("B-[0-9]{6}-[0-9]{3}"))throw invalid("id");return value;
    }
    public static ApiException invalid(String field) {
        String message="입력 항목의 형식과 길이를 확인해 주세요.";
        return new ApiException(400,"INVALID_INPUT",message,field,Map.of("violations",java.util.List.of(Map.of("field",field,"message",message))));
    }
    private static ApiException unknown(String field) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 항목입니다.",field,Map.of("violations",java.util.List.of(Map.of("field",field,"message","허용되지 않은 항목입니다."))));}
    public static void query(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(k->{if(!allowed.contains(k))throw unknown(k);});
    }
    public static int detailQuery(Map<String,String> q) {query(q,Set.of("year"));return integer(q.get("year"),"year");}
    public static Query list(Map<String,String> q) {
        query(q,Set.of("year","status","month","projectId","includeCarryover","page","size","sort","direction"));
        int year=integer(q.get("year"),"year"),page=q.containsKey("page")?integer(q.get("page"),"page"):1,size=q.containsKey("size")?integer(q.get("size"),"size"):50;
        if(page<1)throw invalid("page");if(!Set.of(20,50,100).contains(size))throw invalid("size");
        String status=q.get("status"),month=q.get("month"),project=q.get("projectId"),sort=q.get("sort"),direction=q.getOrDefault("direction","DESC");
        if(status!=null&&!Set.of("PAID","UNPAID","LATE","CNCL").contains(status))throw invalid("status");
        if(month!=null)month=ProjectInputPolicy.month(month,"month").toString();
        if(project!=null)project=ProjectInputPolicy.text(project,20,"projectId");
        if(sort!=null&&!SORTS.contains(sort))throw invalid("sort");if(!Set.of("ASC","DESC").contains(direction))throw invalid("direction");
        String carry=q.getOrDefault("includeCarryover","true");if(!Set.of("true","false").contains(carry))throw invalid("includeCarryover");
        return new Query(year,status,month,project,Boolean.parseBoolean(carry),page,size,sort,direction);
    }
    public static CreateBillRequest create(JsonNode b) {
        allow(b,Set.of("year","projectId","billingMonth","supplyAmount","vatRate","billingDate","dueDate","taxInvoiceNo","confirmDuplicate","duplicateToken","checkContext","copyContext"));
        CheckContext check=check(b);CopyContext copy=null;
        if(b.has("copyContext")) {JsonNode c=b.get("copyContext");allow(c,Set.of("sourceBillId","expectedRevision"));copy=new CopyContext(id(text(c,"sourceBillId")),revision(c,"expectedRevision"));}
        if(check!=null&&copy!=null)throw invalid("checkContext");
        boolean confirm=false;if(b.has("confirmDuplicate")){if(!b.get("confirmDuplicate").isBoolean())throw invalid("confirmDuplicate");confirm=b.get("confirmDuplicate").booleanValue();}
        String token=b.has("duplicateToken")?revision(b,"duplicateToken"):null;
        return new CreateBillRequest(year(b),ProjectInputPolicy.text(text(b,"projectId"),20,"projectId"),ProjectInputPolicy.month(text(b,"billingMonth"),"billingMonth").toString(),editable(b),confirm,token,check,copy);
    }
    public static PutBillRequest update(JsonNode b) {
        allow(b,Set.of("year","expectedRevision","supplyAmount","vatRate","billingDate","dueDate","taxInvoiceNo","checkContext"));
        return new PutBillRequest(year(b),revision(b,"expectedRevision"),editable(b),check(b));
    }
    private static CheckContext check(JsonNode b) {
        if(!b.has("checkContext"))return null;JsonNode c=b.get("checkContext");allow(c,Set.of("projectId","month","checkRevision"));
        return new CheckContext(ProjectInputPolicy.text(text(c,"projectId"),20,"projectId"),ProjectInputPolicy.month(text(c,"month"),"month").toString(),revision(c,"checkRevision"));
    }
    private static Editable editable(JsonNode b) {
        BigDecimal supply=amount(text(b,"supplyAmount")),rate=rate(text(b,"vatRate"));
        LocalDate date=ProjectInputPolicy.date(text(b,"billingDate"),"billingDate"),due=ProjectInputPolicy.date(text(b,"dueDate"),"dueDate");
        if(due.isBefore(date))throw invalid("dueDate");
        String invoice=b.hasNonNull("taxInvoiceNo")?ProjectInputPolicy.optionalText(text(b,"taxInvoiceNo"),30,"taxInvoiceNo"):null;
        total(supply,rate);return new Editable(supply,rate,date,due,invoice);
    }
    public static BigDecimal amount(String value) {
        String clean=value.replaceAll("[\\s\\p{Z},]","").replaceFirst("원$","");
        if(!clean.matches("[0-9]+"))throw invalid("supplyAmount");BigDecimal result=new BigDecimal(clean);
        if(result.signum()<=0||result.compareTo(MAX)>0)throw invalid("supplyAmount");return result.setScale(0);
    }
    public static BigDecimal rate(String value) {
        String clean=value.replaceAll("[\\s\\p{Z},]","").replaceFirst("%$","");
        if(!clean.matches("[0-9]+(?:\\.[0-9]{1,2})?"))throw invalid("vatRate");BigDecimal result=new BigDecimal(clean);
        if(result.compareTo(new BigDecimal("100"))>0)throw invalid("vatRate");return result.stripTrailingZeros();
    }
    public static BigDecimal vat(BigDecimal supply,BigDecimal rate) {return supply.multiply(rate).movePointLeft(2).setScale(0,RoundingMode.DOWN);}
    public static BigDecimal total(BigDecimal supply,BigDecimal rate) {BigDecimal total=supply.add(vat(supply,rate));if(total.compareTo(MAX)>0)throw invalid("supplyAmount");return total;}
    private static int year(JsonNode b) {JsonNode v=b.get("year");if(v==null||!v.isIntegralNumber()||!v.canConvertToInt())throw invalid("year");return v.intValue();}
    private static int integer(String value,String field) {try{if(value==null||!value.matches("[0-9]+"))throw invalid(field);return Integer.parseInt(value);}catch(NumberFormatException e){throw invalid(field);}}
    private static String revision(JsonNode b,String field) {return ProjectInputPolicy.text(text(b,field),200,field);}
    private static String text(JsonNode b,String field) {JsonNode v=b.get(field);if(v==null||!v.isTextual())throw invalid(field);return v.textValue();}
    private static void allow(JsonNode b,Set<String> allowed) {if(b==null||!b.isObject())throw invalid("body");b.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw unknown(k);});}
}
