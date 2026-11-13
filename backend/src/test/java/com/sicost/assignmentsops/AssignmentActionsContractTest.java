package com.sicost.assignmentsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssignmentActionsController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=AssignmentActionsController.class)
@Import({AssignmentActionsService.class,AssignmentActionsRepository.class,AssignmentMonthlyService.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AssignmentActionsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired AssignmentActionsService service;
    @Autowired AssignmentMonthlyService monthly;
    @MockBean AssignmentActionsMapper mapper;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean MasterIdService ids;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Map<String,Source> rows;
    List<Event> events;
    List<ValueHistory> histories;
    Project project;
    LocalDate day=LocalDate.of(2026,7,1);
    @BeforeEach void setup() {
        rows=new HashMap<>();rows.put("A-101",source("A-101","ACT"));events=new ArrayList<>();histories=new ArrayList<>();histories.add(new ValueHistory(1,"INPUT",day,new BigDecimal("1.00"),new BigDecimal("100"),"N",null));
        project=new Project("P-101","프로젝트","ING","MO",day.minusMonths(1),day.plusYears(1));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(months.cashBase()).thenReturn("202501");when(months.source(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(months.histories(anyString())).thenAnswer(c->c.getArgument(0).equals("A-101")?List.copyOf(histories):List.of());
        when(months.months(anyString())).thenReturn(List.of());
        when(mapper.project(anyString())).thenAnswer(c->project);
        when(mapper.developer(anyString())).thenAnswer(c->new Developer(c.getArgument(0),"인력","M-101","FT","DRCT","PART-1","Y",day.minusYears(1),null));
        when(mapper.labels(anyString())).thenReturn(new Labels("프로젝트","인력","M-101","개발"));
        when(mapper.snapshot(anyString())).thenAnswer(c->rows.get(c.getArgument(0)).toString()+events.toString());
        when(mapper.matchingIds(any())).thenAnswer(c->rows.keySet().stream().sorted().toList());
        when(mapper.active(any())).thenReturn(true);when(mapper.mmLimit(anyString())).thenReturn(new BigDecimal("1.00"));when(mapper.totalMm(any())).thenReturn(BigDecimal.ZERO);
        when(mapper.price(any())).thenReturn(new BigDecimal("100"));when(mapper.overlaps(any())).thenReturn(List.of());when(mapper.candidates(any())).thenReturn(List.of());
        when(mapper.serial(anyString())).thenAnswer(c->1+events.size());
        when(mapper.update(any())).thenAnswer(c->{Write w=c.getArgument(0);rows.put(w.source().id(),w.source());return 1;});
        when(mapper.insert(any())).thenAnswer(c->{Write w=c.getArgument(0);rows.put(w.source().id(),w.source());return 1;});
        when(mapper.event(any())).thenAnswer(c->{Event e=c.getArgument(0);events.add(e);if(e.id().equals("A-101"))histories.add(new ValueHistory(e.serial(),e.type(),e.date(),e.mm(),e.price(),"N",null));return 1;});
        when(months.saveMonth(any())).thenReturn(1);when(ids.nextAssignmentId()).thenReturn("A-0102");
    }
    Source source(String id,String status) {return new Source(id,"P-101","D-101","DEV",day,day.plusMonths(2).minusDays(1),new BigDecimal("1.00"),new BigDecimal("100"),"NEW",null,"H",0,"N",status);}
    String revision(String id) {return revisions.revision(mapper.snapshot(id));}
    ObjectNode change(String type) {return json.createObjectNode().put("year",2026).put("expectedRevision",revision("A-101")).put("type",type);}
    ObjectNode create() {return json.createObjectNode().put("year",2026).put("projectId","P-101").put("developerId","D-102").put("jobCode","DEV").put("startDate","2026-07-01").put("endDate","2026-08-31").put("contractMm","1.00");}
    ObjectNode bulk(String type) {ObjectNode n=json.createObjectNode().put("year",2026).put("type",type);n.putArray("items").addObject().put("id","A-101").put("expectedRevision",revision("A-101"));return n;}
    void postBody(String path,ObjectNode n,int status) throws Exception {mvc.perform(post(path).contentType("application/json").content(n.toString())).andExpect(r->assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status));}
    void noWrites() {verify(mapper,never()).update(any());verify(mapper,never()).insert(any());verify(mapper,never()).event(any());verify(months,never()).saveMonth(any());verifyNoInteractions(audit,invalidation);}
    @Test void monthsReturnsStoredValuesInDecimalStringsWithoutRegenerating() throws Exception {
        when(months.months("A-101")).thenReturn(List.of(new StoredMonth("202607",31,new BigDecimal("1.00"),new BigDecimal("100"),new BigDecimal("100"),"Y")));
        mvc.perform(get("/api/assignments/A-101/months").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].monthlyMm").value("1.00")).andExpect(jsonPath("$.data.items[0].fixed").value("Y")).andExpect(jsonPath("$.data.total").value(1));noWrites();
    }
    @Test void monthsEmptyIsEmptyAndMissingIs404() throws Exception {
        mvc.perform(get("/api/assignments/A-101/months").param("year","2026")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/assignments/missing/months").param("year","2026")).andExpect(status().isNotFound());noWrites();
    }
    @Test void candidatesUseLastAssignmentEndMonthNotCurrentDeveloperPrice() throws Exception {
        when(mapper.candidates(any())).thenReturn(List.of(new CandidateSource("D-101","인력","M-101","A-101",day.plusMonths(2).minusDays(1))));
        histories.add(new ValueHistory(2,"UPRC",day.plusMonths(1),null,new BigDecimal("200"),"Y",null));
        mvc.perform(get("/api/assignments/substitution-candidates").param("projectId","P-101").param("developerId","D-102")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].defaultUnitPrice").value("200"));verify(mapper,never()).price(any());noWrites();
    }
    @Test void emptyCandidatesAndInvalidQuery() throws Exception {
        mvc.perform(get("/api/assignments/substitution-candidates").param("projectId","P-101").param("developerId","D-102")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/assignments/substitution-candidates").param("developerId","D-102")).andExpect(status().isBadRequest());noWrites();
    }
    @Test void previewHasNoWritesAndIncludesEveryProjectsMmAndPartWarning() throws Exception {
        when(mapper.totalMm(any())).thenReturn(new BigDecimal("0.01"));when(mapper.projectPart(any())).thenReturn("PART-OTHER");
        mvc.perform(post("/api/assignments/preview").contentType("application/json").content(create().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].monthlyMm").value("1.00")).andExpect(jsonPath("$.data.warnings[?(@.code == 'PART_DIFFERENCE')]").isNotEmpty()).andExpect(jsonPath("$.data.warnings[?(@.code == 'MM_OVER')]").isNotEmpty());noWrites();
    }
    @Test void previewRejectsMmPrecisionAndContractOutsideWithoutWrites() throws Exception {
        postBody("/api/assignments/preview",create().put("contractMm","0.123"),400);postBody("/api/assignments/preview",create().put("endDate","2029-01-01"),400);noWrites();
    }
    @Test void rateCurrentMonthUsesFirstDayAndServerFlagsOnlyTypedMm() throws Exception {
        postBody("/api/assignments/A-101/changes",change("RATE").put("applyMonth","2026-07").put("newContractMm","0.50"),200);
        assertThat(events).singleElement().satisfies(e->{assertThat(e.mm()).isEqualByComparingTo("0.50");assertThat(e.price()).isNull();assertThat(e.retroactive()).isEqualTo("N");assertThat(e.actor()).isEqualTo("admin");});verify(months,atLeastOnce()).saveMonth(argThat(w->w.value().monthlyMm().equals("0.50")));
    }
    @ParameterizedTest @ValueSource(strings={"0.01","1.00"}) void rateBoundaries(String value) throws Exception {
        if(value.equals("1.00"))histories.set(0,new ValueHistory(1,"INPUT",day,new BigDecimal("0.50"),new BigDecimal("100"),"N",null));
        postBody("/api/assignments/A-101/changes",change("RATE").put("applyMonth","2026-07").put("newContractMm",value),200);
    }
    @Test void sameEffectiveValueHasNoHistory() throws Exception {
        mvc.perform(post("/api/assignments/A-101/changes").contentType("application/json").content(change("UPRC").put("applyMonth","2026-07").put("newUnitPrice","100").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SAME_VALUE"));noWrites();
    }
    @Test void extensionRequiresLaterEndAndLimits999() throws Exception {
        postBody("/api/assignments/A-101/changes",change("EXT").put("newEndDate","2026-08-31"),400);
        Source r=rows.get("A-101");rows.put(r.id(),r.changed(r.endDate(),r.contractMm(),r.price(),r.inputType(),r.extensionPossibility(),999,r.earlyWithdrawal(),r.status()));
        postBody("/api/assignments/A-101/changes",change("EXT").put("newEndDate","2026-09-30"),409);noWrites();
    }
    @Test void extensionUpdatesCountAndPreservesFixedMonth() throws Exception {
        when(months.months("A-101")).thenReturn(List.of(new StoredMonth("202607",31,BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("100"),"Y")));
        postBody("/api/assignments/A-101/changes",change("EXT").put("newEndDate","2026-09-30").put("reasonCode","ETC"),200);assertThat(rows.get("A-101").extensionCount()).isEqualTo(1);verify(months,never()).saveMonth(argThat(w->w.month().equals("202607")));
    }
    @Test void earlySameEndFirstChangesNToYThenNoChange() throws Exception {
        postBody("/api/assignments/A-101/changes",change("EARLY").put("withdrawalDate","2026-08-31").put("reasonCode","ETC"),200);
        clearInvocations(mapper,months,audit,invalidation);postBody("/api/assignments/A-101/changes",change("EARLY").put("withdrawalDate","2026-08-31").put("reasonCode","ETC"),200);noWrites();
    }
    @Test void possibilityCanReturnToNullImmediatelyEvenFutureDate() throws Exception {
        postBody("/api/assignments/A-101/changes",change("EXTP").put("applyDate","2026-08-01").putNull("extensionPossibility"),200);assertThat(rows.get("A-101").extensionPossibility()).isNull();
        postBody("/api/assignments/A-101/changes",change("EXTP").put("applyDate","2026-08-01").putNull("extensionPossibility"),400);
    }
    @Test void kindPreservesPeriodAndDisallowsSubTransition() throws Exception {
        postBody("/api/assignments/A-101/changes",change("KIND").put("applyDate","2026-07-01").put("inputType","ADD"),200);assertThat(rows.get("A-101").endDate()).isEqualTo(day.plusMonths(2).minusDays(1));
        postBody("/api/assignments/A-101/changes",change("KIND").put("applyDate","2026-07-01").put("inputType","SUB"),400);
    }
    @Test void staleAndCancelledAndLoseAreRejectedWithoutMutation() throws Exception {
        postBody("/api/assignments/A-101/changes",change("EXTP").put("applyDate","2026-07-01").putNull("extensionPossibility").put("expectedRevision","stale"),409);
        rows.put("A-101",source("A-101","CNCL"));postBody("/api/assignments/A-101/changes",change("EXTP").put("applyDate","2026-07-01").putNull("extensionPossibility"),409);
        rows.put("A-101",source("A-101","ACT"));project=new Project(project.id(),project.name(),"LOSE","MO",project.startDate(),project.endDate());postBody("/api/assignments/A-101/changes",change("EXT").put("newEndDate","2026-09-30"),409);noWrites();
    }
    @Test void selectionFixesRevisionsAndExcludesActualIdWithoutWrites() throws Exception {
        rows.put("A-102",source("A-102","ACT"));ObjectNode n=json.createObjectNode().put("year",2026).put("scope","ALL");n.putObject("filters").put("year",2026);n.putArray("excludedIds").add("A-101");
        mvc.perform(post("/api/assignments/selection").contentType("application/json").content(n.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("A-102"));noWrites();
    }
    @Test void selectionAcceptsTheSameUncontractedWarningAsTheList() throws Exception {
        project=new Project(project.id(),project.name(),"PRE",project.contractType(),project.startDate(),project.endDate());
        ObjectNode n=json.createObjectNode().put("year",2026).put("scope","ALL");n.putObject("filters").put("year",2026).put("warning","UNCONTRACTED");
        mvc.perform(post("/api/assignments/selection").contentType("application/json").content(n.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("A-101"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"202612","202701"}) void selectionMmWarningIsLimitedToTheListYear(String month) throws Exception {
        Source row=rows.get("A-101");rows.put(row.id(),row.changed(LocalDate.of(2027,1,31),row.contractMm(),row.price(),row.inputType(),row.extensionPossibility(),row.extensionCount(),row.earlyWithdrawal(),row.status()));
        when(months.months(row.id())).thenReturn(List.of(new StoredMonth(month,31,BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("100"),"N")));
        when(mapper.totalMm(any())).thenReturn(new BigDecimal("0.01"));
        ObjectNode n=json.createObjectNode().put("year",2026).put("scope","ALL");n.putObject("filters").put("year",2026).put("warning","MM_OVER");
        mvc.perform(post("/api/assignments/selection").contentType("application/json").content(n.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(month.startsWith("2026")?1:0));
        noWrites();
    }
    @Test void selectionRejectsHistoricalYearAndUnknownId() throws Exception {
        ObjectNode n=json.createObjectNode().put("year",2025).put("scope","ALL");n.putObject("filters").put("year",2025);postBody("/api/assignments/selection",n,409);
        n.put("year",2026);n.withObject("filters").put("year",2026);n.putArray("ids").add("missing");postBody("/api/assignments/selection",n,400);noWrites();
    }
    @Test void bulkPartitionsStaleAndSuccessAndKeepsSuccessTransaction() throws Exception {
        rows.put("A-102",source("A-102","ACT"));ObjectNode n=bulk("EXTP").putNull("extensionPossibility");n.withArray("items").addObject().put("id","A-102").put("expectedRevision","stale");
        mvc.perform(post("/api/assignments/bulk").contentType("application/json").content(n.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.requested").value(2)).andExpect(jsonPath("$.data.applied").value(1)).andExpect(jsonPath("$.data.rejected").value(1));assertThat(rows.get("A-101").extensionPossibility()).isNull();assertThat(rows.get("A-102").extensionPossibility()).isEqualTo("H");verify(manager,atLeastOnce()).commit(any());verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void cancellationBeforeStartAndReplayIsNoChange() throws Exception {
        postBody("/api/assignments/bulk",bulk("CNCL"),200);assertThat(rows.get("A-101").status()).isEqualTo("CNCL");int count=events.size();postBody("/api/assignments/bulk",bulk("CNCL"),200);assertThat(events).hasSize(count);
    }
    @Test void cancellationProtectedRequiresReasonAndPreservesRows() throws Exception {
        when(months.months("A-101")).thenReturn(List.of(new StoredMonth("202607",31,BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("100"),"Y")));
        postBody("/api/assignments/bulk",bulk("CNCL"),200);assertThat(rows.get("A-101").status()).isEqualTo("ACT");postBody("/api/assignments/bulk",bulk("CNCL").put("reasonCode","ETC"),200);verify(months,never()).deleteMonth(anyString(),anyString());verify(months,never()).saveMonth(any());
    }
    @Test void duplicateBulkIdsAreBoundaryError() throws Exception {
        ObjectNode n=bulk("CNCL");n.withArray("items").addObject().put("id","A-101").put("expectedRevision","different");postBody("/api/assignments/bulk",n,400);noWrites();
    }
    @Test void subPreviewAndOnlySaveNeverWithdrawExisting() throws Exception {
        when(mapper.candidates(any())).thenReturn(List.of(new CandidateSource("D-101","계약상","M-101","A-101",day.plusMonths(2))));ObjectNode c=create().put("inputType","SUB").put("contractDeveloperId","D-101");
        ObjectNode body=json.createObjectNode().set("assignment",c);String response=mvc.perform(post("/api/assignments/substitution-preview").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isEmpty()).andReturn().getResponse().getContentAsString();
        body.put("mode","ONLY").put("previewToken",json.readTree(response).path("data").path("previewToken").asText()).putArray("withdrawals");
        mvc.perform(post("/api/assignments/substitutions").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/assignments/substitutions",200))
            .andExpect(jsonPath("$.data.assignment.id").value("A-0102"));
        assertThat(rows.get("A-101").earlyWithdrawal()).isEqualTo("N");assertThat(rows.get("A-0102").inputType()).isEqualTo("SUB");
        verify(mapper).insert(argThat(w->w.source().id().equals("A-0102")));
        verify(manager,atLeastOnce()).commit(any());clearInvocations(mapper,months,audit,invalidation);
        mvc.perform(post("/api/assignments/substitutions").contentType("application/json").content(body.put("REG_USER_ID","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/assignments/substitutions",400));noWrites();
    }
    @Test void subSamePersonAndTogetherEmptyAreInvalid() throws Exception {
        ObjectNode c=create().put("inputType","SUB").put("contractDeveloperId","D-102");ObjectNode n=json.createObjectNode().set("assignment",c);postBody("/api/assignments/substitution-preview",n,400);n.put("mode","TOGETHER").put("previewToken","token").putArray("withdrawals");postBody("/api/assignments/substitutions",n,400);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"/api/assignments/preview","/api/assignments/selection","/api/assignments/bulk","/api/assignments/substitution-preview","/api/assignments/substitutions","/api/assignments/A-101/changes"})
    void serverFieldInjectionRejectedAtEveryCommand(String path) throws Exception {postBody(path,json.createObjectNode().put("REG_USER_ID","attacker"),400);noWrites();}
    @Test void unexpectedQueryAndInvalidSizeRejected() throws Exception {
        for(String path:List.of("/api/assignments/A-101/months","/api/assignments/substitution-candidates"))mvc.perform(get(path).param("size","30").param("year","2026")).andExpect(status().isBadRequest());noWrites();
    }
    @Test void mandatoryAuditFailureIsSanitizedAndRollsBack() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(audit).insert(any());
        mvc.perform(post("/api/assignments/A-101/changes").contentType("application/json").content(change("RATE").put("applyMonth","2026-07").put("newContractMm","0.50").toString())).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);
    }
}
