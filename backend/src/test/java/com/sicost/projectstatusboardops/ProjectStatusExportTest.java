package com.sicost.projectstatusboardops;

import com.sicost.apiops.*;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.me.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectStatusExportTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    ProjectAnalysisService board;
    AccessLogMapper logs;
    PlatformTransactionManager transactions;
    PreferenceService preferences;
    MockMvc mvc;
    @BeforeEach void setup(){
        board=mock(ProjectAnalysisService.class);logs=mock(AccessLogMapper.class);transactions=mock(PlatformTransactionManager.class);preferences=mock(PreferenceService.class);
        var users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(logs.insert(any())).thenReturn(1);when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of()));
        when(board.getProjectStatusBoard(any())).thenAnswer(c->{var q=(ProjectAnalysisInput.Query)c.getArgument(0);assertThat(q.size()).isEqualTo(Integer.MAX_VALUE);return page(57);});
        var excel=new ExcelExportService(users,new AccessLogWriter(logs,users,clock),clock,transactions);
        var exports=new ProjectStatusExportService(excel,board,preferences);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectStatusController(mock(ProjectStatusReadService.class),exports)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    ProjectAnalysisDtos.BoardPage page(int total){
        var rows=IntStream.range(0,total).mapToObj(i->{var r=new LinkedHashMap<String,Object>();r.put("projectName",i==0?"=SUM(A1)":"000"+i);r.put("light","위험");r.put("pm",List.of(new ProjectAnalysisDtos.Actor("0001","+담당자")));r.put("currentStep",null);r.put("planProgress","0.0");r.put("actualProgress","15.0");r.put("costUsage",null);r.put("costProgressGap",null);r.put("openIssueCount",0);r.put("overdueIssueCount",2);r.put("lastUpdatedDate",null);r.put("elapsedDays",17);return (Map<String,Object>)r;}).toList();
        return new ProjectAnalysisDtos.BoardPage(LocalDate.of(2026,7,1),"2026-07",rows,total,1,50,Map.of("unknownCostProjectCount",total),List.of(),"안내");
    }
    byte[] download(String query)throws Exception {return mvc.perform(get("/api/project-status-board/export"+query)).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("20260701_000000.xlsx"))).andReturn().getResponse().getContentAsByteArray();}
    @Test void wholeResultTwoSheetsFilenameAndNumericNullTextCells()throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?contractType=TK&unit=백만원")))){
            assertThat(book.getNumberOfSheets()).isEqualTo(2);assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(57);
            var row=book.getSheetAt(0).getRow(1);assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(1).getStringCellValue()).isEqualTo("=SUM(A1)");
            assertThat(book.getSheetAt(0).getRow(2).getCell(1).getStringCellValue()).isEqualTo("0001");
            assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(row.getCell(4).getNumericCellValue()).isZero();assertThat(row.getCell(6).getCellType()).isEqualTo(CellType.BLANK);
            for(var sheet:book)for(var line:sheet)for(var cell:line)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            assertThat(info(book,"처리자ID").getStringCellValue()).isEqualTo("admin");assertThat(info(book,"기준일").getStringCellValue()).isEqualTo("2026-07-01");assertThat(info(book,"총건수").getNumericCellValue()).isEqualTo(57);assertThat(info(book,"미확인수").getNumericCellValue()).isEqualTo(57);assertThat(info(book,"단위").getStringCellValue()).isEqualTo("백만원");
        }
        verify(board).getProjectStatusBoard(argThat(q->"TK".equals(q.value("contractType"))&&q.page()==1));
    }
    static org.apache.poi.ss.usermodel.Cell info(XSSFWorkbook book,String label){for(var row:book.getSheet("다운로드정보"))if(row.getCell(0).getStringCellValue().equals(label))return row.getCell(1);throw new AssertionError(label);}
    @Test void generationSnapshotCommitsThenIndependentServerActorAuditCommitsBeforeTransfer()throws Exception {
        download("");var order=inOrder(transactions,board,logs);
        order.verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));order.verify(board).getProjectStatusBoard(any());order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(d->!d.isReadOnly()&&d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));order.verify(logs).insert(argThat(e->e.userId().equals("admin")&&e.action().equals("DNLD")&&e.screenId().equals("UI-31")&&e.description().trim().equals("프로젝트현황 57")));order.verify(transactions).commit(any());
    }
    @Test void emptyStillHasTitlesAndInformation()throws Exception {doReturn(page(0)).when(board).getProjectStatusBoard(any());try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("")))){assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);assertThat(info(book,"총건수").getNumericCellValue()).isZero();}}
    @ParameterizedTest @ValueSource(strings={"?page=1","?size=20","?userId=other","?actor=other","?baseDate=2026-07-01","?unit=USD","?order=projectName","?order=light,light,pm,currentStep,planProgress,actualProgress,costUsage,costProgressGap,openIssueCount,overdueIssueCount,lastUpdatedDate,elapsedDays","?hidden=projectName","?hidden=pm,pm","?light=위험&light=위험","?sort=id","?direction=desc"})
    void invalidNeverReadsOrLogsOrSetsAttachment(String query)throws Exception {mvc.perform(get("/api/project-status-board/export"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(header().doesNotExist("Content-Disposition"));verifyNoInteractions(logs,board);}
    @Test void orderAndHiddenAndUnitResolveFromPreferencesAndExplicitOverride()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var value=json.createObjectNode();var view=value.putObject("statusBoard");var reversed=new ArrayList<>(ProjectStatusInput.COLUMNS);Collections.reverse(reversed);view.set("order",json.valueToTree(reversed));view.set("hidden",json.valueToTree(List.of("pm")));
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of(new PreferenceDtos.Preference("COLS","UI-31",value,null,true,null),new PreferenceDtos.Preference("AMT_UNIT","*",json.getNodeFactory().textNode("원"),null,true,null))));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("")))){assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("경과일");assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)11);assertThat(info(book,"단위").getStringCellValue()).isEqualTo("원");}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?hidden=&unit=천원")))){assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)12);assertThat(info(book,"단위").getStringCellValue()).isEqualTo("천원");}
    }
    @Test void auditFailureReturnsOnlySanitizedJsonNoXlsxAndRollsBack()throws Exception {
        when(logs.insert(any())).thenThrow(new IllegalStateException("private SQL"));
        var response=mvc.perform(get("/api/project-status-board/export")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition")).andReturn().getResponse();assertThat(response.getContentAsString()).doesNotContain("private","SQL");assertThat(response.getContentType()).startsWith("application/json");verify(transactions).rollback(any());
    }
    @Test void auditCommitFailureAlsoHasNoAttachment()throws Exception {doNothing().doThrow(new IllegalStateException("commit failure")).when(transactions).commit(any());mvc.perform(get("/api/project-status-board/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));}
    @Test void sourceFailureNeverAppendsAudit()throws Exception {doThrow(new IllegalStateException("source failure")).when(board).getProjectStatusBoard(any());mvc.perform(get("/api/project-status-board/export")).andExpect(status().isInternalServerError());verifyNoInteractions(logs);}
}
