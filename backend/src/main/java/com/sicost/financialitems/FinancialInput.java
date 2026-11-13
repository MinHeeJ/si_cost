package com.sicost.financialitems;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sicost.financialitems.FinancialDtos.*;

public final class FinancialInput {
    private FinancialInput() {}
    public static YearMonth month(String value){
        if(value==null||!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");
        return YearMonth.parse(value);
    }
    public static void allow(JsonNode node,Collection<String> keys,String path){
        if(node==null||!node.isObject())throw invalid(path);
        node.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",path.isEmpty()?k:path+"."+k,null);});
    }
    public static Save save(JsonNode body){
        allow(body,List.of("month","expectedRevision","items","adjustments","transfers","loans","cards","estimatedTax","taxSchedules"),"");
        return new Save(month(text(body,"month",7,true)),text(body,"expectedRevision",200,true),((ObjectNode)body).deepCopy());
    }
    public static String text(JsonNode node,String field,int max,boolean required){
        JsonNode v=node.get(field);if(v==null||v.isNull()){if(required)throw invalid(field);return null;}
        if(!v.isTextual())throw invalid(field);String value=v.textValue().strip();
        if(value.codePointCount(0,value.length())>max||required&&value.isEmpty())throw invalid(field);
        return value.isEmpty()?null:value;
    }
    public static BigDecimal won(JsonNode value,String field,boolean signed,boolean positive){
        if(value==null||!value.isTextual())throw invalid(field);
        String raw=value.textValue().replaceAll("[\\s\\p{Z},]","").replaceFirst("원$","");
        if(!raw.matches(signed?"-?[0-9]+":"[0-9]+"))throw invalid(field);
        BigDecimal amount=new BigDecimal(raw);
        if(amount.abs().compareTo(new BigDecimal("999999999999999"))>0||positive&&amount.signum()<=0||signed&&amount.signum()==0)throw invalid(field);
        return amount;
    }
    public static LocalDate date(JsonNode node,String field,boolean required){
        String value=text(node,field,10,required);if(value==null)return null;
        try{if(!value.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid(field);return LocalDate.parse(value);}catch(DateTimeException e){throw invalid(field);}
    }
    public static List<JsonNode> array(JsonNode parent,String field){
        JsonNode v=parent.get(field);if(v==null)return Collections.emptyList();if(!v.isArray())throw invalid(field);
        List<JsonNode> result=new ArrayList<>();v.forEach(result::add);return result;
    }
    public static Map<String,Object> values(Kind kind,JsonNode node,boolean update){
        Set<String> keys=new HashSet<>(kind.fields.keySet());keys.removeAll(Set.of("autoFill","uploadId","paidDate","payYn"));
        if(update)keys.add(kind.id);if(kind==Kind.ITEMS&&!update)keys.add("copySource");allow(node,keys,kind.collection());
        Map<String,Object> result=new LinkedHashMap<>();
        switch(kind){
            case ITEMS -> {
                result.put("fin_cls_cd",choice(node,"finClass","SGA","NOPR_IN","NOPR_OUT"));result.put("sga_item_cd",text(node,"sgaItemCode",10,false));
                result.put("item_nm",text(node,"itemName",50,true));result.put("amt",won(node.get("amount"),"amount",false,false));
                result.put("vat_amt",node.hasNonNull("vatAmount")&&!node.path("vatAmount").asText().isBlank()?won(node.get("vatAmount"),"vatAmount",false,false):BigDecimal.ZERO);
                range(((BigDecimal)result.get("amt")).add((BigDecimal)result.get("vat_amt")),"amount");
                result.put("io_dt",date(node,"ioDate",false));result.put("rpt_yn",node.has("repeat")?choice(node,"repeat","Y","N"):"N");result.put("rmrk",text(node,"remark",500,false));
            }
            case ADJUSTMENTS -> {result.put("trgt_ym",month(text(node,"targetMonth",7,true)).toString().replace("-",""));result.put("adj_item_cd",choice(node,"itemCode","SALES","COST","SGA","ETC"));result.put("adj_amt",won(node.get("amount"),"amount",true,false));result.put("adj_rsn",text(node,"reason",300,true));}
            case TRANSFERS,LOANS -> {
                result.put(kind.scope,date(node,"date",true));result.put("amt",won(node.get("amount"),"amount",false,true));result.put("rmrk",text(node,"remark",200,false));
                if(kind==Kind.TRANSFERS){result.put("xfer_cls_cd",choice(node,"classCode","EXP","SPCL"));result.put("item_nm",text(node,"itemName",100,true));result.put("rcvr_nm",text(node,"receiver",100,false));}
                else {result.put("io_cd",choice(node,"direction","IN","OUT"));result.put("cntprt_nm",text(node,"counterparty",100,false));}
            }
            default -> throw invalid("collection");
        }
        return result;
    }
    public static String choice(JsonNode node,String field,String... choices){String value=text(node,field,100,true);if(!Arrays.asList(choices).contains(value))throw invalid(field);return value;}
    public static String id(JsonNode node,Kind kind){String value=text(node,kind.id,20,true);if(kind.isNumericId()&&!value.matches(kind==Kind.ITEMS?"[1-9][0-9]{0,4}":"[1-9][0-9]{0,9}"))throw invalid(kind.id);return value;}
    public static void range(BigDecimal value,String field){if(value.abs().compareTo(new BigDecimal("999999999999999"))>0)throw invalid(field);}
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 필수 값을 확인해 주세요.",field,null);}
    public static ApiException row(ApiException error,Kind kind,String id){
        if(error.status()!=400||error.code().equals("UNKNOWN_FIELD"))return error;
        return new ApiException(error.status(),error.code(),error.getMessage(),error.field(),Map.of("financial",Map.of("violations",List.of(Map.of("tab",kind==Kind.TRANSFERS||kind==Kind.LOANS||kind==Kind.CARDS?"CARD_TRANSFER_LOAN":"FINANCIAL_TAX_ADJUSTMENT","collection",kind.collection(),"rowId",id,"field",error.field()==null?"row":error.field(),"message",error.getMessage())))));
    }
}
