package com.sicost.receipts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.receipts.ReceiptDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes=ReceiptController.class)
@Import({ReceiptService.class,ReceiptRepository.class,WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ReceiptContractTest {
    static final String ID="B-202501-001";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired ReceiptService service;
    @MockBean ReceiptMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    StoredBill bill;
    List<StoredReceipt> rows;
    Cache cache;
    List<ChangeHistoryEntry> audits;
    @Test void literalReceiptsHappyAuthBusinessValidationAndNoSideEffect() throws Exception {
        mvc.perform(get("/api/bills/B-202501-001/receipts").param("year","2026"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/receipts",200))
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/bills/B-202501-001/receipts").param("year","2026").param("userId","other"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/receipts",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        bill=null;
        mvc.perform(get("/api/bills/B-202501-001/receipts").param("year","2026"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/receipts",404));noWrites();
    }
    @Test void literalReceiptCreateCommitsIndependentRowAndRejectsOverReceipt() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/B-202501-001/receipts").contentType("application/json").content(create().toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/receipts",201))
            .andExpect(jsonPath("$.data.receipt.amount").value("400"))
            .andExpect(jsonPath("$.data.bill.unpaidAmount").value("700"));
        verify(mapper).insert(anyMap());verify(mapper).updateCache(any());verify(manager).commit(any());
        assertThat(audits).anyMatch(a->a.tableName().equals("TB_BL_RCPT")&&a.type().equals("NEW")&&a.userId().equals("admin"));
        clearInvocations(mapper,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/B-202501-001/receipts").contentType("application/json").content(create().put("amount","701").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/receipts",409))
            .andExpect(jsonPath("$.code").value("OVER_RECEIPT"));noWrites();assertThat(rows).hasSize(1);
    }
    @Test void literalReceiptCancelCommitsWithoutDeletingAndRejectsForgedActor() throws Exception {
        add(1,"2026-07-01","400","N");cache=new Cache(new BigDecimal("400"),new BigDecimal("700"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/B-202501-001/receipts/1/cancel").contentType("application/json").content(cancel().toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/receipts/{receiptId}/cancel",200))
            .andExpect(jsonPath("$.data.receipt.cancelUserId").value("admin"))
            .andExpect(jsonPath("$.data.bill.unpaidAmount").value("1100"));
        verify(mapper).cancel(any());verify(manager).commit(any());assertThat(rows).hasSize(1);
        clearInvocations(mapper,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/B-202501-001/receipts/1/cancel").contentType("application/json").content(cancel().put("cancelUserId","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/receipts/{receiptId}/cancel",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @BeforeEach void setup(){
        bill=bill("N",LocalDate.of(2026,7,1));rows=new ArrayList<>();audits=new ArrayList<>();cache=new Cache(BigDecimal.ZERO,new BigDecimal("1100"));
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.bill(anyString())).thenAnswer(c->bill);
        when(mapper.snapshot(anyString())).thenAnswer(c->snapshot());
        when(mapper.cache(anyString())).thenAnswer(c->cache);
        when(mapper.receipts(anyString())).thenAnswer(c->rows.stream().sorted(Comparator.comparing(StoredReceipt::date).thenComparingLong(StoredReceipt::id).reversed()).toList());
        when(mapper.insert(anyMap())).thenAnswer(c->{Map<String,Object> w=c.getArgument(0);long id=rows.stream().mapToLong(StoredReceipt::id).max().orElse(0)+1;w.put("id",id);rows.add(new StoredReceipt(id,(String)w.get("billId"),(LocalDate)w.get("date"),(BigDecimal)w.get("amount"),"N",null,null,null));return 1;});
        when(mapper.cancel(any())).thenAnswer(c->{Cancellation w=c.getArgument(0);StoredReceipt old=rows.stream().filter(r->r.id()==w.receiptId()).findFirst().orElseThrow();rows.remove(old);rows.add(new StoredReceipt(old.id(),old.billId(),old.date(),old.amount(),"Y",w.reason(),w.actor(),w.now()));return 1;});
        when(mapper.updateCache(any())).thenAnswer(c->{CacheWrite w=c.getArgument(0);cache=new Cache(w.receiptAmount(),w.unpaidAmount());return 1;});
        doAnswer(c->{audits.add(c.getArgument(0));return 1;}).when(history).insert(any());
    }
    StoredBill bill(String cancelled,LocalDate due){return new StoredBill(ID,"P-EXISTING","종료 프로젝트","고객","TK",1,"202501",LocalDate.of(2025,1,1),new BigDecimal("1000"),new BigDecimal("10.00"),new BigDecimal("100"),new BigDecimal("1100"),due,null,cancelled,null,null,null);}
    String snapshot(){return bill==null?null:bill.toString()+rows.stream().sorted(Comparator.comparingLong(StoredReceipt::id)).toList()+cache;}
    String revision(){return revisions.revision(snapshot());}
    ObjectNode create(){return json.createObjectNode().put("year",2026).put("expectedRevision",revision()).put("date","2026-07-01").put("amount","400");}
    ObjectNode cancel(){return json.createObjectNode().put("year",2026).put("expectedRevision",revision()).put("reason","오입금");}
    ResultActions getRows()throws Exception{return mvc.perform(get("/api/bills/"+ID+"/receipts").param("year","2026"));}
    ResultActions post(ObjectNode body)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/"+ID+"/receipts").contentType("application/json").content(body.toString()));}
    ResultActions cancel(long id,ObjectNode body)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bills/"+ID+"/receipts/"+id+"/cancel").contentType("application/json").content(body.toString()));}
    void add(long id,String date,String amount,String state){rows.add(new StoredReceipt(id,ID,LocalDate.parse(date),new BigDecimal(amount),state,"Y".equals(state)?"취소 사유":null,null,null));}
    void noWrites(){verify(mapper,never()).insert(anyMap());verify(mapper,never()).cancel(any());verify(mapper,never()).updateCache(any());verifyNoInteractions(history,invalidation);}
    void invalid(ResultActions r,String field)throws Exception{r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isString());}
    @Test void emptyPublicReadReturnsIntegerCountsAndNoWrites()throws Exception{getRows().andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.normalCount").value(0));verify(users).currentUser();noWrites();}
    @Test void realRowsIncludeCancelledAndUseDateThenNumericIdDescending()throws Exception{
        add(9,"2026-06-30","999999999999999","Y");add(10,"2026-06-30","1","N");add(1,"2026-07-01","2","N");
        getRows().andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.normalCount").value(2)).andExpect(jsonPath("$.data.items[0].id").value(1)).andExpect(jsonPath("$.data.items[1].id").value(10)).andExpect(jsonPath("$.data.items[2].amount").value("999999999999999")).andExpect(jsonPath("$.data.items[2].cancelled").value(true));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"userId","month","size","actor"})
    void unknownQueryIs400NotAuth(String key)throws Exception{mvc.perform(get("/api/bills/"+ID+"/receipts").param("year","2026").param(key,"30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(key));noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-07","2026.5","2028","2024"})
    void invalidYear(String value)throws Exception{invalid(mvc.perform(get("/api/bills/"+ID+"/receipts").param("year",value)),"year");noWrites();}
    @Test void missingYearIsFieldError()throws Exception{invalid(mvc.perform(get("/api/bills/"+ID+"/receipts")),"year");noWrites();}
    @Test void missingBillIs404()throws Exception{bill=null;getRows().andExpect(status().isNotFound());post(create()).andExpect(status().isNotFound());noWrites();}
    @Test void partialReceiptOnPastClosedBillCreatesIndependentRowAndCachesAndNewAudit()throws Exception{
        when(shared.monthCloses("202501","202501")).thenReturn(List.of(new CloseRow("202501","CLOSE")));
        post(create()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.receipt.amount").value("400")).andExpect(jsonPath("$.data.bill.receiptAmount").value("400")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("700")).andExpect(jsonPath("$.data.bill.totalAmount").value("1100"));
        post(create().put("amount","1")).andExpect(status().isCreated());assertThat(rows).hasSize(2);assertThat(cache.receiptAmount()).isEqualByComparingTo("401");
        assertThat(audits).anySatisfy(a->{assertThat(a.tableName()).isEqualTo("TB_BL_RCPT");assertThat(a.targetKey()).isEqualTo("1");assertThat(a.type()).isEqualTo("NEW");assertThat(a.userId()).isEqualTo("admin");});
        verify(shared,never()).monthCloses("202501","202501");
    }
    @ParameterizedTest @ValueSource(strings={"actor","userId","regUserId","RCPT_ID"})
    void unknownCreateFieldsCannotSpoofActor(String field)throws Exception{post(create().put(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.1","1e3","+1","1000000000000000",""})
    void invalidAmounts(String value)throws Exception{invalid(post(create().put("amount",value)),"amount");noWrites();}
    @Test void normalizedAmount()throws Exception{post(create().put("amount"," 1,100 원 ")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.status").value("PAID"));}
    @Test void numericAmountNotCoerced()throws Exception{invalid(post(create().put("amount",1)),"amount");noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-07-02","2026-02-30","2026-7-1"})
    void invalidDates(String value)throws Exception{invalid(post(create().put("date",value)),"date");noWrites();}
    @Test void overReceiptIsConflictAndNoChange()throws Exception{post(create().put("amount","1101")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OVER_RECEIPT")).andExpect(jsonPath("$.details.billing.billId").value(ID));noWrites();}
    @Test void paidBillRejectsReceipt()throws Exception{add(1,"2026-07-01","1100","N");post(create()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));noWrites();}
    @Test void cancelledBillRejectsReceipt()throws Exception{bill=bill("Y",LocalDate.of(2026,7,1));post(create()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));noWrites();}
    @Test void closedReceiptMonthRejectsReceipt()throws Exception{when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));post(create()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void pastContextRejectsReceipt()throws Exception{post(create().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void pastReceiptDateRejectsEvenCurrentContext()throws Exception{post(create().put("date","2025-12-31")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void nextYearContextCanReceiveToday()throws Exception{post(create().put("year",2027)).andExpect(status().isCreated());}
    @Test void staleCreateIs409BeforeWriting()throws Exception{post(create().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void cancelledReceiptsExcludedAndDueDateTodayIsNotLate(){add(1,"2026-07-01","1000","Y");assertThat(service.billView(bill,2026,revision()).status()).isEqualTo("UNPAID");assertThat(service.billView(bill,2026,revision()).receiptAmount()).isEqualTo("0");}
    @Test void dayAfterDueDateIsLate(){bill=bill("N",LocalDate.of(2026,6,30));assertThat(service.billView(bill,2026,revision()).status()).isEqualTo("LATE");}
    @Test void cancelledBillHasZeroEffectiveTotalsAndPriority(){bill=bill("Y",LocalDate.of(2026,6,30));add(1,"2026-07-01","1100","N");var value=service.billView(bill,2026,revision());assertThat(value.status()).isEqualTo("CNCL");assertThat(value.receiptAmount()).isEqualTo("0");assertThat(value.unpaidAmount()).isEqualTo("0");}
    @Test void cancellationPreservesRowAmountAndStoresServerActorAndUpdatAudit()throws Exception{
        add(1,"2026-07-01","1100","N");cache=new Cache(new BigDecimal("1100"),BigDecimal.ZERO);
        cancel(1,cancel()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.receipt.cancelUserId").value("admin")).andExpect(jsonPath("$.data.bill.status").value("UNPAID")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("1100"));
        assertThat(rows).hasSize(1);assertThat(rows.get(0).amount()).isEqualByComparingTo("1100");assertThat(audits).filteredOn(a->a.tableName().equals("TB_BL_RCPT")).allSatisfy(a->assertThat(a.type()).isEqualTo("UPD"));
        assertThat(audits).anySatisfy(a->{assertThat(a.item()).isEqualTo("RCPT_SUM_AMT");assertThat(a.before()).isEqualTo("1100");assertThat(a.after()).isEqualTo("0");});
    }
    @ParameterizedTest @ValueSource(strings={"CNCL_USER_ID","cancelUserId"})
    void unknownCancellationFields(String field)throws Exception{add(1,"2026-07-01","1","N");cancel(1,cancel().put(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"reason","expectedRevision","year"})
    void cancellationRequiredFields(String field)throws Exception{add(1,"2026-07-01","1","N");var b=cancel();b.remove(field);invalid(cancel(1,b),field);noWrites();}
    @ParameterizedTest @ValueSource(strings={"","   "})
    void blankReason(String reason)throws Exception{add(1,"2026-07-01","1","N");invalid(cancel(1,cancel().put("reason",reason)),"reason");noWrites();}
    @Test void longReasonRejected()throws Exception{add(1,"2026-07-01","1","N");invalid(cancel(1,cancel().put("reason","가".repeat(501))),"reason");noWrites();}
    @Test void otherBillReceipt404()throws Exception{add(1,"2026-07-01","1","N");cancel(2,cancel()).andExpect(status().isNotFound());noWrites();}
    @Test void closedReceiptCannotCancel()throws Exception{add(1,"2026-07-01","1","N");when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));cancel(1,cancel()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void pastReceiptCannotCancel()throws Exception{add(1,"2025-12-31","1","N");cancel(1,cancel()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void repeatCancellationValidRevisionIsNoopEvenAfterClose()throws Exception{add(1,"2025-12-31","1","Y");String before=snapshot();cancel(1,cancel()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(snapshot()).isEqualTo(before);noWrites();}
    @Test void staleRepeatIsNotNoop()throws Exception{add(1,"2026-07-01","1","Y");cancel(1,cancel().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void auditFailureRollsBackReceiptAndHidesSql()throws Exception{doThrow(new IllegalStateException("SQL private")).when(history).insert(any());post(create()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL private"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void cancellationAuditFailureRollsBack()throws Exception{add(1,"2026-07-01","1","N");doThrow(new IllegalStateException("audit")).when(history).insert(any());cancel(1,cancel()).andExpect(status().isInternalServerError());verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void timeoutThenSortedSharedLocksThenLatestSnapshot()throws Exception{post(create()).andExpect(status().isCreated());var order=inOrder(mapper,locks);order.verify(mapper).lockTimeout();order.verify(mapper).bill(ID);order.verify(locks).lock("BILL:"+ID);order.verify(locks).lock("BILL_MONTH:202607");order.verify(locks).lock("PROJECT:P-EXISTING");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(mapper).bill(ID);order.verify(mapper).snapshot(ID);}
}
