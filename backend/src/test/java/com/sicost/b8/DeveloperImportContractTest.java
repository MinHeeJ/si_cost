package com.sicost.b8;

import com.sicost.b7.ExcelImportService;
import com.sicost.b7.B7Dtos.Upload;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
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
import static com.sicost.b8.B8Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B8Controller.class)
@ContextConfiguration(classes=B8Controller.class)
@Import({B8Service.class,B8Repository.class,ExcelImportService.class,B8ImportDefinition.class,ExcelExportService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DeveloperImportContractTest {
    @Autowired MockMvc mvc;
    @MockBean B8Mapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean MasterIdService ids;
    @MockBean ChangeHistoryWriter history;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    MockMultipartFile file;
    @BeforeEach void setup() throws Exception {
        file=DeveloperWorkbook.valid();
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(transactions.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(mapper.codes()).thenReturn(DeveloperWorkbook.codes());when(mapper.parts()).thenReturn(List.of(new Part("HQ","본사",true)));when(mapper.managementPreview()).thenReturn(BigInteger.ONE);
        when(mapper.insertUpload(any())).thenAnswer(call->{((UploadDraft)call.getArgument(0)).setUploadId(1L);return 1;});
        when(mapper.insertIssue(any())).thenReturn(1);when(mapper.replaceSummary(any())).thenReturn(1);when(mapper.insertDeveloper(any())).thenReturn(1);when(mapper.insertPrice(any())).thenReturn(1);when(mapper.insertSkill(any())).thenReturn(1);when(mapper.markApplied(anyLong(),any(),any())).thenReturn(1);
        when(ids.nextManagementNo()).thenReturn("K-0001");when(ids.nextDeveloperId()).thenReturn("DEV-000001");when(history.skillsJson(any())).thenReturn("[\"Java\",\"SQL\"]");
        when(mapper.lockUpload(1)).thenReturn(upload("CHECKED"));
    }
    private Upload upload(String status){return new Upload(1,"B8","UI-02",file.getOriginalFilename(),file.getSize(),"admin",status,1,1,0,0,LocalDateTime.of(2026,6,15,9,0));}
    @Test void publicTemplateIsActual17ColumnWorkbookGuideTextStylesLastInfoAndDnld() throws Exception {
        var r=mvc.perform(get("/api/excel/B8/template")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();
        assertThat(r.getResponse().getHeader("Content-Disposition")).contains("20260615_090000.xlsx");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            var s=book.getSheetAt(0);assertThat(s.getRow(0).getLastCellNum()).isEqualTo((short)17);assertThat(s.getPhysicalNumberOfRows()).isEqualTo(1);
            List<String> titles=new ArrayList<>();for(var c:s.getRow(0))titles.add(c.getStringCellValue());assertThat(titles).containsExactlyElementsOf(B8ImportDefinition.HEADERS);
            for(int i:new int[]{0,3,10,14,15,16})assertThat(s.getColumnStyle(i).getDataFormatString()).isEqualTo("@");
            StringBuilder guide=new StringBuilder();for(var row:book.getSheet("입력안내"))guide.append(row.getCell(0).getStringCellValue());
            assertThat(guide.toString()).contains("MEMBER","조건부 필수","코드명","사업자번호","HQ","미예약","경고","managementNo","monthlyPay");
            assertThat(book.getSheetName(book.getNumberOfSheets()-1)).isEqualTo("다운로드정보");assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-02")&&e.count()==0),any());verifyNoInteractions(mapper,ids,history);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","targetMonth","projectId"})
    void templateRejectsUnknownQueryBeforeGeneratingFile(String key) throws Exception {
        mvc.perform(get("/api/excel/B8/template").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(logs,mapper);
    }
    @Test void auditFailureReturnsOnlySafeJsonAndRollsBackAudit() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(logs).append(any(),any());
        var result=mvc.perform(get("/api/excel/B8/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private SQL");assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
    }
    @Test void validationPublicAdminStoresOnlyUploadAndProblemsWithNonReservedExpectedNumber() throws Exception {
        mvc.perform(multipart("/api/excel/B8/validate").file(file)).andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadId").value(1)).andExpect(jsonPath("$.data.canApply").value(true))
            .andExpect(jsonPath("$.data.expectedNumbers[0].managementNo").value("K-0001")).andExpect(jsonPath("$.data.expectedNumbers[0].id").doesNotExist()).andExpect(jsonPath("$.data.status").doesNotExist());
        verify(mapper).insertUpload(argThat(d->d.getWrite().actor().equals("admin")));verify(mapper,never()).insertDeveloper(any());verifyNoInteractions(ids,history);
    }
    @Test void validationSummaryCountsMutuallyExclusiveRowsAndSeparatesFileWarnings() throws Exception {
        file=DeveloperWorkbook.file(b->{for(int i=1;i<=3;i++)DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(0).createCell(17).setCellValue("추가");b.getSheetAt(0).getRow(2).createCell(14).setCellValue("없는곳");b.getSheetAt(0).getRow(3).getCell(1).setCellValue("");});
        mvc.perform(multipart("/api/excel/B8/validate").file(file)).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.totalRows").value(3))
            .andExpect(jsonPath("$.data.summary.successRows").value(1)).andExpect(jsonPath("$.data.summary.warningRows").value(1)).andExpect(jsonPath("$.data.summary.errorRows").value(1)).andExpect(jsonPath("$.data.summary.fileWarningCount").value(1)).andExpect(jsonPath("$.data.canApply").value(false));
        verify(mapper,never()).insertDeveloper(any());verifyNoInteractions(ids,history);
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void actorInjectionCannotForgeAdmin(String operation) throws Exception {
        var request=multipart("/api/excel/B8/"+operation).file(file).param("actor","other");if(operation.equals("apply"))request.param("uploadId","1");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,ids,history);
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void renamedNonXlsxFails415WithNoUploadOrBusinessWrites(String operation) throws Exception {
        var request=multipart("/api/excel/B8/"+operation).file(new MockMultipartFile("file","developers.XLSX",null,new byte[]{1,2,3,4}));if(operation.equals("apply")){when(mapper.lockUpload(1)).thenReturn(new Upload(1,"B8","UI-02","developers.XLSX",4,"admin","CHECKED",1,1,0,0,null));request.param("uploadId","1");}
        mvc.perform(request).andExpect(status().isUnsupportedMediaType());verify(mapper,never()).insertUpload(any());verify(mapper,never()).replaceSummary(any());verifyNoInteractions(ids,history);
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void fileLimitFails413BeforeAnyMapperInteraction(String operation) throws Exception {
        var request=multipart("/api/excel/B8/"+operation).file(new MockMultipartFile("file","large.xlsx",null,new byte[10485761]));if(operation.equals("apply"))request.param("uploadId","1");
        mvc.perform(request).andExpect(status().isPayloadTooLarge());verifyNoInteractions(mapper,ids,history);
    }
    @Test void malformedHeaderAndEmptyWorkbookNeverCreateUpload() throws Exception {
        mvc.perform(multipart("/api/excel/B8/validate").file(DeveloperWorkbook.file(b->{}))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));verifyNoInteractions(mapper);
    }
    @Test void applyPublicAdminWritesAllChildrenHistoryAndAppliedAtomically() throws Exception {
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalNumbers[0].id").value("DEV-000001")).andExpect(jsonPath("$.data.finalNumbers[0].managementNo").value("K-0001")).andExpect(jsonPath("$.data.status").doesNotExist());
        verify(mapper).insertDeveloper(argThat(w->w.actor().equals("admin")&&w.developer().positionCode().equals("MEMBER")));verify(mapper).insertPrice(argThat(w->w.effectiveMonth().equals("202002")));
        verify(mapper,times(2)).insertSkill(any());verify(history).append(argThat(h->h.tableName().equals("TB_HR_DEV")&&h.reason()!=null&&h.reason().equals("B8 업로드:1")));
        verify(mapper).markApplied(eq(1L),eq("admin"),any());verify(transactions).commit(any());verifyNoInteractions(logs);
    }
    @ParameterizedTest @ValueSource(strings={"form","screen","actor","status","filename","size"})
    void contextMismatchBlocksEveryBoundaryBeforeRecheck(String mismatch) throws Exception {
        when(mapper.lockUpload(1)).thenReturn(new Upload(1,mismatch.equals("form")?"B7":"B8",mismatch.equals("screen")?"UI-15":"UI-02",mismatch.equals("filename")?"other.xlsx":file.getOriginalFilename(),file.getSize()+(mismatch.equals("size")?1:0),mismatch.equals("actor")?"other":"admin",mismatch.equals("status")?"CANCEL":"CHECKED",1,1,0,0,null));
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verify(mapper,never()).codes();verifyNoInteractions(ids,history);
    }
    @Test void notFoundReturns404() throws Exception {when(mapper.lockUpload(1)).thenReturn(null);mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isNotFound());}
    @Test void currentDatabaseErrorCommitsReplacementThenReturns422NotSuccess() throws Exception {
        when(mapper.parts()).thenReturn(List.of());mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.summary.errorRows").value(1)).andExpect(jsonPath("$.details.canApply").value(false));
        verify(mapper).deleteIssues(1);verify(transactions).commit(any());verify(transactions,never()).rollback(any());verifyNoInteractions(ids,history);
    }
    @Test void appliedRetryReconstructsFinalNumbersFromNewHistoryWithoutRevalidationOrWrites() throws Exception {
        when(mapper.lockUpload(1)).thenReturn(upload("APPLIED"));when(mapper.recovered("B8 업로드:1")).thenReturn(List.of(new Recovered("DEV-000001","K-0088")));
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalNumbers[0].managementNo").value("K-0088")).andExpect(jsonPath("$.data.finalNumbers[0].rowNumber").value(2));
        verify(mapper,never()).codes();verify(mapper,never()).replaceSummary(any());verifyNoInteractions(ids,history);
    }
    @Test void uniqueCollisionRollsBackThenCommitsFreshCheckedDiagnostics() throws Exception {
        file=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(0).setCellValue("Direct");});when(mapper.lockUpload(1)).thenReturn(upload("CHECKED"));
        when(mapper.developers()).thenReturn(List.of(),List.of(new B8Dtos.Existing("Direct","동시 등록",null)));when(mapper.insertDeveloper(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("private"));
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isUnprocessableEntity());
        var order=inOrder(transactions);order.verify(transactions).getTransaction(any());order.verify(transactions).rollback(any());order.verify(transactions).getTransaction(any());order.verify(transactions).commit(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void historyOrIssueFailureRollsBackAndCannotMarkApplied() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private")).when(history).append(any());
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isInternalServerError());verify(transactions).rollback(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void replacementFailureRollsBackBeforeBusinessWrites() throws Exception {
        when(mapper.parts()).thenReturn(List.of());when(mapper.insertIssue(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private"));
        mvc.perform(multipart("/api/excel/B8/apply").file(file).param("uploadId","1")).andExpect(status().isInternalServerError());verify(transactions).rollback(any());verifyNoInteractions(ids,history);
    }
}
