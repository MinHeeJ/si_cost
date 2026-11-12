package com.sicost.billsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.billsops.BillOperationsDtos.*;

@Service
public class BillOperationsService {
    private final BillOperationsRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final BillEstimateService estimates;
    public BillOperationsService(BillOperationsRepository repository,WriteCoordinator writes,RevisionService revisions,
        CurrentUserProvider users,ChangeHistoryWriter history,BillEstimateService estimates) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.estimates=estimates;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillEstimate getBillEstimate(String project,String month) {users.currentUser();return estimates.estimate(project,month);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillsSummary getBillsSummary(SummaryQuery query) {
        users.currentUser();int year=ProjectInputPolicy.year(query.year(),repository.base(),estimates.clock());
        boolean carry=query.includeCarryover()&&year==estimates.today().getYear();
        List<StoredBill> selected=repository.bills().stream().filter(b->!cancelled(b)&&billYear(b)==year).toList();
        List<StoredBill> current=selected.stream().filter(b->api(b.month()).equals(YearMonth.from(estimates.today()).toString())).toList();
        List<StoredBill> population=repository.bills().stream().filter(b->!cancelled(b)&&(billYear(b)==year||carry&&billYear(b)<year))
            .filter(b->unpaid(b).signum()>0).toList();
        String scope=year+"년"+(carry?" 및 이월 미수":"");
        return new BillsSummary(year,carry,card(selected,StoredBill::total,year+"년 미취소 청구"),
            card(current,StoredBill::total,"선택연도 중 서버 이번달"),card(population,this::unpaid,scope),
            (int)population.stream().filter(b->b.dueDate().isBefore(estimates.today())).count(),scope);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillSelection getBillSelection(SelectionQuery query) {
        users.currentUser();ProjectInputPolicy.year(query.year(),repository.base(),estimates.clock());
        for(String id:query.ids())repository.required(id);
        List<Bill> rows=repository.bills().stream().filter(b->inPopulation(b,query)).map(b->view(b,query.year()))
            .filter(b->query.status()==null||query.status().equals(b.status()))
            .filter(b->query.ids().isEmpty()||query.ids().contains(b.id()))
            .filter(b->!query.excludeIds().contains(b.id())).sorted(order(query)).toList();
        List<BillSelectionItem> items=new ArrayList<>();List<BillSelectionExcluded> excluded=new ArrayList<>();
        for(Bill bill:rows) {
            if(bill.cancelled()||new BigDecimal(bill.unpaidAmount()).signum()<=0)
                excluded.add(new BillSelectionExcluded(bill.id(),bill.cancelled()?"취소된 청구입니다.":"미수금이 없습니다."));
            else items.add(new BillSelectionItem(bill.id(),bill.unpaidAmount(),bill.revision()));
        }
        return new BillSelection(List.copyOf(items),items.size(),List.copyOf(excluded),excluded.size(),!items.isEmpty());
    }
    private boolean inPopulation(StoredBill bill,SelectionQuery q) {
        if(q.projectId()!=null&&!q.projectId().equals(bill.projectId()))return false;
        if(q.month()!=null)return q.month().equals(api(bill.month()))&&(billYear(bill)==q.year()||
            q.year()==estimates.today().getYear()&&q.includeCarryover()&&billYear(bill)<q.year()&&!cancelled(bill)&&unpaid(bill).signum()>0);
        return billYear(bill)==q.year()||q.includeCarryover()&&q.year()==estimates.today().getYear()&&billYear(bill)<q.year()&&!cancelled(bill)&&unpaid(bill).signum()>0;
    }
    private Comparator<Bill> order(SelectionQuery q) {
        if(q.sort()==null)return Comparator.comparing(Bill::billingDate).reversed().thenComparing(Bill::id,Comparator.reverseOrder());
        Comparator<Bill> comparator=switch(q.sort()) {
            case "id" -> Comparator.comparing(Bill::id);
            case "projectName" -> Comparator.comparing(Bill::projectName);
            case "order" -> Comparator.comparingInt(Bill::order);
            case "billingDate" -> Comparator.comparing(Bill::billingDate);
            case "dueDate" -> Comparator.comparing(Bill::dueDate);
            case "supplyAmount" -> money(Bill::supplyAmount);
            case "vatAmount" -> money(Bill::vatAmount);
            case "totalAmount" -> money(Bill::totalAmount);
            case "receiptAmount" -> money(Bill::receiptAmount);
            case "unpaidAmount" -> money(Bill::unpaidAmount);
            case "status" -> Comparator.comparing(b->statusName(b.status()));
            case "dunning" -> Comparator.comparing((Bill b)->b.dunning()==null?null:b.dunning().elapsedDays(),Comparator.nullsLast(Comparator.naturalOrder()));
            default -> throw BillOperationsInput.invalid("sort");
        };
        if("DESC".equals(q.direction())) {
            if("dunning".equals(q.sort()))comparator=Comparator.comparing((Bill b)->b.dunning()==null?null:b.dunning().elapsedDays(),Comparator.nullsLast(Comparator.reverseOrder()));
            else comparator=comparator.reversed();
        }
        return comparator.thenComparing(Bill::id);
    }
    private static Comparator<Bill> money(Function<Bill,String> f) {return Comparator.comparing(b->new BigDecimal(f.apply(b)));}
    private static String statusName(String code) {return switch(code){case "PAID"->"입금 완료";case "LATE"->"연체";case "CNCL"->"취소됨";default->"미수";};}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillCopyDraft getBillCopyDraft(String id,int year) {
        users.currentUser();StoredBill source=repository.required(id);Project project=repository.project(source.projectId());
        writableYear(year);YearMonth target=quickTarget(source,project);protect(target,source.id(),false);
        BillEstimate estimate=estimates.estimate(project.id(),target.toString());
        return new BillCopyDraft(id,revision(id),year,project.id(),target.toString(),"TK".equals(project.contractType())?decimal(source.supply()):estimate.supplyAmount(),
            decimal(source.rate()),estimates.today(),target.plusMonths(1).atDay(25),null,estimate.warnings());
    }
    @Transactional
    public BillMutationResult saveBillCancellation(String id,MutationRequest input) {
        String actor=users.userId();StoredBill initial=repository.required(id);repository.lockTimeout();
        var result=writes.write(keys(initial,YearMonth.parse(api(initial.month()))),input.expectedRevision(),()->repository.snapshot(id),()->{
            StoredBill current=repository.required(id);repository.lockClose();writableYear(input.year());
            if(cancelled(current))return;
            protect(YearMonth.parse(api(current.month())),id,false);
            if("TK".equals(repository.project(current.projectId()).contractType()))protect(YearMonth.from(current.billingDate()),id,false);
            if(repository.receipts(id).stream().anyMatch(r->"N".equals(r.cancelled())))
                throw conflict("INVALID_BILL_STATE",id,api(current.month()),"정상 입금이 있습니다. 먼저 입금을 취소해 주세요.");
        },()->!cancelled(repository.required(id)),()->{
            StoredBill before=repository.required(id);LocalDateTime now=estimates.now();
            repository.cancel(new Cancellation(id,input.reason(),actor,now));
            audit(id,"UPD","CNCL_YN",before.cancelled(),"Y",input.reason());
            audit(id,"UPD","CNCL_RSN",before.cancelReason(),input.reason(),input.reason());
            audit(id,"UPD","CNCL_USER_ID",before.cancelUserId(),actor,input.reason());
            audit(id,"UPD","CNCL_DTTM",before.cancelledAt()==null?null:before.cancelledAt().toString(),now.toString(),input.reason());
            audit(id,"UPD","RCPT_SUM_AMT",decimal(before.receiptCache()),"0",input.reason());
            audit(id,"UPD","UNPAID_AMT",decimal(before.unpaidCache()),"0",input.reason());
        });
        Bill bill=view(repository.required(id),input.year());return new BillMutationResult(result.changed(),bill,bill.revision(),Collections.emptyList());
    }
    @Transactional
    public BillMutationResult createNextMonthBill(String id,MutationRequest input) {
        String actor=users.userId();StoredBill initial=repository.required(id);YearMonth target=YearMonth.parse(api(initial.month())).plusMonths(1);
        repository.lockTimeout();NewBill[] created=new NewBill[1];
        writes.create(keys(initial,target),()->repository.snapshot(created[0].id()),()->{
            repository.lockClose();StoredBill source=repository.required(id);revisions.requireExpected(input.expectedRevision(),revision(id));
            writableYear(input.year());Project project=repository.project(source.projectId());quickTarget(source,project);
            if(!Set.of("MO","DP").contains(project.contractType()))throw conflict("INVALID_BILL_STATE",id,target.toString(),"TK는 고쳐서 청구로 등록해 주세요.");
            protect(target,id,true);
            if(repository.targetCount(project.id(),db(target))>0)throw conflict("INVALID_BILL_STATE",id,target.toString(),"대상월의 미취소 청구가 이미 있습니다.");
            BillEstimate estimate=estimates.estimate(project.id(),target.toString());BigDecimal supply=new BigDecimal(estimate.supplyAmount());
            if(supply.signum()<=0)throw conflict("INVALID_BILL_STATE",id,target.toString(),"추정 공급가가 0원입니다. 고쳐서 청구로 직접 입력해 주세요.");
            LocalDate due=target.plusMonths(1).atDay(25);
            if(due.isBefore(estimates.today()))throw new ApiException(400,"VALIDATION_ERROR","입금예정일이 청구일보다 빠릅니다. 고쳐서 청구에서 날짜를 수정해 주세요.","dueDate",Map.of("billing",Map.of("billId",id,"month",target.toString(),"reason","고쳐서 청구에서 날짜를 수정해 주세요.")));
            if(!BillEstimateService.validRate(source.rate()))throw BillOperationsInput.invalid("vatRate");
            BigDecimal vat=supply.multiply(source.rate()).divide(new BigDecimal("100")).setScale(0,RoundingMode.DOWN),total=supply.add(vat);
            for(BigDecimal amount:List.of(supply,vat,total))if(amount.signum()<0||amount.compareTo(new BigDecimal("999999999999999"))>0)throw BillOperationsInput.invalid("supplyAmount");
            int number=repository.greatestNumber(db(target))+1,order=repository.greatestOrder(project.id())+1;
            if(number>999)throw conflict("BILL_NUMBER_EXHAUSTED",id,target.toString(),"청구월 번호가 소진되었습니다.");
            if(order>999)throw conflict("BILL_ORDER_EXHAUSTED",id,target.toString(),"프로젝트 차수가 소진되었습니다.");
            created[0]=new NewBill("B-"+db(target)+"-"+String.format(Locale.ROOT,"%03d",number),project.id(),order,db(target),estimates.today(),supply,source.rate(),vat,total,due,actor,estimates.now());
        },()->{
            NewBill b=created[0];repository.insert(b);
            Map<String,String> values=new LinkedHashMap<>();values.put("PROJ_ID",b.projectId());values.put("BILL_ORD",Integer.toString(b.order()));
            values.put("BILL_YM",b.month());values.put("BILL_DT",b.billingDate().toString());values.put("SPLY_AMT",decimal(b.supply()));
            values.put("VAT_RT",decimal(b.rate()));values.put("VAT_AMT",decimal(b.vat()));values.put("TOT_AMT",decimal(b.total()));
            values.put("RCPT_SUM_AMT","0");values.put("UNPAID_AMT",decimal(b.total()));values.put("PAY_DUE_DT",b.dueDate().toString());values.put("CNCL_YN","N");
            values.forEach((column,value)->audit(b.id(),"NEW",column,null,value,null));
        });
        Bill bill=view(repository.required(created[0].id()),input.year());
        List<String> warnings=new ArrayList<>();warnings.add("세금계산서 번호 미입력");
        if(target.getYear()!=input.year())warnings.add(target.getYear()+"년 목록에서 신규 청구를 확인해 주세요.");
        return new BillMutationResult(true,bill,bill.revision(),List.copyOf(warnings));
    }
    private void writableYear(Integer year) {ProjectInputPolicy.requireWritableYear(year,repository.base(),estimates.clock());}
    private YearMonth quickTarget(StoredBill source,Project project) {
        YearMonth target=YearMonth.parse(api(source.month())).plusMonths(1);
        if(cancelled(source)||!source.id().equals(repository.latest(source.projectId()))||!"ING".equals(project.status())||project.endDate().isBefore(estimates.today())||"IN".equals(project.contractType())||target.isAfter(YearMonth.from(project.endDate())))
            throw conflict("INVALID_BILL_STATE",source.id(),target.toString(),"프로젝트 전체 최신 미취소 청구와 진행·계약 종료월을 확인해 주세요.");
        return target;
    }
    private void protect(YearMonth target,String id,boolean fixed) {
        if(target.isBefore(repository.base()))throw conflict("CLOSED_MONTH",id,target.toString(),"운영 시작월 이전 청구는 변경할 수 없습니다.");
        ProjectInputPolicy.year(target.getYear(),repository.base(),estimates.clock());
        if(target.getYear()<estimates.today().getYear())throw conflict("READ_ONLY_YEAR",id,target.toString(),"지난 연도의 청구월은 변경할 수 없습니다.");
        if(target.isBefore(repository.base())||repository.closed(db(target))||fixed&&repository.fixed(repository.required(id).projectId(),db(target)))
            throw conflict("CLOSED_MONTH",id,target.toString(),"마감·이관·보호 월은 청구를 변경할 수 없습니다.");
    }
    private List<String> keys(StoredBill bill,YearMonth target) {
        return List.of("BILL:"+bill.id(),"BILL_MONTH:"+db(target),"PROJECT:"+bill.projectId(),"operating-standards:CASH_BASE_YM");
    }
    public String revision(String id) {return revisions.revision(repository.snapshot(id));}
    public Bill view(StoredBill b,int year) {
        Project p=repository.project(b.projectId());boolean cancelled=cancelled(b);BigDecimal paid=cancelled?BigDecimal.ZERO:paid(b.id()),unpaid=cancelled?BigDecimal.ZERO:b.total().subtract(paid);
        String status=cancelled?"CNCL":unpaid.signum()==0?"PAID":estimates.today().isAfter(b.dueDate())?"LATE":"UNPAID";
        boolean quick=year>=estimates.today().getYear()&&!cancelled&&"ING".equals(p.status())&&!p.endDate().isBefore(estimates.today())&&!"IN".equals(p.contractType())
            &&b.id().equals(repository.latest(p.id()))&&!YearMonth.parse(api(b.month())).plusMonths(1).isAfter(YearMonth.from(p.endDate()));
        return new Bill(b.id(),p.id(),p.name(),p.customerName(),p.contractType(),b.order(),api(b.month()),b.billingDate(),decimal(b.supply()),decimal(b.rate()),decimal(b.vat()),decimal(b.total()),decimal(paid),decimal(unpaid),b.dueDate(),b.invoice(),
            b.invoice()==null?"세금계산서 번호 미입력":null,status,cancelled,b.cancelReason(),b.cancelUserId(),b.cancelledAt()==null?null:b.cancelledAt().toString(),billYear(b)<year,
            dunning(b,unpaid),new QuickActions(quick,quick&&Set.of("MO","DP").contains(p.contractType()),quick?null:"빠른 청구 대상이 아닙니다."),revision(b.id()));
    }
    private Dunning dunning(StoredBill bill,BigDecimal unpaid) {
        if(cancelled(bill)||unpaid.signum()<=0)return null;
        String month=db(YearMonth.from(estimates.today()));BigDecimal d1=repository.standard("DUN_D1",month),d2=repository.standard("DUN_D2",month),d3=repository.standard("DUN_D3",month);
        if(d1==null||d2==null||d3==null||d1.compareTo(BigDecimal.ONE)<0||d1.compareTo(d2)>=0||d2.compareTo(d3)>=0)return null;
        int days=(int)Math.max(0,ChronoUnit.DAYS.between(bill.dueDate(),estimates.today()));BigDecimal elapsed=BigDecimal.valueOf(days);int step=elapsed.compareTo(d3)>=0?3:elapsed.compareTo(d2)>=0?2:elapsed.compareTo(d1)>=0?1:0;
        BigDecimal next=step==0?d1:step==1?d2:step==2?d3:null;
        return new Dunning(days,step,step==3?null:step+1,next==null?null:bill.dueDate().plusDays(next.setScale(0,RoundingMode.CEILING).longValueExact()),step==3);
    }
    private BigDecimal paid(String id) {return repository.receipts(id).stream().filter(r->"N".equals(r.cancelled())).map(Receipt::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BigDecimal unpaid(StoredBill b) {return cancelled(b)?BigDecimal.ZERO:b.total().subtract(paid(b.id()));}
    private static boolean cancelled(StoredBill b) {return "Y".equals(b.cancelled());}
    private static int billYear(StoredBill b) {return Integer.parseInt(b.month().substring(0,4));}
    private static String decimal(BigDecimal value) {return value.stripTrailingZeros().toPlainString();}
    private static String api(String value) {return DatabaseMonthCodec.toApi(value);}
    private static String db(YearMonth value) {return DatabaseMonthCodec.toDatabase(value.toString());}
    private static BillingSummaryCard card(List<StoredBill> rows,Function<StoredBill,BigDecimal> value,String scope) {
        return new BillingSummaryCard(decimal(rows.stream().map(value).reduce(BigDecimal.ZERO,BigDecimal::add)),rows.size(),scope);
    }
    private void audit(String id,String type,String column,String before,String after,String reason) {history.append(new ChangeHistoryWriter.Change("TB_BL_BILL",id,type,column,before,after,reason));}
    private static ApiException conflict(String code,String id,String month,String reason) {return new ApiException(409,code,reason,null,Map.of("billing",Map.of("billId",id,"month",month,"reason",reason)));}
}
