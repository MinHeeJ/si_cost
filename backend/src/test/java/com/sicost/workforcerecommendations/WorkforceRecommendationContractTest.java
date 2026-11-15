package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.profitability.*;
import com.sicost.candidates.RecommendationActionDtos;
import com.sicost.candidates.RecommendationActionMapper;
import com.sicost.candidates.RecommendationActionRepository;
import java.math.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkforceRecommendationController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=WorkforceRecommendationController.class)
@Import({WorkforceRecommendationService.class,WorkforceRecommendationRepository.class,RecommendationActionRepository.class,AiWorkforceScoring.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class WorkforceRecommendationContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AiWorkforceScoring scoring;
    @Autowired RecommendationActionRepository actions;
    @MockBean RecommendationActionMapper actionMapper;
    @MockBean WorkforceRecommendationMapper mapper;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean MonthlyCostService costs;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean SettlementSourceLocks locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Row> rows;List<ChangeHistoryEntry> audits;
    Person person(String id,String number){return new Person(id,"추천 인력",number,"BE",null,"MD","FT","Y","DRCT","IDLE",null,null,null,null);}
    @BeforeEach void setup(){
        rows=new ArrayList<>();audits=new ArrayList<>();
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(mapper.target(any())).thenReturn(new Target("O-1",null,"C-1",null,null,null,null,null,"ING"));when(mapper.needExists(any())).thenReturn(true);
        when(mapper.codes()).thenReturn(List.of(new Code("JOB","BE","백엔드",1,null),new Code("GRADE","JR","초급",1,null),new Code("GRADE","MD","중급",2,null),new Code("GRADE","SR","고급",3,null),new Code("GRADE","EX","특급",4,null)));
        List<Standard> standards=new ArrayList<>();int[] weights={25,20,15,10,5,5,10,10};for(int i=0;i<8;i++)standards.add(new Standard("AI_SCORE_"+AiWorkforceScoring.ITEMS.get(i),BigDecimal.valueOf(weights[i])));when(mapper.standards(anyString())).thenReturn(standards);
        when(mapper.people()).thenReturn(List.of(person("D-1","02")));when(mapper.person(anyString())).thenAnswer(c->person(c.getArgument(0),"02"));
        when(mapper.skills()).thenReturn(List.of());when(mapper.areas()).thenReturn(List.of());when(mapper.assignments()).thenReturn(List.of());when(mapper.leaves()).thenReturn(List.of());when(mapper.tentatives()).thenReturn(List.of());when(mapper.exclusions(any())).thenReturn(List.of());
        when(mapper.snapshot()).thenReturn("source-one");when(mapper.nextId()).thenReturn("10");when(monthly.cashBase()).thenReturn(YearMonth.of(2025,1));
        when(costs.getMonthlyCosts(any())).thenReturn(new MonthlyCostDtos.MonthlyCostsData(null,List.of()));
        when(mapper.insert(any())).thenAnswer(c->{rows.add(c.getArgument(0));return 1;});
        when(mapper.rows(anyString())).thenAnswer(c->rows.stream().filter(r->r.rcmdId().equals(c.getArgument(0))).toList());
        when(audit.insert(any())).thenAnswer(c->{audits.add(c.getArgument(0));return 1;});
        when(mapper.metadata(anyString())).thenAnswer(c->audits.stream().filter(a->a.targetKey().equals(c.getArgument(0))).map(a->new AuditValue(a.item(),a.after())).toList());
        when(actionMapper.snapshot(any())).thenReturn("action-source-one");
        when(actionMapper.auditValues(any())).thenAnswer(c->audits.stream().filter(a->a.targetKey().equals(((RecommendationActionDtos.Key)c.getArgument(0)).auditKey())).map(a->new RecommendationActionDtos.AuditValue(a.item(),a.after())).toList());
    }
    ObjectNode body(){return json.createObjectNode().put("targetType","OPPORTUNITY").put("targetId","O-1").put("jobCode","BE").put("startMonth","2026-07").put("requestedGradeCode","MD").put("needSn","1");}
    org.springframework.test.web.servlet.ResultActions create(ObjectNode body) throws Exception{return mvc.perform(post("/api/workforce-recommendations").contentType("application/json").content(body.toString()));}
    void noWrites(){verify(mapper,never()).insert(any());verify(audit,never()).insert(any());}
    @Test void publicGenerationUsesExactScoresServerActorAndOriginalMetadata() throws Exception {
        create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.rcmdId").value("10")).andExpect(jsonPath("$.data.candidates[0].scoreDetail.v").value(1)).andExpect(jsonPath("$.data.candidates[0].scoreDetail.b.length()").value(8)).andExpect(jsonPath("$.data.executedBy").value("admin"));
        assertThat(rows).hasSize(1);assertThat(audits).hasSize(3);assertThat(rows.get(0).executedAt()).isEqualTo(LocalDateTime.of(2026,7,1,0,0));
        var d=json.readTree(rows.get(0).scoreDetail());for(int i=0;i<8;i++)assertThat(d.get("b").get(i).decimalValue()).isEqualByComparingTo(d.get("s").get(i).decimalValue());assertThat(d.get("c").isNull()).isTrue();
        verify(costs).getMonthlyCosts(new MonthlyCostInput.Query(2026,7,null));
    }
    @Test void generatedRevisionsAreAcceptedByTheSelectionBoundaryUntilSourcesChange() throws Exception {
        var response=create(body()).andExpect(status().isOk()).andReturn();
        var candidate=json.readTree(response.getResponse().getContentAsString()).path("data").path("candidates").get(0);
        Row row=rows.get(0);
        var actionRow=new RecommendationActionDtos.Row(row.rcmdId(),row.rank(),row.recommendationType(),row.opportunityId(),row.needSn(),row.projectId(),row.jobCode(),row.startMonth(),row.developerId(),row.scoreDetail(),null,null,null,null,row.executedAt());
        var requested=new RecommendationActionDtos.Revisions(candidate.path("revision").asText(),candidate.path("targetRevision").asText(),candidate.path("candidateRevision").asText());
        assertThatCode(()->actions.requireCurrent(actionRow,requested)).doesNotThrowAnyException();
        when(actionMapper.snapshot(any())).thenReturn("action-source-two");
        assertThatThrownBy(()->actions.requireCurrent(actionRow,requested)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
    @Test void oldSnapshotDoesNotReadCurrentStandardsCostsOrDeletedNeed() throws Exception {
        create(body()).andExpect(status().isOk());String stored=rows.get(0).scoreDetail();clearInvocations(mapper,costs,audit);
        when(mapper.needExists(any())).thenReturn(false);when(mapper.standards(anyString())).thenReturn(List.of());
        mvc.perform(get("/api/workforce-recommendations/10")).andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCondition.needSn").value("1"));
        assertThat(rows.get(0).scoreDetail()).isEqualTo(stored);verify(mapper,never()).standards(anyString());verify(mapper,never()).target(any());verifyNoInteractions(costs);verify(audit,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"0","1000000000000","-1","abc"}) void invalidRoundId(String id) throws Exception {mvc.perform(get("/api/workforce-recommendations/"+id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("rcmdId"));noWrites();}
    @Test void absentRound404() throws Exception{mvc.perform(get("/api/workforce-recommendations/11")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"targetType","targetId","jobCode","startMonth","requestedGradeCode","needSn"}) void requiredFields(String field) throws Exception{var b=body();b.remove(field);create(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"REG_USER_ID","ACT_USER_ID","actor","executedAt","score","totalScore","scoreDetail","sourceScreenId"}) void rejectsServerFields(String field) throws Exception {create(body().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","1000","01"}) void needMustBePositiveString(String need) throws Exception{create(body().put("needSn",need)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("needSn"));noWrites();}
    @Test void numericNeedNotCoerced() throws Exception{create(body().put("needSn",1)).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-13","2026-02-30","0000-01","2024-12"}) void calendarAndBase(String month) throws Exception{create(body().put("startMonth",month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("startMonth"));noWrites();}
    @Test void invalidGrade() throws Exception{create(body().put("requestedGradeCode","XX")).andExpect(status().isBadRequest());noWrites();}
    @Test void unknownJob() throws Exception{create(body().put("jobCode","XX")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("jobCode"));noWrites();}
    @Test void missingNeed() throws Exception{when(mapper.needExists(any())).thenReturn(false);create(body()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("needSn"));noWrites();}
    @Test void missingTarget() throws Exception{when(mapper.target(any())).thenReturn(null);create(body()).andExpect(status().isNotFound());noWrites();}
    @Test void invalidState() throws Exception{when(mapper.target(any())).thenReturn(new Target("O-1",null,null,null,null,null,null,null,"LOSE"));create(body()).andExpect(status().isConflict());noWrites();}
    @Test void unknownQueryBothOperations() throws Exception{mvc.perform(get("/api/workforce-recommendations/10?actor=x")).andExpect(status().isBadRequest());mvc.perform(post("/api/workforce-recommendations?score=1").contentType("application/json").content(body().toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void emptyDoesNotAllocateOrInsert() throws Exception{when(mapper.people()).thenReturn(List.of());create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.rcmdId").isEmpty()).andExpect(jsonPath("$.data.candidates").isEmpty());noWrites();verify(mapper,never()).nextId();}
    @Test void overlappingExclusionsCountPeopleOnce() throws Exception{when(mapper.people()).thenReturn(List.of(new Person("D-1","인력","02","BE",null,"MD","FL","Y","DRCT","RETIRE",null,LocalDate.of(2030,1,1),null,null)));when(mapper.target(any())).thenReturn(new Target("O-1","DP",null,null,null,null,null,null,"ING"));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.exclusions.excludedUniqueCount").value(1)).andExpect(jsonPath("$.data.exclusions.byReason.length()").value(2)).andExpect(jsonPath("$.data.candidates").isEmpty());noWrites();}
    @Test void currentLeaveIsExcludedEvenWhenRequestedMonthIsAfterLeaveEnds() throws Exception {
        when(mapper.leaves()).thenReturn(List.of(new Leave("D-1",LocalDate.of(2026,7,1),LocalDate.of(2026,7,2))));
        create(body().put("startMonth","2026-08")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidates").isEmpty())
            .andExpect(jsonPath("$.data.exclusions.byReason[0].reason").value("휴직"));
        noWrites();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2026-06-01,2026-07-01,0", "2026-07-01,2026-07-01,0", "2026-06-01,2026-06-30,1", "2026-07-02,2026-07-31,1"})
    void currentLeaveIncludesBothEndsButDoesNotExcludeExpiredOrFutureOnlyPeriods(String begin,String end,int count) throws Exception {
        when(mapper.leaves()).thenReturn(List.of(new Leave("D-1",LocalDate.parse(begin),LocalDate.parse(end))));
        create(body().put("startMonth","2026-08")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.candidates.length()").value(count));
        if(count==0)noWrites();
    }
    @Test void leaveOneDayAndOpenEndAreExcluded() throws Exception{when(mapper.leaves()).thenReturn(List.of(new Leave("D-1",LocalDate.of(2026,7,31),null)));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.exclusions.byReason[0].reason").value("휴직"));noWrites();}
    @Test void sameOpportunityActExcluded() throws Exception{when(mapper.tentatives()).thenReturn(List.of(new Tentative("D-1","1","O-1","기회","1","2026-07")));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty());noWrites();}
    @Test void otherOpportunityActStillCandidate() throws Exception{when(mapper.tentatives()).thenReturn(List.of(new Tentative("D-1","1","O-2","다른 기회","1","2026-07")));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates[0].otherTentativeAssignments[0].opportunityId").value("O-2"));}
    @Test void userExclusionIgnoresNeedMonthGrade() throws Exception{when(mapper.exclusions(any())).thenReturn(List.of(new Excluded("D-1")));create(body().put("needSn","2").put("startMonth","2026-09").put("requestedGradeCode","SR")).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty());noWrites();}
    @Test void missingWeight409() throws Exception{when(mapper.standards(anyString())).thenReturn(List.of());create(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STANDARD"));noWrites();}
    @ParameterizedTest @ValueSource(ints={99,101}) void invalidTotalWeight(int total) throws Exception{var w=new ArrayList<Standard>();for(int i=0;i<8;i++)w.add(new Standard("AI_SCORE_"+AiWorkforceScoring.ITEMS.get(i),BigDecimal.valueOf(i==0?total:0)));when(mapper.standards(anyString())).thenReturn(w);create(body()).andExpect(status().isConflict());noWrites();}
    @Test void idExhaustionPreventsEveryRank() throws Exception{when(mapper.nextId()).thenReturn("1000000000000");create(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));noWrites();}
    @Test void thirdInsertFailureRollsBack() throws Exception{when(mapper.people()).thenReturn(List.of(person("D-1","01"),person("D-2","02"),person("D-3","03")));when(mapper.insert(any())).thenAnswer(c->{Row r=c.getArgument(0);if(r.rank()==3)throw new IllegalStateException("private SQL");return 1;});create(body()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());}
    @Test void mandatoryAuditZeroRollsBack() throws Exception{when(audit.insert(any())).thenReturn(0);create(body()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());}
    @Test void tiesUseManagementStringThenDeveloperAndTopFive() throws Exception{when(mapper.people()).thenReturn(List.of(person("D-9","2"),person("D-2","10"),person("D-1","10"),person("D-3","3"),person("D-4","4"),person("D-5","5")));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates.length()").value(5)).andExpect(jsonPath("$.data.candidates[0].developer.developerId").value("D-1")).andExpect(jsonPath("$.data.candidates[1].developer.developerId").value("D-2")).andExpect(jsonPath("$.data.candidates[2].developer.developerId").value("D-9"));}
    void actualMm(String mm) {
        var source=new AssignmentMonthlyService.Source("A-1","P-1","D-1","BE",LocalDate.of(2026,7,20),LocalDate.of(2026,8,31),BigDecimal.ONE,null,"NEW",null,null,0,"N","ACT");
        when(mapper.assignments()).thenReturn(List.of(new Assignment("A-1","D-1",null,null,"ING","MO",false)));
        when(monthly.required("A-1")).thenReturn(source);
        AssignmentMonthlyMapper monthMapper=mock(AssignmentMonthlyMapper.class);when(monthly.mapper()).thenReturn(monthMapper);when(monthMapper.histories("A-1")).thenReturn(List.of());
        when(monthly.preview(any(),anyList(),anyString(),anyString())).thenReturn(List.of(new AssignmentMonthlyService.Month("2026-07","P-1","D-1",31,"1.00",mm,null,"0","Y")));
    }
    @Test void futureConfirmedMmOneExcludedUsingCommonProtectedProjection() throws Exception{actualMm("1.00");create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty()).andExpect(jsonPath("$.data.exclusions.byReason[0].reason").value("실제 투입 MM 포화"));noWrites();}
    @Test void actualMmHalfRemainsEligible() throws Exception{actualMm("0.50");create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates.length()").value(1));}
    @Test void projectStartIsRaisedWithoutChangingOtherConditions() throws Exception{when(mapper.target(any())).thenReturn(new Target("P-1","MO",null,null,null,null,LocalDate.of(2026,8,20),LocalDate.of(2026,12,31),"ING"));var b=body().put("targetType","PROJECT").put("targetId","P-1");b.remove("needSn");create(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCondition.startMonth").value("2026-08"));assertThat(rows.get(0).projectId()).isEqualTo("P-1");assertThat(rows.get(0).opportunityId()).isNull();assertThat(rows.get(0).needSn()).isNull();}
    @Test void afterContractEndRejected() throws Exception{when(mapper.target(any())).thenReturn(new Target("P-1","MO",null,null,null,null,LocalDate.of(2026,1,1),LocalDate.of(2026,6,30),"ING"));var b=body().put("targetType","PROJECT").put("targetId","P-1");b.remove("needSn");create(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("startMonth"));noWrites();}
    @Test void stoppedAndIndirectPeopleNeverBecomeScoreCards() throws Exception{when(mapper.people()).thenReturn(List.of(new Person("D-1","인력","01","BE",null,"MD","FT","N","INDRCT","WORK",null,null,null,null)));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty());noWrites();}
    @Test void newHireAfterStartingMonthExcluded() throws Exception{when(mapper.people()).thenReturn(List.of(new Person("D-1","인력","01","BE",null,"MD","FT","Y","DRCT","WORK",LocalDate.of(2026,8,1),null,null,null)));create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.exclusions.byReason[0].reason").value("시작월 이후 입사"));noWrites();}
}
