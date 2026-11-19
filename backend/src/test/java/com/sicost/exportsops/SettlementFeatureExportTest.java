package com.sicost.exportsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.apiops.*;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.salespartsettlements.*;
import com.sicost.salespartsettlementsops.SettlementTrendService;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementFeatureExportTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    SalesPartSettlementService settlements;RateRenegotiationService rates;SettlementTrendService trends;
    AccessLogWriter logs;MockMvc mvc;MasterDataExportService old;
    ExcelExportService excel;
    SettlementExportService featureExports;
    @BeforeEach void setup() {
        settlements=mock(SalesPartSettlementService.class);rates=mock(RateRenegotiationService.class);trends=mock(SettlementTrendService.class);logs=mock(AccessLogWriter.class);old=mock(MasterDataExportService.class);
        var users=mock(CurrentUserProvider.class);when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var clock=Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        excel=new ExcelExportService(users,logs,clock,tx);
        var controller=new MasterDataExportController(old,mock(PeopleExportService.class),mock(AssignmentExportService.class));
        featureExports=new SettlementExportService(excel,settlements,trends,rates,clock);
        controller.settlements(featureExports);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    Result result(int count) {
        var rows=new ArrayList<Row>();
        for(int i=0;i<count;i++)rows.add(new Row("D"+i,"=이름"+i,"0001","FL",List.of("@프로젝트"),null,"0.50","100",null,"10","0",null,null,null,"급여 미확인",new Basis("UNKNOWN",false,false,null,30,30,null,"40",List.of(),List.of()),List.of()));
        var total=new Totals(count,0,"30.50","6100",null,"610","0",null,null,null,"5",count==0?"-5":null);
        return new Result("PART-001","2026-11","WORK",new Standard("admin","관리자","40","10","2026-01",true),"5","revision",null,null,null,null,false,List.of(),true,false,false,total,total,rows,new Page(1,50,count),List.of());
    }
    String settlementBody(String mode) {return "{\"view\":\"SALES_PART_SETTLEMENTS\",\"filters\":{\"partId\":\"PART-001\",\"month\":\"2026-11\",\"mode\":\""+mode+"\"},\"amountUnit\":\"원\"}";}
    @Test void monthExportsAll61PeopleAndNumericNullTextCellsWithCommittedDownloadAudit() throws Exception {
        when(settlements.exportSettlement(eq("PART-001"),any())).thenReturn(result(61));
        var response=mvc.perform(post("/api/exports/master-data").contentType("application/json").content(settlementBody("MONTH"))).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheet("이번 달");assertThat(sheet.getLastRowNum()).isEqualTo(62);
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).startsWith("=");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("0001");
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(0.5);
            assertThat(sheet.getRow(1).getCell(6).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(book.getSheet("계산 방법")).isNotNull();
        }
        verify(logs).append(argThat(e->e.screenId().equals("UI-19")&&e.count()==61&&e.filename().equals("영업파트이익금_PART-001_202611_이번달.xlsx")),any());verifyNoInteractions(old);
    }
    @Test void zeroPersonMonthStillExportsCardAndNegativePartTotal() throws Exception {
        when(settlements.exportSettlement(eq("PART-001"),any())).thenReturn(result(0));
        var response=mvc.perform(post("/api/exports/master-data").contentType("application/json").content(settlementBody("MONTH"))).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheet("다운로드정보");var values=new ArrayList<Double>();
            for(var row:sheet)if(row.getCell(1)!=null&&row.getCell(1).getCellType()==org.apache.poi.ss.usermodel.CellType.NUMERIC)values.add(row.getCell(1).getNumericCellValue());
            assertThat(values).contains(5d,-5d);
        }
    }
    @Test void rateExportUsesWholePopulationSimulationSelectedColumnsAndSummary() throws Exception {
        var row=new RateRenegotiationDtos.Row("A","D","이름","0001","FL","P","프로젝트","2026-11-14",0,false,"1.00","0.52","6000000","3120000","2808000","10.0","6750000","12.5","3600000","9000000",List.of());
        var data=new RateRenegotiationDtos.Result("2026-11",OffsetDateTime.now(),"5","20",new RateRenegotiationDtos.Summary(1,61,"10.0","219600000","549000000"),Collections.nCopies(61,row),new RateRenegotiationDtos.Page(1,50,61),0,List.of(),List.of());
        when(rates.export(any())).thenReturn(data);
        var response=mvc.perform(post("/api/exports/master-data").contentType("application/json").content("{\"view\":\"RATE_RENEGOTIATIONS\",\"filters\":{\"increaseRate\":\"5\"},\"columns\":[\"name\",\"projectName\",\"annualGain\"],\"amountUnit\":\"천원\"}")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheet("단가 재협상");assertThat(sheet.getLastRowNum()).isEqualTo(62);
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(3600);
            assertThat(sheet.getRow(62).getCell(2).getNumericCellValue()).isEqualTo(219600);
        }
        verify(logs).append(argThat(e->e.screenId().equals("UI-32")&&e.filename().equals("단가재협상_20261114.xlsx")),any());
    }
    @Test void invalidViewSpecificFieldsDoNotReadOrLog() throws Exception {
        for(String body:List.of(settlementBody("WRONG"),settlementBody("MONTH").replace("\"amountUnit\":\"원\"","\"page\":2"),"{\"view\":\"RATE_RENEGOTIATIONS\",\"filters\":{\"month\":\"2026-11\"},\"columns\":[\"name\",\"projectName\"]}","{\"view\":\"RATE_RENEGOTIATIONS\",\"filters\":{},\"columns\":[\"name\"]}"))
            mvc.perform(post("/api/exports/master-data").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(settlements,rates,logs,old);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void rateWorkbookKeepsClassificationAndMethodsButOnlyFinanceReceivesMonthlyCost(boolean sensitive) throws Exception {
        var permissions=mock(com.sicost.permission.PermissionService.class);
        Set<String> fields=new HashSet<>(SettlementExportService.RATE_COLUMNS);
        if(!sensitive)fields.remove("monthlyLaborCost");
        when(permissions.capabilities()).thenReturn(new com.sicost.permission.PermissionService.Capabilities(
            Map.of("UI-32",Set.of("INQ","DNLD")),
            new com.sicost.permission.PermissionService.Scope(sensitive,Set.of("OPERATIONS"),Set.of()),
            sensitive,Map.of("UI-32",fields),Map.of(),Set.of()));
        when(permissions.candidateRateAllowed()).thenReturn(true);
        var projection=new com.sicost.permission.PermissionProjectionService(permissions);
        featureExports.setProjection(projection,json);
        excel.setProjection(new com.sicost.permission.PermissionExportProjection(
            projection,permissions,mock(com.sicost.authentication.AuthenticationTaskService.class)));
        var row=new RateRenegotiationDtos.Row("A","D","이름","0001","FL","P","프로젝트",
            "2026-11-14",0,true,"1.00","0.52","6000000","3120000","2808000",
            "10.0","6750000","12.5","3600000","9000000",List.of());
        var summary=new RateRenegotiationDtos.Summary(1,1,"10.0","3600000","9000000");
        when(rates.export(any())).thenReturn(new RateRenegotiationDtos.Result("2026-11",
            OffsetDateTime.parse("2026-11-14T09:00:00+09:00"),"5","20",summary,
            List.of(row),new RateRenegotiationDtos.Page(1,50,1),0,List.of(),List.of()));
        String body=json.writeValueAsString(Map.of("view","RATE_RENEGOTIATIONS",
            "filters",Map.of("renewalWindow","ALL","marginBelow","ALL","employeeType","FL"),
            "columns",SettlementExportService.RATE_COLUMNS,"amountUnit","원"));
        var response=mvc.perform(post("/api/exports/master-data").contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=book.getSheet("단가 재협상");
            List<String> headers=new ArrayList<>();
            sheet.getRow(0).forEach(cell->headers.add(cell.getStringCellValue()));
            assertThat(headers).contains("구분","이익률","목표 단가","필요 인상률","가정 연 증가");
            assertThat(headers.contains("월 노동비")).isEqualTo(sensitive);
            assertThat(sheet.getRow(1).getCell(headers.indexOf("구분")).getStringCellValue()).isEqualTo("FL");
            assertThat(sheet.getRow(2).getCell(headers.indexOf("가정 연 증가")).getNumericCellValue()).isEqualTo(3600000);
            assertThat(book.getSheet("계산 방법").getRow(1).getCell(1).getStringCellValue()).contains("노동비C");
            var text=new StringBuilder();
            for(var page:book)for(var r:page)for(var cell:r)text.append(cell.toString()).append('\n');
            if(!sensitive)assertThat(text.toString()).doesNotContain("2808000","monthlyLaborCost","월 노동비");
        }
        verify(rates).export(argThat(q->q.employeeType().equals("FL") && q.renewalWindow().equals("ALL")));
    }
    @Test void auditFailurePreventsFileTransmission() throws Exception {
        when(settlements.exportSettlement(eq("PART-001"),any())).thenReturn(result(0));doThrow(new IllegalStateException("audit failed")).when(logs).append(any(),any());
        mvc.perform(post("/api/exports/master-data").contentType("application/json").content(settlementBody("MONTH"))).andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
