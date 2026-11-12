package com.sicost.receiptsops;

import com.sicost.apiops.BillingReviewRepository;
import com.sicost.apiops.ReceivableService;
import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;

@Repository
public class ReceiptBatchRepository {
    private final ReceiptBatchMapper mapper;
    private final SharedQueriesMapper shared;
    private final BillingReviewRepository billing;
    public ReceiptBatchRepository(ReceiptBatchMapper mapper,SharedQueriesMapper shared,BillingReviewRepository billing) {
        this.mapper=mapper;this.shared=shared;this.billing=billing;
    }
    public BillState required(String id) {
        BillState state=mapper.state(id);
        if(state==null)throw new ApiException(404,"NOT_FOUND","청구를 찾을 수 없습니다.",null,
            Map.of("billing",Map.of("billId",id,"reason","청구를 찾을 수 없습니다.")));
        return state;
    }
    public String revision(String id) { required(id);return billing.billRevision(id); }
    public void lockTimeout() {mapper.lockTimeout();}
    public void lockClose() {mapper.lockClose();}
    public YearMonth cashBase() {
        String base=shared.cashBaseMonth();
        if(base==null)throw new IllegalStateException("운영 시작월 설정을 확인해 주세요.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(base));
    }
    public boolean closed(YearMonth month) {
        String db=DatabaseMonthCodec.toDatabase(month.toString());
        return shared.monthCloses(db,db).stream().anyMatch(row->"CLOSE".equals(row.status()));
    }
    public void insert(ReceiptWrite value) {
        if(mapper.insert(value)!=1||value.getId()==null)throw new IllegalStateException("입금을 저장하지 못했습니다.");
    }
    public void cache(CacheWrite value) {
        if(mapper.cache(value)!=1)throw new IllegalStateException("입금 잔액을 저장하지 못했습니다.");
    }
    public Map<String,Object> view(String id,int year,LocalDate today) {
        Map<String,Object> row=new LinkedHashMap<>(mapper.bill(id));
        boolean cancelled=Boolean.TRUE.equals(row.get("cancelled"));
        BigDecimal unpaid=new BigDecimal((String)row.get("unpaidAmount"));
        LocalDate due=LocalDate.parse((String)row.get("dueDate"));
        row.put("status",cancelled?"CNCL":unpaid.signum()==0?"PAID":today.isAfter(due)?"LATE":"UNPAID");
        row.put("carryover",YearMonth.parse((String)row.get("billingMonth")).getYear()<year);
        row.put("taxInvoiceNo",row.get("taxInvoiceNo"));
        row.put("cancelReason",row.get("cancelReason"));row.put("cancelUserId",row.get("cancelUserId"));row.put("cancelledAt",row.get("cancelledAt"));
        row.put("taxInvoiceWarning",row.get("taxInvoiceNo")==null?"세금계산서 번호 미입력":null);
        String month=DatabaseMonthCodec.toDatabase(YearMonth.from(today).toString());
        List<BigDecimal> thresholds=new ArrayList<>();
        for(String code:List.of("DUN_D1","DUN_D2","DUN_D3"))thresholds.add(billing.standard(code,month));
        boolean valid=thresholds.stream().allMatch(Objects::nonNull)&&thresholds.get(0).compareTo(BigDecimal.ONE)>=0
            &&thresholds.get(0).compareTo(thresholds.get(1))<0&&thresholds.get(1).compareTo(thresholds.get(2))<0;
        row.put("dunning",valid&&!cancelled&&unpaid.signum()>0&&due.isBefore(today)?ReceivableService.dunning(due,today,thresholds):null);
        boolean eligible="ING".equals(row.remove("projectStatus"))&&!"IN".equals(row.get("contractType"))
            &&Boolean.TRUE.equals(row.remove("latest"))&&!cancelled;
        YearMonth next=YearMonth.parse((String)row.get("billingMonth")).plusMonths(1);
        eligible=eligible&&!next.isAfter(YearMonth.from(LocalDate.parse((String)row.remove("contractEnd"))))
            &&!next.isBefore(cashBase())&&next.getYear()>=today.getYear()&&next.getYear()<=today.getYear()+1&&!closed(next);
        Map<String,Object> actions=new LinkedHashMap<>();actions.put("copyAllowed",eligible);
        actions.put("nextMonthAllowed",eligible&&Set.of("MO","DP").contains(row.get("contractType")));
        actions.put("reason",eligible?null:"빠른 청구 조건을 확인해 주세요.");row.put("quickActions",actions);
        row.put("revision",revision(id));return row;
    }
}
