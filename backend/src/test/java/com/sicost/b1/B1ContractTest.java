package com.sicost.b1;

import com.sicost.apiops.PayrollMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.b1.B1Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B1Controller.class)
@ContextConfiguration(classes=B1Controller.class)
@Import({B1Service.class,B1ImportDefinition.class,ExcelImportService.class,RevisionService.class,ExcelExportService.class,com.sicost.uploadsops.UploadResultController.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class B1ContractTest {
    @Autowired MockMvc mvc;
    @Autowired RevisionService revisions;
    @MockBean B1Mapper mapper;
    @MockBean B1Repository repository;
    @MockBean PayrollMapper payroll;
    @MockBean AggregateLockMapper locks;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    @MockBean com.sicost.uploadsops.UploadResultService legacyResults;
    MockMultipartFile file;
    String revision;
    Result result;
    @Test void literalValidationHappyAndMissingMonthNeverApplyBusinessRows() throws Exception {
        mvc.perform(post("/api/excel/B1/validate").param("month","2026-06").with(DurableOpenApi.multipart(file)))
            .andExpect(DurableOpenApi.response("POST","/api/excel/B1/validate",200))
            .andExpect(jsonPath("$.data.status").value("CHECKED"))
            .andExpect(jsonPath("$.data.summary.successRows").value(1));
        verify(repository).create(argThat(w->w.actor().equals("admin")),any(),any(),eq(revision));
        mvc.perform(post("/api/excel/B1/validate").with(DurableOpenApi.multipart(file)))
            .andExpect(DurableOpenApi.response("POST","/api/excel/B1/validate",400))
            .andExpect(jsonPath("$.field").value("month"));
        verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void literalApplyCommitsAndStaleBusinessRevisionCannotApplyAgain() throws Exception {
        mvc.perform(post("/api/excel/B1/apply").param("month","2026-06").param("uploadId","1")
            .param("expectedRevision",revision).param("confirmFullReplacement","true").with(DurableOpenApi.multipart(file)))
            .andExpect(DurableOpenApi.response("POST","/api/excel/B1/apply",200))
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andExpect(jsonPath("$.data.results[0].developerId").value("D-test"));
        verify(repository).apply(any(),any(),eq("admin"),any());verify(transactions).commit(any());
        clearInvocations(repository);when(payroll.snapshot(any())).thenReturn("concurrent-change");
        mvc.perform(post("/api/excel/B1/apply").param("month","2026-06").param("uploadId","1")
            .param("expectedRevision",revision).param("confirmFullReplacement","true").with(DurableOpenApi.multipart(file)))
            .andExpect(DurableOpenApi.response("POST","/api/excel/B1/apply",409))
            .andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(repository,never()).apply(any(),any(),any(),any());verify(transactions).rollback(any());
    }
    @BeforeEach void setup() throws Exception {
        file=B1Workbook.valid();revision=revisions.revision("empty-month");
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.cashBase()).thenReturn("202601");when(mapper.developers()).thenReturn(List.of(new Developer("D-test","0001","현재이름","FT","INDRCT",null)));
        when(mapper.rates()).thenReturn(List.of(new Rate("SEV_RATE",BigDecimal.TEN),new Rate("INS_RATE",BigDecimal.TEN)));
        when(mapper.members(any())).thenReturn(Collections.emptyList());when(payroll.snapshot(any())).thenReturn("empty-month");
        when(mapper.lockUpload(1)).thenAnswer(c->upload("CHECKED","admin","UI-02","202606"));
        when(repository.context(1)).thenReturn(Map.of("파일지문",digest(file),"검증revision",revision));
        when(repository.create(any(),any(),any(),any())).thenReturn(1L);
        when(repository.preview(any(),any())).thenReturn(new Replacement(1,0,0,Collections.emptyList()));
        when(repository.apply(any(),any(),any(),any())).thenReturn(true);when(mapper.markApplied(anyLong(),any(),any())).thenReturn(1);
        result=new Result(1,"B1","UI-02","APPLIED",new Summary(1,1,0,0,0,0),List.of(new ResultRow(2,"D-test")),true,"2026-06","first-revision");
        when(repository.result(any())).thenReturn(result);
    }
    Upload upload(String status,String actor,String screen,String month) { return new Upload(1,"B1",screen,"payroll.xlsx",file.getSize(),month,actor,status,1,1,0,0); }
    String digest(MockMultipartFile f) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(f.getBytes())); }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder applyRequest() { return multipart("/api/excel/B1/apply").file(file); }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apply() { return applyRequest().param("month","2026-06").param("uploadId","1").param("expectedRevision",revision).param("confirmFullReplacement","true"); }
    @Test void publicTemplateHasExactlySevenColumnsNoRowsGuideAndCommittedUi02Audit() throws Exception {
        var response=mvc.perform(get("/api/excel/B1/template")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)7);
            for(int i=0;i<7;i++)assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(B1ImportDefinition.HEADERS.get(i)+(i==0?"*":""));
            assertThat(book.getSheet("입력안내")).isNotNull();assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-02")&&e.count()==0),any());verifyNoInteractions(mapper,repository,payroll);
        verify(transactions,times(2)).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"actor","REG_USER_ID","unknown"}) void templateRejectsServerAndUnknownFields(String field) throws Exception {
        mvc.perform(get("/api/excel/B1/template").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(logs,repository);
    }
    @Test void templateRejectsBody() throws Exception { mvc.perform(get("/api/excel/B1/template").contentType("application/json").content("{\"actor\":\"x\"}")).andExpect(status().isBadRequest());verifyNoInteractions(logs); }
    @Test void auditFailureReturns500JsonWithoutBinaryOrPrivateDetail() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(logs).append(any(),any());
        mvc.perform(get("/api/excel/B1/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.requestId").isString()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL detail"))));
    }
    @Test void publicValidationReturnsIndependentB1SchemaCheckedOnly() throws Exception {
        mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month"," 2026-06 ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.formType").value("B1")).andExpect(jsonPath("$.data.screenId").value("UI-02"))
            .andExpect(jsonPath("$.data.status").value("CHECKED")).andExpect(jsonPath("$.data.summary.totalRows").value(1)).andExpect(jsonPath("$.data.summary.successRows").value(1)).andExpect(jsonPath("$.data.results").isEmpty()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.issueCount").value(0)).andExpect(jsonPath("$.data.replacement.newCount").value(1)).andExpect(jsonPath("$.data.canApply").doesNotExist());
        verify(repository).create(argThat(w->w.actor().equals("admin")&&w.month().equals("202606")),any(),any(),eq(revision));verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void recheckKeepsIdAndReplacesOnlyDiagnostics() throws Exception {
        mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-06").param("uploadId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadId").value(1));
        verify(repository).replace(argThat(w->w.uploadId()==1),any(),eq(revision));verify(repository,never()).create(any(),any(),any(),any());verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void validateActorInjectionIs400BeforeAnyUploadWrite() throws Exception {
        mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-06").param("USER_ID","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("USER_ID"));verifyNoInteractions(mapper,repository);
    }
    @Test void missingMonthIs400() throws Exception { mvc.perform(multipart("/api/excel/B1/validate").file(file)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(repository); }
    @Test void invalidMonthIs400() throws Exception { mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-13")).andExpect(status().isBadRequest());verifyNoInteractions(repository); }
    @Test void historicalYearIs409() throws Exception { mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2025-06")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));verifyNoInteractions(repository); }
    @Test void futureMonthIs409() throws Exception { mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-07")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verifyNoInteractions(repository); }
    @Test void cashBaseProtectsMonth() throws Exception { when(mapper.cashBase()).thenReturn("202607");mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-06")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verifyNoInteractions(repository); }
    @Test void closeProtectsValidateAndApply() throws Exception {
        when(mapper.closed("202606")).thenReturn(true);mvc.perform(multipart("/api/excel/B1/validate").file(file).param("month","2026-06")).andExpect(status().isConflict());mvc.perform(apply()).andExpect(status().isConflict());verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void validApplyRevalidatesBeforeAtomicMonthReplacement() throws Exception {
        mvc.perform(apply()).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.results[0].developerId").value("D-test")).andExpect(jsonPath("$.data.revision").value("first-revision"));
        var order=inOrder(repository,mapper);order.verify(repository).replace(any(),any(),any());order.verify(repository).apply(any(),any(),eq("admin"),any());order.verify(repository).receipt(eq(1L),any(),eq(revision),eq(true));order.verify(mapper).markApplied(eq(1L),eq("admin"),any());verify(transactions).commit(any());
    }
    @Test void falseReplacementIs400WithoutWriting() throws Exception {
        mvc.perform(applyRequest().param("month","2026-06").param("uploadId","1").param("expectedRevision",revision).param("confirmFullReplacement","false")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("confirmFullReplacement"));verifyNoInteractions(mapper,repository);
    }
    @Test void applyMissingMonthIs400() throws Exception {
        mvc.perform(applyRequest().param("uploadId","1").param("expectedRevision",revision).param("confirmFullReplacement","true")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"actor","screen","month"}) void appliedContextMismatchIsCheckedBeforeReplay(String field) throws Exception {
        when(mapper.lockUpload(1)).thenReturn(upload("APPLIED",field.equals("actor")?"other":"admin",field.equals("screen")?"UI-15":"UI-02",field.equals("month")?"202605":"202606"));
        mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verify(repository,never()).result(any());verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void appliedReplayIgnoresCurrentRevisionRatesAndClosedMonth() throws Exception {
        when(mapper.lockUpload(1)).thenReturn(upload("APPLIED","admin","UI-02","202606"));when(payroll.snapshot(any())).thenReturn("changed");when(mapper.closed(any())).thenReturn(true);
        mvc.perform(apply()).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value("first-revision"));verifyNoInteractions(payroll);verify(mapper,never()).rates();verify(repository,never()).replace(any(),any(),any());verify(repository,never()).apply(any(),any(),any(),any());
    }
    @Test void staleCheckedMonthRefusesWithoutWrites() throws Exception {
        when(payroll.snapshot(any())).thenReturn("later-month");mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(repository,never()).replace(any(),any(),any());verify(repository,never()).apply(any(),any(),any(),any());verify(transactions).rollback(any());
    }
    @Test void latestUnknownTypeCommitsCheckedIssuesBefore422() throws Exception {
        when(mapper.developers()).thenReturn(List.of(new Developer("D-test","0001","현재이름","INVALID","INDRCT",null)));
        mvc.perform(apply()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REVALIDATION_FAILED")).andExpect(jsonPath("$.details.uploadId").value(1)).andExpect(jsonPath("$.details.summary.errorRows").value(1));
        verify(repository).replace(any(),argThat(c->c.summary().errorRows()==1),any());verify(repository,never()).apply(any(),any(),any(),any());verify(transactions).commit(any());
    }
    @Test void auditFailureRollsBackAndDoesNotMarkApplied() throws Exception {
        doThrow(new IllegalStateException("mandatory audit")).when(repository).receipt(anyLong(),any(),any(),anyBoolean());mvc.perform(apply()).andExpect(status().isInternalServerError());verify(transactions).rollback(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void missingUploadIs404() throws Exception { when(mapper.lockUpload(1)).thenReturn(null);mvc.perform(apply()).andExpect(status().isNotFound());verifyNoInteractions(repository); }
    @Test void alteredFileDigestIs409() throws Exception { when(repository.context(1)).thenReturn(Map.of("파일지문","other","검증revision",revision));mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verify(repository,never()).apply(any(),any(),any(),any()); }
    @Test void sharedResultRouteDispatchesB1ReceiptWithoutChangingLegacyResultBranch() throws Exception {
        when(mapper.upload(1)).thenReturn(upload("APPLIED","admin","UI-02","202606"));
        when(mapper.lockUpload(1)).thenReturn(upload("APPLIED","admin","UI-02","202606"));
        mvc.perform(get("/api/uploads/1/result")).andExpect(status().isOk()).andExpect(jsonPath("$.data.formType").value("B1"))
            .andExpect(jsonPath("$.data.screenId").value("UI-02")).andExpect(jsonPath("$.data.results[0].developerId").value("D-test")).andExpect(jsonPath("$.data.revision").value("first-revision"));
        verifyNoInteractions(legacyResults,payroll);verify(repository,never()).apply(any(),any(),any(),any());
    }
}
