package com.sicost.bills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static com.sicost.bills.BillDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillController.class)
@ContextConfiguration(classes=BillController.class)
@Import({BillService.class,BillRepository.class,WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BillContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean BillMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Stored> bills;
    List<ReceiptStored> receipts;
    List<ChangeHistoryEntry> audits;
    Project project;
    @BeforeEach void setup() {
        bills=new ArrayList<>();receipts=new ArrayList<>();audits=new ArrayList<>();project=new Project("P-BILL","청구 시험","시험 고객","MO","ING",LocalDate.of(2027,12,31));
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.project(anyString())).thenAnswer(c->project);
        when(mapper.find(anyString())).thenAnswer(c->bills.stream().filter(b->b.id().equals(c.getArgument(0))).findFirst().orElse(null));
        when(mapper.revisionSnapshot(anyString())).thenAnswer(c->json.writeValueAsString(new Snapshot(mapper.find(c.getArgument(0)),mapper.receipts(c.getArgument(0)))));
        when(mapper.receipts(anyString())).thenAnswer(c->receipts.stream().filter(r->r.billId().equals(c.getArgument(0))).toList());
        when(mapper.projectBills(anyString())).thenAnswer(c->bills.stream().filter(b->b.projectId().equals(c.getArgument(0))).sorted(Comparator.comparing(Stored::billingMonth).reversed().thenComparing(Stored::id,Comparator.reverseOrder())).toList());
        when(mapper.list(any())).thenAnswer(c->List.copyOf(bills));
        when(mapper.greatestNumber(anyString())).thenAnswer(c->bills.stream().filter(b->b.billingMonth().equals(c.getArgument(0))).mapToInt(b->Integer.parseInt(b.id().substring(9))).max().orElse(0));
        when(mapper.greatestOrder(anyString())).thenAnswer(c->bills.stream().mapToInt(Stored::order).max().orElse(0));
        when(mapper.standard(anyString(),anyString())).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(c->{Write w=c.getArgument(0);bills.add(stored(w,null));return 1;});
        when(mapper.update(any())).thenAnswer(c->{Write w=c.getArgument(0);Stored old=bills.stream().filter(b->b.id().equals(w.id())).findFirst().orElseThrow();bills.remove(old);bills.add(stored(w,old));return 1;});
        doAnswer(c->{audits.add(c.getArgument(0));return 1;}).when(history).insert(any());
    }
    Stored stored(Write w,Stored old) {Editable f=w.fields();return new Stored(w.id(),w.projectId(),w.order(),w.month(),f.billingDate(),f.supplyAmount(),f.vatRate(),w.vat(),w.total(),w.receipts(),w.total().subtract(w.receipts()),f.dueDate(),f.taxInvoiceNo(),false,null,null,null,old==null?w.actor():old.regUserId(),old==null?w.now():old.registeredAt(),old==null?null:w.actor(),old==null?null:w.now());}
    ObjectNode draft() {return json.createObjectNode().put("year",2026).put("projectId","P-BILL").put("billingMonth","2026-07").put("supplyAmount","101").put("vatRate","10").put("billingDate","2026-07-15").put("dueDate","2026-08-25");}
    ObjectNode update() {ObjectNode b=draft();b.remove(List.of("projectId","billingMonth"));b.put("expectedRevision",revisions.revision(mapper.revisionSnapshot(bills.get(0).id())));return b;}
    ResultActions create(ObjectNode b) throws Exception {return mvc.perform(post("/api/bills").contentType("application/json").content(b.toString()));}
    ResultActions put(ObjectNode b) throws Exception {return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/bills/"+bills.get(0).id()).contentType("application/json").content(b.toString()));}
    ResultActions detail(String id) throws Exception {return mvc.perform(get("/api/bills/"+id).param("year","2026"));}
    void invalid(ResultActions r,String field) throws Exception {r.andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty());}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verifyNoInteractions(history,invalidation);}
    @Test void publicEmptyListAndMissingDetailAreRealAndReadOnly() throws Exception {
        mvc.perform(get("/api/bills").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.totals.totalAmount").value("0"));
        detail("B-202607-001").andExpect(status().isNotFound());noWrites();
    }
    @Test void publicCreatePreservesExplicitSupplyAndCalculatesDownVatAndAudit() throws Exception {
        create(draft()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.id").value("B-202607-001")).andExpect(jsonPath("$.data.bill.order").value(1)).andExpect(jsonPath("$.data.bill.supplyAmount").value("101"))
            .andExpect(jsonPath("$.data.bill.vatAmount").value("10")).andExpect(jsonPath("$.data.bill.totalAmount").value("111")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("111")).andExpect(jsonPath("$.data.warnings[0]").value("번호 미입력"));
        assertThat(audits).isNotEmpty().allSatisfy(a->{assertThat(a.tableName()).isEqualTo("TB_BL_BILL");assertThat(a.targetKey()).isEqualTo("B-202607-001");assertThat(a.type()).isEqualTo("NEW");assertThat(a.userId()).isEqualTo("admin");});
        var order=inOrder(mapper,locks);order.verify(mapper).lockTimeout();order.verify(locks).lock("BILL_MONTH:202607");order.verify(locks).lock("PROJECT:P-BILL");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(mapper).lockClose();
    }
    @Test void detailIncludesCancelledReceiptsAndReadsDoNotRepairStoredCaches() throws Exception {
        create(draft()).andExpect(status().isCreated());Stored row=bills.get(0);
        receipts.add(new ReceiptStored(1,row.id(),LocalDate.of(2026,7,15),new BigDecimal("11"),false,null,null,null,"admin",row.registeredAt(),null,null));
        receipts.add(new ReceiptStored(2,row.id(),LocalDate.of(2026,7,16),new BigDecimal("80"),true,"오입금","admin",row.registeredAt(),"admin",row.registeredAt(),null,null));
        clearInvocations(mapper,history,invalidation);
        detail(row.id()).andExpect(status().isOk()).andExpect(jsonPath("$.data.receipts.total").value(2)).andExpect(jsonPath("$.data.receipts.normalCount").value(1)).andExpect(jsonPath("$.data.receipts.items[0].id").value(2))
            .andExpect(jsonPath("$.data.bill.receiptAmount").value("11")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("100"));
        mvc.perform(get("/api/bills").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totals.receiptAmount").value("11"));
        assertThat(bills.get(0)).isEqualTo(row);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"userId","force","size"}) void detailRejectsUnknownQuery(String key) throws Exception {invalid(mvc.perform(get("/api/bills/B-202607-001").param("year","2026").param(key,"other")),key);noWrites();}
    @Test void listRejectsUnknownAndInvalidFilters() throws Exception {
        invalid(mvc.perform(get("/api/bills").param("year","2026").param("userId","other")),"userId");
        invalid(mvc.perform(get("/api/bills").param("year","2026").param("size","30")),"size");
        invalid(mvc.perform(get("/api/bills").param("year","2026").param("month","2026-13")),"month");
        invalid(mvc.perform(get("/api/bills").param("year","2026").param("sort","quickActions")),"sort");noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"id","regUserId","vatAmount","totalAmount","status"}) void createRejectsInjectedFields(String key) throws Exception {invalid(create(draft().put(key,"other")),key);noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.1","1e3","+1","1000000000000000"}) void rejectsBadMoney(String v) throws Exception {invalid(create(draft().put("supplyAmount",v)),"supplyAmount");noWrites();}
    @ParameterizedTest @ValueSource(strings={"10.001","-1","101","1e1"}) void rejectsBadRate(String v) throws Exception {invalid(create(draft().put("vatRate",v)),"vatRate");noWrites();}
    @Test void rejectsOverflowDateAndNumberLength() throws Exception {invalid(create(draft().put("dueDate","2026-07-14")),"dueDate");invalid(create(draft().put("billingDate","2026-02-30")),"billingDate");invalid(create(draft().put("taxInvoiceNo","가".repeat(31))),"taxInvoiceNo");invalid(create(draft().put("supplyAmount","999999999999999")),"supplyAmount");noWrites();}
    @Test void normalizedMoneyAndBlankInvoiceAreAccepted() throws Exception {create(draft().put("supplyAmount"," 1,001 원 ").put("vatRate"," 0 % ").put("taxInvoiceNo","  ")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.supplyAmount").value("1001")).andExpect(jsonPath("$.data.bill.taxInvoiceWarning").value("번호 미입력"));}
    @Test void duplicateFirstRequestAndChangedDraftNeverConsumeNumber() throws Exception {
        create(draft()).andExpect(status().isCreated());int n=audits.size();
        var response=create(draft()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_BILL_CONFIRMATION_REQUIRED")).andExpect(jsonPath("$.details.billing.existingBills[0].id").value(bills.get(0).id())).andReturn();
        String token=json.readTree(response.getResponse().getContentAsString()).at("/details/billing/duplicateToken").asText();
        create(draft().put("confirmDuplicate",true).put("duplicateToken",token).put("supplyAmount","102")).andExpect(status().isConflict());assertThat(bills).hasSize(1);assertThat(audits).hasSize(n);
        create(draft().put("confirmDuplicate",true).put("duplicateToken",token)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.id").value("B-202607-002"));
    }
    @Test void changedReceiptInvalidatesDuplicateConfirmation() throws Exception {
        create(draft()).andExpect(status().isCreated());var r=create(draft()).andReturn();String token=json.readTree(r.getResponse().getContentAsString()).at("/details/billing/duplicateToken").asText();Stored row=bills.get(0);
        receipts.add(new ReceiptStored(1,row.id(),row.billingDate(),BigDecimal.ONE,true,"취소","admin",row.registeredAt(),"admin",row.registeredAt(),null,null));
        create(draft().put("confirmDuplicate",true).put("duplicateToken",token)).andExpect(status().isConflict());assertThat(bills).hasSize(1);
    }
    @Test void exhaustedMonthNumberIs409WithoutWrite() throws Exception {when(mapper.greatestNumber("202607")).thenReturn(999);create(draft()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_NUMBER_EXHAUSTED"));noWrites();}
    @Test void exhaustedProjectOrderIs409WithoutWrite() throws Exception {when(mapper.greatestOrder("P-BILL")).thenReturn(999);create(draft()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_ORDER_EXHAUSTED"));noWrites();}
    @Test void invoiceOnlyUpdateDoesNotEstimateAndNoopPreservesAuditTimes() throws Exception {
        create(draft()).andExpect(status().isCreated());put(update().put("taxInvoiceNo","  INV-1  ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.bill.taxInvoiceNo").value("INV-1"));
        assertThat(audits.stream().filter(a->"UPD".equals(a.type())).toList()).extracting(ChangeHistoryEntry::item).containsExactly("TAX_INVC_NO");
        int count=audits.size();Stored before=bills.get(0);put(update().put("taxInvoiceNo","INV-1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(bills.get(0)).isEqualTo(before);assertThat(audits).hasSize(count);
    }
    @ParameterizedTest @ValueSource(strings={"id","projectId","billingMonth","order","vatAmount","modUserId"}) void updateRejectsIdentityAndAuditInjection(String key) throws Exception {create(draft()).andExpect(status().isCreated());clearInvocations(mapper,history,invalidation);invalid(put(update().put(key,"injected")),key);noWrites();}
    @Test void belowNormalReceiptsIs409AndEqualBecomesPaid() throws Exception {
        create(draft()).andExpect(status().isCreated());Stored row=bills.get(0);receipts.add(new ReceiptStored(1,row.id(),row.billingDate(),new BigDecimal("100"),false,null,null,null,"admin",row.registeredAt(),null,null));int count=audits.size();
        put(update().put("supplyAmount","90")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_TOTAL_BELOW_RECEIPTS"));assertThat(bills.get(0)).isEqualTo(row);assertThat(audits).hasSize(count);
        put(update().put("supplyAmount","100").put("vatRate","0")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.status").value("PAID"));
    }
    @Test void receiptChangeInvalidatesEvenNoop() throws Exception {create(draft()).andExpect(status().isCreated());var b=update();Stored row=bills.get(0);receipts.add(new ReceiptStored(1,row.id(),row.billingDate(),BigDecimal.ONE,false,null,null,null,"admin",row.registeredAt(),null,null));put(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));}
    @Test void closedBillAndPastContextBlockWrites() throws Exception {create(draft()).andExpect(status().isCreated());when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));put(update()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));create(draft()).andExpect(status().isConflict());put(update().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));}
    @Test void turnkeyOldRecognitionMonthCannotMoveOutOfClose() throws Exception {project=new Project(project.id(),project.name(),project.customerName(),"TK","ING",project.endDate());create(draft().put("billingDate","2026-06-30")).andExpect(status().isCreated());when(shared.monthCloses("202606","202606")).thenReturn(List.of(new CloseRow("202606","CLOSE")));put(update()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));assertThat(bills.get(0).billingDate()).isEqualTo(LocalDate.of(2026,6,30));}
    @Test void endedExistingBillIsReadableAndEditableButNotNewOption() throws Exception {create(draft()).andExpect(status().isCreated());project=new Project(project.id(),project.name(),project.customerName(),"MO","END",project.endDate());detail(bills.get(0).id()).andExpect(status().isOk());put(update().put("taxInvoiceNo","END-1")).andExpect(status().isOk());create(draft().put("billingMonth","2026-08")).andExpect(status().isConflict());}
    @Test void nextYearContextAndCrossYearDatesAreNotArtificiallyDisabled() throws Exception {create(draft().put("year",2027).put("billingMonth","2027-02")).andExpect(status().isCreated());}
    @Test void auditFailureRollsBackAndDoesNotInvalidateOrLeakSql() throws Exception {doThrow(new IllegalStateException("SQL private detail")).when(history).insert(any());create(draft()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL private"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void boundaryOnlyAllowsBillingWritesAndExactTimeout() {PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '10s'");PersistenceBoundary.requireAllowed("UPDATE tb_bl_bill SET tax_invc_no=? WHERE bill_id=?");assertThatThrownBy(()->PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '1s'")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->PersistenceBoundary.requireAllowed("DELETE FROM tb_bl_bill WHERE bill_id=?")).isInstanceOf(IllegalStateException.class);}
    @Test void invalidCheckStandardsNeverGuessAToleranceOrWrite() throws Exception {
        var b=draft();b.putObject("checkContext").put("projectId","P-BILL").put("month","2026-07").put("checkRevision","preview");
        create(b).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("청구 누락 허용오차 설정을 확인해 주세요."));noWrites();
    }
    @Test void staleCheckContextRejectsWithoutDuplicateOrWrites() throws Exception {
        when(mapper.standard("BILL_MISS_MIN","202607")).thenReturn(BigDecimal.ZERO);when(mapper.standard("BILL_MISS_RT","202607")).thenReturn(BigDecimal.ONE);
        var b=draft();b.putObject("checkContext").put("projectId","P-BILL").put("month","2026-07").put("checkRevision","stale");
        create(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_CHECK_CHANGED"));noWrites();
    }
    @Test void copyIsNewAndRequiresLatestSourceRevisionAndNextMonth() throws Exception {
        create(draft()).andExpect(status().isCreated());Stored source=bills.get(0);var b=draft().put("billingMonth","2026-08");
        b.putObject("copyContext").put("sourceBillId",source.id()).put("expectedRevision","stale");
        create(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));assertThat(bills).hasSize(1);
        ((ObjectNode)b.get("copyContext")).put("expectedRevision",revisions.revision(mapper.revisionSnapshot(source.id())));
        create(b).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.order").value(2));assertThat(bills.get(0)).isEqualTo(source);
    }
    @Test void cancelledBillKeepsOriginalAmountsAndZeroEffectiveBalance() throws Exception {
        create(draft()).andExpect(status().isCreated());Stored s=bills.remove(0);bills.add(new Stored(s.id(),s.projectId(),s.order(),s.billingMonth(),s.billingDate(),s.supplyAmount(),s.vatRate(),s.vatAmount(),s.totalAmount(),s.receiptAmount(),s.unpaidAmount(),s.dueDate(),s.taxInvoiceNo(),true,"취소 사유","admin",s.registeredAt(),s.regUserId(),s.registeredAt(),s.modUserId(),s.modifiedAt()));
        detail(s.id()).andExpect(jsonPath("$.data.bill.status").value("CNCL")).andExpect(jsonPath("$.data.bill.totalAmount").value("111")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("0"));
        put(update()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));
    }
}
