package com.sicost.bills;

import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.bills.BillDtos.*;

@Repository
public class BillRepository {
    private final BillMapper mapper;
    private final SharedQueriesMapper shared;
    public BillRepository(BillMapper mapper,SharedQueriesMapper shared) {this.mapper=mapper;this.shared=shared;}
    public YearMonth cashBase() {String value=shared.cashBaseMonth();if(value==null)throw new IllegalStateException("운영 시작월 설정을 확인해 주세요.");return YearMonth.parse(DatabaseMonthCodec.toApi(value));}
    public boolean closed(String month) {return shared.monthCloses(month,month).stream().anyMatch(m->"CLOSE".equals(m.status()));}
    public void lockTimeout() {mapper.lockTimeout();}
    public void lockClose() {mapper.lockClose();}
    public List<Stored> list(Filter filter) {return mapper.list(filter);}
    public Stored required(String id) {Stored row=mapper.find(id);if(row==null)throw new ApiException(404,"NOT_FOUND","청구를 찾을 수 없습니다.");return row;}
    public Project project(String id) {Project row=mapper.project(id);if(row==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return row;}
    public List<Stored> projectBills(String projectId) {return mapper.projectBills(projectId);}
    public Snapshot snapshot(String id) {return new Snapshot(required(id),mapper.receipts(id));}
    public String revisionSnapshot(String id) {required(id);return mapper.revisionSnapshot(id);}
    public List<Snapshot> monthBills(String projectId,String month) {return projectBills(projectId).stream().filter(b->b.billingMonth().equals(db(month))).sorted(java.util.Comparator.comparing(Stored::id)).map(b->snapshot(b.id())).toList();}
    public String checkSnapshot(String projectId,String month) {return mapper.sourceSnapshot(projectId,db(month));}
    public int nextNumber(String month) {int n=mapper.greatestNumber(db(month));if(n>=999)throw new ApiException(409,"BILL_NUMBER_EXHAUSTED","해당 청구월의 청구번호를 모두 사용했습니다.");return n+1;}
    public int nextOrder(String projectId) {int n=mapper.greatestOrder(projectId);if(n>=999)throw new ApiException(409,"BILL_ORDER_EXHAUSTED","프로젝트의 청구 차수를 모두 사용했습니다.");return n+1;}
    public BigDecimal standard(String code,String month) {return mapper.standard(code,db(month));}
    public void insert(Write write) {one(mapper.insert(write));}
    public void update(Write write) {one(mapper.update(write));}
    private void one(int count) {if(count!=1)throw new IllegalStateException("청구를 저장하지 못했습니다.");}
    public static String db(String month) {return DatabaseMonthCodec.toDatabase(month);}
    public static String api(String month) {return DatabaseMonthCodec.toApi(month);}
}
