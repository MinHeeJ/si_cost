package com.sicost.b2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.b7.B7Dtos.Issue;
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
import static com.sicost.b2.FinancialImportDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialImportController.class)
@ContextConfiguration(classes=FinancialImportController.class)
@Import({FinancialImportService.class,FinancialImportRepository.class,FinancialImportDefinition.class,ExcelImportService.class,
    RevisionService.class,WriteCoordinator.class,ChangeHistoryWriter.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class FinancialImportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean FinancialImportMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper master;
    @MockBean CommitInvalidation invalidation;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<StoredItem> items;
    List<Metadata> metadata;
    List<Issue> issues;
    List<ChangeHistoryEntry> audits;
    Upload upload;
    MockMultipartFile file;
    @BeforeEach void setup()throws Exception {
        items=new ArrayList<>();metadata=new ArrayList<>();issues=new ArrayList<>();audits=new ArrayList<>();upload=null;file=FinancialWorkbook.valid();
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        Clock fixed=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.cashBase()).thenReturn("202601");when(mapper.items(any())).thenAnswer(c->List.copyOf(items));when(mapper.snapshot(any())).thenAnswer(c->items.toString());
        when(mapper.codes()).thenReturn(List.of(new Code("FIN_CLS","SGA","판관비",null),new Code("FIN_CLS","NOPR_IN","영업외수익",null),new Code("SGA_ITEM","RENT","임대료",null)));
        when(mapper.upload(anyLong())).thenAnswer(c->upload);when(mapper.lockUpload(anyLong())).thenAnswer(c->upload);
        when(mapper.metadata(anyLong())).thenAnswer(c->List.copyOf(metadata));when(mapper.issues(anyLong())).thenAnswer(c->List.copyOf(issues));
        when(mapper.insertUpload(any())).thenAnswer(c->{Draft d=c.getArgument(0);d.setUploadId(19L);setUpload(d.getWrite(),"CHECKED");return 1;});
        when(mapper.replaceSummary(any())).thenAnswer(c->{setUpload(c.getArgument(0),"CHECKED");return 1;});
        when(mapper.insertIssue(any())).thenAnswer(c->{issues.add(((IssueWrite)c.getArgument(0)).issue());return 1;});
        when(mapper.deleteIssues(anyLong())).thenAnswer(c->{issues.clear();return 1;});
        when(history.insert(any())).thenAnswer(c->{ChangeHistoryEntry a=c.getArgument(0);audits.add(a);if(a.tableName().equals("TB_SY_UPLD"))metadata.add(new Metadata(a.item(),a.after()));return 1;});
        when(mapper.deleteUploaded(any())).thenAnswer(c->{int count=items.size();items.removeIf(r->r.uploadId()!=null);return count-items.size();});
        when(mapper.insertItem(any())).thenAnswer(c->{ItemWrite w=c.getArgument(0);Values v=w.value();items.add(new StoredItem(w.month(),w.itemSn(),v.finClass(),v.sgaItemCode(),v.itemName(),v.amount(),v.vatAmount(),v.ioDate(),v.repeat(),v.remark(),"N",w.uploadId(),w.actor(),w.now(),null,null));return 1;});
        when(mapper.markApplied(any())).thenAnswer(c->{setUpload(c.getArgument(0),"APPLIED");return 1;});
        when(mapper.resultItems(anyLong())).thenAnswer(c->audits.stream().filter(a->a.tableName().equals("TB_FI_ITEM")&&a.type().equals("NEW")&&a.item().equals("ITEM_NM")).map(a->new ResultItem(Integer.parseInt(a.reason().split(":")[2]),a.targetKey().split(":")[1])).toList());
    }
    void setUpload(UploadWrite w,String status) { Summary s=w.summary();upload=new Upload(19,"B2","UI-10",w.filename(),w.size(),w.month(),null,null,"admin",status,s.totalRows(),s.successRows(),s.warningRows(),s.errorRows(),status.equals("APPLIED")?w.now():null); }
    MockHttpServletRequestBuilder check() { return multipart("/api/excel/B2/validate").file(file).param("month","2026-06"); }
    MockHttpServletRequestBuilder apply(String revision) { return multipart("/api/excel/B2/apply").file(file).param("month","2026-06").param("uploadId","19").param("expectedRevision",revision); }
    String checked()throws Exception { var result=mvc.perform(check()).andExpect(status().isOk()).andReturn();return json.readTree(result.getResponse().getContentAsString()).at("/data/expectedRevision").asText(); }
    StoredItem row(int id,Long uploadId,LocalDate date) { return new StoredItem("202606",id,"SGA",null,"원래 항목",BigDecimal.ONE,BigDecimal.ZERO,date,"N",null,"N",uploadId,"admin",LocalDateTime.of(2026,6,1,0,0),null,null); }
    void noBusiness() { verify(mapper,never()).deleteUploaded(any());verify(mapper,never()).insertItem(any()); }
    @Test void publicTemplateIsEmptyEightColumnsNumericMoneyTextCodesAndCommittedAudit()throws Exception {
        var response=mvc.perform(get("/api/excel/B2/template").param("month","2026-06")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)8);
            for(int i=0;i<8;i++)assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(FinancialImportDefinition.HEADERS.get(i)+(Set.of(0,2,3).contains(i)?"*":""));
            assertThat(sheet.getColumnStyle(3).getDataFormatString()).isEqualTo("0");assertThat(sheet.getColumnStyle(0).getDataFormatString()).isEqualTo("@");
            assertThat(book.getSheet("다운로드정보").getRow(5).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
        }
        verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-10")),any());verify(manager,times(2)).commit(any());noBusiness();
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","unknown"}) void unknownFieldsRejectedOnAllOperations(String key)throws Exception {
        mvc.perform(get("/api/excel/B2/template").param("month","2026-06").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(check().param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(apply("x").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(history,logs);noBusiness();
    }
    @ParameterizedTest @ValueSource(strings={"0000-01","2026-13","2026-6","bad"}) void invalidMonthNoDownloadOrUpload(String value)throws Exception {
        mvc.perform(get("/api/excel/B2/template").param("month",value)).andExpect(status().isBadRequest());verifyNoInteractions(logs);noBusiness();
    }
    @Test void downloadAuditFailureDoesNotExposeWorkbook()throws Exception { doThrow(new IllegalStateException("private SQL")).when(logs).append(any(),any());mvc.perform(get("/api/excel/B2/template").param("month","2026-06")).andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));noBusiness(); }
    @Test void publicValidateStoresOnlyCheckedMetadataAndReturnsStringIdCountsAndRevision()throws Exception {
        items.add(row(1,null,null));items.add(row(2,5L,null));
        mvc.perform(check()).andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadId").value("19")).andExpect(jsonPath("$.data.status").value("CHECKED")).andExpect(jsonPath("$.data.summary.replaceCount").value(1)).andExpect(jsonPath("$.data.summary.preserveCount").value(1)).andExpect(jsonPath("$.data.summary.successRows").value(1)).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.appliedAt").value(org.hamcrest.Matchers.nullValue()));
        assertThat(audits).allSatisfy(a->assertThat(a.tableName()).isEqualTo("TB_SY_UPLD"));assertThat(metadata).noneMatch(m->m.value().contains("임대료"));noBusiness();
    }
    @Test void recheckCreatesNewUploadNotReplacingExistingDiagnostics()throws Exception { checked();clearInvocations(mapper);checked();verify(mapper).insertUpload(any());verify(mapper,never()).deleteIssues(anyLong());noBusiness(); }
    @Test void applyReplacesOnlyUploadedRowsAndWritesNewAndDeleteAudit()throws Exception {
        var manual=row(1,null,null);items.add(manual);items.add(row(2,5L,null));String revision=checked();
        mvc.perform(apply(revision)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.items[0].rowNumber").value(2)).andExpect(jsonPath("$.data.items[0].itemSn").value("3"));
        assertThat(items).contains(manual).hasSize(2);assertThat(items.get(1).amount()).isEqualByComparingTo("0");assertThat(items.get(1).repeat()).isEqualTo("N");assertThat(items.get(1).uploadId()).isEqualTo(19L);
        assertThat(audits).anyMatch(a->a.tableName().equals("TB_FI_ITEM")&&a.type().equals("DEL")&&a.item().equals("ITEM_NM")&&a.before().equals("원래 항목"));
        var order=inOrder(master,locks);order.verify(master,atLeastOnce()).lockClose();order.verify(locks,atLeastOnce()).lock(anyString());
    }
    @Test void sameCountContentChangeIsStaleAndWritesNothing()throws Exception { items.add(row(1,null,null));String revision=checked();items.set(0,row(1,null,LocalDate.of(2026,6,2)));mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noBusiness(); }
    @Test void latestCodeErrorCommitsOnlyNewCheckedDiagnosticsThen422()throws Exception { String revision=checked();when(mapper.codes()).thenReturn(Collections.emptyList());mvc.perform(apply(revision)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.financial.upload.status").value("CHECKED")).andExpect(jsonPath("$.details.financial.upload.canApply").value(false));verify(mapper).replaceSummary(any());assertThat(issues).isNotEmpty();noBusiness(); }
    @Test void issueReplacementFailureRollsBackAndReturns500()throws Exception { String revision=checked();when(mapper.codes()).thenReturn(Collections.emptyList());doThrow(new IllegalStateException("private")).when(mapper).insertIssue(any());mvc.perform(apply(revision)).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());noBusiness(); }
    @Test void appliedReplayIgnoresClosedPeriodCurrentRevisionAndReferences()throws Exception { String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());clearInvocations(mapper,history);when(mapper.closed(any())).thenReturn(true);when(mapper.codes()).thenReturn(Collections.emptyList());items.clear();mvc.perform(apply("obsolete")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].itemSn").value("1"));noBusiness();verify(mapper,never()).snapshot(any());verifyNoInteractions(history); }
    @Test void contextMismatchBeforeAppliedReplay()throws Exception { String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());clearInvocations(mapper);mvc.perform(apply(revision).param("month","2026-05")).andExpect(status().isBadRequest());noBusiness(); }
    @Test void alteredFileIsContextMismatch()throws Exception { String revision=checked();file=FinancialWorkbook.file(List.of(List.of("판관비","","변경","1")));mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));noBusiness(); }
    @Test void missingUploadIs404()throws Exception { mvc.perform(apply("revision")).andExpect(status().isNotFound());noBusiness(); }
    @Test void selectedCloseIs409AndNoWrites()throws Exception { when(mapper.closed("202606")).thenReturn(true);mvc.perform(check()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noBusiness(); }
    @Test void oldCashMonthCloseProtectsReplacement()throws Exception { items.add(row(1,5L,LocalDate.of(2026,5,1)));String revision=checked();when(mapper.closed("202605")).thenReturn(true);mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noBusiness(); }
    @Test void newCashMonthCloseProtectsReplacement()throws Exception { file=FinancialWorkbook.file(List.of(List.of("판관비","","임대료","1","0","2026-05-01")));String revision=checked();when(mapper.closed("202605")).thenReturn(true);mvc.perform(apply(revision)).andExpect(status().isConflict());noBusiness(); }
    @Test void previousYearIsReadOnly()throws Exception { mvc.perform(multipart("/api/excel/B2/validate").file(file).param("month","2025-06")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noBusiness(); }
    @Test void futureMonthIsReadOnly()throws Exception { mvc.perform(multipart("/api/excel/B2/validate").file(file).param("month","2026-07")).andExpect(status().isConflict());noBusiness(); }
    @Test void beforeOperatingMonthIsReadOnly()throws Exception { when(mapper.cashBase()).thenReturn("202607");mvc.perform(check()).andExpect(status().isConflict());noBusiness(); }
    @Test void validationMetadataFailureRollsBack()throws Exception { doThrow(new IllegalStateException("audit")).when(history).insert(any());mvc.perform(check()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());noBusiness(); }
    @Test void businessAuditFailureDoesNotMarkApplied()throws Exception { String revision=checked();doThrow(new IllegalStateException("audit")).when(history).insert(argThat(a->a!=null&&a.tableName().equals("TB_FI_ITEM")));mvc.perform(apply(revision)).andExpect(status().isInternalServerError());verify(mapper,never()).markApplied(any());verify(manager,atLeastOnce()).rollback(any()); }
    @Test void invalidFileTypeIs415()throws Exception { file=new MockMultipartFile("file","renamed.xlsx","application/octet-stream","text".getBytes());mvc.perform(check()).andExpect(status().isUnsupportedMediaType());noBusiness(); }
}
