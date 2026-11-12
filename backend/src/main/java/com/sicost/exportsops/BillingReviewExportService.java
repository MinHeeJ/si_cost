package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.apiops.BillingCheckService;
import com.sicost.apiops.ReceivableService;
import com.sicost.bills.BillDtos;
import com.sicost.bills.BillInput;
import com.sicost.bills.BillService;
import com.sicost.apiops.BillingReviewDtos.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.MasterDataValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class BillingReviewExportService {
    public static final List<String> CHECK_COLUMNS=List.of("verdict","projectName","contractType","personCount","mm","estimatedAmount","billedAmount","differenceAmount","explanation");
    public static final List<String> BILL_COLUMNS=List.of("id","projectName","order","billingDate","supplyAmount","vatAmount","totalAmount","receiptAmount","unpaidAmount","dueDate","status","dunning");
    private static final Map<String,String> BILL_STATUS=Map.of("PAID","입금 완료","UNPAID","미수","LATE","연체","CNCL","취소됨");
    public static final List<String> RECEIVABLE_COLUMNS=List.of("projectName","billId","unpaidAmount","dueDate","elapsedDays","step","sentHistory","nextNotification");
    private final BillingCheckService checks;
    private final ReceivableService receivables;
    private final ExcelExportService excel;
    private final BillService bills;
    public BillingReviewExportService(BillingCheckService checks,ReceivableService receivables,ExcelExportService excel,BillService bills) {this.checks=checks;this.receivables=receivables;this.excel=excel;this.bills=bills;}
    public static boolean handles(JsonNode body) {return body!=null&&body.path("view").isTextual()&&Set.of("BILLS","BILLING_CHECKS","RECEIVABLES").contains(body.path("view").textValue());}
    public Download create(JsonNode body,HttpServletRequest request) {
        allow(body,Set.of("view","filters","sort","direction","columns","amountUnit"));
        if("BILLS".equals(text(body,"view")))return createBills(body,request);
        boolean checking="BILLING_CHECKS".equals(text(body,"view"));
        List<String> ids=checking?CHECK_COLUMNS:RECEIVABLE_COLUMNS;
        if(!body.has("columns")||!body.get("columns").isArray()||body.get("columns").size()!=ids.size())throw invalid("columns");
        for(int i=0;i<ids.size();i++)if(!body.get("columns").get(i).isTextual()||!ids.get(i).equals(body.get("columns").get(i).textValue()))throw invalid("columns");
        JsonNode filters=body.get("filters");allow(filters,checking?Set.of("month"):Set.of());
        String month=checking?com.sicost.common.validation.ProjectInputPolicy.month(text(filters,"month"),"month").toString():null;
        String sort=body.has("sort")?text(body,"sort"):null,direction=body.has("direction")?text(body,"direction"):"ASC";
        if(!Set.of("ASC","DESC").contains(direction))throw invalid("direction");
        if(sort!=null&&!(checking?Set.of("verdict","projectName","projectId"):Set.of("elapsedDays","dueDate","billId")).contains(sort))throw invalid("sort");
        String unit=body.has("amountUnit")?text(body,"amountUnit"):"천원";
        if(!Set.of("원","천원","백만원").contains(unit))throw invalid("amountUnit");
        List<String> titles=checking?List.of("판정","프로젝트","계약유형","투입 인원","투입 MM","투입 기준 금액","청구 공급가","차이","설명"):
            List.of("프로젝트","청구번호","미수금","입금 예정일","지난 일수","지금 단계","보낸 알림","다음 알림");
        Set<String> money=Set.of("estimatedAmount","billedAmount","differenceAmount","unpaidAmount"),numbers=Set.of("personCount","mm","elapsedDays","step");
        List<Column> columns=new ArrayList<>();
        for(int i=0;i<ids.size();i++)columns.add(new Column(ids.get(i),titles.get(i)+(money.contains(ids.get(i))?" ("+unit+")":""),money.contains(ids.get(i))||numbers.contains(ids.get(i))?CellType.NUMBER:CellType.TEXT));
        return excel.generate(new Specification(checking?"청구누락점검":"미수알림",null,columns,"month="+Objects.toString(month,"")+"; sort="+Objects.toString(sort,"기본")+" "+direction,unit,"UI-13"),sink->{
            if(checking) {
                var rows=new ArrayList<>(checks.allChecks(month));
                if(sort!=null) {
                    Comparator<CheckRow> comparator=switch(sort){case "verdict"->Comparator.comparingInt(r->BillingCheckService.rank(r.verdict()));case "projectName"->Comparator.comparing(CheckRow::projectName);default->Comparator.comparing(CheckRow::projectId);};
                    if("DESC".equals(direction))comparator=comparator.reversed();rows.sort(comparator.thenComparing(CheckRow::projectName).thenComparing(CheckRow::projectId));
                }
                for(CheckRow row:rows)sink.accept(List.of(row.verdict(),row.projectName(),row.contractType(),row.personCount(),new BigDecimal(row.mm()).setScale(2),amount(row.estimatedAmount(),unit),amount(row.billedAmount(),unit),amount(row.differenceAmount(),unit),row.explanation()));
            } else {
                var rows=new ArrayList<>(receivables.allReceivables().items());
                if(sort!=null) {
                    Comparator<Receivable> comparator=switch(sort){case "elapsedDays"->Comparator.comparingInt(r->r.dunning().elapsedDays());case "dueDate"->Comparator.comparing(Receivable::dueDate);default->Comparator.comparing(Receivable::billId);};
                    if("DESC".equals(direction))comparator=comparator.reversed();rows.sort(comparator.thenComparing(Receivable::dueDate).thenComparing(Receivable::billId));
                }
                for(Receivable row:rows)sink.accept(List.of(row.projectName(),row.billId(),amount(row.unpaidAmount(),unit),row.dueDate().toString(),row.dunning().elapsedDays(),row.dunning().step(),
                    row.sentHistory().isEmpty()?"발송 이력 없음":String.join("\n",row.sentHistory().stream().map(h->h.sendDate()+" #"+h.sn()+" "+h.receiverDescription()+" "+h.sendMethodCode()+(h.resend()?" (다시)":"")).toList()),
                    row.dunning().finalStep()?"최종 단계":row.dunning().nextStep()+"단계 "+row.dunning().nextDate()));
            }
        },request);
    }
    /** UI-13 청구 목록 다운로드: 목록 API와 같은 검증·필터·정렬을 쓰고, 페이지 제한 없이 조건에 맞는 전체를 쓴다. */
    private Download createBills(JsonNode body,HttpServletRequest request) {
        if(!body.has("columns")||!body.get("columns").isArray()||body.get("columns").size()!=BILL_COLUMNS.size())throw invalid("columns");
        for(int i=0;i<BILL_COLUMNS.size();i++)if(!body.get("columns").get(i).isTextual()||!BILL_COLUMNS.get(i).equals(body.get("columns").get(i).textValue()))throw invalid("columns");
        JsonNode filters=body.get("filters");allow(filters,Set.of("year","status","month","projectId","includeCarryover"));
        Map<String,String> query=new LinkedHashMap<>();
        filters.fieldNames().forEachRemaining(key->{JsonNode v=filters.get(key);if(v.isNull()||(v.isTextual()&&v.textValue().isBlank()))return;
            if(!v.isTextual()&&!v.isBoolean()&&!v.isInt())throw invalid(key);query.put(key,v.asText());});
        if(body.has("sort"))query.put("sort",text(body,"sort"));
        if(body.has("direction"))query.put("direction",text(body,"direction"));
        BillDtos.Query list=BillInput.list(query);
        String unit=body.has("amountUnit")?text(body,"amountUnit"):"천원";
        if(!Set.of("원","천원","백만원").contains(unit))throw invalid("amountUnit");
        List<BillDtos.Bill> rows=bills.listAll(list);
        List<String> titles=List.of("청구번호","프로젝트","차수","청구일","공급가","부가세","합계","입금액","미수금","입금 예정일","상태","미수 알림");
        Set<String> money=Set.of("supplyAmount","vatAmount","totalAmount","receiptAmount","unpaidAmount");
        List<Column> columns=new ArrayList<>();
        for(int i=0;i<BILL_COLUMNS.size();i++){String id=BILL_COLUMNS.get(i);columns.add(new Column(id,titles.get(i)+(money.contains(id)?" ("+unit+")":""),money.contains(id)||"order".equals(id)?CellType.NUMBER:CellType.TEXT));}
        String conditions="year="+list.year()+"; status="+Objects.toString(list.status(),"전체")+"; month="+Objects.toString(list.month(),"")+"; projectId="+Objects.toString(list.projectId(),"")
            +"; includeCarryover="+list.includeCarryover()+"; sort="+Objects.toString(list.sort(),"기본")+" "+list.direction();
        return excel.generate(new Specification("청구",null,columns,conditions,unit,"UI-13"),sink->{
            for(BillDtos.Bill row:rows)sink.accept(List.of(row.id(),row.projectName(),row.order(),row.billingDate(),amount(row.supplyAmount(),unit),amount(row.vatAmount(),unit),amount(row.totalAmount(),unit),
                amount(row.receiptAmount(),unit),amount(row.unpaidAmount(),unit),row.dueDate(),BILL_STATUS.getOrDefault(row.status(),row.status()),
                row.dunning()==null?"":row.dunning().step()==0?(row.dunning().elapsedDays()>0?"알림 전 ("+row.dunning().elapsedDays()+"일)":"알림 전"):row.dunning().step()+"단계 ("+row.dunning().elapsedDays()+"일)"));
        },request);
    }
    private static BigDecimal amount(String value,String unit) {return MasterDataValidator.amountInUnit(new BigDecimal(value),unit);}
    private static String text(JsonNode body,String field) {if(body==null||!body.has(field)||!body.get(field).isTextual())throw invalid(field);return body.get(field).textValue();}
    private static void allow(JsonNode body,Set<String> allowed) {
        if(body==null||!body.isObject())throw invalid("body");body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 출력 항목입니다.",key,null);});
    }
    private static ApiException invalid(String field) {return new ApiException(400,"VALIDATION_ERROR","출력 조건을 확인해 주세요.",field,null);}
}
