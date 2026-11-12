package com.sicost.receiptsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.BillingReviewRepository;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceiptBatchController.class)
@ContextConfiguration(classes=ReceiptBatchController.class)
@Import({ReceiptBatchService.class,ReceiptBatchRepository.class,WriteCoordinator.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ReceiptBatchContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ReceiptBatchMapper mapper;
    @MockBean BillingReviewRepository billing;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Map<String,BillState> bills;
    List<ReceiptWrite> receipts;
    List<ChangeHistoryEntry> audits;
    @BeforeEach void setup() {
        bills=new LinkedHashMap<>();receipts=new ArrayList<>();audits=new ArrayList<>();
        bills.put("BATCH-A",new BillState("BATCH-A","PROJECT-A","202512",new BigDecimal("1100"),BigDecimal.ZERO,"N"));
        bills.put("BATCH-B",new BillState("BATCH-B","PROJECT-B","202607",new BigDecimal("2200"),BigDecimal.ZERO,"N"));
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("admin");when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.state(anyString())).thenAnswer(call->bills.get(call.getArgument(0)));
        when(billing.billRevision(anyString())).thenAnswer(call->"revision:"+call.getArgument(0)+":"+receipts.size());
        when(mapper.bill(anyString())).thenAnswer(call->{
            BillState b=bills.get(call.getArgument(0));Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",b.id());row.put("projectId",b.projectId());row.put("projectName","시험 프로젝트");row.put("customerName","시험 거래처");
            row.put("contractType","TK");row.put("order",1);row.put("billingMonth",b.month().substring(0,4)+"-"+b.month().substring(4));
            row.put("billingDate","2026-01-01");row.put("dueDate","2026-07-14");row.put("supplyAmount","1000");row.put("vatRate","10");row.put("vatAmount","100");
            row.put("totalAmount",b.total().toPlainString());row.put("receiptAmount",b.receiptAmount().toPlainString());row.put("unpaidAmount",b.unpaid().toPlainString());
            row.put("cancelled",false);row.put("projectStatus","END");row.put("contractEnd","2025-12-31");row.put("latest",true);return row;
        });
        when(mapper.insert(any())).thenAnswer(call->{ReceiptWrite w=call.getArgument(0);w.setId((long)receipts.size()+1);receipts.add(w);
            BillState b=bills.get(w.getBillId());bills.put(b.id(),new BillState(b.id(),b.projectId(),b.month(),b.total(),b.receiptAmount().add(w.getAmount()),b.cancelled()));return 1;});
        when(mapper.cache(any())).thenReturn(1);when(history.insert(any())).thenAnswer(call->{audits.add(call.getArgument(0));return 1;});
    }
    ObjectNode body() {
        ObjectNode b=json.createObjectNode().put("year",2026).put("date","2026-07-15");
        var rows=b.putArray("items");rows.addObject().put("billId","BATCH-A").put("amount","500").put("expectedRevision","revision:BATCH-A:0");
        rows.addObject().put("billId","BATCH-B").put("amount","2200").put("expectedRevision","revision:BATCH-B:0");return b;
    }
    ObjectNode row(ObjectNode b,int index) {return (ObjectNode)b.withArray("items").get(index);}
    ResultActions postBody(ObjectNode b) throws Exception {return mvc.perform(post("/api/receipts/batch").contentType("application/json").content(b.toString()));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).cache(any());verifyNoInteractions(history,invalidation);}
    @Test void publicBatchCommitsIndependentRowsAndExactSumWithCarryoverAndEndedProjects() throws Exception {
        postBody(body()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.completedCount").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value("2700")).andExpect(jsonPath("$.data.items[0].bill.unpaidAmount").value("600"))
            .andExpect(jsonPath("$.data.items[0].bill.carryover").value(true)).andExpect(jsonPath("$.data.items[1].bill.status").value("PAID"))
            .andExpect(jsonPath("$.data.items[0].receipt.cancelReason").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].bill.dunning").value(org.hamcrest.Matchers.nullValue())).andExpect(header().exists("X-Request-Id"));
        assertThat(receipts).hasSize(2);assertThat(audits).hasSize(8).allSatisfy(a->{assertThat(a.tableName()).isEqualTo("TB_BL_RCPT");assertThat(a.type()).isEqualTo("NEW");assertThat(a.before()).isNull();assertThat(a.userId()).isEqualTo("admin");});
        assertThat(audits).extracting(ChangeHistoryEntry::targetKey).containsOnly("1","2");
        verify(manager).commit(any());verify(invalidation).afterCommit("BILL:BATCH-A");
    }
    @Test void forgedNestedAuditFieldIsUnknownAndDoesNotReachWrites() throws Exception {
        var b=body();row(b,1).put("regUserId","forged");postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("items[1].regUserId"));noWrites();
    }
    @Test void emptyItemsIsFieldValidation() throws Exception {var b=body();b.putArray("items");postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items"));noWrites();}
    @Test void duplicateBillIdsRejectedAfterTrim() throws Exception {var b=body();row(b,1).put("billId"," BATCH-A ");postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[1].billId"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","1e3","+1","abc","1000000000000000",""})
    void invalidRowAmountPreventsWholeBatch(String amount) throws Exception {var b=body();row(b,1).put("amount",amount);postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[1].amount"));noWrites();}
    @Test void formattedWonIsNormalizedWithoutRounding() throws Exception {var b=body();row(b,0).put("amount"," 5,00\u00a0원 ");postBody(b).andExpect(status().isCreated()).andExpect(jsonPath("$.data.items[0].receipt.amount").value("500"));}
    @Test void numericAmountIsNotCoerced() throws Exception {var b=body();row(b,0).put("amount",500);postBody(b).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-2-03","0000-01-01"})
    void malformedDateRejected(String date) throws Exception {postBody(body().put("date",date)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("date"));noWrites();}
    @Test void futureDateIsRejected() throws Exception {postBody(body().put("date","2026-07-16")).andExpect(status().isBadRequest());noWrites();}
    @Test void pastContextIsReadOnly() throws Exception {postBody(body().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void pastReceiptDateIsReadOnlyEvenInCurrentContext() throws Exception {postBody(body().put("date","2025-12-31")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void nextYearContextAllowsCurrentReceiptDate() throws Exception {postBody(body().put("year",2027)).andExpect(status().isCreated());}
    @Test void closedReceiptMonthRejectsEntireBatch() throws Exception {when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));postBody(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void closedBillMonthDoesNotBlockOpenReceiptMonth() throws Exception {when(shared.monthCloses("202512","202512")).thenReturn(List.of(new CloseRow("202512","CLOSE")));postBody(body()).andExpect(status().isCreated());}
    @Test void migrationProtectedReceiptMonthRejects() throws Exception {when(shared.cashBaseMonth()).thenReturn("202608");postBody(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void secondRevisionChangedUnderLocksRejectsBeforeFirstInsert() throws Exception {
        doAnswer(call->{when(billing.billRevision("BATCH-B")).thenReturn("changed");return null;}).when(locks).lock("BILL:BATCH-B");
        postBody(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE")).andExpect(jsonPath("$.details.billing.billId").value("BATCH-B"));noWrites();verify(manager).rollback(any());
    }
    @Test void secondBalanceOverpaymentRejectsAll() throws Exception {var b=bills.get("BATCH-B");bills.put(b.id(),new BillState(b.id(),b.projectId(),b.month(),b.total(),BigDecimal.ONE,"N"));postBody(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OVER_RECEIPT"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"CANCELLED","PAID"})
    void secondInvalidStatePreventsFirstInsert(String state) throws Exception {var b=bills.get("BATCH-B");bills.put(b.id(),new BillState(b.id(),b.projectId(),b.month(),b.total(),state.equals("PAID")?b.total():BigDecimal.ZERO,state.equals("CANCELLED")?"Y":"N"));postBody(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));noWrites();}
    @Test void missingBillReturns404() throws Exception {bills.remove("BATCH-B");postBody(body()).andExpect(status().isNotFound());noWrites();}
    @Test void secondReceiptAuditFailureRollsBackWholeTransactionAndHidesSql() throws Exception {
        when(history.insert(any())).thenAnswer(call->{ChangeHistoryEntry e=call.getArgument(0);if(e.targetKey().equals("2"))throw new IllegalStateException("SQL secret stack");audits.add(e);return 1;});
        postBody(body()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL secret"))));verify(manager).rollback(any());verify(manager,never()).commit(any());verifyNoInteractions(invalidation);
    }
    @Test void fixedTimeoutThenSortedLocksThenCloseCheckPrecedeWrites() throws Exception {
        postBody(body()).andExpect(status().isCreated());var order=inOrder(mapper,locks,shared);
        order.verify(mapper).lockTimeout();order.verify(locks).lock("BILL:BATCH-A");order.verify(locks).lock("BILL:BATCH-B");order.verify(locks).lock("BILL_MONTH:202607");order.verify(locks).lock("PROJECT:PROJECT-A");order.verify(locks).lock("PROJECT:PROJECT-B");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(mapper).lockClose();order.verify(shared).cashBaseMonth();
    }
    @Test void invalidJsonAndQueryKeepEnvelope() throws Exception {
        mvc.perform(post("/api/receipts/batch").contentType("application/json").content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));
        mvc.perform(post("/api/receipts/batch").param("force","true").contentType("application/json").content(body().toString())).andExpect(status().isBadRequest());noWrites();
    }
    @Test void persistenceAllowsOnlyFixedTimeoutAndNeverDeletesBillingRows() {
        PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '10s'");PersistenceBoundary.requireAllowed("UPDATE tb_bl_bill SET unpaid_amt=? WHERE bill_id=?");
        assertThatThrownBy(()->PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '0'" )).isInstanceOf(IllegalStateException.class);
        for(String table:List.of("tb_bl_bill","tb_bl_rcpt"))assertThatThrownBy(()->PersistenceBoundary.requireAllowed("DELETE FROM "+table+" WHERE bill_id=?")).isInstanceOf(IllegalStateException.class);
    }
}
