package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.projects.*;
import com.sicost.profitability.*;
import com.sicost.projects.ProjectRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(SalesCommandController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=SalesCommandController.class)
@Import({SalesCommandService.class,SalesWinService.class,SalesCommandRepository.class,SalesQuoteCalculation.class,
    WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class SalesCommandContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean SalesCommandMapper mapper;
    @MockBean MonthlyCostRepository costs;
    @MockBean MonthlyCostService costCalculation;
    @MockBean ProjectRegistrationService registration;
    @MockBean ProjectRepository projects;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Opportunity row;
    List<Tentative> tentatives;
    List<ApprovalEvent> events;
    List<Standard> standards;
    String snapshot(){return row.toString()+tentatives+events+standards;}
    String revision(){return revisions.revision(snapshot());}
    @BeforeEach void setup(){
        row=new Opportunity("O-0101","영업기회","C-0001","PART-0001","FIND",new BigDecimal("10.00"),"N",new BigDecimal("10000000"),"2026-07","TK",6,"서울","비고","ING",null,"WAIT",null,null,null,null,null,"admin");
        tentatives=new ArrayList<>(List.of(new Tentative("101","D-0001","ACT",null)));events=new ArrayList<>();
        standards=new ArrayList<>(List.of(new Standard("QUOTE_APRV_RT",new BigDecimal("15")),new Standard("QUOTE_REAPRV_GAP",new BigDecimal("0.5")),new Standard("TGT_MRGN_RT",new BigDecimal("20")),new Standard("STEP_PRBL_1",new BigDecimal("10")),new Standard("STEP_PRBL_2",new BigDecimal("30")),new Standard("STEP_PRBL_3",new BigDecimal("60")),new Standard("STEP_PRBL_4",new BigDecimal("90"))));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.find(anyString())).thenAnswer(c->c.getArgument(0).equals(row.id())?row:null);
        when(mapper.snapshot(anyString())).thenAnswer(c->snapshot());when(mapper.lockOpportunity(anyString())).thenReturn("O-0101");
        when(mapper.sourceLockKeys()).thenReturn(List.of("DEV:D-0001"));when(mapper.tentatives(anyString())).thenAnswer(c->List.copyOf(tentatives));
        when(mapper.needs(anyString())).thenReturn(List.of(new DraftNeed(1,"DEV",null,1,"2026-07",1,"1.00","10000000")));
        when(mapper.standards(anyString())).thenAnswer(c->List.copyOf(standards));when(mapper.codes()).thenReturn(List.of(new Code("GRADE","MD",null),new Code("JOB","DEV","10000000")));
        when(mapper.update(any())).thenAnswer(c->{row=((Mutation)c.getArgument(0)).value();return 1;});
        when(mapper.transitionTentative(any())).thenAnswer(c->{TentativeMutation m=c.getArgument(0);tentatives.set(0,new Tentative(m.id(),"D-0001",m.status(),m.now()));return 1;});
        when(mapper.approvalEvent(any())).thenAnswer(c->{events.add(c.getArgument(0));return 1;});when(mapper.notification(any())).thenReturn(1);when(mapper.activeLossReason("PRICE")).thenReturn(true);when(audit.insert(any())).thenReturn(1);
    }
    ObjectNode body(){return json.createObjectNode().put("expectedRevision",revision());}
    @Test void stageAuthRejectsActorWithoutChangingOpportunity() throws Exception {
        mvc.perform(post("/api/sales-opportunities/O-0101/stage").contentType("application/json").content(body().put("stage","PROP").put("actor","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/sales-opportunities/{id}/stage",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void lossValidationRejectsInactiveReasonWithoutReleasingTentative() throws Exception {
        mvc.perform(post("/api/sales-opportunities/O-0101/lose").contentType("application/json").content(body().put("reasonCode","INVALID").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/sales-opportunities/{id}/lose",400));
        assertThat(tentatives.get(0).status()).isEqualTo("ACT");noWrites();
    }
    @Test void resumeValidationRequiresRevisionWithoutWriting() throws Exception {
        mvc.perform(post("/api/sales-opportunities/O-0101/resume").contentType("application/json").content("{}"))
            .andExpect(DurableOpenApi.response("POST","/api/sales-opportunities/{id}/resume",400));noWrites();
    }
    @Test void winValidationRequiresRevisionWithoutRegisteringProject() throws Exception {
        mvc.perform(post("/api/sales-opportunities/O-0101/win").contentType("application/json").content("{}"))
            .andExpect(DurableOpenApi.response("POST","/api/sales-opportunities/{id}/win",400));
        verifyNoInteractions(registration);noWrites();
    }
    void request(String operation,ObjectNode body,int expected)throws Exception{mvc.perform(post("/api/sales-opportunities/O-0101/"+operation).contentType("application/json").content(body.toString())).andExpect(r->assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(expected));}
    void noWrites(){verify(mapper,never()).update(any());verify(mapper,never()).approvalEvent(any());verify(mapper,never()).notification(any());verifyNoInteractions(audit);}
    @Test void previewIsPublicAndReadOnlyUnknownIsNotZero()throws Exception{
        mvc.perform(post("/api/sales-opportunities/quote-preview").contentType("application/json").content("{\"needs\":[{\"jobCode\":\"DEV\",\"count\":1,\"startMonth\":\"2026-07\",\"months\":1,\"monthlyMm\":\"1.00\"}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").exists()).andExpect(jsonPath("$.data.cost").isEmpty()).andExpect(jsonPath("$.data.judgement").value("UNKNOWN"));noWrites();verify(users).currentUser();
    }
    @Test void incompletePreviewIsExcluded()throws Exception{mvc.perform(post("/api/sales-opportunities/quote-preview").contentType("application/json").content("{\"needs\":[{}]}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].complete").value(false)).andExpect(jsonPath("$.data.judgement").value("INCOMPLETE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"stage","approval","lose","stop","resume","win"})
    void missingRequiredRevisionIs400(String op)throws Exception{request(op,json.createObjectNode(),400);noWrites();}
    @ParameterizedTest @ValueSource(strings={"stage","approval","lose","stop","resume","win"})
    void actorInjectionIs400(String op)throws Exception{request(op,body().put("actor","attacker"),400);noWrites();}
    @Test void unknownPreviewFieldAndMalformedTypeRejected()throws Exception{
        for(String content:List.of("{\"needs\":[],\"actor\":\"x\"}","{\"needs\":[{\"count\":\"1\"}]}","{\"needs\":[{\"expectedRate\":\"0\"}]}","{\"needs\":[{\"startMonth\":\"0000-01\"}]}"))mvc.perform(post("/api/sales-opportunities/quote-preview").contentType("application/json").content(content)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();
    }
    @Test void stopReleasesActAndLatestNoopDoesNotAudit()throws Exception{
        request("stop",body(),200);assertThat(row.status()).isEqualTo("STOP");assertThat(row.resultDate()).isEqualTo(LocalDate.of(2026,7,15));assertThat(tentatives.get(0).status()).isEqualTo("REL");
        clearInvocations(mapper,audit);request("stop",body(),200);verify(mapper,never()).update(any());verifyNoInteractions(audit);
    }
    @Test void loseStoresFull500CharacterMemoAndReleases()throws Exception{mvc.perform(post("/api/sales-opportunities/O-0101/lose").contentType("application/json").content(body().put("reasonCode","PRICE").put("memo","가".repeat(500)).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").isMap());assertThat(row.status()).isEqualTo("LOSE");assertThat(row.lossMemo()).hasSize(500);assertThat(tentatives.get(0).status()).isEqualTo("REL");}
    @Test void invalidLossReasonIs400()throws Exception{request("lose",body().put("reasonCode","INVALID"),400);noWrites();}
    @Test void overlongLossMemoIs400()throws Exception{request("lose",body().put("reasonCode","PRICE").put("memo","x".repeat(501)),400);noWrites();}
    @Test void staleWriteStopsAllEffects()throws Exception{request("stop",body().put("expectedRevision","old"),409);noWrites();}
    @Test void missingTargetIs404()throws Exception{mvc.perform(post("/api/sales-opportunities/O-9999/stop").contentType("application/json").content(body().toString())).andExpect(status().isNotFound());noWrites();}
    @Test void upwardUnknownIsStateConflictNotRoleForbidden()throws Exception{request("stage",body().put("stage","NEGO"),409);noWrites();}
    @Test void sourceQuoteChangeWinsOverStaleRevision()throws Exception{
        row=withRate("0.00","WAIT");request("approval",body().put("action","OK").put("expectedRevision","old"),409);
        mvc.perform(post("/api/sales-opportunities/O-0101/approval").contentType("application/json").content(body().put("action","OK").put("expectedRevision","old").toString())).andExpect(jsonPath("$.code").value("QUOTE_CHANGED"));noWrites();
    }
    Opportunity withRate(String rate,String status){return SalesCommandService.with(row,new SalesCommandService.Transition(row.stage(),row.probability(),row.manualProbability(),row.status(),rate==null?null:new BigDecimal(rate),status,null,row.resultDate(),row.lossReasonCode(),row.lossMemo(),row.projectId()));}
    @Test void stageChangesDbDefaultAndManualEqualDefaultIsPreserved()throws Exception{
        mvc.perform(post("/api/sales-opportunities/O-0101/stage").contentType("application/json").content(body().put("stage","PROP").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").isMap());assertThat(row.probability()).isEqualByComparingTo("30");
        request("stage",body().put("stage","PROP").put("manualProbability",true).put("probability","30"),200);assertThat(row.manualProbability()).isEqualTo("Y");
        request("stage",body().put("stage","FIND"),200);assertThat(row.probability()).isEqualByComparingTo("30");
        request("stage",body().put("stage","FIND").put("manualProbability",false),200);assertThat(row.manualProbability()).isEqualTo("N");assertThat(row.probability()).isEqualByComparingTo("10");
    }
    @Test void missingProbabilityStandardDoesNotReseed()throws Exception{standards.removeIf(s->s.code().equals("STEP_PRBL_2"));request("stage",body().put("stage","PROP"),409);noWrites();}
    @Test void resumeRequotesButDoesNotRestoreReleasedTentative()throws Exception{request("stop",body(),200);mvc.perform(post("/api/sales-opportunities/O-0101/resume").contentType("application/json").content(body().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").isMap());assertThat(row.status()).isEqualTo("ING");assertThat(row.profitRate()).isNull();assertThat(row.approvalStatus()).isEqualTo("WAIT");assertThat(row.resultDate()).isNull();assertThat(tentatives.get(0).status()).isEqualTo("REL");assertThat(events).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"WIN","LOSE"}) void terminalCannotResume(String state)throws Exception{row=SalesCommandService.with(row,new SalesCommandService.Transition(row.stage(),row.probability(),"N",state,null,"WAIT",null,null,null,null,null));request("resume",body(),409);noWrites();}
    @Test void auditFailureIs500AndRollsBackTransaction()throws Exception{when(audit.insert(any())).thenReturn(0);request("stop",body(),500);verify(manager,atLeastOnce()).rollback(any());}
    @Test void invalidWinDateCannotCreateProject()throws Exception{request("win",body().put("contractAmount","1").put("contractType","TK").put("projectStatus","ING").put("startDate","2026-07-01").put("endDate","2026-07-01"),400);verifyNoInteractions(registration);noWrites();}
    void numericCost(String amount){
        var source=mock(MonthlyCostDtos.Sources.class);var person=mock(MonthlyCostDtos.Person.class);var pay=mock(MonthlyCostDtos.Payroll.class);
        var result=mock(MonthlyCostDtos.MonthlyPersonCost.class);var cost=mock(MonthlyCostDtos.MonthlyCostRow.class);
        when(mapper.latestPayrollMonth("202607")).thenReturn("202606");when(costs.loadMonth(2026,6)).thenReturn(source);
        when(source.people()).thenReturn(List.of(person));when(source.payroll()).thenReturn(List.of(pay));when(source.states()).thenReturn(List.of());
        when(person.personId()).thenReturn("D-0001");when(person.jobCode()).thenReturn("DEV");when(person.gradeCode()).thenReturn("MD");
        when(pay.personId()).thenReturn("D-0001");when(pay.month()).thenReturn("202606");when(result.personId()).thenReturn("D-0001");
        when(result.completeness()).thenReturn(new MonthlyCostDtos.MonthlyCostCompleteness(true,true,false,true,List.of()));when(result.rows()).thenReturn(List.of(cost));
        when(cost.laborAllocated()).thenReturn(amount);when(cost.teamExpense()).thenReturn("0");when(cost.projectExpense()).thenReturn("0");
        when(costCalculation.calculate(source)).thenReturn(new MonthlyCostDtos.MonthlyCostsData(null,List.of(result)));
    }
    @Test void publicApprovalStoresActualRateAndRejectionPreservesFullReasonAndOneNotification()throws Exception{
        numericCost("9000000");row=withRate("10.00","WAIT");
        request("approval",body().put("action","REJ").put("reason","가".repeat(300)),200);
        assertThat(row.approvalStatus()).isEqualTo("REJ");assertThat(events).hasSize(1);assertThat(events.get(0).reason()).hasSize(300);assertThat(events.get(0).profitRate()).isEqualByComparingTo("10");
        var captor=org.mockito.ArgumentCaptor.forClass(Notification.class);verify(mapper).notification(captor.capture());assertThat(captor.getValue().content().length()).isLessThanOrEqualTo(300);assertThat(captor.getValue().recipient()).isEqualTo("admin");
    }
    @Test void approvalOkCopiesStoredRateAndLatestReplayDoesNotWrite()throws Exception{
        numericCost("9000000");row=withRate("10.00","WAIT");request("approval",body().put("action","OK"),200);assertThat(row.approvedProfitRate()).isEqualByComparingTo("10");
        clearInvocations(mapper,audit);request("approval",body().put("action","OK"),200);noWrites();
    }
    @Test void notificationFailureRollsBackApprovalTransaction()throws Exception{
        numericCost("9000000");row=withRate("10.00","WAIT");when(mapper.notification(any())).thenReturn(0);request("approval",body().put("action","REJ"),500);verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void winUsesCommonProjectAndOppPlansThenConvertsAct()throws Exception{
        numericCost("8000000");row=withRate("20.00","NONE");
        when(projects.defaults(anyString())).thenReturn(new com.sicost.projects.ProjectDtos.DefaultsRow(new BigDecimal("202501"),new BigDecimal("20"),BigDecimal.ZERO,BigDecimal.ZERO));
        when(projects.activeType("TK")).thenReturn(true);when(projects.customer(anyString())).thenReturn(new com.sicost.projects.ProjectDtos.Reference("C-0001","고객",true));when(projects.part(anyString())).thenReturn(new com.sicost.projects.ProjectDtos.Reference("PART-0001","파트",true));when(registration.reservedIds(any())).thenReturn(new HashSet<>());when(mapper.insertOpportunityPlan(any())).thenReturn(1);
        mvc.perform(post("/api/sales-opportunities/O-0101/win").contentType("application/json").content(body().put("contractAmount","12000000").put("contractType","TK").put("projectStatus","ING").put("startDate","2026-07-01").put("endDate","2027-06-30").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").isMap());
        assertThat(row.status()).isEqualTo("WIN");assertThat(row.projectId()).isEqualTo("P-0001");assertThat(row.probability()).isEqualByComparingTo("100");assertThat(tentatives.get(0).status()).isEqualTo("CNVT");
        var captor=org.mockito.ArgumentCaptor.forClass(ProjectRegistrationService.Project.class);verify(registration).register(eq("P-0001"),captor.capture(),eq("admin"),any());assertThat(captor.getValue().opportunityId()).isEqualTo(row.id());
        var plan=org.mockito.ArgumentCaptor.forClass(Plan.class);verify(mapper).insertOpportunityPlan(plan.capture());assertThat(plan.getValue().planMm()).isEqualByComparingTo("1.00");
    }
    @Test void changedThresholdWithSameStoredRateRequiresSaveBeforeApproval() throws Exception {
        numericCost("9000000");row=withRate("10.00","WAIT");
        standards.removeIf(s->s.code().equals("QUOTE_APRV_RT"));
        standards.add(new Standard("QUOTE_APRV_RT",new BigDecimal("10")));
        request("approval",body().put("action","OK"),409);
        noWrites();
    }
}
