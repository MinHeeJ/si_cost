package com.sicost.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.imports.BusinessPlanImportDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessPlanImportController.class)
@ContextConfiguration(classes=BusinessPlanImportController.class)
@Import({BusinessPlanImportService.class,BusinessPlanImportRepository.class,BusinessPlanImportDefinition.class,ExcelImportService.class,
    RevisionService.class,WriteCoordinator.class,ChangeHistoryWriter.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BusinessPlanImportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BusinessPlanImportMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper master;
    @MockBean CommitInvalidation invalidation;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Stored> targets;
    List<Close> closes;
    List<Issue> issues;
    List<ChangeHistoryEntry> audits;
    Upload upload;
    MockMultipartFile file;
    @BeforeEach void setup() throws Exception {
        targets=new ArrayList<>();closes=new ArrayList<>();issues=new ArrayList<>();audits=new ArrayList<>();upload=null;file=BusinessPlanWorkbook.valid();
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        Clock fixed=Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(mapper.cashBase()).thenReturn("208007");when(mapper.targets(anyInt())).thenAnswer(c -> List.copyOf(targets));
        when(mapper.closes(anyInt())).thenAnswer(c -> List.copyOf(closes));when(mapper.snapshot(anyInt())).thenAnswer(c -> targets.toString()+closes+mapper.cashBase());
        when(mapper.upload(anyLong())).thenAnswer(c -> upload);when(mapper.lockUpload(anyLong())).thenAnswer(c -> upload);
        when(mapper.issues(anyLong())).thenAnswer(c -> List.copyOf(issues));
        when(mapper.insertUpload(any())).thenAnswer(c -> {Draft d=c.getArgument(0);d.setUploadId(19L);setUpload(d.getWrite(),"CHECKED");return 1;});
        when(mapper.replaceSummary(any())).thenAnswer(c -> {setUpload(c.getArgument(0),"CHECKED");return 1;});
        when(mapper.insertIssue(any())).thenAnswer(c -> {issues.add(((IssueWrite)c.getArgument(0)).issue());return 1;});
        when(mapper.deleteIssues(anyLong())).thenAnswer(c -> {issues.clear();return 1;});
        when(history.insert(any())).thenAnswer(c -> {audits.add(c.getArgument(0));return 1;});
        when(mapper.insertTarget(any())).thenAnswer(c -> {TargetWrite w=c.getArgument(0);targets.add(new Stored(w.month(),w.sales(),w.profit(),w.uploadId(),w.actor(),w.now(),null,null));return 1;});
        when(mapper.updateTarget(any())).thenAnswer(c -> {TargetWrite w=c.getArgument(0);Stored old=targets.stream().filter(t -> w.month().equals(t.month())).findFirst().orElseThrow();targets.remove(old);targets.add(new Stored(w.month(),w.sales(),w.profit(),w.uploadId(),old.regUserId(),old.regDttm(),w.actor(),w.now()));return 1;});
        when(mapper.markApplied(any())).thenAnswer(c -> {setUpload(c.getArgument(0),"APPLIED");return 1;});
        when(mapper.changedMonths(anyLong())).thenAnswer(c -> audits.stream().filter(a -> a.tableName().equals("TB_FI_BIZ_PLAN")).map(a -> BusinessPlanImportRepository.apiMonth(a.targetKey().substring(0,6))).distinct().sorted().toList());
    }
    void setUpload(UploadWrite w,String state) {Summary s=w.summary();upload=new Upload(19,"B10","UI-18",w.filename(),w.size(),w.month(),null,null,"admin",state,s.totalRows(),s.successRows(),s.warningRows(),s.errorRows(),state.equals("APPLIED")?w.now():null);}
    MockHttpServletRequestBuilder check() {return post("/api/business-plans/imports/validate").param("year","2080").with(DurableOpenApi.multipart(file));}
    MockHttpServletRequestBuilder apply(String revision,String mode) {return post("/api/business-plans/imports/apply").param("year","2080").param("uploadId","19").param("expectedRevision",revision).param("mode",mode).with(DurableOpenApi.multipart(file));}
    String checked() throws Exception {var result=mvc.perform(check()).andExpect(status().isOk()).andExpect(validationContract()).andReturn();return json.readTree(result.getResponse().getContentAsString()).at("/data/expectedRevision").asText();}
    ResultMatcher validationContract() {
        return result -> {
            var body=json.readTree(result.getResponse().getContentAsByteArray());
            assertThat(body.path("success").asBoolean()).isTrue();assertThat(body.path("meta").isObject()).isTrue();
            var data=body.path("data");var fields=new HashSet<String>();data.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("uploadId","status","summary","issues","expectedRevision","canApply","warnings","year","changes","requiresClosedMonthConfirmation","blankTargetMonths");
            assertThat(data.path("uploadId").asText()).matches("[1-9][0-9]{0,11}");assertThat(data.path("status").asText()).isEqualTo("CHECKED");
            assertThat(data.path("expectedRevision").asText()).isNotBlank();assertThat(data.path("changes").isArray()).isTrue();
            var summary=data.path("summary");assertThat(summary.path("totalRows").asInt()).isEqualTo(summary.path("successRows").asInt()+summary.path("warningRows").asInt()+summary.path("errorRows").asInt());
            assertThat(summary.has("fileWarningCount")).isTrue();assertThat(data.path("issues").path("items").isArray()).isTrue();
            for(var change:data.path("changes")){assertThat(change.has("before")).isTrue();assertThat(change.path("after").has("salesTargetAmt")).isTrue();assertThat(change.path("after").has("operatingProfitTargetAmt")).isTrue();}
        };
    }
    void noBusiness() {verify(mapper,never()).insertTarget(any());verify(mapper,never()).updateTarget(any());verify(mapper,never()).markApplied(any());}
    Stored target(String month,long amount) {return new Stored(month,BigDecimal.valueOf(amount),BigDecimal.ZERO,null,"admin",LocalDateTime.of(2080,1,1,0,0),null,null);}
    @Test void publicTemplateHasExactlyTwelveBlankMoneyRowsAndCommittedDownloadAudit() throws Exception {
        var response=mvc.perform(get("/api/business-plans/imports/template").param("year","2079")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(13);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("연월*");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("매출 목표*");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("영업이익 목표*");
            for(int m=1;m<=12;m++){assertThat(sheet.getRow(m).getCell(0).getStringCellValue()).isEqualTo(YearMonth.of(2079,m).toString());
                assertThat(sheet.getRow(m).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);assertThat(sheet.getRow(m).getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);}
        }
        verify(logs).append(argThat(e -> e.screenId().equals("UI-18") && e.action().equals("DNLD") && e.count()==12),any());verify(manager,times(2)).commit(any());noBusiness();
    }
    @Test void templateAuditFailureReturnsJsonAndNoWorkbook() throws Exception {
        doThrow(new IllegalStateException("private")).when(logs).append(any(),any());
        var response=mvc.perform(get("/api/business-plans/imports/template").param("year","2080")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private");assertThat(response.getHeader("Content-Disposition")).isNull();noBusiness();
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","REG_DTTM","unknown"})
    void injectedFieldsRejectedAcrossEveryOperation(String field) throws Exception {
        mvc.perform(get("/api/business-plans/imports/template").param("year","2080").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(check().param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(apply("x","ALL").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(history,logs);verify(mapper,never()).insertUpload(any());noBusiness();
    }
    @ParameterizedTest @ValueSource(strings={"0000","10000","2080-07","bad","-1"})
    void invalidYearReturns400WithoutDownload(String year) throws Exception {mvc.perform(get("/api/business-plans/imports/template").param("year",year)).andExpect(status().isBadRequest());verifyNoInteractions(logs);}
    @Test void duplicateQueryRejected() throws Exception {mvc.perform(get("/api/business-plans/imports/template").param("year","2080","2080")).andExpect(status().isBadRequest());verifyNoInteractions(logs);}
    @Test void successfulValidationStoresOnlySummaryAndIssuesNotAuditOrTargets() throws Exception {
        checked();verify(mapper).insertUpload(any());verifyNoInteractions(history);assertThat(upload.formType()).isEqualTo("B10");assertThat(upload.month()).isEqualTo("208001");assertThat(upload.totalRows()).isEqualTo(12);noBusiness();
    }
    @Test void applyWritesAllTwelveMonthsWithFieldAuditAndNoPeriodBaseRestriction() throws Exception {
        String revision=checked();mvc.perform(apply(revision,"ALL")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.changedMonths.length()").value(12));
        assertThat(targets).hasSize(12);assertThat(targets).anyMatch(t -> t.month().equals("208001"));assertThat(targets).anyMatch(t -> t.month().equals("208012"));
        assertThat(audits).allMatch(a -> a.tableName().equals("TB_FI_BIZ_PLAN") && a.targetKey().endsWith(":ALL") && a.reason().matches("BusinessPlanImport:19:[0-9]+:ALL"));
        var order=inOrder(master,locks);order.verify(master,atLeastOnce()).lockClose();order.verify(locks,atLeastOnce()).lock(anyString());verify(mapper).markApplied(any());
    }
    @Test void keepClosedPreservesExistingAndAbsentClosedMonth() throws Exception {
        Stored original=target("208001",8);targets.add(original);closes.add(new Close("208001","CLOSE"));closes.add(new Close("208002","CLOSE"));
        String revision=checked();mvc.perform(apply(revision,"KEEP_CLOSED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.keptMonths.length()").value(2));
        assertThat(targets).contains(original).noneMatch(t -> t.month().equals("208002"));assertThat(targets).hasSize(11);
        assertThat(audits).noneMatch(a -> a.targetKey().startsWith("208001") || a.targetKey().startsWith("208002"));
    }
    @Test void closedStatusChangeMakesRevisionStaleWithoutWrites() throws Exception {
        String revision=checked();closes.add(new Close("208001","CLOSE"));mvc.perform(apply(revision,"KEEP_CLOSED")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noBusiness();verifyNoInteractions(history);
    }
    @Test void baseChangeIsStale() throws Exception {String revision=checked();when(mapper.cashBase()).thenReturn("208008");mvc.perform(apply(revision,"ALL")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noBusiness();}
    @Test void targetValueChangeIsStale() throws Exception {String revision=checked();targets.add(target("208012",2));mvc.perform(apply(revision,"ALL")).andExpect(status().isConflict());noBusiness();}
    @Test void contextOwnerMismatchPreventsWrites() throws Exception {
        String revision=checked();when(users.userId()).thenReturn("other");mvc.perform(apply(revision,"ALL")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));noBusiness();
    }
    @Test void contextYearMismatchPrecedesRevision() throws Exception {
        String revision=checked();upload=new Upload(19,"B10","UI-18",upload.filename(),upload.size(),"207901",null,null,"admin","CHECKED",12,12,0,0,null);
        mvc.perform(apply(revision,"ALL")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));noBusiness();
    }
    @Test void newFileErrorsReplaceCheckedIssuesAndCommitBefore422() throws Exception {
        String revision=checked();file=BusinessPlanWorkbook.file(b -> b.getSheetAt(0).getRow(1).getCell(1).setCellValue(1.5));
        upload=new Upload(19,"B10","UI-18",file.getOriginalFilename(),file.getSize(),"208001",null,null,"admin","CHECKED",12,12,0,0,null);
        mvc.perform(apply(revision,"ALL")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.financial.upload.canApply").value(false));
        assertThat(issues).anyMatch(i -> i.columnName().equals("매출 목표") && i.rowNumber()==2 && i.severity().equals("ERR"));
        verify(mapper).deleteIssues(19);verify(manager,atLeastOnce()).commit(any());noBusiness();
    }
    @Test void identicalFileCompletesWithZeroChangesAndNoAuditOrTargetTouch() throws Exception {
        for(int m=1;m<=12;m++)targets.add(new Stored(String.format("2080%02d",m),BigDecimal.valueOf(1000+m),BigDecimal.valueOf(-m),null,"admin",LocalDateTime.of(2080,1,1,0,0),null,null));
        String revision=checked();var before=List.copyOf(targets);
        mvc.perform(apply(revision,"ALL")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changedMonths").isEmpty()).andExpect(jsonPath("$.data.unchangedMonths.length()").value(12));
        assertThat(targets).isEqualTo(before);verify(mapper,never()).insertTarget(any());verify(mapper,never()).updateTarget(any());verifyNoInteractions(history);verify(mapper).markApplied(any());
    }
    @Test void replayReturnsStoredSummaryBeforeParsingOrRevisionAndDoesNotGuessKeptMonths() throws Exception {
        String revision=checked();mvc.perform(apply(revision,"ALL")).andExpect(status().isOk());clearInvocations(mapper,history);
        file=new MockMultipartFile("file","targets.xlsx","application/octet-stream",new byte[(int)file.getSize()]);
        mvc.perform(apply("obsolete","KEEP_CLOSED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyApplied").value(true)).andExpect(jsonPath("$.data.keptMonths").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper,never()).snapshot(anyInt());verify(mapper,never()).replaceSummary(any());noBusiness();verifyNoInteractions(history);
    }
    @Test void requiredAuditFailureDoesNotMarkAppliedAndRequestsRollback() throws Exception {
        String revision=checked();doThrow(new IllegalStateException("private")).when(history).insert(any());
        mvc.perform(apply(revision,"ALL")).andExpect(status().isInternalServerError());verify(mapper,never()).markApplied(any());verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void previousYearCannotValidate() throws Exception {mvc.perform(post("/api/business-plans/imports/validate").param("year","2079").with(DurableOpenApi.multipart(file))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noBusiness();}
    @Test void missingUploadIs404() throws Exception {mvc.perform(apply("x","ALL")).andExpect(status().isNotFound());noBusiness();}
    @Test void invalidModeIs400() throws Exception {mvc.perform(apply("x","AUTO")).andExpect(status().isBadRequest());noBusiness();}
    @Test void issueFailureRollsBackAndDoesNotMarkApplied() throws Exception {
        file=BusinessPlanWorkbook.file(b -> b.getSheetAt(0).getRow(1).getCell(1).setBlank());String revision=checked();doThrow(new IllegalStateException()).when(mapper).insertIssue(any());
        mvc.perform(apply(revision,"ALL")).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());noBusiness();
    }
}
