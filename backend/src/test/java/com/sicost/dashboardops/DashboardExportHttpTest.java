package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.me.PreferenceService;
import com.sicost.me.PreferenceDtos.PreferencesData;
import com.sicost.shared.SharedQueriesMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real servlet query handling: MockMvc/dispatcher mocks do not merge include parameters. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, classes=DashboardExportHttpTest.Application.class)
class DashboardExportHttpTest {
    @Configuration(proxyBeanMethods=false)
    @TestComponent
    @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
    @Import({DashboardController.class,DashboardExportController.class,DashboardExportService.class,DashboardSnapshotReader.class,
        ExcelExportService.class,AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
    static class Application {}
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @MockBean DashboardService dashboard;
    @MockBean SharedQueriesMapper shared;
    @MockBean PreferenceService preferences;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean AccessLogMapper logs;
    @MockBean PlatformTransactionManager transactions;
    ObjectNode data;
    @BeforeEach void setup() throws Exception {
        Clock fixed=Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("000001","임시 관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("000001");
        when(shared.cashBaseMonth()).thenReturn("202601");
        when(preferences.getPreferences()).thenReturn(new PreferencesData(List.of()));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(logs.insert(any())).thenReturn(1);
        data=DashboardExportFixture.data(json);
        when(dashboard.getDashboard(any())).thenAnswer(c->{data.put("selectedMonth",c.getArgument(0,String.class));return json.treeToValue(data,DashboardDtos.Data.class);});
    }
    HttpResponse<byte[]> get(String suffix) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/dashboard/export"+suffix)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void explicitSelectedMonthAndOmittedMonthBothDownloadThroughRealServlet() throws Exception {
        for(String query:List.of("?month=2026-11","?month=2026-01","")) {
            var response=get(query);
            assertThat(response.statusCode()).as(query+" "+new String(response.body(),java.nio.charset.StandardCharsets.UTF_8).substring(0,Math.min(150,response.body().length))).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Disposition")).isPresent();
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {assertThat(book.getNumberOfSheets()).isEqualTo(11);}
        }
        verify(dashboard,times(2)).getDashboard("2026-11");verify(dashboard).getDashboard("2026-01");
        verify(logs,times(3)).insert(any());
    }
    @Test void emptySectionsAndUnconfirmedCountsStillProduceACompleteWorkbook() throws Exception {
        for(String section:List.of("actions","projectAlerts","salesParts"))data.putArray(section);
        ((ObjectNode)data.path("closeProgress")).put("passedCheckCount",5);
        ((ObjectNode)data.path("closeProgress").path("remainingChecks").get(0)).putNull("count").put("status","UNKNOWN");
        var response=get("?month=2026-07");assertThat(response.statusCode()).isEqualTo(200);
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
            assertThat(book.getSheet("확인할 일").getLastRowNum()).isZero();
            var close=book.getSheet("마감 진행");
            assertThat(close.getRow(1).getCell(DashboardExportContractTest.col(close,"remainingChecks.0.count")).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(close.getRow(1).getCell(DashboardExportContractTest.col(close,"passedCheckCount")).getNumericCellValue()).isEqualTo(5);
        }
    }
    @Test void invalidScopeAndInjectedActorNeverReadOrAudit() throws Exception {
        for(String query:List.of("?month=2025-12","?month=2026-12","?month=2026-11&month=2026-11","?month=2026-11&userId=other"))
            assertThat(get(query).statusCode()).as(query).isEqualTo(400);
        verifyNoInteractions(dashboard,logs);
    }
    @Test void generationFailureDoesNotRecordDownloadOrExposeFile() throws Exception {
        ((ObjectNode)data.withArray("metrics").get(0)).put("value","invalid-decimal");
        var response=get("?month=2026-11");assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
        verifyNoInteractions(logs);verify(transactions).rollback(any());
    }
    @Test void failedMandatoryAuditDoesNotExposeFile() throws Exception {
        when(logs.insert(any())).thenReturn(0);
        var response=get("?month=2026-11");assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();verify(transactions).rollback(any());
    }
}
