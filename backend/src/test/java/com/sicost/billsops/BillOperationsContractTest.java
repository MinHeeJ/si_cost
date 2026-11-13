package com.sicost.billsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.*;
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
import static com.sicost.billsops.BillOperationsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillOperationsController.class)
@ContextConfiguration(classes=BillOperationsController.class)
@Import({BillOperationsService.class,BillEstimateService.class,BillOperationsRepository.class,AssignmentMonthlyService.class,
    WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BillOperationsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BillOperationsService service;
    @Autowired RevisionService revisions;
    @MockBean BillOperationsMapper mapper;
    @MockBean AssignmentMonthlyMapper monthly;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Map<String,StoredBill> bills;
    Map<String,List<Receipt>> receipts;
    Project project;
    List<ChangeHistoryEntry> audits;
    static final String ID="B-202607-001";
    @Test void literalCopyDraftHappyAuthValidationAndBusinessAreReadOnly() throws Exception {
        mvc.perform(get("/api/bills/B-202607-001/copy-draft").param("year","2026"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/copy-draft",200))
            .andExpect(jsonPath("$.data.billingMonth").value("2026-08"))
            .andExpect(jsonPath("$.data.supplyAmount").value("1001"));
        mvc.perform(get("/api/bills/B-202607-001/copy-draft").param("year","2026").param("userId","other"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/copy-draft",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        bills.clear();
        mvc.perform(get("/api/bills/B-202607-001/copy-draft").param("year","2026"))
            .andExpect(DurableOpenApi.response("GET","/api/bills/{id}/copy-draft",404));noWrites();
    }
    @Test void literalCancellationCommitsServerActorAndRejectsStaleRevision() throws Exception {
        var body=command(true);
        mvc.perform(post("/api/bills/B-202607-001/cancel").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/cancel",200))
            .andExpect(jsonPath("$.data.bill.cancelled").value(true))
            .andExpect(jsonPath("$.data.bill.unpaidAmount").value("0"));
        verify(mapper).cancel(argThat(w->w.actor().equals("admin")));verify(manager).commit(any());
        assertThat(audits).isNotEmpty().allMatch(a->a.type().equals("UPD"));clearInvocations(mapper,history,invalidation);
        mvc.perform(post("/api/bills/B-202607-001/cancel").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/cancel",409))
            .andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void literalNextMonthCommitsOneBillAndRejectsSourceRevisionReuse() throws Exception {
        var body=command(false);var source=bills.get(ID);
        mvc.perform(post("/api/bills/B-202607-001/next-month").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/next-month",201))
            .andExpect(jsonPath("$.data.bill.id").value("B-202608-001"))
            .andExpect(jsonPath("$.data.bill.supplyAmount").value("1001"));
        verify(mapper).insert(argThat(w->w.actor().equals("admin")));verify(manager).commit(any());
        assertThat(bills.get(ID)).isEqualTo(source);clearInvocations(mapper,history,invalidation);
        mvc.perform(post("/api/bills/B-202607-001/next-month").contentType("application/json").content(body.put("expectedRevision","stale").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/bills/{id}/next-month",409));noWrites();
    }
    static BigDecimal n(String s) {return new BigDecimal(s);}
    @BeforeEach void setup() {
        bills=new LinkedHashMap<>();receipts=new HashMap<>();audits=new ArrayList<>();
        project=new Project("P","시험 프로젝트","시험 고객사","MO","ING",LocalDate.of(2027,12,31));
        bills.put(ID,bill(ID,"202607","N",LocalDate.of(2026,8,25)));
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-14T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.project(anyString())).thenAnswer(c->project);
        when(mapper.bill(anyString())).thenAnswer(c->bills.get(c.getArgument(0)));
        when(mapper.snapshot(anyString())).thenAnswer(c->json.writeValueAsString(new Snapshot(mapper.bill(c.getArgument(0)),mapper.receipts(c.getArgument(0)))));
        when(mapper.bills()).thenAnswer(c->new ArrayList<>(bills.values()));
        when(mapper.receipts(anyString())).thenAnswer(c->receipts.getOrDefault(c.getArgument(0),Collections.emptyList()));
        when(mapper.latest(anyString())).thenAnswer(c->bills.values().stream().filter(b->b.cancelled().equals("N")).sorted(Comparator.comparing(StoredBill::month).thenComparing(StoredBill::id).reversed()).map(StoredBill::id).findFirst().orElse(null));
        when(mapper.targetCount(anyString(),anyString())).thenAnswer(c->(int)bills.values().stream().filter(b->b.month().equals(c.getArgument(1))&&b.cancelled().equals("N")).count());
        when(mapper.greatestOrder(anyString())).thenAnswer(c->bills.values().stream().mapToInt(StoredBill::order).max().orElse(0));
        when(mapper.greatestNumber(anyString())).thenReturn(0);
        when(shared.cashBaseMonth()).thenReturn("202501");when(monthly.cashBase()).thenReturn("202501");
        when(shared.monthCloses(anyString(),anyString())).thenReturn(Collections.emptyList());
        when(shared.parameter(eq("VAT_RT"),anyString())).thenReturn(new Parameter("VAT_RT","10","%","2025-01"));
        when(mapper.assignments(anyString())).thenReturn(List.of("A"));
        when(mapper.developer(anyString())).thenReturn(new Developer("D","시험 개발자","TEST-D"));
        when(monthly.source("A")).thenReturn(source("ACT","PAID"));
        when(monthly.histories("A")).thenReturn(Collections.emptyList());when(monthly.months("A")).thenReturn(Collections.emptyList());
        when(mapper.insert(any())).thenAnswer(c->{NewBill b=c.getArgument(0);bills.put(b.id(),new StoredBill(b.id(),b.projectId(),b.order(),b.month(),b.billingDate(),b.supply(),b.rate(),b.vat(),b.total(),BigDecimal.ZERO,b.total(),b.dueDate(),null,"N",null,null,null,b.actor(),b.now(),null,null));return 1;});
        when(mapper.cancel(any())).thenAnswer(c->{Cancellation v=c.getArgument(0);StoredBill b=bills.get(v.id());bills.put(v.id(),new StoredBill(b.id(),b.projectId(),b.order(),b.month(),b.billingDate(),b.supply(),b.rate(),b.vat(),b.total(),BigDecimal.ZERO,BigDecimal.ZERO,b.dueDate(),b.invoice(),"Y",v.reason(),v.actor(),v.now(),b.registeredBy(),b.registeredAt(),v.actor(),v.now()));return 1;});
        when(history.insert(any())).thenAnswer(c->{audits.add(c.getArgument(0));return 1;});
    }
    AssignmentMonthlyService.Source source(String status,String kind) {return new AssignmentMonthlyService.Source("A","P","D","DEV",LocalDate.of(2026,7,1),LocalDate.of(2027,12,31),n("1.00"),n("1001"),kind,null,null,0,"N",status);}
    StoredBill bill(String id,String month,String cancel,LocalDate due) {return new StoredBill(id,"P",1,month,LocalDate.of(2026,7,1),n("1000"),n("7.25"),n("72"),n("1072"),n("999"),n("73"),due,"INVOICE",cancel,null,null,null,"admin",LocalDateTime.of(2026,7,1,9,0,0,123456000),null,null);}
    Receipt receipt(String cancel,String amount) {return new Receipt(1,ID,LocalDate.of(2026,7,2),n(amount),cancel,null,null,null,"admin",LocalDateTime.of(2026,7,2,9,0),null,null);}
    ObjectNode command(boolean cancel) {var b=json.createObjectNode().put("year",2026).put("expectedRevision",service.revision(ID));if(cancel)b.put("reason"," 취소 사유 ");return b;}
    ResultActions mutate(String action,ObjectNode body) throws Exception {return mvc.perform(post("/api/bills/"+ID+"/"+action).contentType("application/json").content(body.toString()));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).cancel(any());verifyNoInteractions(history,invalidation);}
    @Test void publicEstimateUsesActualMonthlyPreviewAndExactStringsWithoutWrites() throws Exception {
        mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.supplyAmount").value("1001"))
            .andExpect(jsonPath("$.data.vatRate").value("10")).andExpect(jsonPath("$.data.sources[0].protected").value(false))
            .andExpect(jsonPath("$.data.defaultBillingDate").value("2026-07-15")).andExpect(jsonPath("$.data.defaultDueDate").value("2026-09-25"));noWrites();
    }
    @Test void emptyEstimateReturnsZeroNoVirtualSource() throws Exception {when(mapper.assignments("P")).thenReturn(Collections.emptyList());mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08")).andExpect(jsonPath("$.data.supplyAmount").value("0")).andExpect(jsonPath("$.data.sources").isEmpty());noWrites();}
    @Test void tkEstimateIsManual() throws Exception {project=new Project("P","p","c","TK","ING",project.endDate());mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08")).andExpect(jsonPath("$.data.supplyAmount").value(org.hamcrest.Matchers.nullValue()));noWrites();}
    @ParameterizedTest @ValueSource(strings={"-1","100.01","10.001"}) void invalidVatDefaultIsNullNotRounded(String rate) throws Exception {when(shared.parameter(eq("VAT_RT"),anyString())).thenReturn(new Parameter("VAT_RT",rate,"%","2025-01"));mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08")).andExpect(jsonPath("$.data.vatRate").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.warnings").isNotEmpty());noWrites();}
    @Test void freeAssignmentsExcluded() throws Exception {when(monthly.source("A")).thenReturn(source("ACT","FREE"));mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08")).andExpect(jsonPath("$.data.supplyAmount").value("0"));noWrites();}
    @Test void protectedCancelledParentStillUsesStoredPriceAndMm() throws Exception {
        when(monthly.source("A")).thenReturn(source("CNCL","PAID"));when(monthly.months("A")).thenReturn(List.of(new AssignmentMonthlyService.StoredMonth("202608",31,n("0.33"),n("1001"),n("330"),"Y")));
        mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month","2026-08")).andExpect(jsonPath("$.data.supplyAmount").value("330")).andExpect(jsonPath("$.data.sources[0].protected").value(true));noWrites();
    }
    @Test void missingProject404() throws Exception {when(mapper.project("missing")).thenReturn(null);mvc.perform(get("/api/bills/estimate").param("projectId","missing").param("month","2026-08")).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-13","0000-01","202608","2026-8"}) void invalidMonth(String month) throws Exception {mvc.perform(get("/api/bills/estimate").param("projectId","P").param("month",month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));noWrites();}
    @Test void publicSummaryRecomputesNormalReceiptsIgnoresCancelledAndNoFanout() throws Exception {
        receipts.put(ID,List.of(receipt("N","72")));bills.put("B-202512-001",bill("B-202512-001","202512","N",LocalDate.of(2026,7,14)));
        bills.put("B-202607-002",bill("B-202607-002","202607","Y",LocalDate.of(2026,7,1)));
        mvc.perform(get("/api/bills/summary").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.billed.amount").value("1072"))
            .andExpect(jsonPath("$.data.billed.count").value(1)).andExpect(jsonPath("$.data.currentMonth.amount").value("1072"))
            .andExpect(jsonPath("$.data.unpaid.amount").value("2072")).andExpect(jsonPath("$.data.lateCount").value(1));noWrites();
    }
    @Test void emptySummaryIsZero() throws Exception {bills.clear();mvc.perform(get("/api/bills/summary").param("year","2026")).andExpect(jsonPath("$.data.billed.amount").value("0")).andExpect(jsonPath("$.data.unpaid.count").value(0));noWrites();}
    @Test void summaryCarryoverOffAndOtherYearCurrentMonthZero() throws Exception {mvc.perform(get("/api/bills/summary").param("year","2025").param("includeCarryover","true")).andExpect(jsonPath("$.data.includeCarryover").value(false)).andExpect(jsonPath("$.data.currentMonth.amount").value("0"));noWrites();}
    @Test void selectionReturnsRevisionExclusionsAndNoPageLimit() throws Exception {
        for(int i=2;i<=70;i++){String id=String.format("B-202607-%03d",i);bills.put(id,bill(id,"202607",i==2?"Y":"N",LocalDate.of(2026,8,25)));}
        receipts.put(ID,List.of(receipt("N","1072")));
        mvc.perform(get("/api/bills/selection").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(68)).andExpect(jsonPath("$.data.items.length()").value(68))
            .andExpect(jsonPath("$.data.excludedCount").value(2)).andExpect(jsonPath("$.data.canSave").value(true)).andExpect(jsonPath("$.data.items[0].expectedRevision").isString());noWrites();
    }
    @Test void selectionAllExcludedCannotSave() throws Exception {receipts.put(ID,List.of(receipt("N","1072")));mvc.perform(get("/api/bills/selection").param("year","2026")).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.canSave").value(false)).andExpect(jsonPath("$.data.excludedCount").value(1));noWrites();}
    @Test void selectionIdsAndIndividualExclusion() throws Exception {mvc.perform(get("/api/bills/selection").param("year","2026").param("ids",ID).param("excludeIds",ID)).andExpect(jsonPath("$.data.total").value(0));noWrites();}
    @Test void selectionDuplicateIdRejected() throws Exception {mvc.perform(get("/api/bills/selection").param("year","2026").param("ids",ID,ID)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("ids"));noWrites();}
    @Test void selectionInvalidIdRejected() throws Exception {mvc.perform(get("/api/bills/selection").param("year","2026").param("excludeIds","bad")).andExpect(status().isBadRequest());noWrites();}
    @Test void selectionMonthOverridesCarryoverUnion() throws Exception {bills.put("B-202512-001",bill("B-202512-001","202512","N",LocalDate.of(2026,1,25)));mvc.perform(get("/api/bills/selection").param("year","2026").param("month","2026-07")).andExpect(jsonPath("$.data.total").value(1));noWrites();}
    @Test void copyDraftIsFixedNextMonthOriginalRateAndNoWrites() throws Exception {mvc.perform(get("/api/bills/"+ID+"/copy-draft").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.billingMonth").value("2026-08"))
        .andExpect(jsonPath("$.data.supplyAmount").value("1001")).andExpect(jsonPath("$.data.vatRate").value("7.25")).andExpect(jsonPath("$.data.taxInvoiceNo").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.expectedRevision").value(service.revision(ID)));noWrites();}
    @Test void tkCopyUsesSourceSupply() throws Exception {project=new Project("P","p","c","TK","ING",project.endDate());mvc.perform(get("/api/bills/"+ID+"/copy-draft").param("year","2026")).andExpect(jsonPath("$.data.supplyAmount").value("1000"));noWrites();}
    @Test void unknownFieldsRejectedEveryOperation() throws Exception {
        for(String path:List.of("estimate","selection","summary",ID+"/copy-draft"))mvc.perform(get("/api/bills/"+path).param("userId","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(header().exists("X-Request-Id"));
        mutate("cancel",command(true).put("cancelUserId","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mutate("next-month",command(false).put("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void summaryRejectsListFilters() throws Exception {mvc.perform(get("/api/bills/summary").param("year","2026").param("status","PAID")).andExpect(status().isBadRequest());noWrites();}
    @Test void selectionRejectsPaginationAndActionSort() throws Exception {mvc.perform(get("/api/bills/selection").param("year","2026").param("size","30")).andExpect(status().isBadRequest());mvc.perform(get("/api/bills/selection").param("year","2026").param("sort","quickActions")).andExpect(status().isBadRequest());noWrites();}
    @Test void publicCancellationPreservesAmountsAndCancelledReceiptHistory() throws Exception {
        receipts.put(ID,List.of(receipt("Y","100")));StoredBill original=bills.get(ID);
        mutate("cancel",command(true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.cancelled").value(true)).andExpect(jsonPath("$.data.bill.unpaidAmount").value("0"))
            .andExpect(jsonPath("$.data.bill.supplyAmount").value("1000")).andExpect(jsonPath("$.data.bill.taxInvoiceNo").value("INVOICE"));
        assertThat(bills.get(ID).total()).isEqualTo(original.total());assertThat(receipts.get(ID)).hasSize(1);
        assertThat(audits).allSatisfy(a->{assertThat(a.type()).isEqualTo("UPD");assertThat(a.tableName()).isEqualTo("TB_BL_BILL");assertThat(a.reason()).isEqualTo("취소 사유");});
    }
    @Test void existingEndProjectCancellationAllowed() throws Exception {project=new Project("P","p","c","MO","END",LocalDate.of(2026,6,30));mutate("cancel",command(true)).andExpect(status().isOk());}
    @Test void normalReceiptBlocksCancel() throws Exception {receipts.put(ID,List.of(receipt("N","1")));mutate("cancel",command(true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"","   "}) void reasonRequired(String reason) throws Exception {mutate("cancel",command(true).put("reason",reason)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reason"));noWrites();}
    @Test void reasonTooLong() throws Exception {mutate("cancel",command(true).put("reason","가".repeat(501))).andExpect(status().isBadRequest());noWrites();}
    @Test void validAlreadyCancelledIsNoOp() throws Exception {bills.put(ID,bill(ID,"202607","Y",LocalDate.of(2026,8,25)));StoredBill before=bills.get(ID);mutate("cancel",command(true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(bills.get(ID)).isEqualTo(before);noWrites();}
    @Test void staleEvenAlreadyCancelledRejected() throws Exception {bills.put(ID,bill(ID,"202607","Y",LocalDate.of(2026,8,25)));mutate("cancel",command(true).put("expectedRevision","stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void closedCancellation() throws Exception {when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));mutate("cancel",command(true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void pastContextRejectedBothMutations() throws Exception {mutate("cancel",command(true).put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));mutate("next-month",command(false).put("year",2025)).andExpect(status().isConflict());noWrites();}
    @Test void pastActualMonthRejectsCancellationFromCurrentContext() throws Exception {bills.put(ID,bill(ID,"202512","N",LocalDate.of(2026,1,25)));mutate("cancel",command(true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void tkRecognitionMonthProtectedSeparately() throws Exception {project=new Project("P","p","c","TK","ING",project.endDate());when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));mutate("cancel",command(true)).andExpect(status().isConflict());noWrites();}
    @Test void nextMonthCreates201FromEstimateOriginalRateAndNullInvoice() throws Exception {
        StoredBill source=bills.get(ID);mutate("next-month",command(false)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.id").value("B-202608-001"))
            .andExpect(jsonPath("$.data.bill.billingMonth").value("2026-08")).andExpect(jsonPath("$.data.bill.supplyAmount").value("1001"))
            .andExpect(jsonPath("$.data.bill.vatRate").value("7.25")).andExpect(jsonPath("$.data.bill.vatAmount").value("72"))
            .andExpect(jsonPath("$.data.bill.billingDate").value("2026-07-15")).andExpect(jsonPath("$.data.bill.dueDate").value("2026-09-25"))
            .andExpect(jsonPath("$.data.bill.taxInvoiceNo").value(org.hamcrest.Matchers.nullValue()));assertThat(bills.get(ID)).isEqualTo(source);assertThat(audits).allSatisfy(a->assertThat(a.type()).isEqualTo("NEW"));
    }
    @Test void noModalTkDenied() throws Exception {project=new Project("P","p","c","TK","ING",project.endDate());mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void nonLatestDenied() throws Exception {when(mapper.latest("P")).thenReturn("B-202607-002");mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void cancelledSourceDenied() throws Exception {bills.put(ID,bill(ID,"202607","Y",LocalDate.of(2026,8,25)));mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void targetDuplicateDenied() throws Exception {when(mapper.targetCount("P","202608")).thenReturn(1);mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void zeroEstimateDenied() throws Exception {when(mapper.assignments("P")).thenReturn(Collections.emptyList());mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void contractEndExceededDenied() throws Exception {project=new Project("P","p","c","MO","ING",LocalDate.of(2026,7,31));mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void fixedTargetDenied() throws Exception {when(mapper.fixed("P","202608")).thenReturn(true);mutate("next-month",command(false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void closedTargetDenied() throws Exception {when(shared.monthCloses("202608","202608")).thenReturn(List.of(new CloseRow("202608","CLOSE")));mutate("next-month",command(false)).andExpect(status().isConflict());noWrites();}
    @Test void staleSourceDenied() throws Exception {mutate("next-month",command(false).put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void defaultDateInversionRequiresCopy() throws Exception {
        bills.put(ID,bill(ID,"202604","N",LocalDate.of(2026,5,25)));
        var original=source("ACT","PAID");
        when(monthly.source("A")).thenReturn(new AssignmentMonthlyService.Source(original.id(),original.projectId(),original.developerId(),original.jobCode(),LocalDate.of(2026,1,1),original.endDate(),original.contractMm(),original.price(),original.inputType(),null,null,0,"N","ACT"));
        mutate("next-month",command(false)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("dueDate")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("고쳐서 청구")));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision"}) void mutationRequiredFields(String field) throws Exception {var b=command(false);b.remove(field);mutate("next-month",b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void nextYearContextStillWritable() throws Exception {mutate("next-month",command(false).put("year",2027)).andExpect(status().isCreated());}
    @Test void numberExhaustion() throws Exception {when(mapper.greatestNumber("202608")).thenReturn(999);mutate("next-month",command(false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_NUMBER_EXHAUSTED"));noWrites();}
    @Test void orderExhaustion() throws Exception {when(mapper.greatestOrder("P")).thenReturn(999);mutate("next-month",command(false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_ORDER_EXHAUSTED"));noWrites();}
    @Test void missingSingleTarget404BothReadsAndWrites() throws Exception {bills.clear();mvc.perform(get("/api/bills/"+ID+"/copy-draft").param("year","2026")).andExpect(status().isNotFound());mvc.perform(post("/api/bills/"+ID+"/next-month").contentType("application/json").content("{\"year\":2026,\"expectedRevision\":\"old\"}")).andExpect(status().isNotFound());noWrites();}
    @Test void auditFailureRollsBackAndHidesSql() throws Exception {doThrow(new IllegalStateException("SQL private stack")).when(history).insert(any());mutate("next-month",command(false)).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL private"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void sortedSharedLocksAndFixedTimeoutPrecedeFinalChecks() throws Exception {mutate("next-month",command(false)).andExpect(status().isCreated());var order=inOrder(mapper,locks);order.verify(mapper).lockTimeout();order.verify(locks).lock("BILL:"+ID);order.verify(locks).lock("BILL_MONTH:202608");order.verify(locks).lock("PROJECT:P");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(mapper).lockClose();}
    @Test void persistenceAllowsOnlyFixedTimeoutAndNeverBillingDelete() {assertThatCode(()->PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '10s'")).doesNotThrowAnyException();assertThatThrownBy(()->PersistenceBoundary.requireAllowed("SET LOCAL lock_timeout = '1s'")).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->PersistenceBoundary.requireAllowed("DELETE FROM tb_bl_bill WHERE bill_id=?")).isInstanceOf(IllegalStateException.class);}
}
