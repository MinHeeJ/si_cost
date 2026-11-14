package com.sicost.salesopportunities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.salesopportunitiesops.*;
import com.sicost.salesopportunitiesops.SalesCommandDtos.*;
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
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SalesOpportunityController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=SalesOpportunityController.class)
@Import({SalesOpportunityService.class,SalesOpportunityRepository.class,SalesQuoteCalculation.class,WriteCoordinator.class,
    RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class SalesOpportunityContractTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired RevisionService revisions;
    @MockBean SalesOpportunityMapper mapper; @MockBean SalesCommandRepository commands;
    @MockBean SalesOpportunityTentatives tentatives; @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit; @MockBean AggregateLockMapper locks; @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager; @MockBean Clock clock;
    Stored stored;List<DraftNeed> needs;QuoteSources sources;int version;
    Stored row(Save s,String status,BigDecimal rate,String approval,BigDecimal approved){return new Stored("O-0101",s.name(),s.customerId(),s.partId(),s.stage(),new BigDecimal(s.probability()),s.manualProbability()?"Y":"N",new BigDecimal(s.amount()),s.startMonth(),s.contractType(),s.months(),s.nextTask(),s.nextDue(),s.workplace(),s.memo(),status,rate,approval,approved,null,null,null,null,"admin",LocalDateTime.of(2026,7,1,9,0),"admin",LocalDateTime.of(2026,7,1,9,0));}
    ObjectNode body(){try{return (ObjectNode)json.readTree("""
        {"name":"영업 기회","customerId":"C-live","partId":"PART-live","stage":"FIND","probability":"50","manualProbability":true,"amount":"100","startMonth":"2026-08","months":null,"nextTask":"제안서","nextDue":"2020-01-01","needs":[{"jobCode":"BE","gradeCode":null,"count":2,"startMonth":"2026-08","months":6,"monthlyMm":"0.5","expectedRate":"10000000"}]}
        """);}catch(Exception e){throw new RuntimeException(e);}}
    String revision(){return revisions.revision("snapshot-"+version);}
    @BeforeEach void setup(){
        version=0;needs=List.of(new DraftNeed(1,"BE",null,2,"2026-08",6,"0.5","10000000"));stored=row(SalesOpportunityInput.save(body(),false),"ING",null,"WAIT",null);
        sources=new QuoteSources("2026-07",null,List.of(new Standard("CASH_BASE_YM",new BigDecimal("202601")),new Standard("QUOTE_APRV_RT",new BigDecimal("15")),new Standard("QUOTE_REAPRV_GAP",new BigDecimal("0.5")),new Standard("TGT_MRGN_RT",new BigDecimal("20"))),List.of(new Code("JOB","BE","10000000"),new Code("GRADE","MD",null)),List.of());
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(mapper.find("O-0101")).thenAnswer(c->stored);when(mapper.all()).thenAnswer(c->List.of(stored));
        when(mapper.opportunityKeys()).thenReturn(List.of("OPP:O-0101"));when(commands.keys(anyString())).thenReturn(List.of("OPP:O-0101"));when(commands.snapshot(anyString())).thenAnswer(c->"snapshot-"+version);when(commands.quoteSources(any())).thenAnswer(c->sources);when(commands.needs(anyString())).thenAnswer(c->needs);
        when(mapper.customer(anyString())).thenReturn(true);when(mapper.part(anyString())).thenReturn(true);when(mapper.code(any())).thenReturn(true);when(mapper.greatestNeedSn(anyString())).thenReturn(1);when(mapper.nextId()).thenReturn("101");
        when(mapper.insert(any())).thenAnswer(c->{Write w=c.getArgument(0);stored=row(w.input(),"ING",w.rate(),w.approval(),w.approved());version++;return 1;});when(mapper.update(any())).thenAnswer(c->{Write w=c.getArgument(0);stored=row(w.input(),"ING",w.rate(),w.approval(),w.approved());version++;return 1;});
        when(mapper.insertNeed(any())).thenReturn(1);when(mapper.updateNeed(any())).thenReturn(1);when(audit.insert(any())).thenReturn(1);
        when(mapper.fields(anyString())).thenAnswer(c->List.of(new Field("TB_SL_OPP","O-0101","OPP_NM",stored.name())));
        when(tentatives.resolve(any(),any())).thenAnswer(c->c.getArgument(0));when(tentatives.changes(any(),any(),any(),any())).thenReturn(List.of());when(tentatives.apply(anyString(),any())).thenReturn(List.of());
    }
    ObjectNode edit(){ObjectNode b=body();((ObjectNode)b.withArray("needs").get(0)).put("needSn",1);return b.put("expectedRevision",revision());}
    void noWrites(){verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).insertNeed(any());verify(mapper,never()).deleteNeed(any());verifyNoInteractions(audit);}
    @Test void listIsPublicExactWeightedPeopleAndFourteenDaysIsNotStale()throws Exception{mvc.perform(get("/api/sales-opportunities")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").exists()).andExpect(jsonPath("$.data.summary.weightedAmount").value("50")).andExpect(jsonPath("$.data.summary.weightedPeople").value("0.5")).andExpect(jsonPath("$.data.summary.staleCount").value(0));noWrites();}
    @Test void detailContainsNullsNeedsHistoryQuoteAndCompleteRevision()throws Exception{mvc.perform(get("/api/sales-opportunities/O-0101")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("O-0101")).andExpect(jsonPath("$.data.needs[0].needSn").value(1)).andExpect(jsonPath("$.data.quote.cost").isEmpty()).andExpect(jsonPath("$.data.quote.judgement").value("UNKNOWN")).andExpect(jsonPath("$.data.revision").value(revision()));noWrites();}
    @Test void missingDetailIs404()throws Exception{mvc.perform(get("/api/sales-opportunities/O-9999")).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor=x","page=0","page=2147483648","size=30","status=CLOSED","result=ING","direction=DOWN","sort=actor","includeStopped=1"})
    void invalidListQueriesAre400(String query)throws Exception{mvc.perform(get("/api/sales-opportunities?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();}
    @Test void detailRejectsActor()throws Exception{mvc.perform(get("/api/sales-opportunities/O-0101?actor=x")).andExpect(status().isBadRequest());noWrites();}
    @Test void createAllowsPastDueAndUnknownCostWithoutFakeHistory()throws Exception{mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(body().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.opportunityId").value("O-0101")).andExpect(jsonPath("$.data.changed").value(true));assertThat(stored.approvalStatus()).isEqualTo("WAIT");assertThat(stored.profitRate()).isNull();assertThat(stored.months()).isNull();verify(commands,never()).approval(any());verify(mapper).insertNeed(any());}
    @Test void updateKeepsExistingNeedAndLatestNoopHasNoWrites()throws Exception{mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();}
    @Test void updateChangesNameOnlyAndRetainsNulls()throws Exception{mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("name","변경 기회").toString())).andExpect(status().isOk());assertThat(stored.name()).isEqualTo("변경 기회");verify(mapper,never()).updateNeed(any());verify(audit).insert(any());}
    @Test void staleUpdateIs409WithoutEffects()throws Exception{mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("expectedRevision","old").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"WIN","LOSE","STOP"}) void terminalEditIs409(String status)throws Exception{stored=row(SalesOpportunityInput.save(body(),false),status,null,"WAIT",null);mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().toString())).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"name","customerId","partId","stage","probability","manualProbability","amount","startMonth","needs"}) void missingRequiredCreateField(String field)throws Exception{ObjectNode b=body();b.remove(field);mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor","status","regUserId","projectId","expectedRevision"}) void serverOwnedCreateFieldsRejected(String field)throws Exception{mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(body().put(field,"x").toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void missingRevisionIs400()throws Exception{mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(body().toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void inactiveNewCustomerAndPartAreRejected()throws Exception{when(mapper.customer(anyString())).thenReturn(false);mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(body().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("customerId"));noWrites();}
    @Test void oldInactiveReferencesCanBeRetained()throws Exception{when(mapper.customer(anyString())).thenReturn(false);when(mapper.part(anyString())).thenReturn(false);mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("name","새 이름").toString())).andExpect(status().isOk());}
    @Test void missingDefaultRateIsRowError()throws Exception{ObjectNode b=body();((ObjectNode)b.withArray("needs").get(0)).putNull("expectedRate");sources=new QuoteSources("2026-07",null,sources.standards(),List.of(),List.of());mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("needs[0].expectedRate"));noWrites();}
    @Test void numericThresholdUsesExactFractionAndAppendsOneRealRequest()throws Exception{sources=new QuoteSources("2026-07","2026-06",sources.standards(),sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("8500000"))));mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(body().toString())).andExpect(status().isOk());assertThat(stored.approvalStatus()).isEqualTo("NONE");verify(commands,never()).approval(any());}
    @Test void lowNumericQuoteAppendsRealRateRequest()throws Exception{sources=new QuoteSources("2026-07","2026-06",sources.standards(),sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("9000000"))));mvc.perform(post("/api/sales-opportunities").contentType("application/json").content(body().toString())).andExpect(status().isOk());assertThat(stored.profitRate()).isEqualByComparingTo("10");verify(commands).approval(argThat(e->e.action().equals("REQ")&&e.profitRate().compareTo(new BigDecimal("10"))==0));}
    @Test void auditFailureRollsBackTransaction()throws Exception{when(audit.insert(any())).thenReturn(0);mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("name","변경").toString())).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());}
    @Test void rejectedQuoteEditedAboveThresholdAppendsAutoRelease() throws Exception {
        stored=row(SalesOpportunityInput.save(body(),false),"ING",new BigDecimal("10"),"REJ",null);
        sources=new QuoteSources("2026-07","2026-06",sources.standards(),sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("8000000"))));
        mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("name","수정 견적").toString()))
            .andExpect(status().isOk());
        assertThat(stored.approvalStatus()).isEqualTo("NONE");
        verify(commands).approval(argThat(e->e.action().equals("AUTO_REL") && e.profitRate().compareTo(new BigDecimal("20"))==0));
    }
    @Test void rejectedQuoteEditedBelowThresholdRequestsAgain() throws Exception {
        stored=row(SalesOpportunityInput.save(body(),false),"ING",new BigDecimal("10"),"REJ",null);
        sources=new QuoteSources("2026-07","2026-06",sources.standards(),sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("9000000"))));
        mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().put("name","수정 견적").toString()))
            .andExpect(status().isOk());
        assertThat(stored.approvalStatus()).isEqualTo("WAIT");
        verify(commands).approval(argThat(e->e.action().equals("REQ")));
    }
    @Test void sameContentReevaluatesChangedApprovalThresholdAtUnchangedStoredRate() throws Exception {
        stored=row(SalesOpportunityInput.save(body(),false),"ING",new BigDecimal("20"),"NONE",null);
        var standards=sources.standards().stream().map(s->s.code().equals("QUOTE_APRV_RT")?new Standard(s.code(),new BigDecimal("21")):s).toList();
        sources=new QuoteSources("2026-07","2026-06",standards,sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("8000000"))));
        mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(stored.approvalStatus()).isEqualTo("WAIT");
        verify(commands).approval(argThat(e->e.action().equals("REQ")));
    }
    @Test void sameContentReleasesWaitAtNewExactApprovalBoundary() throws Exception {
        stored=row(SalesOpportunityInput.save(body(),false),"ING",new BigDecimal("10"),"WAIT",null);
        var standards=sources.standards().stream().map(s->s.code().equals("QUOTE_APRV_RT")?new Standard(s.code(),new BigDecimal("10")):s).toList();
        sources=new QuoteSources("2026-07","2026-06",standards,sources.codes(),List.of(new PersonCost("D-a","BE","MD",new BigDecimal("9000000"))));
        mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json").content(edit().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(stored.approvalStatus()).isEqualTo("NONE");
        verify(commands).approval(argThat(e->e.action().equals("AUTO_REL")));
    }
    @Test void createWithDefaultProbabilityUsesCurrentStageStandard() throws Exception {
        var standards=new ArrayList<>(sources.standards());standards.add(new Standard("STEP_PRBL_2",new BigDecimal("30")));
        sources=new QuoteSources(sources.month(),sources.costMonth(),standards,sources.codes(),sources.people());
        mvc.perform(post("/api/sales-opportunities").contentType("application/json")
            .content(body().put("stage","PROP").put("manualProbability",false).put("probability","10").toString()))
            .andExpect(status().isOk());
        assertThat(stored.probability()).isEqualByComparingTo("30");
        assertThat(stored.manualProbability()).isEqualTo("N");
    }
    @Test void stageEditThroughSaveUsesDefaultWithoutOverwritingExplicitManualProbability() throws Exception {
        var standards=new ArrayList<>(sources.standards());standards.add(new Standard("STEP_PRBL_2",new BigDecimal("30")));
        sources=new QuoteSources(sources.month(),sources.costMonth(),standards,sources.codes(),sources.people());
        mvc.perform(put("/api/sales-opportunities/O-0101").contentType("application/json")
            .content(edit().put("stage","PROP").put("manualProbability",false).put("probability","10").toString()))
            .andExpect(status().isOk());
        assertThat(stored.probability()).isEqualByComparingTo("30");
        assertThat(stored.manualProbability()).isEqualTo("N");
    }
    @Test void manualProbabilityEqualToDefaultRetainsManualFlag() throws Exception {
        mvc.perform(post("/api/sales-opportunities").contentType("application/json")
            .content(body().put("stage","PROP").put("manualProbability",true).put("probability","30").toString()))
            .andExpect(status().isOk());
        assertThat(stored.probability()).isEqualByComparingTo("30");
        assertThat(stored.manualProbability()).isEqualTo("Y");
    }
}
