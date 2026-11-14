package com.sicost.staffingplancomparisonops;

import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
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
import static com.sicost.staffingplancomparisonops.StaffingComparisonDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffingComparisonExportTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    StaffingComparisonMapper mapper;
    AssignmentMonthlyMapper months;
    AccessLogMapper logs;
    ChangeHistoryWriter changes;
    PlatformTransactionManager transactions;
    MockMvc mvc;
    @BeforeEach void setup() {
        mapper=mock(StaffingComparisonMapper.class);months=mock(AssignmentMonthlyMapper.class);logs=mock(AccessLogMapper.class);
        changes=mock(ChangeHistoryWriter.class);transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        var users=mock(CurrentUserProvider.class);when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(logs.insert(any())).thenReturn(1);when(months.cashBase()).thenReturn("202501");
        var project=new Project("0001","=SUM(A1)",LocalDate.of(2025,7,1),LocalDate.of(2026,6,30),"ING","MO");
        when(mapper.projects(any())).thenAnswer(c -> {Query q=c.getArgument(0);return q.projectId()==null || q.projectId().equals(project.id())?List.of(project):List.of();});
        when(mapper.plans("0001")).thenReturn(List.of(new Plan("0001","DEV",new BigDecimal("12.00")),new Plan("0001","ZERO",BigDecimal.ZERO)));
        when(mapper.assignmentIds("0001")).thenReturn(List.of("A-1","A-2"));
        when(months.source("A-1")).thenReturn(new Source("A-1","0001","D-1","DEV",LocalDate.of(2026,1,1),LocalDate.of(2026,7,31),BigDecimal.ONE,null,"NEW",null,null,0,"N","ACT"));
        when(months.source("A-2")).thenReturn(new Source("A-2","0001","D-2","QA",LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),BigDecimal.ONE,null,"NEW",null,null,0,"N","ACT"));
        when(months.histories(anyString())).thenReturn(List.of());when(months.months(anyString())).thenReturn(List.of());
        var monthly=new AssignmentMonthlyService(months,users,changes,clock);
        var repository=new StaffingComparisonRepository(mapper,monthly);
        var excel=new ExcelExportService(users,new AccessLogWriter(logs,users,clock),clock,transactions);
        mvc=MockMvcBuilders.standaloneSetup(new StaffingComparisonExportController(new StaffingComparisonExportService(repository,excel,clock)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    byte[] download(String query) throws Exception {
        return mvc.perform(get("/api/staffing-plan-comparison/export"+query)).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".xlsx")))
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
    }
    @Test void publicAdminDownloadUsesAnnualPlanAndAllTwelveMonthsWithNumericTotalsAndSafeText() throws Exception {
        try (var book=new XSSFWorkbook(new ByteArrayInputStream(download("?year=2026&projectId=0001&amountUnit=MILLION")))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(3);
            var row=sheet.getRow(1);assertThat(row.getCell(0).getStringCellValue()).isEqualTo("0001");assertThat(row.getCell(1).getStringCellValue()).isEqualTo("=SUM(A1)");
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(6);assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(7);assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("QA");assertThat(sheet.getRow(2).getCell(3).getNumericCellValue()).isZero();
            assertThat(sheet.getRow(3).getCell(3).getNumericCellValue()).isEqualTo(6);assertThat(sheet.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(8);assertThat(sheet.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            for(var s:book) for(var r:s) for(var cell:r) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            assertThat(info(book,"기간")).isEqualTo("2026-01 ~ 2026-12");assertThat(info(book,"단위")).isEqualTo("백만원");assertThat(info(book,"원천")).contains("preview");
        }
        verify(logs).insert(argThat(e -> e.action().equals("DNLD") && e.screenId().equals("UI-11") && e.userId().equals("admin") && e.description().contains("계획대비투입 2")));
        verifyNoInteractions(changes);verify(months,never()).saveMonth(any());verify(months,never()).project(any());
    }
    static String info(XSSFWorkbook book,String label) {for(var row:book.getSheet("다운로드정보"))if(row.getCell(0).getStringCellValue().equals(label))return row.getCell(1).toString();throw new AssertionError(label);}
    @Test void wholePopulationNotPageAndOneSnapshotBeforeIndependentAuditCommit() throws Exception {
        List<Plan> plans=new ArrayList<>();for(int i=0;i<57;i++)plans.add(new Plan("0001","J"+i,new BigDecimal("2.00")));
        when(mapper.plans("0001")).thenReturn(plans);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?difference=SHORT")))) {assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(58);assertThat(book.getSheetAt(0).getRow(58).getCell(3).getNumericCellValue()).isEqualTo(57);}
        var order=inOrder(transactions,mapper,logs);
        order.verify(transactions).getTransaction(argThat(t -> t.isReadOnly() && t.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
        order.verify(mapper).projects(any());order.verify(mapper).plans("0001");order.verify(mapper).assignmentIds("0001");order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(t -> !t.isReadOnly() && t.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));order.verify(logs).insert(any());order.verify(transactions).commit(any());
    }
    @Test void exactZeroMatchAndBothZeroRowsOmitted() throws Exception {
        when(mapper.plans("0001")).thenReturn(List.of(new Plan("0001","DEV",new BigDecimal("14.00")),new Plan("0001","ZERO",BigDecimal.ZERO)));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?difference=MATCH")))) {assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);assertThat(book.getSheetAt(0).getRow(1).getCell(5).getNumericCellValue()).isZero();}
    }
    @Test void emptyIsNotSourceFailure() throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("?projectId=absent")))) {assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(1);assertThat(book.getSheetAt(0).getRow(1).getCell(3).getNumericCellValue()).isZero();}
    }
    @ParameterizedTest @ValueSource(strings={"?actor=x","?page=1","?size=50","?from=2026-01","?months=13","?year=0","?year=10000","?year=-1","?year=1.0","?year=2026&year=2027","?difference=short","?amountUnit=USD","?projectId=","?projectId=abcdefghijklmnopqrstu"})
    void invalidQueryHasFieldRequestIdAndNoBusinessOrAuditAccess(String query) throws Exception {
        mvc.perform(get("/api/staffing-plan-comparison/export"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").exists()).andExpect(jsonPath("$.field").isString()).andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.requestId").isString()).andExpect(header().exists("X-Request-Id")).andExpect(header().doesNotExist("Content-Disposition"));
        verifyNoInteractions(mapper,months,logs,changes);
    }
    @ParameterizedTest @ValueSource(strings={"2024","2028"}) void outOfOperatingPeriodRejected(String year) throws Exception {
        mvc.perform(get("/api/staffing-plan-comparison/export?year="+year)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));verifyNoInteractions(mapper,logs,changes);
    }
    @Test void requestBodyRejected() throws Exception {mvc.perform(get("/api/staffing-plan-comparison/export").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());verifyNoInteractions(mapper,logs);}
    @Test void protectedMonthsRemainAuthoritativeWithoutSynchronization() throws Exception {
        when(months.months("A-1")).thenReturn(List.of(new StoredMonth("202601",31,new BigDecimal("0.50"),null,BigDecimal.ZERO,"Y")));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(download("")))) {assertThat(book.getSheetAt(0).getRow(1).getCell(4).getNumericCellValue()).isEqualTo(6.5);}
        verifyNoInteractions(changes);verify(months,never()).saveMonth(any());
    }
    @Test void auditFailurePreventsBinaryAndSanitizesError() throws Exception {
        when(logs.insert(any())).thenThrow(new IllegalStateException("private SQL"));
        var response=mvc.perform(get("/api/staffing-plan-comparison/export")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private","SQL");verify(transactions).rollback(any());
    }
    @Test void failedAuditRowCountPreventsBinary() throws Exception {when(logs.insert(any())).thenReturn(0);mvc.perform(get("/api/staffing-plan-comparison/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));}
    @Test void auditCommitFailurePreventsBinary() throws Exception {doNothing().doThrow(new IllegalStateException("commit")).when(transactions).commit(any());mvc.perform(get("/api/staffing-plan-comparison/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));}
    @Test void sourceFailureNotConvertedToZeroWorkbook() throws Exception {doThrow(new IllegalStateException("private SQL")).when(mapper).projects(any());mvc.perform(get("/api/staffing-plan-comparison/export")).andExpect(status().isInternalServerError());verifyNoInteractions(logs,changes);}
}
