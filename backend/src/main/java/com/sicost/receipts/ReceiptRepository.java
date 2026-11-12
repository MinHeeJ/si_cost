package com.sicost.receipts;

import com.sicost.common.api.ApiException;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.receipts.ReceiptDtos.*;

@Repository
public class ReceiptRepository {
    private final ReceiptMapper mapper;
    private final SharedQueriesMapper shared;
    public ReceiptRepository(ReceiptMapper mapper,SharedQueriesMapper shared){this.mapper=mapper;this.shared=shared;}
    public StoredBill required(String id){StoredBill b=mapper.bill(id);if(b==null)throw missing();return b;}
    public String snapshot(String id){required(id);return mapper.snapshot(id);}
    public List<StoredReceipt> receipts(String id){return mapper.receipts(id);}
    public StoredReceipt receipt(String id,long receiptId){return receipts(id).stream().filter(r->r.id()==receiptId).findFirst().orElseThrow(this::missing);}
    public YearMonth cashBase(){String s=shared.cashBaseMonth();if(s==null)throw new IllegalStateException("운영 시작월이 없습니다.");return YearMonth.of(Integer.parseInt(s.substring(0,4)),Integer.parseInt(s.substring(4)));}
    public boolean closed(YearMonth month){String s=month.toString().replace("-","");return shared.monthCloses(s,s).stream().anyMatch(r->"CLOSE".equals(r.status()));}
    public void lockClose(){mapper.lockClose();}
    public void lockTimeout(){mapper.lockTimeout();}
    public long insert(ReceiptWrite w){
        Map<String,Object> values=new HashMap<>();values.put("billId",w.billId());values.put("date",w.date());values.put("amount",w.amount());values.put("actor",w.actor());values.put("now",w.now());
        one(mapper.insert(values));Object id=values.get("id");if(!(id instanceof Number))throw new IllegalStateException("입금 식별자를 받지 못했습니다.");return ((Number)id).longValue();
    }
    public void cancel(Cancellation w){one(mapper.cancel(w));}
    public void updateCache(CacheWrite w){one(mapper.updateCache(w));}
    public Cache cache(String id){return mapper.cache(id);}
    public QuickSource quickSource(String id){return mapper.quickSource(id);}
    public BigDecimal operatingValue(String code,String month){return mapper.operatingValue(code,month);}
    public BigDecimal normalSum(String id){return receipts(id).stream().filter(r->"N".equals(r.cancelled())).map(StoredReceipt::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void one(int count){if(count!=1)throw new IllegalStateException("입금을 저장하지 못했습니다.");}
    private ApiException missing(){return new ApiException(404,"NOT_FOUND","청구 또는 해당 청구의 입금을 찾을 수 없습니다.");}
}
