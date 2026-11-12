package com.sicost.b9;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.projects.*;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
import static com.sicost.b9.B9Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B9Controller.class)
@ContextConfiguration(classes=B9Controller.class)
@Import({B9Service.class,B9Repository.class,B9ImportDefinition.class,ExcelImportService.class,ExcelExportService.class,
    ProjectRegistrationService.class,WriteCoordinator.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectImportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean B9Mapper mapper;
    @MockBean ProjectRegistrationMapper projects;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean AccessLogWriter logs;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    MockMultipartFile file;
    final AtomicReference<Upload> stored=new AtomicReference<>();
    final List<Receipt> receipts=new ArrayList<>();
    final LocalDateTime now=LocalDateTime.of(2026,6,15,9,0);
    @BeforeEach void setup() throws Exception {
        file=ProjectWorkbook.valid();receipts.clear();stored.set(upload("CHECKED"));
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(transactions.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(mapper.codes()).thenReturn(ProjectWorkbook.codes());when(mapper.customers()).thenReturn(List.of(new Candidate("C-1","고객","0012345678")));when(mapper.parts()).thenReturn(List.of(new Part("HQ","본사",true)));
        when(mapper.defaults("202606")).thenReturn(new Defaults("202501","20","50000","20000"));
        when(mapper.insertUpload(any())).thenAnswer(call->{((UploadDraft)call.getArgument(0)).setUploadId(1L);return 1;});
        when(mapper.insertIssue(any())).thenReturn(1);when(mapper.replaceSummary(any())).thenReturn(1);
        when(projects.insertProject(any())).thenReturn(1);when(projects.insertInitialPart(any())).thenReturn(1);when(projects.insertContract(any())).thenReturn(1);
        when(mapper.lockUpload(1)).thenAnswer(call->stored.get());
        when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2026,sha(file))));
        when(mapper.markApplied(eq(1L),any(),any())).thenAnswer(call->{stored.set(upload("APPLIED"));return 1;});
        doAnswer(call->{ChangeHistoryWriter.Change change=call.getArgument(0);if(change.tableName().equals("TB_PJ_PROJ"))receipts.add(new Receipt(change.after(),change.reason()));return null;}).when(history).append(any());
        when(mapper.results("B9 업로드:1;행:")).thenAnswer(call->List.copyOf(receipts));
    }
    private String sha(MockMultipartFile f) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(f.getBytes()));}
    private Upload upload(String status){return new Upload(1,"B9","UI-01",file.getOriginalFilename(),file.getSize(),"admin",status,1,1,0,0,null,null,status.equals("APPLIED")?now:null);}
    private void contextForFile() throws Exception {stored.set(upload("CHECKED"));when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2026,sha(file))));}
    @Test void validateRejectsJsonBeforeUploadOrBusinessWrites() throws Exception {
        mvc.perform(post("/api/excel/B9/validate").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(mapper,projects,history,transactions);
    }
    @Test void applyRejectsJsonBeforeUploadOrBusinessWrites() throws Exception {
        mvc.perform(post("/api/excel/B9/apply").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(mapper,projects,history,transactions);
    }
    @Test void publicTemplateIsActualEmpty15ColumnWorkbookWithGuideAndDnldBeforeResponse() throws Exception {
        var result=mvc.perform(get("/api/excel/B9/template").header("X-Request-Id","b9-template-test")).andExpect(status().isOk()).andExpect(header().string("X-Request-Id","b9-template-test"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)15);assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            List<String> titles=new ArrayList<>();for(var cell:sheet.getRow(0))titles.add(cell.getStringCellValue());assertThat(titles).containsExactlyElementsOf(B9ImportDefinition.HEADERS);
            assertThat(sheet.getColumnStyle(0).getDataFormatString()).isEqualTo("@");StringBuilder guide=new StringBuilder();for(var row:book.getSheet("입력안내"))guide.append(row.getCell(0).getStringCellValue());
            assertThat(guide.toString()).contains("기존 프로젝트 수정은 화면", "사업자", "MANUAL", "TGT_MRGN_RT", "TEAM_FEE");
            assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-01")&&e.count()==0),any());verifyNoInteractions(mapper,projects,history);
    }
    @Test void auditCommitFailureSendsOnlySafe500JsonNeverWorkbook() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(transactions).commit(any());
        var r=mvc.perform(get("/api/excel/B9/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().exists("X-Request-Id")).andReturn();
        assertThat(r.getResponse().getHeader("Content-Disposition")).isNull();assertThat(r.getResponse().getContentAsString()).doesNotContain("private SQL");verifyNoInteractions(projects);
    }
    @ParameterizedTest @ValueSource(strings={"year","actor","projectId","expectedRevision"})
    void templateRejectsUnknownFieldBeforeGeneration(String key) throws Exception {
        mvc.perform(get("/api/excel/B9/template").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(key));verifyNoInteractions(logs,projects);
    }
    @Test void validationPublicAdminRecordsCheckedAndContextOnly() throws Exception {
        mvc.perform(multipart("/api/excel/B9/validate").file(file).param("year","2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.uploadId").value(1)).andExpect(jsonPath("$.data.canApply").value(true)).andExpect(jsonPath("$.data.summary.totalRows").value(1)).andExpect(jsonPath("$.data.summary.successRows").value(1));
        verify(mapper).insertUpload(argThat(d->d.getWrite().actor().equals("admin")));verify(history).append(argThat(c->c.tableName().equals("TB_SY_UPLD")&&c.item().equals("검증 문맥")&&c.after().contains("contentSha256")&&c.after().contains("2026")));
        verifyNoInteractions(projects);verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void missingFileIsFieldError(String op) throws Exception {
        var request=multipart("/api/excel/B9/"+op).param("year","2026");if(op.equals("apply"))request.param("uploadId","1");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("file"));verifyNoInteractions(projects);
    }
    @Test void missingUploadIdHasExactField() throws Exception {
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("year","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("uploadId"));verifyNoInteractions(projects);
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void missingYearAndUnregisteredMultipartFieldAreRejected(String op) throws Exception {
        var a=multipart("/api/excel/B9/"+op).file(file);if(op.equals("apply"))a.param("uploadId","1");
        mvc.perform(a).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));
        mvc.perform(multipart("/api/excel/B9/"+op).file(file).param("year","2026").param("actor","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("actor"));verifyNoInteractions(projects);
    }
    @Test void all15TitlesAreRequired() throws Exception {
        file=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(0).removeCell(b.getSheetAt(0).getRow(0).getCell(14));});
        mvc.perform(multipart("/api/excel/B9/validate").file(file).param("year","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("file"));verifyNoInteractions(mapper,projects);
    }
    @Test void customerAmbiguityReturnsAllNumbersAndPreventsApply() throws Exception {
        when(mapper.customers()).thenReturn(List.of(new Candidate("C-1","고객","0012345678"),new Candidate("C-2","고객","0012345679")));
        mvc.perform(multipart("/api/excel/B9/validate").file(file).param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canApply").value(false))
            .andExpect(jsonPath("$.data.issues.items[0].candidates[1].businessNo").value("0012345679"));verifyNoInteractions(projects);
    }
    @Test void warningRowsAndErrorsAreMutuallyExclusiveAndFileWarningsSeparate() throws Exception {
        file=ProjectWorkbook.file(b->{for(int i=1;i<=3;i++)ProjectWorkbook.validRow(b,i);b.getSheetAt(0).getRow(0).createCell(15).setCellValue("알수없음");b.getSheetAt(0).getRow(2).getCell(3).setCellValue("내부(자체 개발)");b.getSheetAt(0).getRow(3).getCell(1).setCellValue("");});
        mvc.perform(multipart("/api/excel/B9/validate").file(file).param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.totalRows").value(3))
            .andExpect(jsonPath("$.data.summary.successRows").value(1)).andExpect(jsonPath("$.data.summary.warningRows").value(1)).andExpect(jsonPath("$.data.summary.errorRows").value(1)).andExpect(jsonPath("$.data.summary.fileWarningCount").value(1));
        verify(mapper,atLeastOnce()).insertIssue(argThat(w->w.issue().severity().equals("WARN")));verifyNoInteractions(projects);
    }
    @Test void applyUsesCommonRegistrationWithOriginalAmountPartContractAndExactAuditMapping() throws Exception {
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.formType").value("B9")).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.results[0].rowNumber").value(2)).andExpect(jsonPath("$.data.results[0].id").value("P-0001")).andExpect(jsonPath("$.data.results[0].name").value("검사 프로젝트")).andExpect(jsonPath("$.data.revision").isString());
        var order=inOrder(locks,projects);order.verify(locks).lock("PROJECT:CREATE");order.verify(locks).lock("UPLOAD:1");order.verify(projects).existingIds();order.verify(projects).insertProject(argThat(w->w.project().contractAmount().toPlainString().equals("1000")));order.verify(projects).insertInitialPart(argThat(w->w.startMonth().equals("202601")));order.verify(projects).insertContract(any());
        verify(history).append(argThat(c->c.tableName().equals("TB_PJ_PROJ")&&c.type().equals("NEW")&&c.item().equals("등록")&&c.reason().equals("B9 업로드:1;행:2")&&c.after().contains("P-0001")));
        verify(mapper).markApplied(eq(1L),eq("admin"),any());verify(transactions,atLeastOnce()).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"actor","form","screen","filename","size","target","step","year","digest"})
    void contextMismatch409LeavesCheckedAndBusinessUnchanged(String which) throws Exception {
        stored.set(new Upload(1,which.equals("form")?"B8":"B9",which.equals("screen")?"UI-02":"UI-01",which.equals("filename")?"other.xlsx":file.getOriginalFilename(),file.getSize()+(which.equals("size")?1:0),which.equals("actor")?"other":"admin","CHECKED",1,1,0,0,which.equals("target")?"P-1":null,which.equals("step")?"PLAN":null,null));
        if(which.equals("year"))when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2027,sha(file))));
        if(which.equals("digest"))when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2026,"0".repeat(64))));
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));
        assertThat(stored.get().status()).isEqualTo("CHECKED");verify(mapper,never()).replaceSummary(any());verifyNoInteractions(projects);
    }
    @Test void notFoundIs404() throws Exception {stored.set(null);mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isNotFound());}
    @Test void newlyDuplicatedExplicitIdCommitsSameUploadDiagnosticsAnd422WithoutAnyProjects() throws Exception {
        file=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(0).setCellValue("Taken");});contextForFile();when(mapper.existingIds()).thenReturn(List.of("Taken"));
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.uploadId").value(1)).andExpect(jsonPath("$.details.canApply").value(false));
        verify(mapper).deleteIssues(1);verify(mapper).replaceSummary(argThat(w->w.summary().errorRows()==1));verify(transactions).commit(any());verifyNoInteractions(projects);assertThat(stored.get().status()).isEqualTo("CHECKED");
    }
    @Test void currentInactiveReferenceAlsoCommits422Diagnostics() throws Exception {
        when(mapper.parts()).thenReturn(List.of());mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isUnprocessableEntity());verify(mapper).deleteIssues(1);verifyNoInteractions(projects);
    }
    @Test void appliedRetryReturnsOriginalReceiptAndRevisionWithoutAnyMutation() throws Exception {
        stored.set(upload("APPLIED"));receipts.add(new Receipt("{\"id\":\"P-0900\",\"name\":\"최초 이름\"}","B9 업로드:1;행:2"));
        String first=mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.results[0].name").value("최초 이름")).andReturn().getResponse().getContentAsString();
        String second=mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);verify(mapper,never()).codes();verify(mapper,never()).replaceSummary(any());verify(mapper,never()).markApplied(anyLong(),any(),any());verifyNoInteractions(projects,history);
    }
    @Test void databaseRaceRollsBackThenCommitsReplacementDiagnosticsInNewTransaction() throws Exception {
        file=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(0).setCellValue("Taken");});contextForFile();
        when(mapper.existingIds()).thenReturn(List.of(),List.of("Taken"));when(projects.insertProject(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("private SQL"));
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isUnprocessableEntity());
        verify(transactions,atLeastOnce()).rollback(any());verify(transactions).commit(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void requiredHistoryFailureRollsBackAllAndNeverMarksApplied() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(history).append(any());
        var r=mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2026")).andExpect(status().isInternalServerError()).andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("private SQL");verify(transactions,atLeastOnce()).rollback(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void oversizeAndTooManyHiddenRows413(String op) throws Exception {
        var a=multipart("/api/excel/B9/"+op).file(new MockMultipartFile("file","large.xlsx",null,new byte[10485761])).param("year","2026");if(op.equals("apply"))a.param("uploadId","1");mvc.perform(a).andExpect(status().isPayloadTooLarge());
        var many=ProjectWorkbook.file(b->{for(int i=1;i<=5001;i++){var row=b.getSheetAt(0).createRow(i);row.createCell(1).setCellValue("행");row.setZeroHeight(true);}});
        var b=multipart("/api/excel/B9/"+op).file(many).param("year","2026");if(op.equals("apply"))b.param("uploadId","1");mvc.perform(b).andExpect(status().isPayloadTooLarge());verifyNoInteractions(projects);
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"})
    void nonOoxmlAndEncrypted415CorruptAndEmpty400(String op) throws Exception {
        for(byte[] bytes:List.of(new byte[]{1,2,3,4},new byte[]{(byte)0xd0,(byte)0xcf,0,0,0,0,0,0})) {
            var r=multipart("/api/excel/B9/"+op).file(new MockMultipartFile("file","renamed.xlsx",null,bytes)).param("year","2026");if(op.equals("apply"))r.param("uploadId","1");mvc.perform(r).andExpect(status().isUnsupportedMediaType());
        }
        for(MockMultipartFile f:List.of(new MockMultipartFile("file","empty.xlsx",null,new byte[0]),ProjectWorkbook.file(b->{}),new MockMultipartFile("file","corrupt.xlsx",null,new byte[]{'P','K',0,0}))) {
            var r=multipart("/api/excel/B9/"+op).file(f).param("year","2026");if(op.equals("apply"))r.param("uploadId","1");mvc.perform(r).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        verifyNoInteractions(projects);
    }
    @Test void pastYearValidateAndApplyAre409AndNeverRegister() throws Exception {
        mvc.perform(multipart("/api/excel/B9/validate").file(file).param("year","2025")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));
        when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2025,sha(file))));
        mvc.perform(multipart("/api/excel/B9/apply").file(file).param("uploadId","1").param("year","2025")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));verifyNoInteractions(projects);
    }
}
