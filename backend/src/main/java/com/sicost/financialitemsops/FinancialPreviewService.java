package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.financial.FinancialCalculation;
import com.sicost.financialitems.FinancialRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.common.financial.FinancialCalculation.*;
import static com.sicost.financialitemsops.FinancialPreviewDtos.*;

@Service
public class FinancialPreviewService {
    private final FinancialPreviewRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final FinancialRepository editorRepository;
    public FinancialPreviewService(FinancialPreviewRepository repository,RevisionService revisions,CurrentUserProvider users,Clock clock,FinancialRepository editorRepository){this.repository=repository;this.revisions=revisions;this.users=users;this.clock=clock;this.editorRepository=editorRepository;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public YearStatus getFinancialYearStatus(String year){
        FinancialPreviewInput.year(year);users.currentUser();LocalDate today=today();YearMonth base=repository.cashBase();
        if(Integer.parseInt(year)>today.getYear()||Integer.parseInt(year)<base.getYear())throw FinancialPreviewInput.invalid("year");
        List<MonthStatus> months=repository.yearStatus(year).stream().map(m->{YearMonth month=YearMonth.parse(m.month());boolean selectable=!month.isAfter(YearMonth.from(today));return new MonthStatus(m.month(),m.closeStatus(),selectable?m.itemCount():null,selectable,selectable&&month.getYear()==today.getYear()&&!month.isBefore(base)&&!m.closeStatus().equals("CLOSE"));}).toList();
        return new YearStatus(year,today.toString(),base.toString(),months);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview createFinancialPreview(ObjectNode request){
        users.currentUser();String month=request.path("month").asText();YearMonth selected=YearMonth.parse(month);
        String revision=revision(selected);
        revisions.requireExpected(request.path("expectedRevision").asText(),revision);writable(selected);
        ObjectNode stored=repository.snapshot(month);
        ObjectNode draft=stored.deepCopy();
        for(String collection:List.of("items","adjustments","transfers","loans"))if(request.has(collection))apply(draft,request.get(collection),collection,selected);
        cards(draft,request.path("cards"));taxes(draft,request,selected);
        List<Draft> added=new ArrayList<>();
        if(request.path("action").asText().equals("LOAD_PREVIOUS")){
            Set<List<String>> present=new HashSet<>();for(JsonNode row:draft.path("items"))if(selected.toString().replace("-","").equals(text(row,"fin_ym")))present.add(key(row));
            for(JsonNode row:repository.previous(selected.minusMonths(1)))if(!present.contains(key(row))){
                ObjectNode copy=((ObjectNode)row).deepCopy();copy.put("fin_ym",month.replace("-",""));copy.remove(List.of("item_sn","reg_user_id","reg_dttm","mod_user_id","mod_dttm"));copy.putNull("upld_id");copy.put("auto_fill_yn","N");String date=movedDate(text(row,"io_dt"),selected);copy.put("io_dt",date);
                List<Map<String,String>> errors=new ArrayList<>();if(date!=null){YearMonth cash=YearMonth.from(LocalDate.parse(date));if(cash.isBefore(repository.cashBase())||repository.closed(cash))errors.add(Map.of("field","ioDate","message","입출금일의 월을 확인해 주세요."));}
                String code=text(row,"sga_item_cd");if(code!=null&&!repository.itemCodeExists(code,true))errors.add(Map.of("field","sgaItemCode","message","사용할 수 없는 판관비 항목입니다. 다시 선택하거나 직접 입력해 주세요."));
                added.add(new Draft(selected.minusMonths(1).toString(),text(row,"item_sn"),text(row,"fin_cls_cd"),text(row,"sga_item_cd"),text(row,"item_nm"),money(number(row,"amt")),money(number(row,"vat_amt")),date,text(row,"rpt_yn"),text(row,"rmrk"),"N",errors));
                ((ArrayNode)draft.get("items")).add(copy);
            }
        }
        BigDecimal annual=null;for(JsonNode t:draft.path("taxes"))if(selected.toString().substring(0,4).equals(text(t,"tax_yr")))annual=number(t,"est_tax_amt");
        var allocation=allocate(month.substring(0,4),annual,repository.taxMonths(month));
        if(request.has("estimatedTax")&&(!allocation.verified()||allocation.openMonthCount()==0))throw conflict("PERIOD_READ_ONLY",month,"마감월 법인세 자료를 확인해 주세요.");
        return new Preview(month,revision,List.copyOf(added),added.size(),request.path("action").asText().equals("LOAD_PREVIOUS")?(added.isEmpty()?"불러올 항목이 없습니다":"지난달 항목을 불러왔습니다. 금액을 확인한 뒤 저장해 주세요."):"계산했습니다.",allocation,FinancialCalculation.contributions(draft,month,allocation));
    }
    public String revision(YearMonth month){return revisions.revision(FinancialRepository.canonical(editorRepository.snapshot(month)));}
    private void apply(ObjectNode draft,JsonNode changes,String collection,YearMonth month){
        ArrayNode rows=(ArrayNode)draft.get(collection);String pk=switch(collection){case "items"->"item_sn";case "adjustments"->"adj_id";case "transfers"->"xfer_sn";default->"loan_sn";};
        Set<String> touched=new HashSet<>();String identity=FinancialPreviewInput.identity(collection);
        for(JsonNode id:changes.path("deleteIds")){String value=id.asText();if(!touched.add(value))throw FinancialPreviewInput.invalid(collection+".deleteIds");ObjectNode old=required(rows,pk,value,collection,month);protectOld(old,collection,month);rows.remove(index(rows,old));}
        for(JsonNode update:changes.path("update")){String value=update.path(identity).asText();if(!touched.add(value))throw FinancialPreviewInput.invalid(collection+"."+identity);ObjectNode old=required(rows,pk,value,collection,month);protectOld(old,collection,month);ObjectNode next=translated(update,collection,month);validateRow(next,old,collection,month,update);next.fields().forEachRemaining(e->old.set(e.getKey(),e.getValue()));}
        for(JsonNode addition:changes.path("add")){ObjectNode next=translated(addition,collection,month);validateRow(next,null,collection,month,addition);rows.add(next);}
    }
    private ObjectNode translated(JsonNode input,String collection,YearMonth month){
        ObjectNode r=JsonNodeFactory.instance.objectNode();Map<String,String> fields=new LinkedHashMap<>();
        switch(collection){
            case "items"->{fields.putAll(Map.of("finClass","fin_cls_cd","sgaItemCode","sga_item_cd","itemName","item_nm","amount","amt","vatAmount","vat_amt","ioDate","io_dt","repeat","rpt_yn","remark","rmrk"));r.put("fin_ym",month.toString().replace("-",""));}
            case "adjustments"->{fields.putAll(Map.of("targetMonth","trgt_ym","itemCode","adj_item_cd","amount","adj_amt","reason","adj_rsn"));r.put("aply_ym",month.toString().replace("-",""));}
            case "transfers"->fields.putAll(Map.of("date","xfer_dt","classCode","xfer_cls_cd","itemName","item_nm","receiver","rcvr_nm","amount","amt","remark","rmrk"));
            default->fields.putAll(Map.of("date","loan_dt","direction","io_cd","counterparty","cntprt_nm","amount","amt","remark","rmrk"));
        }
        fields.forEach((api,db)->{JsonNode n=input.get(api);r.set(db,n==null?NullNode.instance:n);});
        if(collection.equals("adjustments"))r.put("trgt_ym",input.path("targetMonth").asText().replace("-",""));
        if(input.has("copySource")){
            JsonNode source=input.get("copySource");if(!source.path("month").asText().equals(month.minusMonths(1).toString()))throw FinancialPreviewInput.invalid("items.copySource.month");
            JsonNode origin=null;for(JsonNode old:repository.previous(month.minusMonths(1)))if(Objects.equals(text(old,"item_sn"),source.path("itemSn").asText()))origin=old;
            if(origin==null||source.path("mode").asText().equals("REPEAT")&&!"Y".equals(text(origin,"rpt_yn")))throw FinancialPreviewInput.invalid("items.copySource");
        }
        return r;
    }
    private void validateRow(ObjectNode row,ObjectNode old,String collection,YearMonth month,JsonNode input){
        if(collection.equals("transfers")||collection.equals("loans")){String date=text(row,collection.equals("transfers")?"xfer_dt":"loan_dt");if(!date.startsWith(month.toString()))throw FinancialPreviewInput.invalid(collection+".date");}
        if(collection.equals("items")){String date=text(row,"io_dt");YearMonth cash=date==null?month:YearMonth.from(LocalDate.parse(date));protectCash(cash,"items.ioDate");
            String code=text(row,"sga_item_cd");boolean keep=old!=null&&Objects.equals(text(old,"sga_item_cd"),code);
            if(input.has("copySource"))for(JsonNode source:repository.previous(month.minusMonths(1)))if(Objects.equals(text(source,"item_sn"),input.path("copySource").path("itemSn").asText())&&Objects.equals(text(source,"sga_item_cd"),code))keep=true;
            if(code!=null&&!repository.itemCodeExists(code,keep))throw FinancialPreviewInput.invalid("items.sgaItemCode");
        }
        if(collection.equals("adjustments")){YearMonth target=YearMonth.parse(apiMonth(text(row,"trgt_ym")));YearMonth latest=repository.yearStatus(String.format("%04d",today().getYear())).stream().filter(m->m.closeStatus().equals("CLOSE")).map(m->YearMonth.parse(m.month())).max(Comparator.naturalOrder()).orElse(null);
            if(latest==null||!target.isBefore(month)||target.isBefore(repository.cashBase())||target.isBefore(latest.minusMonths(11))||!repository.closed(target))throw FinancialPreviewInput.invalid("adjustments.targetMonth");}
    }
    private void protectOld(ObjectNode old,String collection,YearMonth month){if(collection.equals("items")){String date=text(old,"io_dt");protectCash(date==null?month:YearMonth.from(LocalDate.parse(date)),"items.ioDate");}}
    private void cards(ObjectNode draft,JsonNode changes){
        if(changes.isMissingNode())return;ArrayNode cards=(ArrayNode)draft.get("cards"),payments=(ArrayNode)draft.get("cardPayments");Set<String> seen=new HashSet<>();
        for(JsonNode r:changes.path("states")){String id=r.path("cardId").asText();if(!seen.add(id))throw FinancialPreviewInput.invalid("cards.states");ObjectNode old=find(cards,"card_id",id);if(old==null)throw FinancialPreviewInput.invalid("cards.cardId");old.put("use_yn",r.path("useYn").asText());}
        seen.clear();for(JsonNode r:changes.path("payments")){String id=r.path("cardId").asText();if(!seen.add(id))throw FinancialPreviewInput.invalid("cards.payments");ObjectNode card=find(cards,"card_id",id),old=find(payments,"card_id",id);if(card==null)throw FinancialPreviewInput.invalid("cards.cardId");if("N".equals(text(card,"use_yn"))&&(old==null||number(old,"pay_amt").signum()==0)&&new BigDecimal(r.path("amount").asText()).signum()>0)throw FinancialPreviewInput.invalid("cards.amount");if(old==null){old=payments.addObject();old.put("card_id",id);}old.set("pay_amt",r.get("amount"));}
        seen.clear();BigDecimal sum=BigDecimal.ZERO;for(JsonNode r:payments)sum=sum.add(number(r,"pay_amt"));
        for(JsonNode r:changes.path("add")){if(!seen.add(r.path("clientKey").asText()))throw FinancialPreviewInput.invalid("cards.clientKey");for(JsonNode old:cards)if(Objects.equals(text(old,"card_nm"),r.path("cardName").asText())&&Objects.equals(text(old,"card_no_l4"),r.path("last4").asText()))throw conflict("CARD_EXISTS","", "같은 이름과 끝 4자리의 카드가 있습니다. 되살리기를 사용해 주세요.");sum=sum.add(r.has("amount")?new BigDecimal(r.path("amount").asText()):BigDecimal.ZERO);}
        ArrayNode months=(ArrayNode)draft.get("cardMonths");ObjectNode value=months.isEmpty()?months.addObject():(ObjectNode)months.get(0);value.put("card_sum_amt",money(sum));
        JsonNode override=changes.path("cashOverride");if(!override.isMissingNode())value.put("edit_yn",override.path("mode").asText().equals("MANUAL")?"Y":"N");
        if(!override.isMissingNode()&&override.path("mode").asText().equals("MANUAL"))value.set("cash_amt",override.get("amount"));else if(!"Y".equals(text(value,"edit_yn")))value.put("cash_amt",money(sum));
        if(sum.precision()>15||number(value,"cash_amt").precision()>15)throw FinancialPreviewInput.invalid("cards.amount");
    }
    private void taxes(ObjectNode draft,JsonNode request,YearMonth selected){
        if(request.has("estimatedTax")){ObjectNode annual=find((ArrayNode)draft.get("taxes"),"tax_yr",selected.toString().substring(0,4));if(annual==null)throw FinancialPreviewInput.invalid("estimatedTax");annual.set("est_tax_amt",request.get("estimatedTax"));}
        Set<String> seen=new HashSet<>();for(JsonNode change:request.path("taxSchedules")){String key=change.path("year").asText()+":"+change.path("payType").asText();if(!seen.add(key))throw FinancialPreviewInput.invalid("taxSchedules");ObjectNode row=null;for(JsonNode r:draft.path("taxPayments"))if(key.equals(text(r,"tax_yr")+":"+text(r,"pay_type_cd")))row=(ObjectNode)r;
            if(row==null)throw FinancialPreviewInput.invalid("taxSchedules");if("Y".equals(text(row,"pay_yn")))throw conflict("TAX_ALREADY_PAID",selected.toString(),"납부 확인한 행은 변경할 수 없습니다.");protectCash(YearMonth.from(LocalDate.parse(text(row,"pay_due_dt"))),"taxSchedules.dueDate");protectCash(YearMonth.from(LocalDate.parse(change.path("dueDate").asText())),"taxSchedules.dueDate");row.set("pay_due_dt",change.get("dueDate"));row.set("pay_amt",change.get("payAmount"));
        }
    }
    private static List<String> key(JsonNode row){return Arrays.asList(text(row,"fin_cls_cd"),text(row,"sga_item_cd"),text(row,"item_nm").trim());}
    private static String movedDate(String date,YearMonth month){return date==null?null:month.atDay(Math.min(LocalDate.parse(date).getDayOfMonth(),month.lengthOfMonth())).toString();}
    private static ObjectNode find(ArrayNode rows,String pk,String id){for(JsonNode r:rows)if(Objects.equals(text(r,pk),id))return (ObjectNode)r;return null;}
    private static ObjectNode required(ArrayNode rows,String pk,String id,String collection,YearMonth month){for(JsonNode row:rows)if(Objects.equals(text(row,pk),id)&&(!collection.equals("items")||Objects.equals(text(row,"fin_ym"),month.toString().replace("-",""))))return (ObjectNode)row;throw FinancialPreviewInput.invalid(collection+"."+FinancialPreviewInput.identity(collection));}
    private static int index(ArrayNode rows,JsonNode row){for(int i=0;i<rows.size();i++)if(rows.get(i)==row)return i;throw new IllegalStateException();}
    private LocalDate today(){return LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));}
    private void writable(YearMonth month){if(month.isAfter(YearMonth.from(today())))throw FinancialPreviewInput.invalid("month");if(month.getYear()!=today().getYear()||month.isBefore(repository.cashBase()))throw conflict("PERIOD_READ_ONLY",month.toString(),"지난 연도 또는 운영 시작 전 기간은 읽기 전용입니다.");if(repository.closed(month))throw conflict("MONTH_CLOSED",month.toString(),"마감한 월은 변경할 수 없습니다.");}
    private void protectCash(YearMonth month,String field){if(month.isBefore(repository.cashBase()))throw FinancialPreviewInput.invalid(field);if(repository.closed(month))throw conflict("MONTH_CLOSED",month.toString(),"현금 귀속월이 마감되었습니다.");}
    private static ApiException conflict(String code,String month,String message){return new ApiException(409,code,message,null,Map.of("financial",Map.of("month",month,"business","재무 항목")));}
}
