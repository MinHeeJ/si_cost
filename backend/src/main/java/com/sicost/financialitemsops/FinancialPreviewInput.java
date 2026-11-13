package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.util.MultiValueMap;

public final class FinancialPreviewInput {
    private FinancialPreviewInput() {}
    public static void query(MultiValueMap<String,String> query,boolean year){query.forEach((k,v)->{if(!year||!k.equals("year"))throw unknown(k);if(v.size()!=1)throw invalid(k);});}
    public static String year(String value){if(value==null||!value.matches("(?!0000)[0-9]{4}"))throw invalid("year");return value;}
    public static YearMonth month(String value,String field){try{if(value==null||!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid(field);return YearMonth.parse(value);}catch(DateTimeException e){throw invalid(field);}}
    public static ObjectNode preview(JsonNode body){
        ObjectNode b=object(body,"body","month expectedRevision action items adjustments transfers loans cards estimatedTax taxSchedules").deepCopy();
        month(text(b,"month",7,true),"month");text(b,"expectedRevision",1000,true);choice(b,"action","CALCULATE","LOAD_PREVIOUS");
        for(String collection:List.of("items","adjustments","transfers","loans"))if(b.has(collection)) {
            ObjectNode changes=object(b.get(collection),collection,"add update deleteIds");
            for(String mode:List.of("add","update"))if(changes.has(mode))for(JsonNode row:array(changes.get(mode),collection+"."+mode))row((ObjectNode)object(row,collection,fields(collection,mode)),collection,mode);
            if(changes.has("deleteIds"))for(JsonNode id:array(changes.get("deleteIds"),collection+".deleteIds"))id(id,collection.equals("items")?5:10,collection+".deleteIds");
        }
        if(b.has("estimatedTax"))won(b,"estimatedTax",false,false);
        if(b.has("taxSchedules"))for(JsonNode n:array(b.get("taxSchedules"),"taxSchedules")){
            ObjectNode r=object(n,"taxSchedules","year payType dueDate payAmount");year(text(r,"year",4,true));choice(r,"payType","MID","CNFM");date(r,"dueDate",true);if(!r.has("payAmount"))throw invalid("taxSchedules.payAmount");if(!r.get("payAmount").isNull())won(r,"payAmount",false,false);
        }
        if(b.has("cards"))cards(object(b.get("cards"),"cards","add states payments cashOverride"));
        return b;
    }
    private static String fields(String collection,String mode){return switch(collection){
        case "items"->"finClass sgaItemCode itemName amount vatAmount ioDate repeat remark "+(mode.equals("add")?"copySource":"itemSn");
        case "adjustments"->"targetMonth itemCode amount reason "+(mode.equals("update")?"adjustmentId":"");
        case "transfers"->"date classCode itemName receiver amount remark "+(mode.equals("update")?"transferId":"");
        default->"date direction amount counterparty remark "+(mode.equals("update")?"loanId":"");};}
    private static void row(ObjectNode r,String collection,String mode){
        if(mode.equals("update"))id(r.get(identity(collection)),collection.equals("items")?5:10,collection+"."+identity(collection));
        won(r,"amount",collection.equals("adjustments"),!collection.equals("items"));
        switch(collection){
            case "items"->{choice(r,"finClass","SGA","NOPR_IN","NOPR_OUT");text(r,"itemName",50,true);text(r,"sgaItemCode",10,false);text(r,"remark",500,false);date(r,"ioDate",false);
                if(!r.hasNonNull("vatAmount")||r.path("vatAmount").asText().isBlank())r.put("vatAmount","0");won(r,"vatAmount",false,false);
                if(new BigDecimal(r.get("amount").asText()).add(new BigDecimal(r.get("vatAmount").asText())).compareTo(new BigDecimal("999999999999999"))>0)throw invalid("items.amount");
                if(!r.has("repeat"))r.put("repeat","N");choice(r,"repeat","Y","N");
                if(r.has("copySource")){ObjectNode c=object(r.get("copySource"),"copySource","month itemSn mode");month(text(c,"month",7,true),"copySource.month");id(c.get("itemSn"),5,"copySource.itemSn");choice(c,"mode","REPEAT","PREVIOUS");}
            }
            case "adjustments"->{month(text(r,"targetMonth",7,true),"targetMonth");choice(r,"itemCode","SALES","COST","SGA","ETC");text(r,"reason",300,true);}
            case "transfers"->{date(r,"date",true);choice(r,"classCode","EXP","SPCL");text(r,"itemName",100,true);text(r,"receiver",100,false);text(r,"remark",200,false);}
            default->{date(r,"date",true);choice(r,"direction","IN","OUT");text(r,"counterparty",100,false);text(r,"remark",200,false);}
        }
    }
    private static void cards(ObjectNode c){
        if(c.has("add"))for(JsonNode n:array(c.get("add"),"cards.add")){ObjectNode r=object(n,"cards.add","clientKey cardName last4 amount");text(r,"clientKey",100,true);text(r,"cardName",100,true);if(!text(r,"last4",4,true).matches("[0-9]{4}"))throw invalid("last4");if(r.has("amount"))won(r,"amount",false,false);}
        if(c.has("states"))for(JsonNode n:array(c.get("states"),"cards.states")){ObjectNode r=object(n,"cards.states","cardId useYn");text(r,"cardId",20,true);choice(r,"useYn","Y","N");}
        if(c.has("payments"))for(JsonNode n:array(c.get("payments"),"cards.payments")){ObjectNode r=object(n,"cards.payments","cardId amount");text(r,"cardId",20,true);won(r,"amount",false,false);}
        if(c.has("cashOverride")){ObjectNode r=object(c.get("cashOverride"),"cashOverride","mode amount");choice(r,"mode","AUTO","MANUAL");if(r.path("mode").asText().equals("MANUAL"))won(r,"amount",false,false);else if(r.has("amount"))throw invalid("cards.cashOverride.amount");}
    }
    public static String identity(String collection){return switch(collection){case "items"->"itemSn";case "adjustments"->"adjustmentId";case "transfers"->"transferId";default->"loanId";};}
    public static ObjectNode object(JsonNode node,String field,String allowed){if(node==null||!node.isObject())throw invalid(field);Set<String> keys=new HashSet<>(Arrays.asList(allowed.split(" ")));node.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw unknown(k);});return (ObjectNode)node;}
    public static ArrayNode array(JsonNode n,String field){if(n==null||!n.isArray())throw invalid(field);return (ArrayNode)n;}
    private static void id(JsonNode n,int digits,String field){if(n==null||!n.isTextual()||!n.asText().matches("[1-9][0-9]{0,"+(digits-1)+"}"))throw invalid(field);}
    private static String text(ObjectNode r,String field,int max,boolean required){JsonNode n=r.get(field);if(n==null||n.isNull()){if(required)throw invalid(field);return null;}if(!n.isTextual())throw invalid(field);String value=n.asText().trim();if(value.length()>max||(required&&value.isEmpty()))throw invalid(field);if(!required&&value.isEmpty()){r.putNull(field);return null;}r.put(field,value);return value;}
    private static void choice(ObjectNode r,String field,String... values){String value=text(r,field,30,true);if(!List.of(values).contains(value))throw invalid(field);}
    private static void date(ObjectNode r,String field,boolean required){String v=text(r,field,10,required);if(v==null)return;try{if(!v.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid(field);LocalDate.parse(v);}catch(DateTimeException e){throw invalid(field);}}
    private static void won(ObjectNode r,String field,boolean signed,boolean nonzero){JsonNode n=r.get(field);if(n==null||!n.isTextual())throw invalid(field);String v=n.asText().replaceAll("[\\s\\p{Z},]","").replaceFirst("원$","");if(!v.matches((signed?"-?":"")+"(0|[1-9][0-9]{0,14})")||(nonzero&&new BigDecimal(v).signum()==0))throw invalid(field);r.put(field,new BigDecimal(v).toPlainString());}
    public static ApiException unknown(String field){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
    public static ApiException invalid(String field){String collection=field.contains(".")?field.substring(0,field.indexOf('.')):field;String tab=Set.of("cards","transfers","loans").contains(collection)?"CARD_TRANSFER_LOAN":"FINANCIAL_TAX_ADJUSTMENT";return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 범위를 확인해 주세요.",field,Map.of("financial",Map.of("violations",List.of(Map.of("tab",tab,"collection",collection,"rowId","","field",field,"message","입력 항목을 확인해 주세요.")))));}
}
