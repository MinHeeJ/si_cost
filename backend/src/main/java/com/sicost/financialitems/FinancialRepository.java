package com.sicost.financialitems;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.financialitems.FinancialDtos.*;

@Repository
public class FinancialRepository {
    private final FinancialMapper mapper;
    private final SharedQueriesMapper shared;
    private final MasterLockMapper master;
    private final ObjectMapper json;
    private final ChangeHistoryWriter history;
    public FinancialRepository(FinancialMapper mapper,SharedQueriesMapper shared,MasterLockMapper master,ObjectMapper json,ChangeHistoryWriter history){this.mapper=mapper;this.shared=shared;this.master=master;this.json=json;this.history=history;}
    public List<ObjectNode> rows(Kind kind,YearMonth month){return mapper.rows(Scope.of(kind,month)).stream().map(this::parse).toList();}
    private ObjectNode parse(String value){try{return (ObjectNode)json.readTree(value);}catch(Exception error){throw new IllegalStateException("재무 저장값을 읽을 수 없습니다.",error);}}
    public Snapshot snapshot(YearMonth month){
        Map<Kind,List<ObjectNode>> rows=new EnumMap<>(Kind.class);for(Kind kind:Kind.values())rows.put(kind,rows(kind,month));
        int year=month.getYear();List<ObjectNode> payments=rows.get(Kind.TAX_PAYMENTS).stream().filter(p->{int y=p.path("tax_yr").asInt();return y==year||y==year-1&&p.path("pay_type_cd").asText().equals("CNFM")||y<year-1&&p.path("pay_yn").asText().equals("N");}).toList();
        Set<String> years=new HashSet<>();years.add(month.toString().substring(0,4));payments.forEach(p->years.add(p.path("tax_yr").asText()));
        rows.put(Kind.TAX_PAYMENTS,rows.get(Kind.TAX_PAYMENTS).stream().filter(p->years.contains(text(p,"tax_yr"))).toList());
        rows.put(Kind.ANNUAL,rows.get(Kind.ANNUAL).stream().filter(a->years.contains(a.path("tax_yr").asText())).toList());
        return new Snapshot(rows);
    }
    public static Map<Kind,List<ObjectNode>> canonical(Snapshot snapshot){
        Map<Kind,List<ObjectNode>> result=new EnumMap<>(Kind.class);
        snapshot.rows().forEach((kind,rows)->result.put(kind,rows.stream().sorted((a,b)->{
            int order=kind.isNumericId()?number(a,kind.idColumn).compareTo(number(b,kind.idColumn)):text(a,kind.idColumn).compareTo(text(b,kind.idColumn));
            return order==0&&kind==Kind.TAX_PAYMENTS?text(a,"pay_type_cd").compareTo(text(b,"pay_type_cd")):order;
        }).toList()));return result;
    }
    public YearMonth cashBase(){String value=shared.cashBaseMonth();if(value==null)throw new IllegalStateException("운영 시작월이 없습니다.");return apiMonth(value);}
    public List<YearMonth> closed(){return mapper.closedMonths().stream().map(FinancialRepository::apiMonth).toList();}
    public List<String> targets(YearMonth selected){
        List<YearMonth> closes=closed();if(closes.isEmpty())return Collections.emptyList();YearMonth oldest=closes.get(0).minusMonths(11),base=cashBase();
        return closes.stream().filter(m->m.isBefore(selected)&&!m.isBefore(oldest)&&!m.isBefore(base)).map(YearMonth::toString).toList();
    }
    public void lockClose(){mapper.lockTimeout();master.lockClose();}
    public String code(String group,String code){return code==null?null:mapper.code(group,code);}
    public String nextId(Kind kind){return mapper.nextId(kind);}
    public String greatestCardNumber(){return mapper.greatestCardNumber();}
    public Map<String,Object> view(Kind kind,ObjectNode row){
        Map<String,Object> result=new LinkedHashMap<>();result.put(kind.id,text(row,kind.idColumn));
        if(kind.scope!=null&&kind!=Kind.CARDS)result.put("month",kind.isDated()?text(row,kind.scope).substring(0,7):apiMonth(text(row,kind.scope)).toString());
        kind.fields.forEach((field,column)->result.put(field,column.equals("trgt_ym")?apiMonth(text(row,column)).toString():text(row,column)));
        result.put("audit",audit(row));return result;
    }
    public Map<String,Object> audit(ObjectNode row){Map<String,Object> audit=new LinkedHashMap<>();audit.put("regUserId",text(row,"reg_user_id"));audit.put("regDttm",text(row,"reg_dttm"));audit.put("modUserId",text(row,"mod_user_id"));audit.put("modDttm",text(row,"mod_dttm"));return audit;}
    public void persist(Kind kind,YearMonth month,ObjectNode before,Map<String,Object> after,String actor,LocalDateTime now){
        String key=before!=null?text(before,kind.idColumn):Objects.toString(after.get(kind.idColumn));
        if(after!=null&&before!=null&&after.entrySet().stream().allMatch(e->Objects.equals(text(before,e.getKey()),string(e.getValue()))))return;
        List<Cell> cells=after==null?Collections.emptyList():after.entrySet().stream().filter(e->before==null||!e.getKey().equals(kind.idColumn)).map(e->new Cell(e.getKey(),e.getValue())).toList();
        if(kind==Kind.TAX_PAYMENTS&&after!=null){List<Cell> ordered=new ArrayList<>();ordered.add(new Cell("pay_type_cd",after.get("pay_type_cd")));cells.stream().filter(c->!c.column().equals("pay_type_cd")).forEach(ordered::add);cells=ordered;}
        Object id=kind.isNumericId()?new BigDecimal(key):key;
        Mutation write=new Mutation(Scope.of(kind,month),id,cells,actor,now);
        int count=after==null?mapper.delete(write):before==null?mapper.insert(write):mapper.update(write);
        if(count!=1)throw new IllegalStateException("재무 항목 저장 범위가 달라졌습니다.");
        String target=kind==Kind.ITEMS||kind==Kind.PAYMENTS?db(month)+":"+key:kind==Kind.TAX_PAYMENTS?key+":"+after.get("pay_type_cd"):key;
        String type=after==null?"DEL":before==null?"NEW":kind==Kind.CARDS?("N".equals(after.get("use_yn"))?"STOP":"RESUME"):"UPD";
        Set<String> fields=new LinkedHashSet<>();if(before!=null)before.fieldNames().forEachRemaining(fields::add);if(after!=null)fields.addAll(after.keySet());
        for(String field:fields){if(after!=null&&Set.of("reg_user_id","reg_dttm","mod_user_id","mod_dttm").contains(field))continue;
            String b=before==null?null:text(before,field),a=after==null?null:after.containsKey(field)?string(after.get(field)):b;
            if(!Objects.equals(b,a))history.append(new ChangeHistoryWriter.Change(kind.table,target,type,field.toUpperCase(Locale.ROOT),b,a,null));
        }
    }
    public static String string(Object value){return value==null?null:value instanceof BigDecimal n?n.stripTrailingZeros().toPlainString():value.toString();}
    public static String text(JsonNode node,String field){JsonNode value=node.get(field);return value==null||value.isNull()?null:value.isNumber()?value.decimalValue().stripTrailingZeros().toPlainString():value.asText();}
    public static BigDecimal number(JsonNode node,String field){String value=text(node,field);return value==null?BigDecimal.ZERO:new BigDecimal(value);}
    public static YearMonth apiMonth(String value){return YearMonth.of(Integer.parseInt(value.substring(0,4)),Integer.parseInt(value.substring(4)));}
    public static String db(YearMonth month){return month.toString().replace("-","");}
}
