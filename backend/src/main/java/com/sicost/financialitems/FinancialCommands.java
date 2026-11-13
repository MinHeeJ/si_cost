package com.sicost.financialitems;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.financialitems.FinancialDtos.*;
import static com.sicost.financialitems.FinancialRepository.*;
import static com.sicost.financialitems.FinancialService.conflict;

@Service
public class FinancialCommands {
    private final FinancialRepository repository;
    private final WriteCoordinator writes;
    private final FinancialService query;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final FinancialEditorSources sources;
    public FinancialCommands(FinancialRepository repository,WriteCoordinator writes,FinancialService query,CurrentUserProvider users,Clock clock,FinancialEditorSources sources){this.repository=repository;this.writes=writes;this.query=query;this.users=users;this.clock=clock;this.sources=sources;}
    private record Change(Kind kind,ObjectNode before,Map<String,Object> after) {}
    public static final class ExpandedScope extends RuntimeException {final Set<String> keys;ExpandedScope(Set<String> keys){this.keys=Set.copyOf(keys);}}
    @Transactional
    public Commit save(Save input,Set<String> discovered){
        users.currentUser();repository.lockClose();Snapshot initial=repository.snapshot(input.month());Set<String> keys=new TreeSet<>(discovered);keys.addAll(keys(input,initial));
        Snapshot[] current={initial};List<Change> plan=new ArrayList<>();List<Map<String,String>> cards=new ArrayList<>();
        var result=writes.write(keys,input.expectedRevision(),()->{current[0]=repository.snapshot(input.month());return FinancialRepository.canonical(current[0]);},()->{
            Set<String> latest=keys(input,current[0]);if(!keys.containsAll(latest)){keys.addAll(latest);throw new ExpandedScope(keys);}
            writable(input.month());build(input,current[0],plan,cards);
        },()->!plan.isEmpty(),()->{
            String actor=users.userId();LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
            for(Change change:plan)repository.persist(change.kind(),input.month(),change.before(),change.after(),actor,now);
        });
        Snapshot after=repository.snapshot(input.month());return new Commit(result.changed(),query.revision(after),result.changed()?"저장했습니다.":"바뀐 내용이 없습니다.",query.editor(input.month(),after),cards);
    }
    private Set<String> keys(Save input,Snapshot snapshot){
        Set<String> result=new TreeSet<>();result.add("FINANCIAL_MONTH:"+db(input.month()));result.add("FINANCIAL_CARDS");result.add("operating-standards:CASH_BASE_YM");
        for(String group:List.of("FIN_CLS","SGA_ITEM","ADJ_ITEM","XFER_CLS","IO"))result.add("code-group:"+group);
        for(ObjectNode annual:snapshot.rows(Kind.ANNUAL))result.add("CORPORATE_TAX:"+text(annual,"tax_yr"));result.add("CORPORATE_TAX:"+input.month().getYear());
        for(ObjectNode pay:snapshot.rows(Kind.TAX_PAYMENTS)){String year=text(pay,"tax_yr");result.add("CORPORATE_TAX:"+year);result.add("TAX_PAYMENT:"+year+":"+text(pay,"pay_type_cd"));result.add("TAX_PAYMENT:"+year+":MID");cashKey(result,text(pay,"pay_due_dt"));}
        for(Kind kind:List.of(Kind.ITEMS,Kind.TRANSFERS,Kind.LOANS))for(ObjectNode row:snapshot.rows(kind)){YearMonth cash=cash(kind,row,input.month());if(cash!=null)result.add("CASH_MONTH:"+db(cash));if(row.hasNonNull("upld_id"))result.add("UPLOAD:"+text(row,"upld_id"));}
        collectDates(input.body(),result);result.add("CASH_MONTH:"+db(input.month()));return result;
    }
    private void collectDates(JsonNode node,Set<String> keys){if(node.isObject())node.fields().forEachRemaining(e->{if(Set.of("ioDate","date","dueDate").contains(e.getKey()))cashKey(keys,e.getValue().asText());if(e.getKey().equals("year")&&e.getValue().isTextual()&&e.getValue().asText().matches("[0-9]{4}")){keys.add("CORPORATE_TAX:"+e.getValue().asText());for(String type:List.of("MID","CNFM"))keys.add("TAX_PAYMENT:"+e.getValue().asText()+":"+type);}collectDates(e.getValue(),keys);});else if(node.isArray())node.forEach(n->collectDates(n,keys));}
    private void cashKey(Set<String> keys,String date){if(date!=null&&date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))keys.add("CASH_MONTH:"+date.substring(0,7).replace("-",""));}
    private void writable(YearMonth month){if(repository.closed().contains(month))throw conflict("MONTH_CLOSED",month,"마감된 월은 변경할 수 없습니다.");if(month.getYear()!=query.today().getYear()||month.isAfter(YearMonth.from(query.today()))||month.isBefore(repository.cashBase()))throw conflict("PERIOD_READ_ONLY",month,"올해 운영 시작 이후의 편집 가능한 월만 저장할 수 있습니다.");}
    private void protect(YearMonth month){if(month==null)return;if(repository.closed().contains(month))throw conflict("MONTH_CLOSED",month,"현금 반영월이 마감되었습니다.");if(month.isBefore(repository.cashBase()))throw conflict("PERIOD_READ_ONLY",month,"운영 시작 이전 현금월은 변경할 수 없습니다.");}
    private void build(Save input,Snapshot snapshot,List<Change> plan,List<Map<String,String>> created){
        for(Kind kind:List.of(Kind.ITEMS,Kind.ADJUSTMENTS,Kind.TRANSFERS,Kind.LOANS))collection(kind,input,snapshot,plan);
        taxes(input,snapshot,plan);cards(input,snapshot,plan,created);
    }
    private void collection(Kind kind,Save input,Snapshot snapshot,List<Change> plan){
        JsonNode changes=input.body().get(kind.collection());if(changes==null)return;FinancialInput.allow(changes,List.of("add","update","deleteIds"),kind.collection());
        Set<String> used=new HashSet<>();Map<String,ObjectNode> old=index(snapshot.rows(kind),kind.idColumn);int next=kind==Kind.ITEMS?old.keySet().stream().mapToInt(Integer::parseInt).max().orElse(0):0;
        for(String action:List.of("update","deleteIds","add")){int ordinal=0;for(JsonNode node:FinancialInput.array(changes,action)){String id="new-"+(++ordinal);
            try{
                boolean adding=action.equals("add"),deleting=action.equals("deleteIds");ObjectNode before=null;
                if(!adding){if(deleting){if(!node.isTextual())throw FinancialInput.invalid(kind.id);id=node.asText();if(!id.matches(kind==Kind.ITEMS?"[1-9][0-9]{0,4}":"[1-9][0-9]{0,9}"))throw FinancialInput.invalid(kind.id);}else id=FinancialInput.id(node,kind);
                    if(!used.add(id)||!old.containsKey(id))throw FinancialInput.invalid(kind.id);before=old.get(id);
                }
                if(deleting){protect(cash(kind,before,input.month()));plan.add(new Change(kind,before,null));continue;}
                Map<String,Object> after=FinancialInput.values(kind,node,!adding);
                if(kind!=Kind.ITEMS){String field=kind==Kind.ADJUSTMENTS?"adj_item_cd":kind==Kind.TRANSFERS?"xfer_cls_cd":"io_cd",group=kind==Kind.ADJUSTMENTS?"ADJ_ITEM":kind==Kind.TRANSFERS?"XFER_CLS":"IO";String code=(String)after.get(field),state=repository.code(group,code);if(state==null||!"Y".equals(state)&&(before==null||!code.equals(text(before,field))))throw FinancialInput.invalid(kind==Kind.ADJUSTMENTS?"itemCode":kind==Kind.TRANSFERS?"classCode":"direction");}
                if(kind.isDated()&&!YearMonth.from((LocalDate)after.get(kind.scope)).equals(input.month()))throw FinancialInput.invalid("date");
                if(kind==Kind.ITEMS){references(node,before,after,input.month());if(adding){after.put("auto_fill_yn",node.has("copySource")&&node.path("copySource").path("mode").asText().equals("REPEAT")?"Y":"N");after.put("upld_id",null);}}
                if(!adding&&same(before,after))continue;
                if(kind==Kind.ADJUSTMENTS){List<String> targets=repository.targets(input.month());String target=apiMonth((String)after.get("trgt_ym")).toString();if(!targets.contains(target)||before!=null&&!targets.contains(apiMonth(text(before,"trgt_ym")).toString()))throw FinancialInput.invalid("targetMonth");}
                protect(cash(kind,before,input.month()));protect(cash(kind,after,input.month()));
                if(adding){if(kind==Kind.ITEMS){if(++next>99999)throw conflict("ID_EXHAUSTED",input.month(),"월 항목 번호를 모두 사용했습니다.");id=Integer.toString(next);}else{id=repository.nextId(kind);if(!id.matches("[1-9][0-9]{0,9}"))throw conflict("ID_EXHAUSTED",input.month(),"식별자 범위를 초과했습니다.");}
                    after.put(kind.idColumn,new BigDecimal(id));if(kind.scope!=null&&!kind.isDated())after.put(kind.scope,db(input.month()));
                }else after.put(kind.idColumn,new BigDecimal(id));plan.add(new Change(kind,before,after));
            }catch(ApiException error){throw FinancialInput.row(error,kind,id);}
        }}
    }
    private void references(JsonNode node,ObjectNode before,Map<String,Object> values,YearMonth month){
        ObjectNode source=before;
        if(node.has("copySource")){JsonNode copy=node.get("copySource");FinancialInput.allow(copy,List.of("month","itemSn","mode"),"copySource");String sourceMonth=FinancialInput.text(copy,"month",7,true);if(!FinancialInput.month(sourceMonth).equals(month.minusMonths(1)))throw FinancialInput.invalid("copySource.month");String id=FinancialInput.id(copy,Kind.ITEMS);source=repository.rows(Kind.ITEMS,month.minusMonths(1)).stream().filter(r->id.equals(text(r,"item_sn"))).findFirst().orElseThrow(()->FinancialInput.invalid("copySource.itemSn"));String mode=FinancialInput.choice(copy,"mode","REPEAT","PREVIOUS");if(mode.equals("REPEAT")&&!"Y".equals(text(source,"rpt_yn")))throw FinancialInput.invalid("copySource.mode");}
        for(String field:List.of("fin_cls_cd","sga_item_cd")){String code=(String)values.get(field);if(code==null)continue;String state=repository.code(field.equals("fin_cls_cd")?"FIN_CLS":"SGA_ITEM",code);if(state==null||!"Y".equals(state)&&(source==null||!code.equals(text(source,field))))throw FinancialInput.invalid(field.equals("fin_cls_cd")?"finClass":"sgaItemCode");}
    }
    private void taxes(Save input,Snapshot snapshot,List<Change> plan){
        String year=input.month().toString().substring(0,4);if(input.body().has("estimatedTax")){
            BigDecimal amount=FinancialInput.won(input.body().get("estimatedTax"),"estimatedTax",false,false);ObjectNode before=index(snapshot.rows(Kind.ANNUAL),"tax_yr").get(year);
            if(before==null)throw FinancialInput.invalid("estimatedTax");Map<String,Object> after=new LinkedHashMap<>();after.put("tax_yr",year);after.put("est_tax_amt",amount);
            if(!same(before,after)){var allocation=sources.allocationValue(year);if(allocation.openMonthCount()==0||allocation.months().stream().anyMatch(m->m.closeStatus().equals("CLOSE")&&!m.verified()))throw conflict("PERIOD_READ_ONLY",input.month(),"마감 법인세 사본이 없거나 열린 운영월이 없습니다.");plan.add(new Change(Kind.ANNUAL,before,after));}
        }
        Set<String> ids=new HashSet<>();for(JsonNode schedule:FinancialInput.array(input.body(),"taxSchedules")){
            try{FinancialInput.allow(schedule,List.of("year","payType","dueDate","payAmount"),"taxSchedules");String y=FinancialInput.text(schedule,"year",4,true),type=FinancialInput.choice(schedule,"payType","MID","CNFM");if(!y.matches("(?!0000)[0-9]{4}"))throw FinancialInput.invalid("year");String key=y+":"+type;
                if(!ids.add(key))throw FinancialInput.invalid("payType");ObjectNode before=snapshot.rows(Kind.TAX_PAYMENTS).stream().filter(p->y.equals(text(p,"tax_yr"))&&type.equals(text(p,"pay_type_cd"))).findFirst().orElseThrow(()->FinancialInput.invalid("year"));
                if(!y.equals(year)&&!type.equals("CNFM"))throw FinancialInput.invalid("payType");if("Y".equals(text(before,"pay_yn")))throw conflict("TAX_ALREADY_PAID",input.month(),"납부 확인된 행은 변경할 수 없습니다.");
                if(!schedule.has("payAmount"))throw FinancialInput.invalid("payAmount");Map<String,Object> after=new LinkedHashMap<>();after.put("tax_yr",y);after.put("pay_type_cd",type);after.put("pay_due_dt",FinancialInput.date(schedule,"dueDate",true));after.put("pay_amt",schedule.get("payAmount").isNull()?null:FinancialInput.won(schedule.get("payAmount"),"payAmount",false,false));
                if(!same(before,after)){protect(YearMonth.from(LocalDate.parse(text(before,"pay_due_dt"))));protect(YearMonth.from((LocalDate)after.get("pay_due_dt")));plan.add(new Change(Kind.TAX_PAYMENTS,before,after));}
            }catch(ApiException e){throw FinancialInput.row(e,Kind.TAX_PAYMENTS,schedule.path("year").asText());}
        }
    }
    private void cards(Save input,Snapshot snapshot,List<Change> plan,List<Map<String,String>> created){
        JsonNode changes=input.body().get("cards");if(changes==null)return;FinancialInput.allow(changes,List.of("add","states","payments","cashOverride"),"cards");
        Map<String,ObjectNode> cards=index(snapshot.rows(Kind.CARDS),"card_id"),oldPay=index(snapshot.rows(Kind.PAYMENTS),"card_id");Map<String,String> states=new HashMap<>();cards.forEach((id,row)->states.put(id,text(row,"use_yn")));
        Set<String> seen=new HashSet<>();for(JsonNode state:FinancialInput.array(changes,"states")){FinancialInput.allow(state,List.of("cardId","useYn"),"cards.states");String id=FinancialInput.id(state,Kind.CARDS),use=FinancialInput.choice(state,"useYn","Y","N");if(!seen.add(id)||!cards.containsKey(id))throw FinancialInput.row(FinancialInput.invalid("cardId"),Kind.CARDS,id);states.put(id,use);Map<String,Object> after=new LinkedHashMap<>();after.put("card_id",id);after.put("use_yn",use);add(plan,Kind.CARDS,cards.get(id),after);}
        Map<String,BigDecimal> payments=new LinkedHashMap<>();oldPay.forEach((id,row)->payments.put(id,number(row,"pay_amt")));seen.clear();
        for(JsonNode payment:FinancialInput.array(changes,"payments")){FinancialInput.allow(payment,List.of("cardId","amount"),"cards.payments");String id=FinancialInput.id(payment,Kind.CARDS);try{if(!seen.add(id)||!cards.containsKey(id))throw FinancialInput.invalid("cardId");BigDecimal amount=FinancialInput.won(payment.get("amount"),"amount",false,false);if(amount.signum()>0&&"N".equals(states.get(id))&&(!oldPay.containsKey(id)||number(oldPay.get(id),"pay_amt").signum()==0))throw FinancialInput.invalid("cardId");payments.put(id,amount);}catch(ApiException e){throw FinancialInput.row(e,Kind.CARDS,id);}}
        BigInteger next=new BigInteger(repository.greatestCardNumber());Set<String> clients=new HashSet<>(),identities=new HashSet<>();cards.values().forEach(r->identities.add(text(r,"card_nm")+"\u0000"+text(r,"card_no_l4")));
        for(JsonNode card:FinancialInput.array(changes,"add")){FinancialInput.allow(card,List.of("clientKey","cardName","last4","amount"),"cards.add");String client=FinancialInput.text(card,"clientKey",100,true);try{String name=FinancialInput.text(card,"cardName",100,true),last=FinancialInput.text(card,"last4",4,true);if(!last.matches("[0-9]{4}")||!clients.add(client))throw FinancialInput.invalid("last4");if(!identities.add(name+"\u0000"+last)){ObjectNode existing=cards.values().stream().filter(r->name.equals(text(r,"card_nm"))&&last.equals(text(r,"card_no_l4"))).findFirst().orElse(null);throw new ApiException(409,"CARD_EXISTS","같은 카드가 있습니다. 기존 카드를 사용하거나 되살려 주세요.",null,Map.of("financial",existing==null?Map.of():Map.of("card",repository.view(Kind.CARDS,existing))));}
                next=next.add(BigInteger.ONE);if(next.toString().length()>18)throw conflict("ID_EXHAUSTED",input.month(),"카드 번호 범위를 초과했습니다.");String id="K-"+(next.toString().length()<2?"0":"")+next;Map<String,Object> after=new LinkedHashMap<>();after.put("card_id",id);after.put("card_nm",name);after.put("card_no_l4",last);after.put("use_yn","Y");plan.add(new Change(Kind.CARDS,null,after));created.add(Map.of("clientKey",client,"cardId",id));if(card.has("amount"))payments.put(id,FinancialInput.won(card.get("amount"),"amount",false,false));
            }catch(ApiException e){throw FinancialInput.row(e,Kind.CARDS,client);}}
        BigDecimal sum=payments.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);FinancialInput.range(sum,"cardSumAmount");ObjectNode oldMon=snapshot.rows(Kind.CARD_MONTH).stream().findFirst().orElse(null);
        String edit=oldMon==null?"N":text(oldMon,"edit_yn");BigDecimal cash=oldMon==null?sum:number(oldMon,"cash_amt");
        if(changes.has("cashOverride")){JsonNode override=changes.get("cashOverride");FinancialInput.allow(override,List.of("mode","amount"),"cards.cashOverride");String mode=FinancialInput.choice(override,"mode","AUTO","MANUAL");if(mode.equals("MANUAL")){edit="Y";cash=FinancialInput.won(override.get("amount"),"amount",false,false);}else{if(override.has("amount"))throw FinancialInput.invalid("amount");edit="N";}}
        if(edit.equals("N"))cash=sum;FinancialInput.range(cash,"cashAmount");List<Change> payChanges=new ArrayList<>();for(var entry:payments.entrySet()){Map<String,Object> after=new LinkedHashMap<>();after.put("pay_ym",db(input.month()));after.put("card_id",entry.getKey());after.put("pay_amt",entry.getValue());add(payChanges,Kind.PAYMENTS,oldPay.get(entry.getKey()),after);}
        if(!payChanges.isEmpty()||changes.has("cashOverride")){Map<String,Object> after=new LinkedHashMap<>();after.put("pay_ym",db(input.month()));after.put("card_sum_amt",sum);after.put("cash_amt",cash);after.put("edit_yn",edit);add(plan,Kind.CARD_MONTH,oldMon,after);plan.addAll(payChanges);}
    }
    private static Map<String,ObjectNode> index(List<ObjectNode> rows,String key){Map<String,ObjectNode> result=new LinkedHashMap<>();rows.forEach(r->result.put(text(r,key),r));return result;}
    private static boolean same(ObjectNode before,Map<String,Object> after){return before!=null&&after.entrySet().stream().allMatch(e->Objects.equals(text(before,e.getKey()),string(e.getValue())));}
    private static void add(List<Change> plan,Kind kind,ObjectNode before,Map<String,Object> after){if(!same(before,after))plan.add(new Change(kind,before,after));}
    private static YearMonth cash(Kind kind,ObjectNode row,YearMonth selected){if(row==null)return null;Map<String,Object> values=new HashMap<>();row.fields().forEachRemaining(e->values.put(e.getKey(),e.getValue().isNull()?null:e.getValue().asText()));return cash(kind,values,selected);}
    private static YearMonth cash(Kind kind,Map<String,Object> row,YearMonth selected){if(row==null||kind==Kind.ADJUSTMENTS)return null;if(kind==Kind.ITEMS){Object date=row.get("io_dt");return date==null?("NOPR_IN".equals(row.get("fin_cls_cd"))?null:selected):YearMonth.from(LocalDate.parse(date.toString()));}return kind.isDated()?YearMonth.from(LocalDate.parse(row.get(kind.scope).toString())):selected;}
}
