package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
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
import static com.sicost.apiops.BillingReviewDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingCheckController.class)
@ContextConfiguration(classes=BillingCheckController.class)
@Import({BillingCheckService.class,BillingReviewRepository.class,AssignmentMonthlyService.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BillingCheckContractTest {
    @Autowired MockMvc mvc;
    @Autowired BillingCheckService service;
    @Autowired ObjectMapper json;
    @MockBean BillingReviewMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean AssignmentMonthlyMapper monthly;
    @MockBean CurrentUserProvider users;
    @MockBean com.sicost.common.history.ChangeHistoryWriter audit;
    @MockBean Clock clock;
    List<CheckBill> bills;
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        Clock fixed=Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(shared.cashBaseMonth()).thenReturn("202601");when(monthly.cashBase()).thenReturn("202601");
        when(shared.parameter(anyString(),anyString())).thenAnswer(c->new Parameter(c.getArgument(0),"BILL_MISS_MIN".equals(c.getArgument(0))?"0":"0.5","원","2026-01"));
        when(mapper.checkProjects()).thenReturn(List.of(new Project("P","시험 프로젝트","MO","ING")));
        when(mapper.project("P")).thenReturn(new Project("P","시험 프로젝트","MO","ING"));
        when(mapper.assignments("P")).thenReturn(List.of(new AssignmentReference("A","시험 인력","0001")));
        when(monthly.source("A")).thenReturn(new AssignmentMonthlyService.Source("A","P","D","JAVA",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),new BigDecimal("0.50"),new BigDecimal("101"),"PAID",null,null,0,"N","ACT"));
        when(monthly.histories(anyString())).thenReturn(List.of());when(monthly.months(anyString())).thenReturn(List.of());
        bills=new ArrayList<>();when(mapper.checkBills(any())).thenAnswer(c->List.copyOf(bills));
        when(mapper.checkSnapshot(any())).thenReturn("stored-sources");when(mapper.billSnapshot(anyString())).thenReturn("bill-and-all-receipts");
    }
    CheckBill bill(String id,String amount) {return new CheckBill(id,LocalDate.of(2026,7,1),new BigDecimal(amount),new BigDecimal("10"),BigDecimal.ZERO);}
    @Test void publicSnapshotReusesMonthlyCalculationAndProducesExactCardsSourcesAndPreview() throws Exception {
        bills.add(bill("B1","10"));bills.add(bill("B2","15"));
        mvc.perform(get("/api/billing-checks").param("month","2026-07")).andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.data.items[0].estimatedAmount").value("50")).andExpect(jsonPath("$.data.items[0].billedAmount").value("25"))
            .andExpect(jsonPath("$.data.items[0].differenceAmount").value("25")).andExpect(jsonPath("$.data.items[0].tolerance").value("0.25"))
            .andExpect(jsonPath("$.data.items[0].verdict").value("적게 청구")).andExpect(jsonPath("$.data.summary.differenceCount").value(1))
            .andExpect(jsonPath("$.data.summary.missingSupplyAmount").value("25")).andExpect(jsonPath("$.data.items[0].remedy.proposedSupplyAmount").value("35"))
            .andExpect(jsonPath("$.data.items[0].sources[0].protected").value(false)).andExpect(jsonPath("$.data.items[0].checkRevision").isString());
        verify(users).currentUser();verifyNoInteractions(audit);verify(monthly,never()).saveMonth(any());
    }
    @Test void zeroBillsIsMissingAndCreateFixesIdentityAndEstimate() throws Exception {
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].verdict").value("청구 없음"))
            .andExpect(jsonPath("$.data.items[0].remedy.mode").value("CREATE")).andExpect(jsonPath("$.data.items[0].remedy.projectId").value("P"))
            .andExpect(jsonPath("$.data.items[0].remedy.proposedSupplyAmount").value("50")).andExpect(jsonPath("$.data.summary.missingCount").value(1));
    }
    @Test void toleranceEqualityIsMatchWithoutRounding() throws Exception {
        when(shared.parameter("BILL_MISS_MIN","202607")).thenReturn(new Parameter("BILL_MISS_MIN","1","원","2026-01"));bills.add(bill("B","49"));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].verdict").value("맞음"))
            .andExpect(jsonPath("$.data.items[0].remedy.mode").value("NONE")).andExpect(jsonPath("$.data.summary.matchedCount").value(1));
    }
    @Test void negativeDifferenceProducesOverVerdictNotNegativeCreate() throws Exception {
        bills.add(bill("B","51"));mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].verdict").value("많이 청구"))
            .andExpect(jsonPath("$.data.items[0].differenceAmount").value("-1")).andExpect(jsonPath("$.data.summary.missingSupplyAmount").value("0"));
    }
    @Test void closedSnapshotSurvivesCancelledParentAndNeverOffersEdit() throws Exception {
        when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of(new CloseRow("202607","CLOSE")));
        when(monthly.closeStatus("202607")).thenReturn("CLOSE");
        var old=monthly.source("A");when(monthly.source("A")).thenReturn(old.changed(old.endDate(),old.contractMm(),old.price(),old.inputType(),null,0,"N","CNCL"));
        when(monthly.months("A")).thenReturn(List.of(new AssignmentMonthlyService.StoredMonth("202607",31,new BigDecimal("0.50"),new BigDecimal("101"),new BigDecimal("50"),"Y")));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].estimatedAmount").value("50"))
            .andExpect(jsonPath("$.data.items[0].sources[0].protected").value(true)).andExpect(jsonPath("$.data.items[0].remedy.mode").value("DETAIL_ONLY"))
            .andExpect(jsonPath("$.data.items[0].remedy.proposedSupplyAmount").isEmpty()).andExpect(jsonPath("$.data.includesPlanned").value(false));
    }
    @Test void endedProjectWithSourcesIsIncluded() throws Exception {
        when(mapper.checkProjects()).thenReturn(List.of(new Project("P","완료 프로젝트","DP","END")));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void freeOnlyWithoutBillIsExcluded() throws Exception {
        var old=monthly.source("A");when(monthly.source("A")).thenReturn(old.changed(old.endDate(),old.contractMm(),old.price(),"FREE",null,0,"N","ACT"));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void eachAssignmentIsTruncatedBeforeSumAndPersonCountDistinct() throws Exception {
        when(mapper.assignments("P")).thenReturn(List.of(new AssignmentReference("A","인력","0001"),new AssignmentReference("A2","인력","0001")));
        var a=monthly.source("A");when(monthly.source("A2")).thenReturn(new AssignmentMonthlyService.Source("A2",a.projectId(),a.developerId(),a.jobCode(),a.startDate(),a.endDate(),a.contractMm(),a.price(),a.inputType(),null,null,0,"N","ACT"));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].estimatedAmount").value("100"))
            .andExpect(jsonPath("$.data.items[0].personCount").value(1)).andExpect(jsonPath("$.data.items[0].mm").value("1.00"));
    }
    @Test void newcomerExplanationUsesCombinedActualAssignmentsWithoutChangingProposal() throws Exception {
        bills.add(bill("B","1"));mvc.perform(get("/api/billing-checks")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].explanation").value(org.hamcrest.Matchers.containsString("시험 인력(0001)")))
            .andExpect(jsonPath("$.data.items[0].remedy.proposedSupplyAmount").value("50"));
    }
    @Test void nonPositiveProposalIsDetailOnly() throws Exception {
        bills.add(bill("B1","1"));bills.add(bill("B2","100"));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].remedy.mode").value("DETAIL_ONLY"));
    }
    @Test void proposalBelowNormalReceiptsIsDetailOnly() throws Exception {
        bills.add(new CheckBill("B",LocalDate.of(2026,7,1),new BigDecimal("100"),BigDecimal.TEN,new BigDecimal("90")));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].remedy.mode").value("DETAIL_ONLY"));
    }
    @ParameterizedTest @ValueSource(strings={"2026-13","202607","2026-7","0000-01","2025-07","2026-08"})
    void invalidOrOutsideCurrentMonthIsRejected(String month) throws Exception {
        mvc.perform(get("/api/billing-checks").param("month",month)).andExpect(status().isBadRequest());verifyNoInteractions(audit);
    }
    @ParameterizedTest @ValueSource(strings={"userId","year","actor","sort","estimatedAmount"})
    void unknownQueryRejectedBeforeDataRead(String key) throws Exception {
        mvc.perform(get("/api/billing-checks").param(key,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"30","0","-1","1.5"})
    void invalidSizeRejected(String value) throws Exception {mvc.perform(get("/api/billing-checks").param("size",value)).andExpect(status().isBadRequest());}
    @Test void beforeOperatingRangeIsRejected() throws Exception {
        when(shared.cashBaseMonth()).thenReturn("202607");mvc.perform(get("/api/billing-checks").param("month","2026-06")).andExpect(status().isBadRequest());
    }
    @Test void operatingNotStartedHasExplicitEmptyWarningAndNoGuessedTolerance() throws Exception {
        when(shared.cashBaseMonth()).thenReturn("202701");mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultMonth").isEmpty()).andExpect(jsonPath("$.data.warnings[0]").value("운영 시작 전입니다."));verifyNoInteractions(mapper);
    }
    @Test void missingToleranceFailsOnlyChecksWithLocalizedSafeEnvelope() throws Exception {
        when(shared.parameter("BILL_MISS_MIN","202607")).thenReturn(null);
        mvc.perform(get("/api/billing-checks")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("설정")));verifyNoInteractions(audit);
    }
    @Test void negativeToleranceFailsWithoutVerdict() throws Exception {
        when(shared.parameter("BILL_MISS_RT","202607")).thenReturn(new Parameter("BILL_MISS_RT","-0.1","%","2026-01"));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isInternalServerError());
    }
    @Test void monthsIncludeNoBillMonthsAndDefaultLatestClose() throws Exception {
        when(shared.monthCloses("202601","202607")).thenReturn(List.of(new CloseRow("202603","CLOSE"),new CloseRow("202606","CLOSE")));
        mvc.perform(get("/api/billing-checks")).andExpect(status().isOk()).andExpect(jsonPath("$.data.availableMonths.length()").value(7)).andExpect(jsonPath("$.data.defaultMonth").value("2026-06"));
    }
    @Test void changedReceiptOrAssignmentSnapshotRejectsContextBeforeRemedy() {
        var context=service.getBillingChecks(new PageQuery("2026-07",1,50)).items().get(0).remedy().checkContext();
        when(mapper.checkSnapshot(any())).thenReturn("receipt-or-month-changed");
        assertThatThrownBy(()->service.validateContext(context)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("BILL_CHECK_CHANGED"));verifyNoInteractions(audit);
    }
    @Test void repeatedGetHasStableRevisionAndNoPersistenceCalls() throws Exception {
        var a=service.getBillingChecks(new PageQuery(null,1,50));var b=service.getBillingChecks(new PageQuery(null,1,50));assertThat(a).isEqualTo(b);
        verifyNoInteractions(audit);verify(monthly,never()).saveMonth(any());verify(monthly,never()).project(any());verify(monthly,never()).deleteMonth(anyString(),anyString());
    }
}
