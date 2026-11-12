package com.sicost.assignments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignmentsops.*;
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
import org.junit.jupiter.params.provider.CsvSource;
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

@WebMvcTest(AssignmentController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=AssignmentController.class)
@Import({AssignmentService.class,AssignmentRepository.class,AssignmentActionsService.class,AssignmentActionsRepository.class,
    AssignmentMonthlyService.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AssignmentContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AssignmentActionsMapper mapper;
    @MockBean AssignmentMapper references;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean MasterIdService ids;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Map<String,Source> rows;
    Map<String,List<ValueHistory>> history;
    Map<String,List<StoredMonth>> monthlyRows;
    List<Event> events;
    Project project;
    Developer developer;
    LocalDate day=LocalDate.of(2026,7,1);
    @BeforeEach void setup() {
        rows=new LinkedHashMap<>();history=new HashMap<>();monthlyRows=new HashMap<>();events=new ArrayList<>();
        project=new Project("P-101","프로젝트","ING","MO",day.minusYears(1),day.plusYears(1));
        developer=new Developer("D-101","개발자","M-101","FT","DRCT","PART-1","Y",day.minusYears(1),null);
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(months.cashBase()).thenReturn("202501");when(months.source(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(months.histories(anyString())).thenAnswer(c->history.getOrDefault(c.getArgument(0),List.of()));
        when(months.months(anyString())).thenAnswer(c->monthlyRows.getOrDefault(c.getArgument(0),List.of()));
        when(mapper.project(anyString())).thenAnswer(c->project);when(mapper.developer(anyString())).thenAnswer(c->developer);
        when(mapper.labels(anyString())).thenReturn(new Labels("프로젝트","개발자","M-101","개발"));
        when(mapper.snapshot(anyString())).thenAnswer(c->rows.get(c.getArgument(0))+"/"+history.get(c.getArgument(0))+"/"+monthlyRows.get(c.getArgument(0)));
        when(mapper.matchingIds(any())).thenAnswer(c->List.copyOf(rows.keySet()));
        when(mapper.active(any())).thenReturn(true);when(mapper.price(any())).thenReturn(new BigDecimal("100"));when(mapper.totalMm(any())).thenReturn(BigDecimal.ZERO);when(mapper.mmLimit(anyString())).thenReturn(new BigDecimal("1.00"));
        when(mapper.insert(any())).thenAnswer(c->{Write write=c.getArgument(0);rows.put(write.source().id(),write.source());return 1;});
        when(mapper.event(any())).thenAnswer(c->{Event event=c.getArgument(0);events.add(event);history.computeIfAbsent(event.id(),k->new ArrayList<>()).add(new ValueHistory(event.serial(),event.type(),event.date(),event.mm(),event.price(),"N",null));return 1;});
        when(months.saveMonth(any())).thenAnswer(c->{MonthWrite write=c.getArgument(0);var value=write.value();monthlyRows.computeIfAbsent(write.source().id(),k->new ArrayList<>()).add(new StoredMonth(write.month(),value.inputDays(),new BigDecimal(value.monthlyMm()),value.appliedUnitPrice()==null?null:new BigDecimal(value.appliedUnitPrice()),new BigDecimal(value.salesAmount()),"N"));return 1;});
        when(ids.nextAssignmentId()).thenReturn("A-0102");when(references.labels()).thenReturn(List.of());
    }
    Source source(String id,String price) {return new Source(id,"P-101","D-101","DEV",day,day.plusMonths(1).minusDays(1),new BigDecimal("1.00"),price==null?null:new BigDecimal(price),"NEW",null,null,0,"N","ACT");}
    void existing(String id,String price) {rows.put(id,source(id,price));history.put(id,new ArrayList<>(List.of(new ValueHistory(1,"INPUT",day,BigDecimal.ONE,price==null?null:new BigDecimal(price),"N",null))));}
    ObjectNode create() {return json.createObjectNode().put("year",2026).put("projectId","P-101").put("developerId","D-101").put("jobCode","DEV").put("startDate","2026-07-01").put("endDate","2026-07-31");}
    void postBody(ObjectNode body,int status) throws Exception {mvc.perform(post("/api/assignments").contentType("application/json").content(body.toString())).andExpect(r->assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).event(any());verify(mapper,never()).update(any());verify(months,never()).saveMonth(any());verify(months,never()).project(any());verifyNoInteractions(audit,invalidation);}
    @Test void listIsUnmaskedDecimalReadAndKeepsStoredProtectedMmAndRevision() throws Exception {
        existing("A-101","999999999999999");monthlyRows.put("A-101",List.of(new StoredMonth("202607",31,new BigDecimal("0.50"),BigDecimal.TEN,BigDecimal.TEN,"Y")));
        mvc.perform(get("/api/assignments").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].billingUnitPrice").value("999999999999999")).andExpect(jsonPath("$.data.items[0].yearMm").value("0.50")).andExpect(jsonPath("$.data.items[0].protectedMonths[0]").value("2026-07")).andExpect(jsonPath("$.data.items[0].revision").isNotEmpty()).andExpect(jsonPath("$.data.total").value(1));noWrites();verify(users,atLeastOnce()).currentUser();
    }
    @Test void emptyListContainsNoVirtualRows() throws Exception {mvc.perform(get("/api/assignments").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));noWrites();}
    @Test void detailUsesActualAndContractDeveloperIdsAndDoesNotWrite() throws Exception {
        Source row=source("A-101","100");rows.put(row.id(),new Source(row.id(),row.projectId(),row.developerId(),row.jobCode(),row.startDate(),row.endDate(),row.contractMm(),row.price(),"SUB","D-102",null,0,"N","ACT"));
        mvc.perform(get("/api/assignments/A-101").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.developerId").value("D-101")).andExpect(jsonPath("$.data.contractDeveloperId").value("D-102")).andExpect(jsonPath("$.data.revision").isNotEmpty());noWrites();
    }
    @Test void missingDetailReturns404WithoutWrites() throws Exception {mvc.perform(get("/api/assignments/missing").param("year","2026")).andExpect(status().isNotFound()).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();}
    @ParameterizedTest @CsvSource({"page,0","size,30","sort,warnings","year,text","includeCancelled,yes","actor,attacker","REG_USER_ID,attacker","direction,sideways"})
    void invalidListQueryIs400AndDoesNotWrite(String field,String value) throws Exception {var request=get("/api/assignments");if(!field.equals("year"))request.param("year","2026");mvc.perform(request.param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));noWrites();}
    @Test void qOver100IsNotTruncated() throws Exception {mvc.perform(get("/api/assignments").param("year","2026").param("q","a".repeat(101))).andExpect(status().isBadRequest());noWrites();}
    @Test void literalSearchTrimPassesToOneSharedListAndCountSource() throws Exception {
        existing("A-101","100");mvc.perform(get("/api/assignments").param("year","2026").param("q","  Ab_%  ").param("projectId","P-101").param("jobCode","DEV")).andExpect(jsonPath("$.data.total").value(1));verify(mapper).matchingIds(new Filter(2026,"P-101","DEV",null,"Ab_%",false));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"year","REG_USER_ID","actor"}) void invalidDetailQuery(String field) throws Exception {var request=get("/api/assignments/A-101");if(!field.equals("year"))request.param("year","2026");mvc.perform(request.param(field,"invalid")).andExpect(status().isBadRequest());noWrites();}
    @Test void detailIdLengthIsCheckedBeforeRepository() throws Exception {mvc.perform(get("/api/assignments/"+"a".repeat(21)).param("year","2026")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);noWrites();}
    @Test void numericSortIsNumericIdTiesAscendingAndNullAlwaysLast() throws Exception {
        existing("A-103",null);existing("A-102","100");existing("A-101","20");existing("A-104","20");
        mvc.perform(get("/api/assignments").param("year","2026").param("sort","billingUnitPrice")).andExpect(jsonPath("$.data.items[*].id").value(org.hamcrest.Matchers.contains("A-101","A-104","A-102","A-103")));
        mvc.perform(get("/api/assignments").param("year","2026").param("sort","billingUnitPrice").param("direction","DESC")).andExpect(jsonPath("$.data.items[*].id").value(org.hamcrest.Matchers.contains("A-102","A-101","A-104","A-103")));noWrites();
    }
    @Test void filteredWarningsCountMatchesPageAndThresholdEqualityIsNormal() throws Exception {
        existing("A-101","100");monthlyRows.put("A-101",List.of(new StoredMonth("202607",31,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN,"N")));
        mvc.perform(get("/api/assignments").param("year","2026").param("warning","MM_OVER")).andExpect(jsonPath("$.data.total").value(0));
        when(mapper.totalMm(any())).thenReturn(new BigDecimal("0.01"));mvc.perform(get("/api/assignments").param("year","2026").param("warning","MM_OVER")).andExpect(jsonPath("$.data.total").value(1));noWrites();
    }
    @Test void uncontractedWarningUsesTheApiEnumRatherThanTheDatabaseCodeLength() throws Exception {
        existing("A-101","100");project=new Project(project.id(),project.name(),"PRE",project.contractType(),project.startDate(),project.endDate());
        mvc.perform(get("/api/assignments").param("year","2026").param("warning","UNCONTRACTED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("A-101"));
        noWrites();
    }
    @Test void createCommitsSourceInputMonthsWithServerActorAndReturnedIdReadback() throws Exception {
        mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.assignment.id").value("A-0102")).andExpect(jsonPath("$.data.assignment.contractMm").value("1.00")).andExpect(jsonPath("$.data.assignment.extensionCount").value(0)).andExpect(jsonPath("$.data.assignment.earlyWithdrawal").value("N")).andExpect(jsonPath("$.data.assignment.assignmentStatus").value("ACT"));
        assertThat(events).singleElement().satisfies(e->{assertThat(e.type()).isEqualTo("INPUT");assertThat(e.actor()).isEqualTo("admin");assertThat(e.date()).isEqualTo(day);assertThat(e.mm()).isEqualByComparingTo("1.00");assertThat(e.price()).isEqualByComparingTo("100");assertThat(e.before()).isNull();assertThat(e.after()).hasSizeLessThanOrEqualTo(200);});
        mvc.perform(get("/api/assignments/A-0102").param("year","2026")).andExpect(jsonPath("$.data.yearMm").value("1.00"));verify(audit,atLeastOnce()).insert(any());verify(manager,atLeastOnce()).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"0.001","0","1.01","-1","1e0"}) void invalidMmCreatesNothing(String value) throws Exception {postBody(create().put("contractMm",value),400);noWrites();}
    @ParameterizedTest @ValueSource(strings={"REG_USER_ID","actor","assignmentStatus","extensionCount","expectedRevision","id"}) void serverFieldsCannotBeInjected(String field) throws Exception {postBody(create().put(field,"attacker"),400);noWrites();}
    @Test void invalidRealDateIs400WithoutWrites() throws Exception {postBody(create().put("startDate","2026-02-30"),400);noWrites();}
    @Test void outsideProjectPeriodIsRejectedWithoutClipping() throws Exception {postBody(create().put("endDate","2028-01-01"),400);noWrites();}
    @Test void inactiveJobIsRejected() throws Exception {when(mapper.active(new CodeQuery("JOB","DEV"))).thenReturn(false);postBody(create(),400);noWrites();}
    @Test void paidZeroIsRejected() throws Exception {postBody(create().put("billingUnitPrice","0"),400);noWrites();}
    @Test void pastYearIsReadOnlyBeforeAnyWrites() throws Exception {mvc.perform(post("/api/assignments").contentType("application/json").content(create().put("year",2025).toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void selectedYearMustOverlapButNextYearIsWritable() throws Exception {postBody(create().put("year",2027),400);noWrites();postBody(create().put("year",2027).put("startDate","2027-01-01").put("endDate","2027-01-31"),201);}
    @Test void exactDuplicateIsConflictWithoutBusinessWrites() throws Exception {when(mapper.duplicate(any())).thenReturn(true);mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ASSIGNMENT"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"STOPPED","INDIRECT","RETIRED"}) void ineligibleDeveloperCannotCreate(String kind) throws Exception {developer=new Developer("D-101","개발자","M-101","FT",kind.equals("INDIRECT")?"INDR":"DRCT","PART-1",kind.equals("STOPPED")?"N":"Y",day.minusYears(1),kind.equals("RETIRED")?day:null);postBody(create(),400);noWrites();}
    @Test void joiningAndRetiringBoundsAreNotSilentlyClipped() throws Exception {developer=new Developer("D-101","개발자","M-101","FT","DRCT","PART-1","Y",day.plusDays(1),null);postBody(create(),400);noWrites();}
    @ParameterizedTest @ValueSource(strings={"TK","IN"}) void hiddenUnitPriceIsRejectedAndAbsentStaysNull(String type) throws Exception {
        project=new Project(project.id(),project.name(),"ING",type,project.startDate(),project.endDate());postBody(create().put("billingUnitPrice","100"),400);noWrites();postBody(create(),201);assertThat(rows.get("A-0102").price()).isNull();assertThat(events.get(0).price()).isNull();
    }
    @Test void freePreservesZeroAndRejectsSuppliedHiddenPrice() throws Exception {postBody(create().put("inputType","FREE").put("billingUnitPrice","0"),400);noWrites();postBody(create().put("inputType","FREE"),201);assertThat(rows.get("A-0102").price()).isEqualByComparingTo("0");}
    @Test void protectedWholePeriodOnlyCreatesSourceAndInput() throws Exception {
        when(months.closeStatus("202607")).thenReturn("CLOSE");postBody(create(),201);verify(months,never()).saveMonth(any());assertThat(rows).hasSize(1);assertThat(events).hasSize(1);assertThat(events.get(0).closeCross()).isEqualTo("Y");
    }
    @Test void simpleOverlapWarnsInsteadOfRejectingAndDoesNotTouchOtherSources() throws Exception {
        when(mapper.totalMm(any())).thenReturn(new BigDecimal("0.01"));mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings[?(@.code == 'MM_OVER')]").isNotEmpty());verify(mapper,never()).update(any());
    }
    @Test void subUsesContractIdentityAndNeverActualDefault() throws Exception {
        existing("A-101","200");when(mapper.candidates(any())).thenReturn(List.of(new CandidateSource("D-102","계약상","M-102","A-101",day.plusMonths(1))));postBody(create().put("inputType","SUB").put("contractDeveloperId","D-102"),201);assertThat(rows.get("A-0102").developerId()).isEqualTo("D-101");assertThat(rows.get("A-0102").contractDeveloperId()).isEqualTo("D-102");verify(mapper,never()).price(any());
    }
    @Test void missingSubPriceNeedsManualInput() throws Exception {
        existing("A-101",null);when(mapper.candidates(any())).thenReturn(List.of(new CandidateSource("D-102","계약상","M-102","A-101",day.plusMonths(1))));postBody(create().put("inputType","SUB").put("contractDeveloperId","D-102"),400);noWrites();postBody(create().put("inputType","SUB").put("contractDeveloperId","D-102").put("billingUnitPrice","150"),201);
    }
    @Test void auditFailureRollsBackAndDoesNotExposeSql() throws Exception {doThrow(new IllegalStateException("private SQL password")).when(audit).insert(any());mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);}
}
