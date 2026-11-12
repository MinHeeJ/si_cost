package com.sicost.receiptsops;

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
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;

@Service
public class ReceiptBatchService {
    private final ReceiptBatchRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ReceiptBatchService(ReceiptBatchRepository repository,WriteCoordinator writes,RevisionService revisions,
                               CurrentUserProvider users,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional
    public Result createBatchReceipts(Request input) {
        String actor=users.userId();repository.lockTimeout();
        Set<String> keys=new TreeSet<>();keys.add("operating-standards:CASH_BASE_YM");
        keys.add("BILL_MONTH:"+input.date().toString().substring(0,7).replace("-",""));
        Map<String,BillState> originals=new LinkedHashMap<>();
        for(Item item:input.items()) {
            BillState bill=repository.required(item.billId());originals.put(item.billId(),bill);
            keys.add("BILL:"+bill.id());keys.add("PROJECT:"+bill.projectId());
        }
        List<ReceiptResult> results=new ArrayList<>();
        writes.create(keys,()->input.items().stream().map(i->repository.revision(i.billId())).toList(),()->{
            repository.lockClose();validateDate(input);
            for(int i=0;i<input.items().size();i++) {
                Item item=input.items().get(i);BillState bill=repository.required(item.billId());
                if(!bill.projectId().equals(originals.get(item.billId()).projectId()))throw conflict("STALE_WRITE",bill.id(),"청구가 변경되었습니다. 다시 조회해 주세요.");
                try {revisions.requireExpected(item.expectedRevision(),repository.revision(bill.id()));}
                catch(ApiException e) {throw conflict("STALE_WRITE",bill.id(),"다른 사용자가 변경했습니다. 전체 선택을 다시 조회해 주세요.");}
                if("Y".equals(bill.cancelled())||bill.unpaid().signum()<=0)throw conflict("INVALID_BILL_STATE",bill.id(),"취소되었거나 입금이 완료된 청구입니다.");
                if(ReceiptBatchInput.amount(item.amount(),"items["+i+"].amount").compareTo(bill.unpaid())>0)
                    throw conflict("OVER_RECEIPT",bill.id(),"입금액이 현재 미수금을 초과합니다.");
            }
        },()->{
            LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
            for(Item item:input.items()) {
                ReceiptWrite value=new ReceiptWrite(item.billId(),input.date(),new BigDecimal(item.amount()),actor,now);
                repository.insert(value);
                BillState current=repository.required(item.billId());
                repository.cache(new CacheWrite(current.id(),current.receiptAmount(),current.unpaid(),actor,now));
                audit(value,"BILL_ID",item.billId());audit(value,"RCPT_DT",input.date().toString());
                audit(value,"RCPT_AMT",item.amount());audit(value,"CNCL_YN","N");
                String revision=repository.revision(item.billId());
                results.add(new ReceiptResult(true,new Receipt(value.getId(),item.billId(),input.date().toString(),item.amount(),false,null,null,null),
                    repository.view(item.billId(),input.year(),ProjectInputPolicy.today(clock)),revision));
            }
        });
        String total=input.items().stream().map(i->new BigDecimal(i.amount())).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString();
        return new Result(List.copyOf(results),results.size(),total);
    }
    private void validateDate(Request input) {
        YearMonth base=repository.cashBase();
        ProjectInputPolicy.requireWritableYear(input.year(),base,clock);
        LocalDate today=ProjectInputPolicy.today(clock);YearMonth actual=YearMonth.from(input.date());
        if(input.date().isAfter(today))throw ReceiptBatchInput.invalid("date");
        if(actual.getYear()<today.getYear())throw conflict("READ_ONLY_YEAR",null,"지난 연도의 입금일에는 등록할 수 없습니다.");
        if(actual.isBefore(base)||repository.closed(actual))throw new ApiException(409,"CLOSED_MONTH","마감·이관 월에는 입금할 수 없습니다.","date",
            Map.of("billing",Map.of("month",actual.toString(),"reason","입금일의 월이 마감·이관 월입니다.")));
    }
    private ApiException conflict(String code,String id,String reason) {
        Map<String,Object> detail=new LinkedHashMap<>();detail.put("reason",reason);if(id!=null)detail.put("billId",id);
        return new ApiException(409,code,reason,null,Map.of("billing",detail));
    }
    private void audit(ReceiptWrite value,String item,String after) {
        history.append(new ChangeHistoryWriter.Change("TB_BL_RCPT",value.getId().toString(),"NEW",item,null,after,null));
    }
}
