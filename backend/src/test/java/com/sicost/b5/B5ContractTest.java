package com.sicost.b5;

import com.sicost.assignmentsops.AssignmentActionsRepository;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import java.io.ByteArrayInputStream;
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
import static com.sicost.b5.B5Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B5Controller.class)
@ContextConfiguration(classes=B5Controller.class)
@Import({B5Service.class,RevisionService.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class B5ContractTest {
    @Autowired MockMvc mvc;
    @Autowired RevisionService revisions;
    @MockBean B5Mapper mapper;
    @MockBean B5Repository repository;
    @MockBean B5ImportDefinition definition;
    @MockBean AssignmentActionsRepository assignments;
    @MockBean AssignmentMonthlyService months;
    @MockBean CurrentUserProvider users;
    @MockBean AggregateLockMapper locks;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    MockMultipartFile file;
    Checked checked;
    String token;
    @BeforeEach void setup() throws Exception {
        file=new MockMultipartFile("file","assignments.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new byte[]{80,75,1,2});
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(months.cashBase()).thenReturn(YearMonth.of(2025,1));when(months.mapper()).thenReturn(mock(com.sicost.common.assignments.AssignmentMonthlyMapper.class));
        when(definition.filename(any())).thenReturn("assignments.xlsx");when(definition.parse(any())).thenReturn(new Parsed("assignments.xlsx",file.getSize(),Map.of(),List.of(),List.of()));
        checked=new Checked(List.of(),new Summary(1,1,0,0,0,0),List.of(),List.of(),List.of());when(definition.check(any(),any())).thenReturn(checked);
        when(mapper.snapshot()).thenReturn("original");token=revisions.revision(Arrays.asList(2026,List.of(),List.of(),"original"));
        when(repository.context(7)).thenReturn(Map.of("연도","2026","파일지문",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())),"미리보기토큰",token,"철수집합",revisions.revision(List.of())));
        when(mapper.lockUpload(7)).thenReturn(upload("CHECKED","admin","B5","UI-12"));when(repository.create(any(),any(),any())).thenReturn(7L);
        when(repository.result(any())).thenReturn(new Result(7,"B5","UI-12","APPLIED",checked.summary(),List.of(new ResultRow(2,"A-receipt")),true,List.of()));
    }
    Upload upload(String status,String actor,String form,String screen) {return new Upload(7,form,screen,"assignments.xlsx",file.getSize(),actor,status,1,1,0,0);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder apply() {return multipart("/api/excel/B5/apply").file(file).file(new MockMultipartFile("withdrawalSelections","","application/json","[]".getBytes())).param("year","2026").param("uploadId","7").param("previewToken",token);}
    @Test void templateTenColumnsEmptyDataGuideAndUi12Audit() throws Exception {
        var response=mvc.perform(get("/api/excel/B5/template")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)10);
            for(int i=0;i<10;i++){String h=B5ImportDefinition.HEADERS.get(i);assertThat(book.getSheetAt(0).getRow(0).getCell(i).getStringCellValue()).isEqualTo(h+(B5ImportDefinition.REQUIRED.contains(h)?"*":""));}
            assertThat(book.getSheet("입력안내")).isNotNull();assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-12")&&e.count()==0),any());verify(transactions,times(2)).commit(any());verifyNoInteractions(repository,assignments);
    }
    @ParameterizedTest @ValueSource(strings={"actor","REG_USER_ID","unknown"}) void templateRejectsInjectionWithoutAnyWrite(String field) throws Exception {mvc.perform(get("/api/excel/B5/template").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));verifyNoInteractions(logs,repository);}
    @Test void templateRejectsBody() throws Exception {mvc.perform(get("/api/excel/B5/template").content("{}" )).andExpect(status().isBadRequest());verifyNoInteractions(logs);}
    @Test void failedMandatoryDownloadAuditReturnsJsonNotBinary() throws Exception {doThrow(new IllegalStateException("private SQL")).when(logs).append(any(),any());mvc.perform(get("/api/excel/B5/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));}
    @Test void publicValidateReturnsExactIndependentSchemaAndNoBusinessWrite() throws Exception {
        mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year"," 2026 ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.formType").value("B5")).andExpect(jsonPath("$.data.screenId").value("UI-12")).andExpect(jsonPath("$.data.previewToken").value(token)).andExpect(jsonPath("$.data.issueCount").value(0)).andExpect(jsonPath("$.data.results").isEmpty()).andExpect(jsonPath("$.data.canApply").doesNotExist());
        verify(repository).create(argThat(w->w.actor().equals("admin")),eq(checked),any());verify(repository,never()).apply(any(),any(),anyInt());
    }
    @Test void actorMultipartInjectionRejectedBeforeUploadRecord() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year","2026").param("REG_USER_ID","forged")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void duplicateYearRejected() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year","2026","2027")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void unknownFilePartRejected() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).file(new MockMultipartFile("actor",new byte[]{1})).param("year","2026")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void queryRejected() throws Exception {mvc.perform(multipart("/api/excel/B5/validate?year=2026").file(file)).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void missingYearRejected() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file)).andExpect(status().isBadRequest());}
    @Test void historicalYear409() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year","2025")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));verifyNoInteractions(repository);}
    @Test void futureOutOfRangeYear400() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year","2028")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void sameFileRecheckKeepsIdAndReplacesDiagnosticsOnly() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).param("year","2026").param("uploadId","7")).andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadId").value(7));verify(repository).replace(any(),eq(checked),eq(token));verify(repository,never()).create(any(),any(),any());verify(repository,never()).apply(any(),any(),anyInt());}
    @Test void validApplyRechecksAndCommitsAtomicRepository() throws Exception {mvc.perform(apply()).andExpect(status().isOk()).andExpect(jsonPath("$.data.results[0].assignmentId").value("A-receipt"));var order=inOrder(repository);order.verify(repository).replace(any(),eq(checked),eq(token));order.verify(repository).apply(any(),eq(checked),eq(2026));verify(transactions).commit(any());}
    @ParameterizedTest @ValueSource(strings={"actor","form","screen"}) void contextMismatchBeforeAppliedReplay(String field) throws Exception {
        when(mapper.lockUpload(7)).thenReturn(upload("APPLIED",field.equals("actor")?"other":"admin",field.equals("form")?"B1":"B5",field.equals("screen")?"UI-02":"UI-12"));
        mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verify(repository,never()).result(any());verify(repository,never()).apply(any(),any(),anyInt());
    }
    @Test void appliedReplayPrecedesCurrentSnapshotAndWritableYear() throws Exception {when(mapper.lockUpload(7)).thenReturn(upload("APPLIED","admin","B5","UI-12"));when(mapper.snapshot()).thenReturn("changed");when(months.cashBase()).thenThrow(new IllegalStateException("must not calculate"));mvc.perform(apply()).andExpect(status().isOk());verify(definition,never()).parse(any());verify(repository,never()).replace(any(),any(),any());verify(repository,never()).apply(any(),any(),anyInt());}
    @Test void changedYearContextIs409EvenApplied() throws Exception {when(repository.context(7)).thenReturn(Map.of("연도","2027"));when(mapper.lockUpload(7)).thenReturn(upload("APPLIED","admin","B5","UI-12"));mvc.perform(apply()).andExpect(status().isConflict());verify(repository,never()).result(any());}
    @Test void oldToken409AndNoBusinessWrite() throws Exception {when(mapper.snapshot()).thenReturn("changed");mvc.perform(apply()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(repository,never()).apply(any(),any(),anyInt());verify(transactions).rollback(any());}
    @Test void latestErrorsCommitCheckedBefore422() throws Exception {var rejected=new Checked(List.of(),new Summary(1,0,0,1,0,0),List.of(),List.of(),List.of());when(definition.check(any(),any())).thenReturn(rejected);mvc.perform(apply()).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REVALIDATION_FAILED")).andExpect(jsonPath("$.details.summary.errorRows").value(1));verify(repository).replace(any(),eq(rejected),any());verify(repository,never()).apply(any(),any(),anyInt());verify(transactions).commit(any());}
    @Test void withdrawalFailureRollsBackAndNoAppliedReceipt() throws Exception {doThrow(new IllegalStateException("withdrawal failed")).when(repository).apply(any(),any(),anyInt());mvc.perform(apply()).andExpect(status().isInternalServerError());verify(transactions).rollback(any());verify(repository,never()).result(any());}
    @Test void missingUpload404() throws Exception {when(mapper.lockUpload(7)).thenReturn(null);mvc.perform(apply()).andExpect(status().isNotFound());verifyNoInteractions(repository);}
    @Test void unknownSelectionJsonField400() throws Exception {mvc.perform(multipart("/api/excel/B5/validate").file(file).file(new MockMultipartFile("withdrawalSelections","","application/json","[{\"rowNumber\":2,\"withdrawals\":[],\"actor\":\"x\"}]".getBytes())).param("year","2026")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void missingSelectionsApply400() throws Exception {mvc.perform(multipart("/api/excel/B5/apply").file(file).param("year","2026").param("uploadId","7").param("previewToken",token)).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
}
