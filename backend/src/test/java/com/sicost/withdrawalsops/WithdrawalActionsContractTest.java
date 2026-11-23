package com.sicost.withdrawalsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.candidates.RecommendationActionRepository;
import com.sicost.candidates.RecommendationActionDtos.Revisions;
import com.sicost.forecast.SalesForecastDtos;
import com.sicost.workforcerecommendations.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(WithdrawalActionsController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=WithdrawalActionsController.class)
@Import({AssignmentActionsService.class,AssignmentActionsRepository.class,AssignmentMonthlyService.class,RevisionService.class,
    ChangeHistoryWriter.class,AccessLogWriter.class,ExcelExportService.class,WithdrawalService.class,WithdrawalExportService.class,NextPositionRecommendationService.class,
    WorkforceRecommendationRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class WithdrawalActionsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean AssignmentActionsMapper assignments;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AccessLogMapper access;
    @MockBean CurrentUserProvider users;
    @MockBean AggregateLockMapper locks;
    @MockBean SettlementSourceLocks sourceLocks;
    @MockBean CommitInvalidation invalidation;
    @MockBean MasterIdService ids;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    @MockBean WithdrawalRepository withdrawals;
    @MockBean NextPositionRepository next;
    @MockBean WorkforceRecommendationMapper recommendations;
    @MockBean RecommendationActionRepository actions;
    @MockBean WorkforceForecastService forecasts;
    @Autowired ExcelExportService excelService;
    @MockBean com.sicost.common.excel.ExportLabels exportLabels;
    AssignmentMonthlyService.Source assignment;
    WithdrawalDtos.Source person;
    List<AssignmentActionsDtos.Event> events;
    List<WithdrawalDtos.Source> sources;
    List<WorkforceRecommendationDtos.Row> written;
    LocalDate day=LocalDate.of(2026,11,15);
    @BeforeEach void setup(){
        com.sicost.common.excel.ExportTestLookups.configure(exportLabels);
        com.sicost.common.excel.ExportTestLookups.configure(excelService);
        events=new ArrayList<>();sources=new ArrayList<>();written=new ArrayList<>();
        assignment=new AssignmentMonthlyService.Source("A-001","P-001","D-001","BE",day.minusMonths(2),LocalDate.of(2026,11,20),BigDecimal.ONE,new BigDecimal("100"),"NEW",null,"H",2,"N","ACT");
        person=source(assignment.id(),assignment.endDate(),"M");
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(access.insert(any())).thenReturn(1);
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(months.cashBase()).thenReturn("202501");when(months.source("A-001")).thenAnswer(c->assignment);when(months.months(anyString())).thenReturn(List.of(new AssignmentMonthlyService.StoredMonth("202610",31,BigDecimal.ONE,new BigDecimal("100"),new BigDecimal("100"),"Y")));
        when(months.closeStatus(anyString())).thenReturn("CLOSE");
        when(assignments.project(anyString())).thenReturn(new AssignmentActionsDtos.Project("P-001","원프로젝트","ING","MO",day.minusYears(1),day.plusYears(1)));
        when(assignments.matchingIds(any())).thenReturn(List.of("A-001"));when(assignments.active(any())).thenReturn(true);
        when(assignments.serial(anyString())).thenAnswer(c->events.size());
        when(assignments.snapshot(anyString())).thenAnswer(c->assignment.toString()+events.toString());
        when(assignments.updateExtension(any())).thenAnswer(c->{assignment=((AssignmentActionsDtos.Write)c.getArgument(0)).source();return 1;});
        when(assignments.event(any())).thenAnswer(c->{events.add(c.getArgument(0));return 1;});when(audit.insert(any())).thenReturn(1);
        when(withdrawals.sources(any())).thenAnswer(c->List.copyOf(sources));when(withdrawals.activeJob(anyString())).thenReturn(true);
        when(withdrawals.revision(anyString())).thenReturn("source-revision");
        when(withdrawals.next(any())).thenReturn(List.of());when(withdrawals.leaves(anyString())).thenReturn(List.of());when(withdrawals.positions(anyString())).thenReturn(List.of());
        when(next.cashBase()).thenReturn(YearMonth.of(2025,1));when(next.lock(anyString())).thenAnswer(c->person);
        when(next.leaves(anyString())).thenReturn(List.of());when(next.next(any())).thenReturn(List.of());when(next.positions(anyString())).thenReturn(List.of(position(1,"2026-12","BE",null,"MD","60","C-001",1,"MO")));
        when(next.nextId()).thenReturn("123");doAnswer(c->{written.add(c.getArgument(0));return null;}).when(next).insert(any(),anyString());
        when(actions.current(any())).thenReturn(new Revisions("r-card","r-target","r-person"));
        when(recommendations.nextCard(any())).thenReturn(new WorkforceRecommendationDtos.NextCard("다음 기회","LEAD","C-001","원고객",false));
        when(recommendations.metadata(anyString())).thenReturn(List.of());when(recommendations.tentatives()).thenReturn(List.of());
        when(forecasts.forecast(any())).thenReturn(new SalesForecastDtos.Workforce("2026-12",6,true,day.toString(),"2026-11",List.of(),List.of(),List.of(),List.of(),null,null,null,null));
    }
    WithdrawalDtos.Source source(String id,LocalDate date,String extension){return new WithdrawalDtos.Source(id,"D-001","개발자","001","BE","FE","MD","FT","P-001","원프로젝트","BE",date,2,true,extension,"END","Y","DRCT","IDLE",day.minusYears(1),null,"C-001");}
    WithdrawalDtos.Position position(int serial,String start,String job,String related,String grade,String probability,String customer,int capacity,String type){return new WithdrawalDtos.Position("O-001",serial,job,grade,start,12,capacity,type,related,customer,new BigDecimal(probability));}
    String revision(){return revisions.revision(assignments.snapshot("A-001"));}
    ObjectNode extension(){return json.createObjectNode().put("year",2026).put("expectedRevision",revision()).putNull("extensionPossibility");}
    ObjectNode recommendation(){return json.createObjectNode().put("year",2026).put("expectedRevision","source-revision");}
    org.springframework.test.web.servlet.ResultActions postBody(String action,ObjectNode body) throws Exception{return mvc.perform(post("/api/withdrawals/A-001/"+action).contentType("application/json").content(body.toString()));}
    void noExtensionWrites(){verify(assignments,never()).updateExtension(any());verify(assignments,never()).event(any());verify(audit,never()).insert(any());}
    @Test void extensionAuthRejectsForgedActorWithoutMetadataOrAuditWrites() throws Exception {
        mvc.perform(post("/api/withdrawals/A-001/extension-possibility").contentType("application/json").content(extension().put("actor","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/withdrawals/{assignmentId}/extension-possibility",400))
            .andExpect(jsonPath("$.field").value("actor"));noExtensionWrites();
    }
    @Test void nextPositionsValidationRequiresRevisionWithoutAllocatingRound() throws Exception {
        mvc.perform(post("/api/withdrawals/A-001/next-positions").contentType("application/json").content("{\"year\":2026}"))
            .andExpect(DurableOpenApi.response("POST","/api/withdrawals/{assignmentId}/next-positions",400))
            .andExpect(jsonPath("$.field").value("expectedRevision"));
        verify(next,never()).nextId();verify(next,never()).insert(any(),anyString());noExtensionWrites();
    }
    @Test void publicExtensionWithProtectedMonthChangesOnlyMetadataAndOneRequiredAudit() throws Exception {
        var before=assignment;
        mvc.perform(post("/api/withdrawals/A-001/extension-possibility").contentType("application/json").content(extension().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.assignmentId").value("A-001")).andExpect(jsonPath("$.data.appliedDate").value(day.toString())).andExpect(jsonPath("$.data.historySerial").value("1"));
        assertThat(assignment.extensionPossibility()).isNull();assertThat(assignment.endDate()).isEqualTo(before.endDate());assertThat(assignment.extensionCount()).isEqualTo(before.extensionCount());
        assertThat(events).singleElement().satisfies(e->{assertThat(e.type()).isEqualTo("EXTP");assertThat(e.before()).isEqualTo("H");assertThat(e.after()).isNull();assertThat(e.reason()).isNull();assertThat(e.memo()).isNull();assertThat(e.mm()).isNull();assertThat(e.price()).isNull();assertThat(e.retroactive()).isEqualTo("N");assertThat(e.closeCross()).isEqualTo("N");assertThat(e.actor()).isEqualTo("admin");});
        verify(audit).insert(argThat(e->e.item().equals("EXT_PSBL_CD")));verify(assignments,never()).update(any());verify(months,never()).saveMonth(any());verify(months,never()).histories(anyString());verify(invalidation).afterCommit("workforce-forecasts");
    }
    @Test void futureStartUsesStartDateNotClientDate() throws Exception {assignment=new AssignmentMonthlyService.Source(assignment.id(),assignment.projectId(),assignment.developerId(),assignment.jobCode(),day.plusDays(2),assignment.endDate(),assignment.contractMm(),assignment.price(),assignment.inputType(),null,"H",2,"N","ACT");postBody("extension-possibility",extension()).andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedDate").value(day.plusDays(2).toString()));}
    @Test void equalNullIsCompleteNoOp() throws Exception {assignment=assignment.changed(assignment.endDate(),assignment.contractMm(),assignment.price(),assignment.inputType(),null,2,"N","ACT");String before=revision();postBody("extension-possibility",extension()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.revision").value(before));noExtensionWrites();verifyNoInteractions(invalidation);}
    @ParameterizedTest @ValueSource(strings={"NULL","","X"}) void rejectsInvalidExtensionCode(String value) throws Exception {postBody("extension-possibility",extension().put("extensionPossibility",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("extensionPossibility"));noExtensionWrites();}
    @Test void requiresExplicitNullRatherThanMissing() throws Exception {var body=extension();body.remove("extensionPossibility");postBody("extension-possibility",body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("extensionPossibility"));noExtensionWrites();}
    @ParameterizedTest @ValueSource(strings={"reasonCode","memo","newContractMm","REG_USER_ID","ACT_USER_ID","actor","processedAt","score","applyDate"}) void extensionRejectsUndeclaredFields(String field) throws Exception {postBody("extension-possibility",extension().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty());noExtensionWrites();}
    @Test void pastYearIsConflictAndUnchanged() throws Exception {postBody("extension-possibility",extension().put("year",2025)).andExpect(status().isConflict());noExtensionWrites();}
    @Test void withdrawnIsConflictAndUnchanged() throws Exception {assignment=assignment.changed(day.minusDays(1),assignment.contractMm(),assignment.price(),assignment.inputType(),"H",2,"N","ACT");postBody("extension-possibility",extension()).andExpect(status().isConflict());noExtensionWrites();}
    @Test void canceledIsConflictAndUnchanged() throws Exception {assignment=assignment.changed(assignment.endDate(),assignment.contractMm(),assignment.price(),assignment.inputType(),"H",2,"N","CNCL");postBody("extension-possibility",extension()).andExpect(status().isConflict());noExtensionWrites();}
    @Test void lostProjectIsConflictAndUnchanged() throws Exception {when(assignments.project(anyString())).thenReturn(new AssignmentActionsDtos.Project("P-001","실주","LOSE","MO",day.minusYears(1),day.plusYears(1)));postBody("extension-possibility",extension()).andExpect(status().isConflict());noExtensionWrites();}
    @Test void staleRevisionCannotChangeMetadata() throws Exception {postBody("extension-possibility",extension().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noExtensionWrites();}
    @Test void missingAssignmentIs404() throws Exception {when(months.source("A-001")).thenReturn(null);postBody("extension-possibility",extension()).andExpect(status().isNotFound());noExtensionWrites();}
    @Test void mandatoryAuditFailureRollsBackTransactionAndLeaksNoSql() throws Exception {when(audit.insert(any())).thenReturn(0);postBody("extension-possibility",extension()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").isNotEmpty());verify(manager,atLeastOnce()).rollback(any());}
    @Test void publicNextProducesFixedFiveScoreSnapshotAndServerIdentity() throws Exception {
        mvc.perform(post("/api/withdrawals/A-001/next-positions").contentType("application/json").content(recommendation().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.rcmdId").value("123")).andExpect(jsonPath("$.data.sourceAssignmentId").value("A-001"))
            .andExpect(jsonPath("$.data.candidates[0].developer.managementNumber").value("001")).andExpect(jsonPath("$.data.candidates[0].totalScore").value("100"))
            .andExpect(jsonPath("$.data.candidates[0].scoreDetail.w[0]").value(40)).andExpect(jsonPath("$.data.candidates[0].scoreDetail.e.wait").value(10))
            .andExpect(jsonPath("$.data.candidates[0].nextPosition.probability").value("60")).andExpect(jsonPath("$.data.executedBy").value("admin"));
        assertThat(written).singleElement().satisfies(r->{assertThat(r.projectId()).isNull();assertThat(r.actionCode()).isNull();assertThat(r.scoreDetail().length()).isLessThanOrEqualTo(500);});
        verify(actions).storeOriginal(any());verify(next).requireRevision("A-001","source-revision");verify(forecasts).forecast(argThat(q->q.includeUnfilled()&&q.months()==6&&q.from().equals(YearMonth.of(2026,12))));
    }
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision"}) void nextRequiresFields(String field) throws Exception {var body=recommendation();body.remove(field);postBody("next-positions",body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verify(next,never()).insert(any(),anyString());}
    @ParameterizedTest @ValueSource(strings={"REG_USER_ID","ACT_USER_ID","actor","executedAt","totalScore","scoreDetail","sourceScreenId"}) void nextRejectsServerFields(String field) throws Exception {postBody("next-positions",recommendation().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verify(next,never()).insert(any(),anyString());}
    @Test void nextNotFoundIs404WithoutRound() throws Exception {when(next.lock(anyString())).thenThrow(new ApiException(404,"NOT_FOUND","철수 배정을 찾을 수 없습니다."));postBody("next-positions",recommendation()).andExpect(status().isNotFound());verify(next,never()).nextId();}
    @Test void nextPastYearIsConflictWithoutRound() throws Exception {postBody("next-positions",recommendation().put("year",2025)).andExpect(status().isConflict());verify(next,never()).nextId();}
    @Test void nextWithdrawnIsConflict() throws Exception {person=source("A-001",day.minusDays(1),"M");postBody("next-positions",recommendation()).andExpect(status().isConflict());verify(next,never()).nextId();}
    @Test void nextHighBlocksNew() throws Exception {person=source("A-001",day.plusDays(1),"H");postBody("next-positions",recommendation()).andExpect(status().isConflict());verify(next,never()).nextId();}
    @Test void nextLaterBlocksNew() throws Exception {person=source("A-001",day.plusMonths(4),"M");postBody("next-positions",recommendation().put("year",2027)).andExpect(status().isConflict());verify(next,never()).nextId();}
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","CANDIDATE"}) void nextConfirmedAndConvertedBlockReplacement(String kind) throws Exception {when(next.next(any())).thenReturn(List.of(new WithdrawalDtos.NextAssignment(kind,"A-next","9","P-next","O-next","확정",day.plusDays(1),null)));postBody("next-positions",recommendation()).andExpect(status().isConflict());verify(next,never()).nextId();}
    @Test void existingActMayChangeEvenHighAndLater() throws Exception {person=source("A-001",day.plusMonths(4),"H");when(next.next(any())).thenReturn(List.of(new WithdrawalDtos.NextAssignment("TENTATIVE",null,"9",null,"O-001","기존 기회",null,"2027-04")));when(next.positions(anyString())).thenReturn(List.of(position(1,"2027-04","BE",null,"MD","60","C-001",1,"MO")));postBody("next-positions",recommendation().put("year",2027)).andExpect(status().isOk());verify(next).insert(any(),eq("A-001"));}
    @Test void ineligiblePersonIsConflict() throws Exception {var s=person;person=new WithdrawalDtos.Source(s.assignmentId(),s.developerId(),s.name(),s.managementNumber(),s.developerJob(),s.subJobCode(),s.gradeCode(),s.employeeTypeCode(),s.projectId(),s.projectName(),s.jobCode(),s.withdrawalDate(),s.extensionCount(),s.earlyWithdrawal(),s.extensionPossibility(),s.projectStatus(),"N",s.directClass(),s.workStatus(),s.joinDate(),s.retireDate(),s.customerId());postBody("next-positions",recommendation()).andExpect(status().isConflict());verify(next,never()).nextId();}
    @Test void zeroCandidatesDoesNotAllocateSequenceOrRows() throws Exception {when(next.positions(anyString())).thenReturn(List.of(position(1,"2027-04","BE",null,"MD","60","C-001",1,"MO")));postBody("next-positions",recommendation()).andExpect(status().isOk()).andExpect(jsonPath("$.data.rcmdId").isEmpty()).andExpect(jsonPath("$.data.candidates").isEmpty());verify(next,never()).nextId();verify(next,never()).insert(any(),anyString());}
    @Test void requiredRecommendationAuditFailureRollsBackWholeRound() throws Exception {doThrow(new IllegalStateException("private SQL")).when(actions).storeOriginal(any());postBody("next-positions",recommendation()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());}
    @Test void forecastUsesOnlyStrictShortPrimaryJobMonths() throws Exception {
        var shortMonth=new SalesForecastDtos.WorkforceMonth("2026-12","1","3","0","3","-2","1","3","0","SHORT");
        var equalMonth=new SalesForecastDtos.WorkforceMonth("2027-01","1","2","0","2","-1","1","2","0","BALANCED");
        when(forecasts.forecast(any())).thenReturn(new SalesForecastDtos.Workforce("2026-12",6,true,day.toString(),"2026-11",List.of(),List.of(new SalesForecastDtos.JobMonth("BE",shortMonth,"근거"),new SalesForecastDtos.JobMonth("BE",equalMonth,"근거"),new SalesForecastDtos.JobMonth("FE",shortMonth,"근거")),List.of(),List.of(),null,null,null,null));
        postBody("next-positions",recommendation()).andExpect(status().isOk()).andExpect(jsonPath("$.data.shortageMonths.length()").value(1)).andExpect(jsonPath("$.data.shortageMonths[0].shortagePeople").value("2"));
    }
    @Test void publicExportContainsAllRowsTypedDatesNegativeNumbersAndSafeText() throws Exception {
        String[] names={"001","=SUM(1,1)","+1","-1","@x"};
        for(int i=0;i<55;i++){var s=source("A-"+i,day.minusDays(1),"M");sources.add(new WithdrawalDtos.Source(s.assignmentId(),s.developerId(),names[i%names.length],names[i%names.length],s.developerJob(),s.subJobCode(),s.gradeCode(),s.employeeTypeCode(),s.projectId(),s.projectName(),s.jobCode(),s.withdrawalDate(),s.extensionCount(),s.earlyWithdrawal(),s.extensionPossibility(),s.projectStatus(),s.developerUse(),s.directClass(),s.workStatus(),s.joinDate(),s.retireDate(),s.customerId()));}
        byte[] bytes=mvc.perform(get("/api/withdrawals/export").param("year","2026").param("extension","M")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".xlsx"))).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(55);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)10);for(int i=1;i<=55;i++){var r=sheet.getRow(i);assertThat(r.getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);assertThat(r.getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);assertThat(r.getCell(3).getStringCellValue()).isEqualTo("백엔드");assertThat(DateUtil.isCellDateFormatted(r.getCell(4))).isTrue();assertThat(new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(r.getCell(4))).isEqualTo("2026-11-14");assertThat(r.getCell(5).getNumericCellValue()).isEqualTo(-1);assertThat(r.getCell(6).getNumericCellValue()).isEqualTo(2);assertThat(r.getCell(8).getStringCellValue()).isEqualTo("보통");}assertThat(book.getSheet("다운로드정보").getRow(6).getCell(0).getStringCellValue()).isEqualTo("기준일");}
        verify(access).insert(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-05")&&e.userId().equals("admin")&&e.description().equals(" 철수리스트 55")));verify(next,never()).insert(any(),anyString());noExtensionWrites();
    }
    @ParameterizedTest @CsvSource({"year,2024","year,2028","period,90D","page,1","size,50","actor,x"}) void exportRejectsInvalidFilters(String field,String value) throws Exception {mvc.perform(get("/api/withdrawals/export").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(access);}
    @Test void historicalExportAndEmptyHeaderWorkbookAllowed() throws Exception {sources.add(source("A-old",day.minusYears(1),null));var bytes=mvc.perform(get("/api/withdrawals/export").param("year","2025")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(1);}sources.clear();bytes=mvc.perform(get("/api/withdrawals/export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(book.getSheetAt(0).getLastRowNum()).isZero();assertThat((Object)book.getSheet("다운로드정보").getRow(6)).isNotNull();}verify(next,never()).nextId();noExtensionWrites();}
    @Test void downloadAuditFailureReturnsOnlyJsonNeverAttachment() throws Exception {when(access.insert(any())).thenThrow(new IllegalStateException("private SQL"));mvc.perform(get("/api/withdrawals/export")).andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(header().doesNotExist("Content-Disposition")).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());}
    @Test void zeroAuditRowsPreventsAttachment() throws Exception {
        when(access.insert(any())).thenReturn(0);
        mvc.perform(get("/api/withdrawals/export")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void auditCommitFailurePreventsAttachment() throws Exception {
        var auditWritten=new java.util.concurrent.atomic.AtomicBoolean();
        when(access.insert(any())).thenAnswer(c->{auditWritten.set(true);return 1;});
        doAnswer(c->{if(auditWritten.get())throw new IllegalStateException("private commit failure");return null;}).when(manager).commit(any());
        mvc.perform(get("/api/withdrawals/export")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private commit failure"))));
        verify(access).insert(any());
    }
    @Test void exportSnapshotCompletesBeforeIndependentAuditCommit() throws Exception {
        var read=mock(WithdrawalService.class);
        when(read.listAll(any())).thenReturn(new WithdrawalDtos.Data(List.of(),2026,1,50,0,
            new WithdrawalDtos.Guidance(true,0,0,0,""),day,"2025-01"));
        var excel=new ExcelExportService(users,new AccessLogWriter(access,users,clock),clock,manager);
        com.sicost.common.excel.ExportTestLookups.configure(excel);
        var exporter=new WithdrawalExportService(read,excel);
        exporter.setLabels(com.sicost.common.excel.ExportTestLookups.labels());
        var controller=new WithdrawalActionsController(mock(AssignmentActionsService.class),
            mock(NextPositionRecommendationService.class),exporter);
        var exportMvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        exportMvc.perform(get("/api/withdrawals/export")).andExpect(status().isOk());
        var order=inOrder(manager,read,access);
        order.verify(manager).getTransaction(argThat(t->t.isReadOnly()
            &&t.getIsolationLevel()==org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ
            &&t.getPropagationBehavior()==org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(read).listAll(any());
        order.verify(manager).commit(any());
        order.verify(manager).getTransaction(argThat(t->!t.isReadOnly()
            &&t.getPropagationBehavior()==org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(access).insert(argThat(e->e.screenId().equals("UI-05")&&e.action().equals("DNLD")));
        order.verify(manager).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"extension-possibility","next-positions"}) void postRejectsQueryInjection(String action) throws Exception {mvc.perform(post("/api/withdrawals/A-001/"+action).param("actor","x").contentType("application/json").content(extension().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actor"));noExtensionWrites();verify(next,never()).nextId();}
    @ParameterizedTest @ValueSource(strings={"name","project","job","date","days","history","extension","next"})
    void exportAcceptsEverySortAndReadsAllRowsWithPkTies(String sort) throws Exception {
        for(int i=25;i>=1;i--){
            String id=String.format("A-%02d",i);
            sources.add(new WithdrawalDtos.Source(id,"D-001","개발자",id,"BE","FE","MD","FT","P-001","원프로젝트","BE",day,2,true,"H","END","Y","DRCT","IDLE",day.minusYears(1),null,"C-001"));
        }
        for(String direction:List.of("ASC","DESC")){
            byte[] bytes=mvc.perform(get("/api/withdrawals/export").param("sort",sort).param("direction",direction)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
                var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(25);
                for(int i=1;i<=25;i++)assertThat(sheet.getRow(i).getCell(1).getStringCellValue()).isEqualTo(String.format("A-%02d",i));
            }
        }
        noExtensionWrites();verify(next,never()).insert(any(),anyString());
    }
    @ParameterizedTest @CsvSource({"sort,unknown","sort,","direction,down","direction,"})
    void invalidExportSortDoesNotReadOrLog(String field,String value) throws Exception {
        mvc.perform(get("/api/withdrawals/export").param(field,value==null?"":value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(withdrawals,access);
    }

}
