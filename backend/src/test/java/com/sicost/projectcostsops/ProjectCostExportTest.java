package com.sicost.projectcostsops;

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

class ProjectCostExportTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    ProjectAnalysisService costs;
    AccessLogMapper logs;
    PlatformTransactionManager transactions;
    PreferenceService preferences;
    MockMvc mvc;
    ExcelExportService excel;
    @BeforeEach void setup(){
        costs=mock(ProjectAnalysisService.class);logs=mock(AccessLogMapper.class);transactions=mock(PlatformTransactionManager.class);preferences=mock(PreferenceService.class);
        var users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(logs.insert(any())).thenReturn(1);when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of()));
        when(costs.getProjectCosts(any())).thenAnswer(c->{var q=(ProjectAnalysisInput.Query)c.getArgument(0);assertThat(q.size()).isEqualTo(Integer.MAX_VALUE);assertThat(q.page()).isEqualTo(1);return page(57);});
        excel=new ExcelExportService(users,new AccessLogWriter(logs,users,clock),clock,transactions);
        var exports=new ProjectCostExportService(excel,costs,preferences);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectCostExportController(costs,exports)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    ProjectAnalysisDtos.CostPage page(int total){
        var rows=IntStream.range(0,total).mapToObj(i->{var r=new LinkedHashMap<String,Object>();
            r.put("projectName",i==0?"=SUM(A1)":i==1?"0001":i==2?"+user":i==3?"-user":"@user");
            r.put("contractType","TK");r.put("status","ING");r.put("contractAmount","1234500");r.put("planMm",null);r.put("actualMm","0.00");r.put("cumulativeCost","0");r.put("cumulativeMargin","15.5");r.put("monthlyMargin","12.5");r.put("targetMargin","20.0");r.put("budgetAmount","800000");r.put("targetGap","-1.2");r.put("alerts",List.of(Map.of("message","예산 초과")));return (Map<String,Object>)r;}).toList();
        var completeness=new ProjectAnalysisDtos.Completeness(false,true,false,List.of(),total,"2025-01","2026-06",null);
        return new ProjectAnalysisDtos.CostPage("2026-06",LocalDate.of(2026,6,30),rows,total,1,50,Map.of("completeness",completeness));
    }
    byte[] download(String query)throws Exception {return mvc.perform(get("/api/project-costs/export"+query)).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("202606_20260701_000000.xlsx"))).andReturn().getResponse().getContentAsByteArray();}
    @Test void wholeFilteredResultTwoSheetsExactFilenameNumericNullAndTextCells()throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?month=2026-06&contractType=TK&sort=projectName&direction=DESC&unit=천원")))){
            assertThat(book.getNumberOfSheets()).isEqualTo(2);assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(57);
            var row=book.getSheetAt(0).getRow(1);assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=SUM(A1)");
            assertThat(book.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("0001");
            assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(1234.5);
            assertThat(row.getCell(5).getCellType()).isEqualTo(CellType.BLANK);assertThat(row.getCell(6).getNumericCellValue()).isZero();assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(15.5);
            assertThat(row.getCell(ProjectCostExportInput.COLUMNS.indexOf("targetGap")).getNumericCellValue()).isEqualTo(-1.2);
            assertThat(row.getCell(ProjectCostExportInput.COLUMNS.indexOf("monthlyMargin")).getNumericCellValue()).isEqualTo(12.5);
            assertThat(row.getCell(ProjectCostExportInput.COLUMNS.indexOf("targetMargin")).getNumericCellValue()).isEqualTo(20.0);
            assertThat(row.getCell(ProjectCostExportInput.COLUMNS.indexOf("budgetAmount")).getNumericCellValue()).isEqualTo(800.0);
            for(var sheet:book)for(var line:sheet)for(var cell:line)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            assertThat(info(book,"처리자ID").getStringCellValue()).isEqualTo("admin");assertThat(info(book,"기준월").getStringCellValue()).isEqualTo("2026-06");assertThat(info(book,"총건수").getNumericCellValue()).isEqualTo(57);assertThat(info(book,"미확인수").getNumericCellValue()).isEqualTo(57);assertThat(info(book,"단위").getStringCellValue()).isEqualTo("천원");
        }
        verify(costs).getProjectCosts(argThat(q->"TK".equals(q.value("contractType"))&&"projectName".equals(q.sort())&&"DESC".equals(q.direction())));
    }
    static org.apache.poi.ss.usermodel.Cell info(XSSFWorkbook book,String label){for(var row:book.getSheet("다운로드정보"))if(row.getCell(0).getStringCellValue().equals(label))return row.getCell(1);throw new AssertionError(label);}
    @Test void snapshotThenIndependentServerActorAuditCommitBeforeTransfer()throws Exception {
        download("");var order=inOrder(transactions,costs,logs,preferences);
        order.verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));order.verify(preferences).getPreferences();order.verify(costs).getProjectCosts(any());order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(d->!d.isReadOnly()&&d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));order.verify(logs).insert(argThat(e->e.userId().equals("admin")&&e.action().equals("DNLD")&&e.screenId().equals("UI-04")&&e.description().trim().equals("프로젝트 원가 57")));order.verify(transactions).commit(any());
    }
    @Test void zeroRowsStillHasTitlesAndInformation()throws Exception {doReturn(page(0)).when(costs).getProjectCosts(any());try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("")))){assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);assertThat(info(book,"총건수").getNumericCellValue()).isZero();}}
    @ParameterizedTest @ValueSource(strings={"?page=1","?size=20","?userId=other","?actor=other","?baseDate=2026-07-01","?unit=USD","?order=projectName","?order=projectName,projectName","?hidden=projectName","?hidden=alerts,alerts","?month=2026-06&month=2026-06","?alertOnly=1","?status=PRE","?contractType=OTHER","?sort=id","?direction=desc","?month=2026-13","?month=0000-01","?hidden=unknown"})
    void invalidNeverReadsLogsOrSetsAttachment(String query)throws Exception {mvc.perform(get("/api/project-costs/export"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(header().doesNotExist("Content-Disposition"));verifyNoInteractions(logs,costs);}
    @Test void duplicateCompleteOrderIsRejected()throws Exception {String order=String.join(",",ProjectCostExportInput.COLUMNS).replace("contractType","projectName");mvc.perform(get("/api/project-costs/export").param("order",order)).andExpect(status().isBadRequest());verifyNoInteractions(costs,logs);}
    @Test void unexpectedBodyRejected()throws Exception {mvc.perform(get("/api/project-costs/export").contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(costs,logs);}
    @Test void columnsAndUnitFromPreferencesWithExplicitOverride()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var value=json.createObjectNode();var view=value.putObject("projectCost");var reversed=new ArrayList<>(ProjectCostExportInput.COLUMNS);Collections.reverse(reversed);view.set("order",json.valueToTree(reversed));view.set("hidden",json.valueToTree(List.of("alerts")));
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of(new PreferenceDtos.Preference("COLS","UI-04",value,null,true,null),new PreferenceDtos.Preference("AMT_UNIT","*",json.getNodeFactory().textNode("원"),null,true,null))));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("")))){assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("무상 원가");assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)(ProjectCostExportInput.COLUMNS.size()-1));assertThat(info(book,"단위").getStringCellValue()).isEqualTo("원");}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?hidden=&unit=백만원")))){assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)ProjectCostExportInput.COLUMNS.size());assertThat(info(book,"단위").getStringCellValue()).isEqualTo("백만원");}
    }
    @Test void auditFailureReturnsOnlySanitizedJsonAndRollsBack()throws Exception {
        when(logs.insert(any())).thenThrow(new IllegalStateException("private SQL"));
        var response=mvc.perform(get("/api/project-costs/export")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition")).andReturn().getResponse();assertThat(response.getContentAsString()).doesNotContain("private","SQL");assertThat(response.getContentType()).startsWith("application/json");verify(transactions).rollback(any());
    }
    @Test void failedAuditInsertCountDoesNotSendFile()throws Exception {when(logs.insert(any())).thenReturn(0);mvc.perform(get("/api/project-costs/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));}
    @Test void auditCommitFailureHasNoAttachment()throws Exception {doNothing().doThrow(new IllegalStateException("commit failure")).when(transactions).commit(any());mvc.perform(get("/api/project-costs/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));}
    @Test void sourceFailureNeverAppendsAudit()throws Exception {doThrow(new IllegalStateException("source failure")).when(costs).getProjectCosts(any());mvc.perform(get("/api/project-costs/export")).andExpect(status().isInternalServerError());verifyNoInteractions(logs);}
    @Test void existingAnnualAndBoardQualifiersStillWork()throws Exception {
        for(String screen:List.of("UI-03","UI-31")) {
            String kind=screen.equals("UI-03")?"개인원가수익":"프로젝트현황",qualifier=screen.equals("UI-03")?"2026":"20260701";
            var columns=List.of(new ExcelExportService.Column("name","이름",ExcelExportService.CellType.TEXT));
            try(var file=excel.generateWorkbook(new ExcelExportService.Specification(kind,null,columns,"","원",screen),()->new ExcelExportService.WorkbookData(List.of(new ExcelExportService.Sheet("목록",columns,sink->{})),List.of(),0,qualifier),new org.springframework.mock.web.MockHttpServletRequest())) {
                assertThat(file.filename()).isEqualTo(kind+(screen.equals("UI-03")?"_2026":"")+"_20260701_000000.xlsx");
            }
        }
    }
}
