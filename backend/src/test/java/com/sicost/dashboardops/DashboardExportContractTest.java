package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.me.PreferenceDtos.PreferencesData;
import com.sicost.me.PreferenceService;
import com.sicost.shared.SharedQueriesMapper;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(DashboardExportController.class)
@ContextConfiguration(classes=DashboardExportController.class)
@Import({DashboardExportService.class,ExcelExportService.class,AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DashboardExportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean DashboardSnapshotReader dashboard;
    @MockBean SharedQueriesMapper shared;
    @MockBean PreferenceService preferences;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean AccessLogMapper logs;
    @MockBean PlatformTransactionManager transactions;
    ObjectNode data;
    @BeforeEach void setup() throws Exception {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("000001","임시 관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("000001");
        when(shared.cashBaseMonth()).thenReturn("202601");
        when(preferences.getPreferences()).thenReturn(new PreferencesData(List.of()));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(logs.insert(any())).thenReturn(1);
        data=DashboardExportFixture.data(json);
        when(dashboard.read(eq("2026-07"),any(),any())).thenReturn(data);
    }
    byte[] download() throws Exception {
        return mvc.perform(get("/api/dashboard/export").param("month","2026-07"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("X-Request-Id"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".xlsx")))
            .andReturn().getResponse().getContentAsByteArray();
    }
    static int col(Sheet sheet,String name) {
        for(Cell cell:sheet.getRow(0))if(cell.getStringCellValue().equals(name))return cell.getColumnIndex();
        throw new AssertionError("열 없음: "+name);
    }
    @Test void exportsEveryRegionAndEveryNumericTableWithoutPagingOrNullSubstitution() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(download()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(11);
            assertThat(workbook.getSheet("요약").getLastRowNum()).isEqualTo(5);
            assertThat(workbook.getSheet("매출·원가 추이").getLastRowNum()).isEqualTo(12);
            assertThat(workbook.getSheet("인력 수급 예측").getLastRowNum()).isEqualTo(6);
            Sheet trend=workbook.getSheet("매출·원가 추이");
            assertThat(trend.getRow(1).getCell(col(trend,"salesAmount (원)")).getNumericCellValue()).isEqualTo(1234567);
            assertThat(trend.getRow(8).getCell(col(trend,"salesAmount (원)")).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(trend.getRow(8).getCell(col(trend,"salesTargetAmount (원)")).getNumericCellValue()).isEqualTo(2000000);
            Sheet supply=workbook.getSheet("인력 수급 예측");
            assertThat(supply.getRow(6).getCell(col(supply,"differencePersons")).getNumericCellValue()).isEqualTo(-1.5);
            Sheet cash=workbook.getSheet("자금 3개월");
            assertThat(cash.getLastRowNum()).isEqualTo(3);
            assertThat(cash.getRow(3).getCell(col(cash,"month")).getStringCellValue()).isEqualTo("2027-02");
            assertThat(cash.getRow(1).getCell(col(cash,"closingBalance (원)")).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(cash.getRow(1).getCell(col(cash,"minimumCashAmount (원)")).getNumericCellValue()).isZero();
            assertThat(cash.getRow(1).getCell(col(cash,"unconfirmedReasons.0")).getStringCellValue()).isEqualTo("급여 원천 미확인");
        }
        verify(dashboard,times(1)).read(eq("2026-07"),any(),any());
        verify(shared).cashBaseMonth();verifyNoMoreInteractions(shared);
    }
    @Test void identifiersAndFormulaTextAreStringCellsWhileZeroNegativeAndRatesRemainNumeric() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(download()))) {
            Sheet alerts=workbook.getSheet("프로젝트 경보");
            assertThat(alerts.getRow(1).getCell(col(alerts,"projectId")).getStringCellValue()).isEqualTo("000012");
            assertThat(alerts.getRow(1).getCell(col(alerts,"projectName")).getStringCellValue()).isEqualTo("+계약 검토");
            Sheet actions=workbook.getSheet("확인할 일");
            assertThat(actions.getRow(1).getCell(col(actions,"reason")).getStringCellValue()).isEqualTo("=SUM(A1:A2)");
            Sheet parts=workbook.getSheet("영업 파트별");
            assertThat(parts.getRow(1).getCell(col(parts,"salesAmount (원)")).getNumericCellValue()).isZero();
            assertThat(parts.getRow(1).getCell(col(parts,"projectCostAmount (원)")).getNumericCellValue()).isEqualTo(-1001);
            assertThat(parts.getRow(1).getCell(col(parts,"projectCostAmount (천원)")).getNumericCellValue()).isEqualTo(-1.001);
            assertThat(workbook.getSheet("요약").getRow(4).getCell(col(workbook.getSheet("요약"),"value")).getNumericCellValue()).isZero();
            for(Sheet sheet:workbook)for(Row row:sheet)for(Cell cell:row)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
        }
    }
    @Test void realTemporaryActorAndSeoulTimeAreRecordedAndAuditCommitsBeforeResponse() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(download()))) {
            Sheet info=workbook.getSheet("다운로드정보");
            assertThat(info.getRow(0).getCell(1).getStringCellValue()).isEqualTo("000001");
            assertThat(info.getRow(2).getCell(1).getStringCellValue()).isEqualTo("2026-07-15 00:00:00");
        }
        var order=inOrder(transactions,dashboard,logs);
        order.verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
        order.verify(dashboard).read(eq("2026-07"),any(),any());order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(d->!d.isReadOnly()&&d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(logs).insert(argThat(e->e.userId().equals("000001")&&e.action().equals("DNLD")&&e.screenId().equals("UI-16")&&e.now().equals(LocalDateTime.of(2026,7,15,0,0))));
        order.verify(transactions).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"2025-12","2026-08"})
    void outOfRangeMonthDoesNotReadDashboardOrRecordDownload(String month) throws Exception {
        mvc.perform(get("/api/dashboard/export").param("month",month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        verifyNoInteractions(dashboard,logs);
    }
    @ParameterizedTest @ValueSource(strings={"actor","userId","unit","page","asOfDate","size","sort"})
    void unknownQueryRejectedBeforeSnapshot(String key) throws Exception {
        mvc.perform(get("/api/dashboard/export").param(key,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(transactions,shared,logs,dashboard);
    }
    @ParameterizedTest @ValueSource(strings={"","2026-13","0000-01","202607","2026-7","2026-07\r\n"})
    void malformedMonthRejected(String month) throws Exception {
        mvc.perform(get("/api/dashboard/export").param("month",month)).andExpect(status().isBadRequest());verifyNoInteractions(logs,dashboard);
    }
    @Test void duplicateMonthAndBodyAreRejected() throws Exception {
        mvc.perform(get("/api/dashboard/export").param("month","2026-06","2026-07")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/export").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());verifyNoInteractions(logs,dashboard);
    }
    @Test void omittedMonthUsesServerCurrentMonth() throws Exception {
        mvc.perform(get("/api/dashboard/export")).andExpect(status().isOk());verify(dashboard).read(eq("2026-07"),any(),any());
    }
    @Test void auditFailureReturnsJson500AndNeverExposesAFile() throws Exception {
        when(logs.insert(any())).thenReturn(0);
        mvc.perform(get("/api/dashboard/export")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(header().doesNotExist("Content-Disposition"));verify(transactions).rollback(any());
    }
    @Test void unavailableSourceDoesNotCreateDownloadAudit() throws Exception {
        when(dashboard.read(anyString(),any(),any())).thenThrow(new ApiException(500,"INTERNAL_ERROR","전체 조회 실패"));
        mvc.perform(get("/api/dashboard/export")).andExpect(status().isInternalServerError());verifyNoInteractions(logs);
    }
}
