package com.sicost.b7;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.security.AccountCipher;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.b7.B7Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B7Controller.class)
@ContextConfiguration(classes=B7Controller.class)
@Import({B7Service.class,B7Repository.class,ExcelImportService.class,B7ImportDefinition.class,ExcelExportService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class B7ContractTest {
    @Autowired MockMvc mvc;
    @MockBean B7Mapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean MasterIdService ids;
    @MockBean AccountCipher cipher;
    @MockBean ChangeHistoryWriter history;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    private MockMultipartFile validFile;
    @BeforeEach void setup() throws Exception {
        validFile=B7Workbook.valid();
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(transactions.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(mapper.codes()).thenReturn(B7Workbook.codes());when(mapper.clients()).thenReturn(List.of());
        when(mapper.nextUploadId()).thenReturn(1L);when(mapper.insertUpload(any())).thenReturn(1);
        when(mapper.insertIssue(any())).thenReturn(1);when(mapper.replaceSummary(any())).thenReturn(1);
        when(mapper.insertClient(any())).thenReturn(1);when(mapper.markApplied(anyLong(),any(),any())).thenReturn(1);
        when(ids.nextClientId()).thenReturn("C-001");
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B7","UI-15","clients.XLSX",validFile.getSize(),"admin","CHECKED",1,1,0,0,null));
    }
    @Test void publicTemplateIsActualEmptyElevenColumnXlsxWithTextStylesGuideAndLastInfo() throws Exception {
        var result=mvc.perform(get("/api/excel/B7/template")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
            for(int i=0;i<11;i++)assertThat(book.getSheetAt(0).getRow(0).getCell(i).getStringCellValue()).isEqualTo(ExcelImportService.HEADERS.get(i));
            for(int i:new int[]{2,6,9})assertThat(book.getSheetAt(0).getColumnStyle(i).getDataFormatString()).isEqualTo("@");
            assertThat(book.getSheet("입력안내")).isNotNull();assertThat(book.getSheetName(book.getNumberOfSheets()-1)).isEqualTo("다운로드정보");
            assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.count()==0),any());verifyNoInteractions(history,ids,cipher,mapper);
    }
    @Test void templateRejectsUnknownProcessorQueryBeforeGeneration() throws Exception {
        mvc.perform(get("/api/excel/B7/template").param("processor","injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("processor"));
        verifyNoInteractions(logs,mapper,history);
    }
    @Test void templateAuditFailureProvidesJsonNotBinary() throws Exception {
        doThrow(new IllegalStateException("private detail")).when(logs).append(any(),any());
        mvc.perform(get("/api/excel/B7/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private detail"))));
    }
    @Test void publicValidationStoresCheckedOnlyAndAllowsWarningRow() throws Exception {
        var file=B7Workbook.file(book->B7Workbook.row(book,1,"고객사","검사 거래처","1234567890","","","","","finance@example.test","discarded","discarded","discarded"));
        mvc.perform(multipart("/api/excel/B7/validate").file(file)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CHECKED"))
            .andExpect(jsonPath("$.data.summary.warningRows").value(1)).andExpect(jsonPath("$.data.canApply").value(true));
        verify(mapper).insertUpload(argThat(w->w.actor().equals("admin")&&w.summary().totalRows()==1));verify(mapper,never()).insertClient(any());verifyNoInteractions(history,ids,cipher);
    }
    @Test void validationUnknownMultipartFieldDoesNotWrite() throws Exception {
        mvc.perform(multipart("/api/excel/B7/validate").file(B7Workbook.valid()).param("regUserId","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper);
    }
    @Test void validationMissingFileIs400() throws Exception {mvc.perform(multipart("/api/excel/B7/validate")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void validationFakeXlsxIs415BeforeUpload() throws Exception {
        mvc.perform(multipart("/api/excel/B7/validate").file(new MockMultipartFile("file","fake.xlsx","application/octet-stream",new byte[]{1,2,3,4}))).andExpect(status().isUnsupportedMediaType());verifyNoInteractions(mapper);
    }
    @Test void validationOversizeIs413BeforeUpload() throws Exception {
        mvc.perform(multipart("/api/excel/B7/validate").file(new MockMultipartFile("file","large.xlsx","application/octet-stream",new byte[10485761]))).andExpect(status().isPayloadTooLarge());verifyNoInteractions(mapper);
    }
    @Test void validationEmptyWorkbookIs400BeforeUpload() throws Exception {
        mvc.perform(multipart("/api/excel/B7/validate").file(B7Workbook.file(book->{}))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));verifyNoInteractions(mapper);
    }
    @Test void validationIssueInsertFailureRollsBackAndHidesDatabaseError() throws Exception {
        when(mapper.clients()).thenReturn(List.of(new Existing("123-45-67890","existing")));when(mapper.insertIssue(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private SQL"));
        mvc.perform(multipart("/api/excel/B7/validate").file(B7Workbook.valid())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verify(transactions).rollback(any());verify(mapper,never()).insertClient(any());
    }
    @Test void publicApplyCreatesAllAndHistoryThenApplied() throws Exception {
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.appliedCount").value(1));
        verify(mapper).insertClient(argThat(w->w.actor().equals("admin")&&w.client().businessNo().equals("123-45-67890")&&w.encryptedAccount()==null));
        verify(history).append(argThat(h->h.type().equals("NEW")&&h.item().equals("등록")&&h.before()==null
            &&h.after().startsWith("C-001 ")&&h.reason().equals("B7 업로드:1")));verify(mapper).markApplied(eq(1L),eq("admin"),any());
    }
    @Test void appliedRetryUsesSavedSummaryWithoutParsingOrNewWrites() throws Exception {
        var file=new MockMultipartFile("file","clients.XLSX","application/octet-stream",new byte[4]);
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B7","UI-15","clients.XLSX",4,"admin","APPLIED",1,1,0,0,LocalDateTime.of(2026,6,15,9,0)));
        mvc.perform(multipart("/api/excel/B7/apply").file(file).param("uploadId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCount").value(1));
        verify(mapper,never()).codes();verify(mapper,never()).insertClient(any());verifyNoInteractions(ids,history);
    }
    @Test void applyMissingUploadIdIs400() throws Exception {mvc.perform(multipart("/api/excel/B7/apply").file(B7Workbook.valid())).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void applyUnknownActorIs400() throws Exception {mvc.perform(multipart("/api/excel/B7/apply").file(B7Workbook.valid()).param("uploadId","1").param("actor","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper);}
    @Test void applyMissingUploadIs404() throws Exception {when(mapper.lockUpload(1)).thenReturn(null);mvc.perform(multipart("/api/excel/B7/apply").file(B7Workbook.valid()).param("uploadId","1")).andExpect(status().isNotFound());}
    @Test void applyContextMismatchIs409() throws Exception {
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B7","UI-15","different.xlsx",4,"admin","CHECKED",1,1,0,0,null));
        mvc.perform(multipart("/api/excel/B7/apply").file(B7Workbook.valid()).param("uploadId","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verifyNoInteractions(ids,history);
    }
    @Test void applyCurrentDuplicateCommitsReplacementBefore422() throws Exception {
        when(mapper.clients()).thenReturn(List.of(new Existing("123-45-67890","other")));
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.canApply").value(false)).andExpect(jsonPath("$.details.summary.errorRows").value(1));
        verify(transactions).commit(any());verify(transactions,never()).rollback(any());verify(mapper).deleteIssues(1);verify(mapper,never()).insertClient(any());
    }
    @Test void applyHistoryFailureRollsBackEntireTransaction() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("reject")).when(history).append(any());
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1")).andExpect(status().isInternalServerError());verify(transactions).rollback(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void applyMissingFileDoesNotLockOrWrite() throws Exception {
        mvc.perform(multipart("/api/excel/B7/apply").param("uploadId","1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("file"));
        verifyNoInteractions(mapper,ids,history);
    }
    @Test void applyOversizeFailsBeforeLockAndBusinessWrites() throws Exception {
        mvc.perform(multipart("/api/excel/B7/apply").file(new MockMultipartFile("file","large.xlsx",null,new byte[10485761])).param("uploadId","1"))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.field").value("file"));
        verifyNoInteractions(mapper,ids,history);
    }
    @Test void applyFakeWorkbookFailsWithoutBusinessOrSummaryWrites() throws Exception {
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B7","UI-15","clients.XLSX",4,"admin","CHECKED",1,1,0,0,null));
        mvc.perform(multipart("/api/excel/B7/apply").file(new MockMultipartFile("file","clients.XLSX",null,new byte[]{1,2,3,4})).param("uploadId","1"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.field").value("file"));
        verify(mapper,never()).replaceSummary(any());verify(mapper,never()).insertClient(any());verifyNoInteractions(ids,history);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"actor","form","screen","status","size"})
    void uploadContextChecksEveryStoredBoundary(String mismatch) throws Exception {
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,mismatch.equals("form")?"B8":"B7",mismatch.equals("screen")?"UI-23":"UI-15","clients.XLSX",
            validFile.getSize()+(mismatch.equals("size")?1:0),mismatch.equals("actor")?"other":"admin",mismatch.equals("status")?"CANCEL":"CHECKED",1,1,0,0,null));
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));
        verify(mapper,never()).codes();verify(mapper,never()).replaceSummary(any());verifyNoInteractions(ids,history);
    }
    @Test void uniqueRaceRollsBackThenCommitsFreshDuplicateDiagnostics() throws Exception {
        when(mapper.clients()).thenReturn(List.of(),List.of(new Existing("123-45-67890","동시 등록")));
        when(mapper.insertClient(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("business number race"));
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details.uploadId").value(1)).andExpect(jsonPath("$.details.summary.errorRows").value(1));
        var order=inOrder(transactions);order.verify(transactions).getTransaction(any());order.verify(transactions).rollback(any());
        order.verify(transactions).getTransaction(any());order.verify(transactions).commit(any());
        verify(mapper,times(2)).lockUpload(1);verify(mapper,never()).markApplied(anyLong(),any(),any());verifyNoInteractions(history);
    }
    @Test void revalidationUsesCurrentStoppedCodeWithoutRegisteringAnything() throws Exception {
        when(mapper.codes()).thenReturn(List.of(new Code("CLNT_TYPE","CUST","고객사",false,null)));
        mvc.perform(multipart("/api/excel/B7/apply").file(validFile).param("uploadId","1"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.canApply").value(false));
        verify(mapper).insertIssue(argThat(w->w.issue().columnName().equals("구분")&&w.issue().severity().equals("ERR")));
        verify(mapper,never()).insertClient(any());verifyNoInteractions(ids,history);
    }
    @Test void secondRowEncryptionFailureDoesNotMarkAppliedAndRollsBack() throws Exception {
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"고객사","첫 거래처","1234567890","","","","","a@example.test");
            B7Workbook.row(b,2,"협력사","둘 거래처","1234567891","","","","","","KB국민은행","0100000000","예금주");});
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B7","UI-15","clients.XLSX",file.getSize(),"admin","CHECKED",2,2,0,0,null));
        when(cipher.encrypt(any())).thenThrow(new ApiException(503,"ENCRYPTION_CONFIG_ERROR","계좌 암호화 설정을 확인해 주세요."));
        mvc.perform(multipart("/api/excel/B7/apply").file(file).param("uploadId","1")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENCRYPTION_CONFIG_ERROR"));
        verify(transactions).rollback(any());verify(mapper,times(1)).insertClient(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
}
