package com.sicost.receipts;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.receipts.ReceiptDtos.*;

@Service
public class ReceiptService {
    private final ReceiptRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ReceiptService(ReceiptRepository repository,WriteCoordinator writes,RevisionService revisions,
        CurrentUserProvider users,ChangeHistoryWriter history,Clock clock){
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillReceipts getBillReceipts(String id,int year){
        ReceiptInput.id(id);users.currentUser();validateYear(year,false);repository.required(id);
        String revision=revisions.revision(repository.snapshot(id));
        List<BillReceipt> rows=repository.receipts(id).stream().map(this::view).toList();
        return new BillReceipts(rows,rows.size(),(int)rows.stream().filter(r->!r.cancelled()).count(),revision);
    }
    @Transactional
    public BillReceiptResult createBillReceipt(String id,CreateReceipt input){
        ReceiptInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());
        if(input.date()==null)throw ReceiptInput.invalid("date");
        if(input.amount()==null || input.amount().scale()>0 || input.amount().signum()<1 || input.amount().precision()>15)throw ReceiptInput.invalid("amount");
        repository.lockTimeout();StoredBill initial=repository.required(id);String actor=users.userId();long[] receiptId=new long[1];
        var result=writes.write(keys(initial,input.date()),input.expectedRevision(),()->repository.snapshot(id),()->{
            StoredBill bill=repository.required(id);period(id,null,input.year(),input.date());
            BigDecimal unpaid=bill.totalAmount().subtract(repository.normalSum(id));
            if("Y".equals(bill.cancelled()) || unpaid.signum()<=0)throw conflict("INVALID_BILL_STATE",id,null,null,"입금완료 또는 취소된 청구에는 입금을 등록할 수 없습니다.");
            if(input.amount().compareTo(unpaid)>0)throw conflict("OVER_RECEIPT",id,null,null,"입금액이 현재 미수금을 초과합니다.");
        },()->true,()->{
            LocalDateTime now=now();receiptId[0]=repository.insert(new ReceiptWrite(id,input.date(),input.amount(),actor,now));
            String key=Long.toString(receiptId[0]);
            AuditTarget target=new AuditTarget("TB_BL_RCPT",key,"NEW");
            audit(target,"BILL_ID",null,id,null);
            audit(target,"RCPT_DT",null,input.date().toString(),null);
            audit(target,"RCPT_AMT",null,money(input.amount()),null);
            audit(target,"CNCL_YN",null,"N",null);
            cache(id,actor,now);
        });
        return result(id,receiptId[0],input.year(),result.changed());
    }
    @Transactional
    public BillReceiptResult saveBillReceiptCancellation(String id,long receiptId,CancelReceipt input){
        ReceiptInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());
        String reason;
        try{reason=ProjectInputPolicy.text(input.reason(),500,"reason");}catch(ApiException e){throw ReceiptInput.invalid("reason");}
        repository.lockTimeout();StoredBill initial=repository.required(id);StoredReceipt initialReceipt=repository.receipt(id,receiptId);
        String actor=users.userId();StoredReceipt[] before=new StoredReceipt[1];
        var result=writes.write(keys(initial,initialReceipt.date()),input.expectedRevision(),()->repository.snapshot(id),()->{
            before[0]=repository.receipt(id,receiptId);validateYear(input.year(),true);
            if(!"Y".equals(before[0].cancelled()))period(id,receiptId,input.year(),before[0].date());
        },()->!"Y".equals(before[0].cancelled()),()->{
            LocalDateTime now=now();repository.cancel(new Cancellation(id,receiptId,reason,actor,now));
            String key=Long.toString(receiptId);
            AuditTarget target=new AuditTarget("TB_BL_RCPT",key,"UPD");
            audit(target,"CNCL_YN","N","Y",reason);
            audit(target,"CNCL_RSN",before[0].cancelReason(),reason,reason);
            audit(target,"CNCL_USER_ID",before[0].cancelUserId(),actor,reason);
            audit(target,"CNCL_DTTM",time(before[0].cancelledAt()),time(now),reason);
            cache(id,actor,now);
        });
        return result(id,receiptId,input.year(),result.changed());
    }
    private List<String> keys(StoredBill b,LocalDate date){
        return List.of("BILL:"+b.id(),"PROJECT:"+b.projectId(),"BILL_MONTH:"+YearMonth.from(date).toString().replace("-",""),"operating-standards:CASH_BASE_YM");
    }
    private void validateYear(Integer year,boolean writing){
        try{if(writing)ProjectInputPolicy.requireWritableYear(year,repository.cashBase(),clock);else ProjectInputPolicy.year(year,repository.cashBase(),clock);}
        catch(ApiException e){if(e.status()==400)throw ReceiptInput.invalid("year");throw e;}
    }
    private void period(String id,Long receiptId,Integer year,LocalDate date){
        repository.lockClose();validateYear(year,true);LocalDate today=ProjectInputPolicy.today(clock);
        if(date.isAfter(today))throw ReceiptInput.invalid("date");
        YearMonth month=YearMonth.from(date);
        if(date.getYear()<today.getYear())throw conflict("READ_ONLY_YEAR",id,receiptId,month,"지난 연도의 입금은 등록·취소할 수 없습니다.");
        if(month.isBefore(repository.cashBase()) || repository.closed(month))throw conflict("CLOSED_MONTH",id,receiptId,month,"입금일의 월이 마감·이관되어 변경할 수 없습니다.");
    }
    private void cache(String id,String actor,LocalDateTime now){
        StoredBill bill=repository.required(id);BigDecimal sum="Y".equals(bill.cancelled())?BigDecimal.ZERO:repository.normalSum(id);
        BigDecimal unpaid="Y".equals(bill.cancelled())?BigDecimal.ZERO:bill.totalAmount().subtract(sum);
        Cache previous=repository.cache(id);
        repository.updateCache(new CacheWrite(id,sum,unpaid,actor,now));
        AuditTarget target=new AuditTarget("TB_BL_BILL",id,"UPD");
        audit(target,"RCPT_SUM_AMT",money(previous.receiptAmount()),money(sum),null);
        audit(target,"UNPAID_AMT",money(previous.unpaidAmount()),money(unpaid),null);
    }
    private BillReceiptResult result(String id,long receiptId,int year,boolean changed){
        String revision=revisions.revision(repository.snapshot(id));
        return new BillReceiptResult(changed,view(repository.receipt(id,receiptId)),billView(repository.required(id),year,revision),revision);
    }
    public Bill billView(StoredBill b,int year,String revision){
        boolean cancelled="Y".equals(b.cancelled());BigDecimal sum=cancelled?BigDecimal.ZERO:repository.normalSum(b.id());
        BigDecimal unpaid=cancelled?BigDecimal.ZERO:b.totalAmount().subtract(sum);LocalDate today=ProjectInputPolicy.today(clock);
        String status=cancelled?"CNCL":unpaid.signum()==0?"PAID":unpaid.signum()>0 && today.isAfter(b.dueDate())?"LATE":"UNPAID";
        return new Bill(b.id(),b.projectId(),b.projectName(),b.customerName(),b.contractType(),b.order(),apiMonth(b.month()),
            b.billingDate().toString(),money(b.supplyAmount()),money(b.vatRate()),money(b.vatAmount()),money(b.totalAmount()),money(sum),money(unpaid),
            b.dueDate().toString(),b.taxInvoiceNo(),b.taxInvoiceNo()==null?"번호 미입력":null,status,cancelled,b.cancelReason(),b.cancelUserId(),time(b.cancelledAt()),
            Integer.parseInt(b.month().substring(0,4))<year,dunning(b,status,today),quick(b,year,today),revision);
    }
    private Dunning dunning(StoredBill b,String status,LocalDate today){
        if(!"LATE".equals(status))return null;
        String month=YearMonth.from(today).toString().replace("-","");
        BigDecimal[] thresholds={repository.operatingValue("DUN_D1",month),repository.operatingValue("DUN_D2",month),repository.operatingValue("DUN_D3",month)};
        if(thresholds[0]==null || thresholds[1]==null || thresholds[2]==null || thresholds[0].compareTo(BigDecimal.ONE)<0 || thresholds[1].compareTo(thresholds[0])<=0 || thresholds[2].compareTo(thresholds[1])<=0)return null;
        int days=Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(b.dueDate(),today));int step=0;
        for(BigDecimal threshold:thresholds)if(BigDecimal.valueOf(days).compareTo(threshold)>=0)step++;
        String next=step==3?null:b.dueDate().plusDays(thresholds[step].setScale(0,java.math.RoundingMode.CEILING).longValueExact()).toString();
        return new Dunning(days,step,step==3?null:step+1,next,step==3);
    }
    private QuickActions quick(StoredBill b,int year,LocalDate today){
        QuickSource source=repository.quickSource(b.id());YearMonth target=YearMonth.parse(apiMonth(b.month())).plusMonths(1);
        boolean eligible=source!=null && "ING".equals(source.status()) && !source.endDate().isBefore(today) && source.latest()
            && !"IN".equals(b.contractType()) && !"Y".equals(b.cancelled()) && year>=today.getYear()
            && !target.isAfter(YearMonth.from(source.endDate())) && !target.isBefore(repository.cashBase())
            && target.getYear()<=today.getYear()+1 && !repository.closed(target) && target.getYear()>=today.getYear();
        return new QuickActions(eligible,eligible && !"TK".equals(b.contractType()) && !source.targetExists(),eligible?null:"프로젝트·최신 청구·대상월 조건을 확인해 주세요.");
    }
    private BillReceipt view(StoredReceipt r){return new BillReceipt(r.id(),r.billId(),r.date().toString(),money(r.amount()),"Y".equals(r.cancelled()),r.cancelReason(),r.cancelUserId(),time(r.cancelledAt()));}
    private record AuditTarget(String table,String key,String type) {}
    private void audit(AuditTarget target,String item,String before,String after,String reason){history.append(new ChangeHistoryWriter.Change(target.table(),target.key(),target.type(),item,before,after,reason));}
    private ApiException conflict(String code,String id,Long receiptId,YearMonth month,String reason){
        Map<String,Object> context=new LinkedHashMap<>();context.put("billId",id);context.put("receiptId",receiptId);context.put("month",month==null?null:month.toString());context.put("reason",reason);
        return new ApiException(409,code,reason,null,Map.of("billing",context));
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    private static String money(BigDecimal v){return ProjectInputPolicy.amountString(v);}
    private static String time(LocalDateTime v){return v==null?null:v.toString();}
    private static String apiMonth(String v){return v.substring(0,4)+"-"+v.substring(4);}
}
