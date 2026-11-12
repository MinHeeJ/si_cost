package com.sicost.bills;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.bills.BillDtos.*;
import static com.sicost.bills.BillRepository.*;

@Service
public class BillService {
    private final BillRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public BillService(BillRepository repository,WriteCoordinator writes,RevisionService revisions,CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.audit=audit;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillsPage getBills(Query query) {
        List<Bill> all=matching(query);
        return new BillsPage(all.stream().skip((long)(query.page()-1)*query.size()).limit(query.size()).toList(),all.size(),query.page(),query.size(),totals(all));
    }
    /** Every row the list shows for these conditions and order, without paging (used by the excel download; same rows as the list and its totals). */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Bill> listAll(Query query) {return matching(query);}
    private List<Bill> matching(Query query) {
        users.currentUser();ProjectInputPolicy.year(query.year(),repository.cashBase(),clock);
        return repository.list(new Filter(query,today().getYear(),today())).stream().map(b->view(repository.snapshot(b.id()),query.year())).sorted(ordering(query)).toList();
    }
    private Totals totals(List<Bill> all) {
        List<Bill> effective=all.stream().filter(b->!b.cancelled()).toList();
        return new Totals(sum(effective,Bill::supplyAmount),sum(effective,Bill::vatAmount),sum(effective,Bill::totalAmount),sum(effective,Bill::receiptAmount),sum(effective,Bill::unpaidAmount));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillDetail getBill(String id,int year) {
        users.currentUser();ProjectInputPolicy.year(year,repository.cashBase(),clock);Snapshot snapshot=repository.snapshot(BillInput.id(id));Bill bill=view(snapshot,year);
        List<Receipt> receipts=snapshot.receipts().stream().sorted(Comparator.comparing(ReceiptStored::date).reversed().thenComparing(Comparator.comparingLong(ReceiptStored::id).reversed()))
            .map(r->new Receipt(r.id(),r.billId(),r.date().toString(),money(r.amount()),r.cancelled(),r.cancelReason(),r.cancelUserId(),time(r.cancelledAt()))).toList();
        return new BillDetail(bill,new Receipts(receipts,receipts.size(),(int)receipts.stream().filter(r->!r.cancelled()).count(),bill.revision()),bill.revision());
    }
    @Transactional
    public BillMutationResult createBill(CreateBillRequest input) {
        String actor=users.userId();repository.lockTimeout();String[] id=new String[1];Project[] project=new Project[1];
        List<String> keys=new ArrayList<>(List.of("PROJECT:"+input.projectId(),"BILL_MONTH:"+db(input.billingMonth()),"operating-standards:CASH_BASE_YM"));
        if(input.copyContext()!=null)keys.add("BILL:"+input.copyContext().sourceBillId());
        writes.create(keys,()->repository.revisionSnapshot(id[0]),()->{
            repository.lockClose();project[0]=repository.project(input.projectId());writable(input.year(),input.billingMonth());
            if("TK".equals(project[0].contractType()))protect(YearMonth.from(input.fields().billingDate()).toString());
            String status=effective(project[0]);
            if("IN".equals(project[0].contractType())||(!"ING".equals(status)&&!("END".equals(status)&&input.checkContext()!=null)))
                throw conflict("INVALID_BILL_STATE","신규 청구는 진행 중인 외부 프로젝트만 선택할 수 있습니다.",null,input.billingMonth());
            validateCheck(input.checkContext(),input.projectId(),input.billingMonth());
            validateCopy(input,project[0]);
            if(input.checkContext()==null)duplicates(input);
        },()->{
            int number=repository.nextNumber(input.billingMonth()),order=repository.nextOrder(input.projectId());
            id[0]="B-"+db(input.billingMonth())+"-"+String.format(Locale.ROOT,"%03d",number);
            Editable f=input.fields();BigDecimal total=BillInput.total(f.supplyAmount(),f.vatRate());
            repository.insert(new Write(id[0],input.projectId(),order,db(input.billingMonth()),f,BillInput.vat(f.supplyAmount(),f.vatRate()),total,BigDecimal.ZERO,actor,now()));
            append(id[0],"NEW","PROJ_ID",null,input.projectId());append(id[0],"NEW","BILL_YM",null,input.billingMonth());append(id[0],"NEW","BILL_ORD",null,Integer.toString(order));
            auditFields(id[0],null,f);append(id[0],"NEW","VAT_AMT",null,money(BillInput.vat(f.supplyAmount(),f.vatRate())));append(id[0],"NEW","TOT_AMT",null,money(total));
            append(id[0],"NEW","RCPT_SUM_AMT",null,"0");append(id[0],"NEW","UNPAID_AMT",null,money(total));append(id[0],"NEW","CNCL_YN",null,"N");
        });
        return result(repository.snapshot(id[0]),input.year(),true);
    }
    @Transactional
    public BillMutationResult putBill(String id,PutBillRequest input) {
        BillInput.id(id);String actor=users.userId();repository.lockTimeout();Stored initial=repository.required(id);Snapshot[] before=new Snapshot[1];
        var result=writes.write(List.of("BILL:"+id,"PROJECT:"+initial.projectId(),"BILL_MONTH:"+initial.billingMonth(),"operating-standards:CASH_BASE_YM"),input.expectedRevision(),
            ()->repository.revisionSnapshot(id),()->{
                repository.lockClose();before[0]=repository.snapshot(id);Stored old=before[0].bill();Project project=repository.project(old.projectId());
                writable(input.year(),api(old.billingMonth()));
                if(old.cancelled())throw conflict("INVALID_BILL_STATE","취소된 청구는 수정할 수 없습니다.",id,api(old.billingMonth()));
                if("TK".equals(project.contractType())){protect(YearMonth.from(old.billingDate()).toString());protect(YearMonth.from(input.fields().billingDate()).toString());}
                validateCheck(input.checkContext(),old.projectId(),api(old.billingMonth()));
                if(BillInput.total(input.fields().supplyAmount(),input.fields().vatRate()).compareTo(receiptSum(before[0]))<0)
                    throw conflict("BILL_TOTAL_BELOW_RECEIPTS","청구 합계는 정상 입금합보다 작을 수 없습니다.",id,api(old.billingMonth()));
            },()->!same(before[0].bill(),input.fields()),()->{
                Stored old=before[0].bill();Editable f=input.fields();BigDecimal total=BillInput.total(f.supplyAmount(),f.vatRate()),paid=receiptSum(before[0]);
                repository.update(new Write(id,old.projectId(),old.order(),old.billingMonth(),f,BillInput.vat(f.supplyAmount(),f.vatRate()),total,paid,actor,now()));
                auditFields(id,old,f);append(id,"UPD","VAT_AMT",money(old.vatAmount()),money(BillInput.vat(f.supplyAmount(),f.vatRate())));
                append(id,"UPD","TOT_AMT",money(old.totalAmount()),money(total));append(id,"UPD","RCPT_SUM_AMT",money(old.receiptAmount()),money(paid));append(id,"UPD","UNPAID_AMT",money(old.unpaidAmount()),money(total.subtract(paid)));
            });
        return result(repository.snapshot(id),input.year(),result.changed());
    }
    private void writable(int year,String month) {ProjectInputPolicy.requireWritableYear(year,repository.cashBase(),clock);protect(month);}
    private void protect(String value) {
        YearMonth month=YearMonth.parse(value),base=repository.cashBase();
        if(month.isBefore(base)||repository.closed(db(value)))throw conflict("CLOSED_MONTH","마감 또는 운영 시작월 이전 청구는 변경할 수 없습니다.",null,value);
        if(month.getYear()<today().getYear())throw conflict("READ_ONLY_YEAR","지난 연도 업무월은 조회만 할 수 있습니다.",null,value);
        if(month.getYear()>today().getYear()+1)throw BillInput.invalid("billingMonth");
    }
    private void duplicates(CreateBillRequest input) {
        List<Snapshot> all=repository.monthBills(input.projectId(),input.billingMonth());List<Snapshot> active=all.stream().filter(s->!s.bill().cancelled()).toList();
        if(active.isEmpty()&&!input.confirmDuplicate())return;
        String token=revisions.revision(new DuplicateSnapshot(new DuplicateDraft(input.year(),input.projectId(),input.billingMonth(),input.fields(),input.copyContext()),all));
        if(!active.isEmpty()&&input.confirmDuplicate()&&Objects.equals(token,input.duplicateToken()))return;
        if(active.isEmpty()&&input.confirmDuplicate()&&Objects.equals(token,input.duplicateToken()))return;
        Map<String,Object> context=new LinkedHashMap<>();context.put("reason","같은 달 청구를 추가하려면 등록을 한 번 더 누르세요.");
        context.put("month",input.billingMonth());context.put("existingBills",active.stream().map(s->new ExistingBill(s.bill().id(),s.bill().order())).toList());context.put("duplicateToken",token);
        throw new ApiException(409,"DUPLICATE_BILL_CONFIRMATION_REQUIRED","같은 달 청구를 추가하려면 등록을 한 번 더 누르세요.",null,Map.of("billing",context));
    }
    private void validateCopy(CreateBillRequest input,Project project) {
        if(input.copyContext()==null)return;Snapshot source=repository.snapshot(input.copyContext().sourceBillId());revisions.requireExpected(input.copyContext().expectedRevision(),revisions.revision(repository.revisionSnapshot(source.bill().id())));
        Stored row=source.bill();String next=YearMonth.parse(api(row.billingMonth())).plusMonths(1).toString();
        if(!row.projectId().equals(input.projectId())||!next.equals(input.billingMonth())||!quick(row,project,input.year()).copyAllowed())
            throw conflict("INVALID_BILL_STATE","복사 원본 또는 고정 프로젝트·다음 청구월을 다시 확인해 주세요.",row.id(),next);
    }
    public void validateCheck(CheckContext context,String projectId,String month) {
        if(context==null)return;
        BigDecimal min=repository.standard("BILL_MISS_MIN",month),rate=repository.standard("BILL_MISS_RT",month);
        if(min==null||rate==null||min.signum()<0||rate.signum()<0)throw new ApiException(500,"INTERNAL_ERROR","청구 누락 허용오차 설정을 확인해 주세요.");
        if(!projectId.equals(context.projectId())||!month.equals(context.month())||!revisions.revision(repository.checkSnapshot(projectId,month)).equals(context.checkRevision()))
            throw conflict("BILL_CHECK_CHANGED","청구 또는 월별 투입 자료가 바뀌었습니다. 점검을 다시 조회해 주세요.",null,month);
    }
    public Bill view(Snapshot snapshot,int year) {
        Stored row=snapshot.bill();Project project=repository.project(row.projectId());BigDecimal paid=row.cancelled()?BigDecimal.ZERO:receiptSum(snapshot),unpaid=row.cancelled()?BigDecimal.ZERO:row.totalAmount().subtract(paid);
        String status=row.cancelled()?"CNCL":unpaid.signum()==0?"PAID":unpaid.signum()>0&&today().isAfter(row.dueDate())?"LATE":"UNPAID";
        return new Bill(row.id(),row.projectId(),project.name(),project.customerName(),project.contractType(),row.order(),api(row.billingMonth()),row.billingDate().toString(),money(row.supplyAmount()),money(row.vatRate()),money(row.vatAmount()),money(row.totalAmount()),money(paid),money(unpaid),row.dueDate().toString(),row.taxInvoiceNo(),row.taxInvoiceNo()==null?"번호 미입력":null,status,row.cancelled(),row.cancelReason(),row.cancelUserId(),time(row.cancelledAt()),Integer.parseInt(row.billingMonth().substring(0,4))<year,dunning(row,unpaid),quick(row,project,year),revisions.revision(repository.revisionSnapshot(row.id())));
    }
    private Dunning dunning(Stored row,BigDecimal unpaid) {
        if(row.cancelled()||unpaid.signum()<=0)return null;
        String month=YearMonth.from(today()).toString();BigDecimal[] thresholds={repository.standard("DUN_D1",month),repository.standard("DUN_D2",month),repository.standard("DUN_D3",month)};
        if(thresholds[0]==null||thresholds[1]==null||thresholds[2]==null||thresholds[0].compareTo(BigDecimal.ONE)<0||thresholds[0].compareTo(thresholds[1])>=0||thresholds[1].compareTo(thresholds[2])>=0)return null;
        int elapsed=(int)Math.max(0,ChronoUnit.DAYS.between(row.dueDate(),today())),step=0;for(int i=0;i<3;i++)if(new BigDecimal(elapsed).compareTo(thresholds[i])>=0)step=i+1;
        return new Dunning(elapsed,step,step==3?null:step+1,step==3?null:row.dueDate().plusDays(thresholds[step].setScale(0,RoundingMode.CEILING).longValueExact()).toString(),step==3);
    }
    private QuickActions quick(Stored row,Project project,int year) {
        Stored latest=repository.projectBills(row.projectId()).stream().filter(b->!b.cancelled()).findFirst().orElse(null);
        YearMonth next=YearMonth.parse(api(row.billingMonth())).plusMonths(1);
        boolean allowed=!row.cancelled()&&latest!=null&&latest.id().equals(row.id())&&"ING".equals(effective(project))&&!"IN".equals(project.contractType())&&year>=today().getYear()
            &&next.getYear()<=today().getYear()+1&&!next.isAfter(YearMonth.from(project.endDate()))&&next.getYear()>=today().getYear()&&!next.isBefore(repository.cashBase())&&!repository.closed(db(next.toString()));
        return new QuickActions(allowed,allowed&&Set.of("MO","DP").contains(project.contractType()),allowed?null:"최신 미취소 청구·진행 프로젝트·열린 다음 청구월만 가능합니다.");
    }
    private String effective(Project p) {return "ING".equals(p.status())&&p.endDate().isBefore(today())?"END":p.status();}
    private Comparator<Bill> ordering(Query q) {
        if(q.sort()==null)return Comparator.comparing(Bill::billingDate).reversed().thenComparing(Bill::id,Comparator.reverseOrder());
        Comparator<Bill> comparator=switch(q.sort()) {
            case "id"->Comparator.comparing(Bill::id);case "projectName"->Comparator.comparing(Bill::projectName,Comparator.nullsLast(Comparator.naturalOrder()));case "order"->Comparator.comparingInt(Bill::order);
            case "billingDate"->Comparator.comparing(Bill::billingDate);case "dueDate"->Comparator.comparing(Bill::dueDate);case "status"->Comparator.comparing(b->statusName(b.status()));
            case "supplyAmount"->numeric(Bill::supplyAmount);case "vatAmount"->numeric(Bill::vatAmount);case "totalAmount"->numeric(Bill::totalAmount);case "receiptAmount"->numeric(Bill::receiptAmount);case "unpaidAmount"->numeric(Bill::unpaidAmount);
            case "dunning"->Comparator.comparing((Bill b)->b.dunning()==null?null:b.dunning().elapsedDays(),Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(b->b.dunning()==null?null:b.dunning().step(),Comparator.nullsLast(Comparator.naturalOrder()));
            default->throw BillInput.invalid("sort");
        };
        if("DESC".equals(q.direction()))comparator=comparator.reversed();
        if("dunning".equals(q.sort()))comparator=Comparator.comparing((Bill b)->b.dunning()==null).thenComparing(comparator);
        if("projectName".equals(q.sort()))comparator=Comparator.comparing((Bill b)->b.projectName()==null).thenComparing(comparator);
        return comparator.thenComparing(Bill::id);
    }
    private Comparator<Bill> numeric(Function<Bill,String> value) {return Comparator.comparing(b->new BigDecimal(value.apply(b)));}
    private String statusName(String value) {return switch(value){case "PAID"->"입금 완료";case "LATE"->"연체";case "CNCL"->"취소됨";default->"미수";};}
    private static String sum(List<Bill> rows,Function<Bill,String> value) {return money(rows.stream().map(value).map(BigDecimal::new).reduce(BigDecimal.ZERO,BigDecimal::add));}
    public static BigDecimal receiptSum(Snapshot snapshot) {return snapshot.receipts().stream().filter(r->!r.cancelled()).map(ReceiptStored::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BillMutationResult result(Snapshot snapshot,int year,boolean changed) {Bill bill=view(snapshot,year);return new BillMutationResult(changed,bill,bill.revision(),bill.taxInvoiceWarning()==null?Collections.emptyList():List.of(bill.taxInvoiceWarning()));}
    private boolean same(Stored old,Editable f) {return old.supplyAmount().compareTo(f.supplyAmount())==0&&old.vatRate().compareTo(f.vatRate())==0&&old.billingDate().equals(f.billingDate())&&old.dueDate().equals(f.dueDate())&&Objects.equals(old.taxInvoiceNo(),f.taxInvoiceNo());}
    private void auditFields(String id,Stored old,Editable f) {
        String type=old==null?"NEW":"UPD";append(id,type,"SPLY_AMT",old==null?null:money(old.supplyAmount()),money(f.supplyAmount()));append(id,type,"VAT_RT",old==null?null:money(old.vatRate()),money(f.vatRate()));
        append(id,type,"BILL_DT",old==null?null:old.billingDate().toString(),f.billingDate().toString());append(id,type,"PAY_DUE_DT",old==null?null:old.dueDate().toString(),f.dueDate().toString());append(id,type,"TAX_INVC_NO",old==null?null:old.taxInvoiceNo(),f.taxInvoiceNo());
    }
    private void append(String id,String type,String item,String before,String after) {if(!Objects.equals(before,after))audit.append(new ChangeHistoryWriter.Change("TB_BL_BILL",id,type,item,before,after,null));}
    private ApiException conflict(String code,String reason,String id,String month) {Map<String,Object> billing=new LinkedHashMap<>();billing.put("reason",reason);if(id!=null)billing.put("billId",id);if(month!=null)billing.put("month",month);return new ApiException(409,code,reason,null,Map.of("billing",billing));}
    private LocalDate today() {return ProjectInputPolicy.today(clock);}
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    private static String time(LocalDateTime time) {return time==null?null:time.toString();}
    public static String money(BigDecimal amount) {return amount.stripTrailingZeros().toPlainString();}
}
