package com.sicost.migration;

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
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HistoricalProfitLossImportController.class)
@ContextConfiguration(classes=HistoricalProfitLossImportController.class)
@Import({HistoricalProfitLossImportService.class,HistoricalProfitLossImportRepository.class,HistoricalProfitLossImportDefinition.class,ExcelImportService.class,
    RevisionService.class,WriteCoordinator.class,ChangeHistoryWriter.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class HistoricalProfitLossImportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired HistoricalProfitLossImportService service;
    @MockBean HistoricalProfitLossImportMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper master;
    @MockBean CommitInvalidation invalidation;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Stored> profits;
    List<Issue> issues;
    List<ChangeHistoryEntry> audits;
    Upload upload;
    MockMultipartFile file;
    @BeforeEach void setup()throws Exception {
        profits=new ArrayList<>();issues=new ArrayList<>();audits=new ArrayList<>();upload=null;file=HistoricalProfitLossWorkbook.valid();
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        Clock fixed=Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.cashBase()).thenReturn("208007");when(mapper.profits()).thenAnswer(c->List.copyOf(profits));
        when(mapper.snapshot()).thenAnswer(c->profits.toString()+mapper.cashBase());
        when(mapper.upload(anyLong())).thenAnswer(c->upload);when(mapper.lockUpload(anyLong())).thenAnswer(c->upload);
        when(mapper.issues(anyLong())).thenAnswer(c->List.copyOf(issues));
        when(mapper.insertUpload(any())).thenAnswer(c->{Draft d=c.getArgument(0);d.setUploadId(19L);setUpload(d.getWrite(),"CHECKED");return 1;});
        when(mapper.replaceSummary(any())).thenAnswer(c->{setUpload(c.getArgument(0),"CHECKED");return 1;});
        when(mapper.insertIssue(any())).thenAnswer(c->{issues.add(((IssueWrite)c.getArgument(0)).issue());return 1;});
        when(mapper.deleteIssues(anyLong())).thenAnswer(c->{issues.clear();return 1;});
        when(history.insert(any())).thenAnswer(c->{audits.add(c.getArgument(0));return 1;});
        when(mapper.insertProfit(any())).thenAnswer(c->{profits.add(stored(c.getArgument(0),null));return 1;});
        when(mapper.updateProfit(any())).thenAnswer(c->{ProfitLossWrite w=c.getArgument(0);Stored old=profits.stream().filter(t->w.month().equals(t.month())).findFirst().orElseThrow();profits.remove(old);profits.add(stored(w,old));return 1;});
        when(mapper.markApplied(any())).thenAnswer(c->{setUpload(c.getArgument(0),"APPLIED");return 1;});
        when(mapper.changedMonths(anyLong())).thenAnswer(c->audits.stream().filter(a->a.tableName().equals("TB_CO_MON_PL")).map(a->a.targetKey().substring(0,4)+"-"+a.targetKey().substring(4)).distinct().sorted().toList());
    }
    static Stored stored(ProfitLossWrite w,Stored old) {
        var a=w.amounts();return new Stored(w.month(),a.get("salesAmt"),a.get("allocCostAmt"),a.get("idleCostAmt"),a.get("drctExpsAmt"),a.get("salesCostAmt"),a.get("grossPrftAmt"),a.get("sgaInputAmt"),a.get("indrctLaborAmt"),a.get("rndAmt"),a.get("sgaTotAmt"),a.get("prevAdjAmt"),a.get("oprtPrftAmt"),a.get("noprIncmAmt"),a.get("noprExpsAmt"),a.get("corpTaxAmt"),a.get("netPrftAmt"),"Y","Y",old==null?w.actor():old.regUserId(),old==null?w.now():old.regDttm(),old==null?null:w.actor(),old==null?null:w.now());
    }
    void setUpload(UploadWrite w,String state) {Summary s=w.summary();upload=new Upload(19,"B11","UI-08",w.filename(),w.size(),null,null,null,"admin",state,s.totalRows(),s.successRows(),s.warningRows(),s.errorRows(),state.equals("APPLIED")?w.now():null);}
    MockHttpServletRequestBuilder check() {return post("/api/profit-loss/migration/imports/validate").with(DurableOpenApi.multipart(file));}
    MockHttpServletRequestBuilder apply(String revision) {return post("/api/profit-loss/migration/imports/apply").param("uploadId","19").param("expectedRevision",revision).with(DurableOpenApi.multipart(file));}
    String checked()throws Exception {
        var result=mvc.perform(check()).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.status").value("CHECKED")).andReturn();
        var data=json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        var fields=new HashSet<String>();data.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("uploadId","status","summary","issues","expectedRevision","canApply","warnings","allowedFromMonth","allowedThroughMonth","changes");
        var summary=data.path("summary");assertThat(summary.path("totalRows").asInt()).isEqualTo(summary.path("successRows").asInt()+summary.path("warningRows").asInt()+summary.path("errorRows").asInt());
        assertThat(data.path("changes").get(0).path("after").size()).isEqualTo(20);
        return data.path("expectedRevision").asText();
    }
    void noBusiness() {verify(mapper,never()).insertProfit(any());verify(mapper,never()).updateProfit(any());verify(mapper,never()).markApplied(any());}
    @Test void publicTemplateContainsSeventeenHeadersAllowedMonthsAndBlankMoney()throws Exception {
        var response=mvc.perform(get("/api/profit-loss/migration/imports/template")).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheetAt(0);var months=HistoricalProfitLossImportDefinition.months("208007");assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(months.size()+1);
            for(int i=0;i<17;i++)assertThat(sheet.getRow(0).getCell(i).getStringCellValue()).isEqualTo(HistoricalProfitLossImportDefinition.HEADERS.get(i)+(i<5?"*":""));
            for(int m=0;m<months.size();m++) {assertThat(sheet.getRow(m+1).getCell(0).getStringCellValue()).isEqualTo(months.get(m));for(int i=1;i<17;i++)assertThat(sheet.getRow(m+1).getCell(i).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);}
            assertThat(book.getSheet("다운로드정보").getRow(3).getCell(1).getStringCellValue()).contains("삭제","빈칸");
        }
        verify(logs).append(argThat(e->e.screenId().equals("UI-08")&&e.action().equals("DNLD")),any());verify(manager,times(2)).commit(any());noBusiness();
    }
    @Test void downloadAuditFailurePreventsWorkbookTransmission()throws Exception {
        doThrow(new IllegalStateException("private")).when(logs).append(any(),any());
        var r=mvc.perform(get("/api/profit-loss/migration/imports/template")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(r.getHeader("Content-Disposition")).isNull();assertThat(r.getContentAsString()).doesNotContain("private");noBusiness();
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","MOD_USER_ID","REG_DTTM","year","mode","month","unit","unknown"})
    void unknownFieldsNeverCreateForgedAuditOrUploads(String field)throws Exception {
        mvc.perform(get("/api/profit-loss/migration/imports/template").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(check().param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(apply("x").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(history,logs);verify(mapper,never()).insertUpload(any());noBusiness();
    }
    @Test void repeatedMultipartFieldIsRejected()throws Exception {mvc.perform(apply("x").param("uploadId","20")).andExpect(status().isBadRequest());noBusiness();}
    @Test void validationWritesOnlyCheckedSummaryAndIssues()throws Exception {
        checked();assertThat(upload.formType()).isEqualTo("B11");assertThat(upload.screenId()).isEqualTo("UI-08");assertThat(upload.month()).isNull();assertThat(upload.totalRows()).isEqualTo(1);verifyNoInteractions(history);noBusiness();
    }
    @Test void firstApplyStoresRawValuesFlagsFieldAuditAndPreservesOtherMonths()throws Exception {
        String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk()).andExpect(jsonPath("$.data.changedMonths[0]").value("2079-01")).andExpect(jsonPath("$.data.keptMonths.length()").value(17));
        assertThat(profits).hasSize(1);assertThat(profits.get(0).salesAmt()).isEqualByComparingTo("1000");assertThat(profits.get(0).grossPrftAmt()).isNull();assertThat(profits.get(0).fixed()).isEqualTo("Y");assertThat(profits.get(0).migrated()).isEqualTo("Y");
        assertThat(audits).allMatch(a->a.targetKey().equals("207901")&&a.reason().equals("HistoricalProfitLossImport:19:2:ALL"));
        verify(mapper).markApplied(any());var order=inOrder(master,locks);order.verify(master,atLeastOnce()).lockClose();order.verify(locks,atLeastOnce()).lock(anyString());
    }
    @Test void reuploadNullReplacesPreviousOptionalAmount()throws Exception {
        String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());
        var original=profits.get(0);var a=new LinkedHashMap<>(original.amounts());a.put("grossPrftAmt",new BigDecimal("999"));profits.set(0,stored(new ProfitLossWrite("207901","208007",a,"admin",LocalDateTime.of(2079,1,1,0,0)),null));
        clearInvocations(mapper);revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());
        assertThat(profits.get(0).grossPrftAmt()).isNull();assertThat(audits).anyMatch(e->e.item().equals("GROSS_PRFT_AMT")&&e.before().equals("999")&&e.after()==null);
    }
    @Test void identicalUploadCompletesAppliedWithoutTouchingModOrAudit()throws Exception {
        String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());var old=List.copyOf(profits);audits.clear();clearInvocations(mapper,history);
        revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk()).andExpect(jsonPath("$.data.changedMonths").isEmpty()).andExpect(jsonPath("$.data.unchangedMonths[0]").value("2079-01"));
        assertThat(profits).isEqualTo(old);verify(mapper,never()).insertProfit(any());verify(mapper,never()).updateProfit(any());verifyNoInteractions(history);verify(mapper).markApplied(any());
    }
    @ParameterizedTest @ValueSource(strings={"B10","owner","filename","size","month","project","screen"})
    void contextMismatchIs409BeforeBusiness(String field)throws Exception {
        String revision=checked();var u=upload;
        upload=new Upload(u.uploadId(),field.equals("B10")?"B10":u.formType(),field.equals("screen")?"UI-18":u.screenId(),field.equals("filename")?"other.xlsx":u.filename(),field.equals("size")?u.size()+1:u.size(),field.equals("month")?"208001":null,field.equals("project")?"P":null,null,field.equals("owner")?"other":u.actor(),u.status(),1,1,0,0,null);
        mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UPLOAD_CONTEXT_MISMATCH"));noBusiness();verifyNoInteractions(history);
    }
    @Test void newErrorReplacesCheckedIssuesAndCommitsBefore422()throws Exception {
        String revision=checked();file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(4).setBlank());setUpload(new UploadWrite(19,file.getOriginalFilename(),file.getSize(),new Summary(1,1,0,0,0),"admin",LocalDateTime.now()),"CHECKED");
        mvc.perform(apply(revision)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.financial.upload.canApply").value(false));
        assertThat(issues).anyMatch(i->i.rowNumber()==2&&i.columnName().equals("영업이익")&&i.severity().equals("ERR"));verify(manager,atLeastOnce()).commit(any());noBusiness();
    }
    @Test void baseChangeRejectsStaleWithoutPartialWrite()throws Exception {String revision=checked();when(mapper.cashBase()).thenReturn("208008");mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noBusiness();}
    @Test void changeOutsideFileMonthInvalidatesWholeRangeRevision()throws Exception {
        String revision=checked();var a=new LinkedHashMap<String,BigDecimal>();HistoricalProfitLossImportDefinition.FIELDS.forEach(k->a.put(k,BigDecimal.ZERO));profits.add(stored(new ProfitLossWrite("207902","208007",a,"admin",LocalDateTime.of(2079,1,1,0,0)),null));
        mvc.perform(apply(revision)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noBusiness();verifyNoInteractions(history);
    }
    @Test void appliedReplayReturnsStoredReceiptBeforeParsingAndRevisionWithoutGuessingLists()throws Exception {
        String revision=checked();mvc.perform(apply(revision)).andExpect(status().isOk());clearInvocations(mapper,history);
        file=new MockMultipartFile("file",file.getOriginalFilename(),"application/octet-stream",new byte[(int)file.getSize()]);
        mvc.perform(apply("obsolete")).andExpect(status().isOk()).andExpect(jsonPath("$.data.alreadyApplied").value(true)).andExpect(jsonPath("$.data.unchangedMonths").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.keptMonths").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper,never()).snapshot();verify(mapper,never()).replaceSummary(any());noBusiness();verifyNoInteractions(history);
        Result result=(Result)service.result(19);assertThat(result.changedMonths()).containsExactly("2079-01");assertThat(result.year()).isNull();assertThat(result.targetMonth()).isNull();
    }
    @Test void auditFailureRequestsRollbackAndNeverMarksApplied()throws Exception {
        String revision=checked();doThrow(new IllegalStateException("private")).when(history).insert(any());mvc.perform(apply(revision)).andExpect(status().isInternalServerError());verify(mapper,never()).markApplied(any());verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void issueFailureRequestsRollbackAndDoesNotWriteBusiness()throws Exception {
        String revision=checked();file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(4).setBlank());setUpload(new UploadWrite(19,file.getOriginalFilename(),file.getSize(),new Summary(1,1,0,0,0),"admin",LocalDateTime.now()),"CHECKED");
        doThrow(new IllegalStateException()).when(mapper).insertIssue(any());mvc.perform(apply(revision)).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());noBusiness();
    }
    @Test void operatingMonthFileHasErrAndNoBusinessWrites()throws Exception {
        file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(0).setCellValue("2080-07"));
        mvc.perform(check()).andExpect(status().isOk()).andExpect(jsonPath("$.data.canApply").value(false)).andExpect(jsonPath("$.data.summary.errorRows").value(1));noBusiness();
    }
    @Test void missingUploadIs404()throws Exception {mvc.perform(apply("x")).andExpect(status().isNotFound());noBusiness();}
}
