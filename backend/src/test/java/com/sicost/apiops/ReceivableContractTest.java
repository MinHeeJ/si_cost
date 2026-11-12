package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.Parameter;
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

@WebMvcTest(ReceivableController.class)
@ContextConfiguration(classes=ReceivableController.class)
@Import({ReceivableService.class,BillingReviewRepository.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ReceivableContractTest {
    @Autowired MockMvc mvc;
    @Autowired ReceivableService service;
    @MockBean BillingReviewMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    final LocalDate today=LocalDate.of(2026,7,15);
    List<OverdueBill> bills;
    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(shared.parameter(anyString(),eq("202607"))).thenAnswer(c->new Parameter(c.getArgument(0),switch((String)c.getArgument(0)){case "DUN_D1"->"7.5";case "DUN_D2"->"30.5";default->"60.5";},"일","2026-01"));
        bills=new ArrayList<>();when(mapper.overdue(today)).thenAnswer(c->List.copyOf(bills));
        when(mapper.eligibleRoles()).thenReturn(List.of(new RecipientRole("pm","프로젝트 담당","PM"),new RecipientRole("leader","파트장","SALES_LDR"),new RecipientRole("executive","임원","EXEC"),new RecipientRole("finance","재무","FIN")));
        when(mapper.recipientScopes()).thenReturn(List.of(new RecipientScope("pm","PROJ",null,"P"),new RecipientScope("pm","PROJ",null,"P")));
        when(mapper.projectPart("P","202607")).thenReturn(new ProjectPart("PART",false,"leader"));
    }
    OverdueBill bill(String id,int days) {return new OverdueBill(id,"P","프로젝트",new BigDecimal("110"),today.minusDays(days));}
    @Test void includesEarlyOverdueCarryoverAndExactDecimalThresholdsWithThreeCards() throws Exception {
        for(int days:List.of(1,6,7,8,30,31,60,61,400))bills.add(bill("B"+days,days));
        mvc.perform(get("/api/receivables")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(9)).andExpect(jsonPath("$.data.tabCount").value(9))
            .andExpect(jsonPath("$.data.asOfDate").value("2026-07-15")).andExpect(jsonPath("$.data.items[0].dunning.step").value(3))
            .andExpect(jsonPath("$.data.items[8].dunning.step").value(0)).andExpect(jsonPath("$.data.items[8].dunning.nextDate").value("2026-07-22"))
            .andExpect(jsonPath("$.data.cards[0].thresholdDays").value("7.5")).andExpect(jsonPath("$.data.cards[0].count").value(2))
            .andExpect(jsonPath("$.data.cards[1].count").value(2)).andExpect(jsonPath("$.data.cards[2].count").value(2)).andExpect(header().exists("X-Request-Id"));
        verify(users).currentUser();
    }
    @Test void dueTodayNotOverdueAndNextDayIsOverdueBeforeNotification() throws Exception {
        bills.add(bill("today",0));bills.add(bill("yesterday",1));
        mvc.perform(get("/api/receivables")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].dunning.elapsedDays").value(1)).andExpect(jsonPath("$.data.items[0].dunning.step").value(0));
    }
    @Test void noHistoryMeansEmptyArrayAndNoInventedDatesOrSendActions() throws Exception {
        bills.add(bill("B",61));mvc.perform(get("/api/receivables")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].sentHistory").isEmpty())
            .andExpect(jsonPath("$.data.items[0].sendNotice").value("발송 기능 연결 예정")).andExpect(jsonPath("$.data.items[0].dunning.finalStep").value(true))
            .andExpect(jsonPath("$.data.items[0].dunning.nextDate").isEmpty());
    }
    @Test void actualHistoricalReceiversRemainSeparateFromCurrentRecipientsAndResendIsPreserved() throws Exception {
        bills.add(bill("B",8));var history=List.of(new DunningHistory("B",1,1,today.minusDays(2),"당시 담당","EMAIL",false),new DunningHistory("B",2,1,today.minusDays(2),"이전 담당","SYSTEM",true));when(mapper.sentHistory("B")).thenReturn(history);
        mvc.perform(get("/api/receivables")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].sentHistory[1].resend").value(true))
            .andExpect(jsonPath("$.data.items[0].sentHistory[0].receiverDescription").value("당시 담당")).andExpect(jsonPath("$.data.items[0].recipients[0].recipients[0].userId").value("pm"));
        assertThat(service.getReceivables(new PageQuery(null,1,50)).items().get(0).sentHistory()).isEqualTo(history);
    }
    @Test void ordinaryPartUsesActualLeaderAndDeduplicatesByUserId() throws Exception {
        bills.add(bill("B",40));mvc.perform(get("/api/receivables")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].recipients[0].recipients.length()").value(1)).andExpect(jsonPath("$.data.items[0].recipients[1].recipients.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].recipients[2].recipients.length()").value(4));
    }
    @Test void missingOrdinaryLeaderNeverFallsBackToAdminOrExecutive() {
        var groups=ReceivableService.recipients("P",new ProjectPart("PART",false,"missing"),mapper.eligibleRoles(),mapper.recipientScopes());
        assertThat(groups.get(1).recipients()).extracting(Recipient::userId).containsExactly("pm");assertThat(groups.get(1).unassignedReasons()).contains("영업파트 SALES_LDR 미지정");
    }
    @Test void headquartersSelectsEveryScopedEligibleSalesLeader() {
        var roles=new ArrayList<>(mapper.eligibleRoles());roles.add(new RecipientRole("leader2","담당2","SALES_LDR"));
        var groups=ReceivableService.recipients("P",new ProjectPart("HQ",true,null),roles,List.of(new RecipientScope("leader","PART","HQ",null),new RecipientScope("leader2","PART","HQ",null)));
        assertThat(groups.get(1).recipients()).extracting(Recipient::userId).containsExactly("leader","leader2");assertThat(groups.get(1).unassignedReasons()).contains("프로젝트 PM 미지정");
    }
    @Test void hqWithoutLeaderUsesOnlyEligibleExecutiveFallback() {
        var groups=ReceivableService.recipients("P",new ProjectPart("HQ",true,null),mapper.eligibleRoles(),List.of());
        assertThat(groups.get(1).recipients()).extracting(Recipient::userId).containsExactly("executive");
    }
    @Test void unassignedPartUsesExecutiveFallbackAndKeepsMissingRoleReasons() {
        var groups=ReceivableService.recipients("P",null,List.of(new RecipientRole("fin","재무","FIN")),List.of());
        assertThat(groups.get(1).recipients()).isEmpty();assertThat(groups.get(1).unassignedReasons()).hasSize(2);
        assertThat(groups.get(2).recipients()).extracting(Recipient::userId).containsExactly("fin");assertThat(groups.get(2).unassignedReasons()).contains("EXEC 미지정");
    }
    @Test void multipleRolesOnSameActorMergeRolesWithoutDuplicates() {
        var roles=List.of(new RecipientRole("u","담당","PM"),new RecipientRole("u","담당","FIN"),new RecipientRole("u","담당","EXEC"));
        var groups=ReceivableService.recipients("P",null,roles,List.of(new RecipientScope("u","PROJ",null,"P")));
        assertThat(groups.get(2).recipients()).hasSize(1);assertThat(groups.get(2).recipients().get(0).roles()).containsExactly("EXEC","FIN","PM");
    }
    @ParameterizedTest @ValueSource(strings={"userId","actor","year","asOfDate","month","status","sort","filter"})
    void injectionIsRejectedBeforeBusinessReads(String key) throws Exception {
        mvc.perform(get("/api/receivables").param(key,"anything")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"30","0","-1","1000","abc"})
    void invalidSizeRejected(String size) throws Exception {mvc.perform(get("/api/receivables").param("size",size)).andExpect(status().isBadRequest());}
    @Test void invalidPageRejected() throws Exception {mvc.perform(get("/api/receivables").param("page","0")).andExpect(status().isBadRequest());}
    @Test void paginationKeepsGlobalCardsAndCountNotPageCount() throws Exception {
        for(int i=0;i<51;i++)bills.add(bill("B"+i,1));mvc.perform(get("/api/receivables").param("page","3").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(11)).andExpect(jsonPath("$.data.total").value(51)).andExpect(jsonPath("$.data.cards[0].count").value(0));
    }
    @Test void missingThresholdReturnsLocalizedSettings500WithoutGuessing() throws Exception {
        when(shared.parameter("DUN_D2","202607")).thenReturn(null);
        mvc.perform(get("/api/receivables")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("설정")));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"0.9","30.5","60.5"})
    void invalidThresholdOrderRejected(String threshold) throws Exception {
        when(shared.parameter("DUN_D1","202607")).thenReturn(new Parameter("DUN_D1",threshold,"일","2026-01"));mvc.perform(get("/api/receivables")).andExpect(status().isInternalServerError());
    }
    @Test void exactIntegerThresholdAdvancesOnBoundaryAndFractionDoesNotTruncate() {
        var thresholds=List.of(new BigDecimal("1"),new BigDecimal("2.5"),new BigDecimal("3.5"));
        assertThat(ReceivableService.dunning(today.minusDays(1),today,thresholds).step()).isEqualTo(1);
        assertThat(ReceivableService.dunning(today.minusDays(2),today,thresholds).step()).isEqualTo(1);
        assertThat(ReceivableService.dunning(today.minusDays(3),today,thresholds).step()).isEqualTo(2);
        assertThat(ReceivableService.dunning(today.minusDays(4),today,thresholds).step()).isEqualTo(3);
    }
}
