package com.sicost.b6;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.b9.B9Dtos.Context;
import com.sicost.b9.B9Dtos.Upload;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.b6.B6Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(B6Controller.class)
@ContextConfiguration(classes=B6Controller.class)
@Import({B6Service.class,B6Repository.class,B6ImportDefinition.class,ExcelImportService.class,ExcelExportService.class,
    WriteCoordinator.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DistributionImportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean B6Mapper mapper;
    @MockBean ProjectBudgetRepository projects;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean AccessLogWriter logs;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    MockMultipartFile file;
    final AtomicReference<Upload> stored=new AtomicReference<>();
    final AtomicReference<String> snapshot=new AtomicReference<>("before");
    final LocalDateTime now=LocalDateTime.of(2026,6,15,9,0);
    @BeforeEach void setup() throws Exception {
        file=DistributionWorkbook.valid();snapshot.set("before");stored.set(upload("CHECKED"));
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.project("P-TEST")).thenReturn(DistributionWorkbook.project());when(projects.operatingValue("CASH_BASE_YM","202606")).thenReturn(new BigDecimal("202501"));
        when(projects.snapshot("P-TEST")).thenAnswer(c->snapshot.get());
        when(mapper.insertUpload(any())).thenAnswer(c->{((B6UploadDraft)c.getArgument(0)).setUploadId(1L);return 1;});
        when(mapper.insertIssue(any())).thenReturn(1);when(mapper.replaceSummary(any())).thenReturn(1);
        when(mapper.insertCell(any())).thenAnswer(c->{snapshot.set("after");return 1;});
        when(mapper.lockUpload(1)).thenAnswer(c->stored.get());
        contextForFile();
        when(mapper.markApplied(eq(1L),any(),any())).thenAnswer(c->{stored.set(upload("APPLIED"));return 1;});
    }
    private Upload upload(String status){return new Upload(1,"B6","UI-01",file.getOriginalFilename(),file.getSize(),"admin",status,1,1,0,0,"P-TEST","PLAN",status.equals("APPLIED")?now:null);}
    private void contextForFile() throws Exception {stored.set(upload("CHECKED"));when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2026,sha(file))));}
    private String sha(MockMultipartFile f) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(f.getBytes()));}
    private MockMultipartHttpServletRequestBuilder request(String op){var r=switch(op){case "validate" -> multipart("/api/excel/B6/validate");case "apply" -> multipart("/api/excel/B6/apply");default -> throw new IllegalArgumentException("Unknown B6 operation: "+op);};r.file(file);r.param("year","2026").param("projectId","P-TEST").param("step","PLAN");if(op.equals("apply"))r.param("uploadId","1").param("expectedRevision",revisions.revision("before"));return r;}
    @Test void validateRejectsJsonBeforeUploadOrBusinessWrites() throws Exception {
        mvc.perform(post("/api/excel/B6/validate").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(mapper,projects,history,transactions);
    }
    @Test void applyRejectsJsonBeforeUploadOrBusinessWrites() throws Exception {
        mvc.perform(post("/api/excel/B6/apply").contentType("application/json").content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(mapper,projects,history,transactions);
    }
    @Test void publicTemplateUsesCurrentContractMonthsBlankDataWonAndCommittedDnldBeforeBytes() throws Exception {
        var r=mvc.perform(get("/api/excel/B6/template").param("projectId","P-TEST").header("X-Request-Id","b6-template"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","b6-template")).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);List<String> columns=new ArrayList<>();for(var cell:sheet.getRow(0))columns.add(cell.getStringCellValue());
            assertThat(columns).containsExactly("구분","관리번호","이름","항목명","2026-06","2026-07","합계");assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(book.getSheet("입력안내")).isNotNull();assertThat(book.getSheet("다운로드정보").getRow(4).getCell(1).getStringCellValue()).isEqualTo("원");
            assertThat(book.getSheet("다운로드정보").getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-01")),any());verify(transactions,atLeastOnce()).commit(any());verify(mapper,never()).insertCell(any());
    }
    @Test void templateMissingProjectId400AndMissingProject404SendNoFile() throws Exception {
        mvc.perform(get("/api/excel/B6/template")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("projectId"));
        mvc.perform(get("/api/excel/B6/template").param("projectId","missing")).andExpect(status().isNotFound());verifyNoInteractions(logs);
    }
    @Test void nonTkTargetRejected() throws Exception {
        var p=DistributionWorkbook.project();when(mapper.project("P-TEST")).thenReturn(new Project(p.id(),p.name(),"MO",p.status(),p.startDate(),p.endDate(),p.contractAmount()));
        mvc.perform(get("/api/excel/B6/template").param("projectId","P-TEST")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("projectId"));verifyNoInteractions(logs);
    }
    @Test void failedDnldCommitReturnsSafe500WithNoWorkbookHeaders() throws Exception {
        doThrow(new IllegalStateException("private SQL")).when(transactions).commit(any());
        var r=mvc.perform(get("/api/excel/B6/template").param("projectId","P-TEST")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(r.getResponse().getHeader("Content-Disposition")).isNull();assertThat(r.getResponse().getContentAsString()).doesNotContain("private SQL");
    }
    @ParameterizedTest @ValueSource(strings={"year","step","actor"}) void unknownTemplateQueryRejected(String key) throws Exception {
        mvc.perform(get("/api/excel/B6/template").param("projectId","P-TEST").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(key));verifyNoInteractions(logs);
    }
    @Test void publicValidationStoresOnlyCheckedContextIssuesAndProjectRevision() throws Exception {
        mvc.perform(request("validate")).andExpect(status().isOk()).andExpect(jsonPath("$.data.canApply").value(true)).andExpect(jsonPath("$.data.uploadedTableTotal").value("100")).andExpect(jsonPath("$.data.revision").value(revisions.revision("before")));
        verify(mapper).insertUpload(argThat(d->d.getWrite().actor().equals("admin")&&d.getWrite().projectId().equals("P-TEST")&&d.getWrite().step().equals("PLAN")));
        verify(history).append(argThat(c->c.tableName().equals("TB_SY_UPLD")&&c.item().equals("검증 문맥")&&c.after().contains("contentSha256")));
        verify(mapper,never()).deleteStage(any(),any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"step","expectedRevision"}) void missingApplyFieldsAre400WithoutStateChanges(String field) throws Exception {
        var r=request("apply");r.with(req->{req.removeParameter(field);return req;});
        mvc.perform(r).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));verify(mapper,never()).replaceSummary(any());verify(mapper,never()).deleteStage(any(),any());
    }
    @Test void fileMissingAndUnknownMultipartField400() throws Exception {
        mvc.perform(multipart("/api/excel/B6/validate").param("year","2026").param("projectId","P-TEST").param("step","PLAN")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("file"));
        mvc.perform(request("validate").param("actor","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actor"));
    }
    @Test void successReplacesOnlyRequestedStagePersistsAllZeroCellsAndAuditsAtomically() throws Exception {
        mvc.perform(request("apply")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.projectId").value("P-TEST")).andExpect(jsonPath("$.data.step").value("PLAN")).andExpect(jsonPath("$.data.results").isEmpty());
        verify(mapper).deleteStage("P-TEST","PLAN");verify(mapper,times(2)).insertCell(any());verify(mapper).insertCell(argThat(w->w.cell().month().equals("202607")&&w.cell().amount().signum()==0&&w.cell().rowSn()==1));
        verify(history,atLeastOnce()).append(argThat(c->c.tableName().equals("TB_PJ_PROJ")&&c.reason().equals("B6 업로드:1")));verify(mapper).markApplied(eq(1L),eq("admin"),any());
    }
    @ParameterizedTest @ValueSource(strings={"actor","project","step","year","digest","form","screen","filename","size"})
    void immutableUploadContextMismatch409NeverMutates(String field) throws Exception {
        stored.set(new Upload(1,field.equals("form")?"B9":"B6",field.equals("screen")?"UI-02":"UI-01",field.equals("filename")?"other.xlsx":file.getOriginalFilename(),file.getSize()+(field.equals("size")?1:0),field.equals("actor")?"other":"admin","CHECKED",1,1,0,0,field.equals("project")?"another":"P-TEST",field.equals("step")?"EST":"PLAN",null));
        if(field.equals("year"))when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2027,sha(file))));
        if(field.equals("digest"))when(mapper.context(1)).thenReturn(json.writeValueAsString(new Context(2026,"0".repeat(64))));
        mvc.perform(request("apply")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));verify(mapper,never()).deleteStage(any(),any());verify(mapper,never()).replaceSummary(any());
    }
    @Test void staleAggregateRevision409() throws Exception {snapshot.set("concurrent");mvc.perform(request("apply")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(mapper,never()).deleteStage(any(),any());}
    @Test void protectedSelectedItemZeroToOne409IncludesMonthDifferenceAndNoReplacement() throws Exception {
        file=DistributionWorkbook.file(b->DistributionWorkbook.item(b,1,"항목","1","0"));contextForFile();
        when(mapper.cells("P-TEST")).thenReturn(List.of(new Cell("EST",1,"202606","ITEM",null,"항목",BigDecimal.ZERO)));when(mapper.protectedMonths("P-TEST")).thenReturn(List.of("202606"));
        mvc.perform(request("apply")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH")).andExpect(jsonPath("$.details.differences[0].month").value("2026-06")).andExpect(jsonPath("$.details.differences[0].difference").value("1"));verify(mapper,never()).deleteStage(any(),any());verify(mapper,never()).replaceSummary(any());
    }
    @Test void latestDeveloperChange422CommitsReplacementIssuesWithoutAnyBusinessWrite() throws Exception {
        file=DistributionWorkbook.file(b->DistributionWorkbook.person(b,1,"000123","인력"));contextForFile();
        mvc.perform(request("apply")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.canApply").value(false)).andExpect(jsonPath("$.details.uploadId").value(1));verify(mapper).deleteIssues(1);verify(mapper).replaceSummary(any());verify(transactions).commit(any());verify(mapper,never()).deleteStage(any(),any());
    }
    @Test void latestContractPeriod422KeepsCheckedDiagnostics() throws Exception {
        var p=DistributionWorkbook.project();when(mapper.project("P-TEST")).thenReturn(new Project(p.id(),p.name(),p.contractType(),p.status(),p.startDate(),p.endDate().plusMonths(1),p.contractAmount()));
        mvc.perform(request("apply")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.summary.errorRows").value(1));verify(mapper).deleteIssues(1);verify(mapper,never()).deleteStage(any(),any());
    }
    @Test void mandatoryAuditFailureRollsBackAndNeverAppliesUpload() throws Exception {
        doThrow(new IllegalStateException("private SQL")).when(history).append(any());
        var r=mvc.perform(request("apply")).andExpect(status().isInternalServerError()).andReturn();assertThat(r.getResponse().getContentAsString()).doesNotContain("private SQL");verify(transactions,atLeastOnce()).rollback(any());verify(mapper,never()).markApplied(anyLong(),any(),any());
    }
    @Test void appliedRetryRestoresOriginalResultEvenAfterProjectChangesAndNeverReplaces() throws Exception {
        stored.set(upload("APPLIED"));snapshot.set("later project");
        String a=mvc.perform(request("apply")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String b=mvc.perform(request("apply")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(b).isEqualTo(a);
        verifyNoInteractions(projects,history);verify(mapper,never()).project(any());verify(mapper,never()).replaceSummary(any());verify(mapper,never()).deleteStage(any(),any());
    }
    @Test void pastYearAndLoseAre409NotAuthorizationErrors() throws Exception {
        mvc.perform(request("validate").param("year","2025")).andExpect(status().isBadRequest());
        var r=request("validate");r.with(req->{req.setParameter("year","2025");return req;});mvc.perform(r).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));
        var p=DistributionWorkbook.project();when(mapper.project("P-TEST")).thenReturn(new Project(p.id(),p.name(),p.contractType(),"LOSE",p.startDate(),p.endDate(),p.contractAmount()));
        mvc.perform(request("validate")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));verify(mapper,never()).insertUpload(any());
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"}) void tooLargeFileAnd5001RowsAre413(String op) throws Exception {
        file=new MockMultipartFile("file","large.xlsx",null,new byte[10485761]);mvc.perform(request(op)).andExpect(status().isPayloadTooLarge());
        file=DistributionWorkbook.file(b->{for(int i=1;i<=5001;i++)DistributionWorkbook.item(b,i,"항목","0","0");});contextForFile();mvc.perform(request(op)).andExpect(status().isPayloadTooLarge());verify(mapper,never()).deleteStage(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"validate","apply"}) void nonOoxmlEncrypted415AndCorruptEmpty400(String op) throws Exception {
        for(byte[] bytes:List.of(new byte[]{1,2,3,4},new byte[]{(byte)0xd0,(byte)0xcf,0,0,0,0,0,0})) {
            file=new MockMultipartFile("file","renamed.xlsx",null,bytes);contextForFile();mvc.perform(request(op)).andExpect(status().isUnsupportedMediaType());
        }
        for(MockMultipartFile f:List.of(new MockMultipartFile("file","empty.xlsx",null,new byte[0]),DistributionWorkbook.file(b->{}),new MockMultipartFile("file","corrupt.xlsx",null,new byte[]{'P','K',0,0}))) {
            file=f;contextForFile();mvc.perform(request(op)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        verify(mapper,never()).deleteStage(any(),any());
    }
}
