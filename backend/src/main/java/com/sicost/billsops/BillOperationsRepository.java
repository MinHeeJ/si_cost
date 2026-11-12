package com.sicost.billsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.billsops.BillOperationsDtos.*;

@Repository
public class BillOperationsRepository {
    private final BillOperationsMapper mapper;
    private final SharedQueriesMapper shared;
    public BillOperationsRepository(BillOperationsMapper mapper,SharedQueriesMapper shared) {this.mapper=mapper;this.shared=shared;}
    public Project project(String id) {Project value=mapper.project(id);if(value==null)throw missing();return value;}
    public StoredBill required(String id) {StoredBill value=mapper.bill(id);if(value==null)throw missing();return value;}
    public String snapshot(String id) {required(id);return mapper.snapshot(id);}
    public List<StoredBill> bills() {return mapper.bills();}
    public List<Receipt> receipts(String id) {return mapper.receipts(id);}
    public List<String> assignments(String id) {return mapper.assignments(id);}
    public Developer developer(String id) {Developer value=mapper.developer(id);if(value==null)throw missing();return value;}
    public YearMonth base() {
        String value=shared.cashBaseMonth();if(value==null)throw new IllegalStateException("운영 시작월 설정을 확인해 주세요.");
        return YearMonth.parse(DatabaseMonthCodec.toApi(value));
    }
    public BigDecimal standard(String code,String month) {
        var row=shared.parameter(code,month);return row==null||row.value()==null?null:new BigDecimal(row.value());
    }
    public boolean closed(String month) {return shared.monthCloses(month,month).stream().anyMatch(r->"CLOSE".equals(r.status()));}
    public boolean fixed(String project,String month) {return mapper.fixed(project,month);}
    public String latest(String project) {return mapper.latest(project);}
    public int targetCount(String project,String month) {return mapper.targetCount(project,month);}
    public int greatestNumber(String month) {return mapper.greatestNumber(month);}
    public int greatestOrder(String project) {return mapper.greatestOrder(project);}
    public void lockClose() {mapper.lockClose();}
    public void lockTimeout() {mapper.lockTimeout();}
    public void insert(NewBill value) {one(mapper.insert(value));}
    public void cancel(Cancellation value) {one(mapper.cancel(value));}
    private static void one(int count) {if(count!=1)throw new IllegalStateException("청구를 저장하지 못했습니다.");}
    private static ApiException missing() {return new ApiException(404,"NOT_FOUND","청구 또는 참조 대상을 찾을 수 없습니다.");}
}
