package com.sicost.exportsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.security.AccountCipher;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.ibatis.cursor.Cursor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(MasterDataExportController.class)
@ContextConfiguration(classes=MasterDataExportController.class)
@Import({MasterDataExportService.class, MasterDataExportRepository.class, ExcelExportService.class,
    AccessLogWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class MasterDataExportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MasterDataExportMapper mapper;
    @MockBean PeopleExportService people;
    @MockBean AssignmentExportService assignments;
    @MockBean AccessLogMapper logs;
    @MockBean CurrentUserProvider users;
    @MockBean AccountCipher accounts;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    Cursor<Map<String,Object>> clients;
    Cursor<Map<String,Object>> parts;
    @SuppressWarnings("unchecked")
    Cursor<Map<String,Object>> cursor(List<Map<String,Object>> rows) {
        Cursor<Map<String,Object>> cursor = mock(Cursor.class);
        when(cursor.iterator()).thenAnswer(call -> rows.iterator());
        return cursor;
    }
    @BeforeEach void setup() {
        var fixed = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant()); when(clock.getZone()).thenReturn(fixed.getZone());
        when(clock.withZone(any())).thenReturn(fixed);
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "임시 관리자", List.of("ADMIN"), true));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(logs.insert(any())).thenReturn(1);
        clients=cursor(List.of()); parts=cursor(List.of());
        when(mapper.clients(any())).thenAnswer(call -> clients); when(mapper.parts(any())).thenAnswer(call -> parts);
    }
    Map<String,Object> clientRequest() { return new LinkedHashMap<>(Map.of("view", "CLIENTS", "filters", Map.of("type", "VNDR", "useYn", "Y", "q", "검증"), "columns", List.of("name", "account", "phone"))); }
    Map<String,Object> partRequest() { return new LinkedHashMap<>(Map.of("view", "PARTS", "filters", Map.of(), "columns", MasterDataExportInput.PART_COLUMNS, "amountUnit", "천원")); }
    byte[] download(Map<String,Object> request) throws Exception {
        return mvc.perform(post("/api/exports/master-data").contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
            .andExpect(header().exists("X-Request-Id")).andReturn().getResponse().getContentAsByteArray();
    }
    @Test void clientsAll51RowsHaveVisibleColumnsMaskedAccountAndInfoWithoutPageCap() throws Exception {
        clients=cursor(IntStream.range(0,51).mapToObj(i -> Map.<String,Object>of("name", "검증"+i, "encryptedAccount", "stored-test-value", "bankName", "검증은행", "phone", "0012345678")).toList());
        when(accounts.masked("stored-test-value")).thenReturn("****1234");
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(download(clientRequest())))) {
            var data=workbook.getSheetAt(0); assertThat(data.getLastRowNum()).isEqualTo(51);
            assertThat(data.getRow(0).getLastCellNum()).isEqualTo((short)3);
            assertThat(data.getRow(0).getCell(0).getStringCellValue()).isEqualTo("상호");
            assertThat(data.getRow(51).getCell(1).getStringCellValue()).isEqualTo("검증은행 ****1234");
            assertThat(data.getRow(1).getCell(2).getStringCellValue()).isEqualTo("0012345678");
            var info=workbook.getSheetAt(workbook.getNumberOfSheets()-1); assertThat(info.getSheetName()).isEqualTo("다운로드정보");
            assertThat(info.getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
            assertThat(info.getRow(1).getCell(1).getStringCellValue()).isEqualTo("임시 관리자");
            assertThat(info.getRow(3).getCell(1).getStringCellValue()).contains("VNDR", "검증", "name ASC");
            assertThat(info.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(51);
        }
        verify(mapper).clients(argThat(q -> q.filters().type().equals("VNDR") && q.filters().useYn().equals("Y") && q.filters().q().equals("검증")));
        verify(logs).insert(argThat(e -> e.userId().equals("admin") && e.action().equals("DNLD") && e.screenId().equals("UI-15") && e.description().strip().equals("거래처 51")));
        verify(clients).close();
    }
    @Test void noAuthenticationHeaderOrBusinessQualificationRequiredAndSnapshotClosesBeforeAudit() throws Exception {
        download(clientRequest());
        var order=inOrder(transactions, mapper, clients, logs);
        order.verify(transactions).getTransaction(argThat(d -> d.isReadOnly() && d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
        order.verify(mapper).clients(any()); order.verify(clients).close(); order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(d -> !d.isReadOnly())); order.verify(logs).insert(any()); order.verify(transactions).commit(any());
    }
    @Test void parts1001WonBecomesExact1001ThousandNumericAndNineColumns() throws Exception {
        parts=cursor(List.of(Map.of("name", "영업", "salesExpenseAmount", new BigDecimal("1001"), "profitRate", new BigDecimal("40.25"), "useYn", "Y", "personCount", 2L, "projectCount", 3L)));
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(download(partRequest())))) {
            var row=workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(BigDecimal.valueOf(row.getCell(3).getNumericCellValue())).isEqualByComparingTo("1.001");
            assertThat(workbook.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)9);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue()).contains("천원");
        }
    }
    @Test void zeroResultsStillHaveTitleRowAndZeroInfoTotal() throws Exception {
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(download(partRequest())))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(workbook.getSheetAt(1).getRow(5).getCell(1).getNumericCellValue()).isZero();
        }
    }
    @ParameterizedTest @ValueSource(strings={"=1+1", "+SUM(A1)", "-1", "@external"})
    void formulaLikeTextIsAlwaysString(String text) throws Exception {
        clients=cursor(List.of(Map.of("name", text)));
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(download(clientRequest())))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo(text);
        }
    }
    @ParameterizedTest @ValueSource(strings={"accountNumber", "unknown", "name,name"})
    void forbiddenUnknownAndDuplicateColumnsFailBeforeReads(String column) throws Exception {
        var request=clientRequest(); request.put("columns", column.equals("name,name") ? List.of("name","name") : List.of("name",column)); reject(request,"columns","VALIDATION_ERROR");
    }
    @Test void hidingRequiredNameFails() throws Exception {var request=clientRequest(); request.put("columns", List.of("phone")); reject(request,"columns","VALIDATION_ERROR");}
    @Test void unregisteredSortFails() throws Exception {var request=clientRequest(); request.put("sort", "name;DROP TABLE"); reject(request,"sort","VALIDATION_ERROR");}
    @Test void forgedProcessorFails() throws Exception {var request=clientRequest(); request.put("regUserId", "other"); reject(request,"regUserId","UNKNOWN_FIELD");}
    @Test void nestedForgedProcessorFails() throws Exception {var request=clientRequest(); request.put("filters", Map.of("userId","other")); reject(request,"filters.userId","UNKNOWN_FIELD");}
    @Test void pageAndSelectionAreNotAccepted() throws Exception {var request=clientRequest(); request.put("page",1); reject(request,"page","UNKNOWN_FIELD");}
    @Test void partsCannotUseClientFilter() throws Exception {var request=partRequest(); request.put("filters", Map.of("type","CUST")); reject(request,"filters.type","VALIDATION_ERROR");}
    @Test void partsColumnOrderIsFixed() throws Exception {var request=partRequest(); request.put("columns", List.of("name")); reject(request,"columns","VALIDATION_ERROR");}
    @Test void unknownQueryFails() throws Exception {
        mvc.perform(post("/api/exports/master-data").param("actor","other").contentType("application/json").content(json.writeValueAsString(clientRequest())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper, logs);
    }
    void reject(Map<String,Object> request,String field,String code) throws Exception {
        var response=mvc.perform(post("/api/exports/master-data").contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.field").value(field))
            .andExpect(header().doesNotExist("Content-Disposition")).andReturn().getResponse();
        var error=json.readTree(response.getContentAsByteArray()); assertThat(error.get("message").asText()).containsPattern("[가-힣]");
        assertThat(error.get("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        verifyNoInteractions(mapper, logs, transactions);
    }
    @Test void omittedStatusMeansAllAndDefaultsAreExplicit() throws Exception {
        var request=clientRequest(); request.put("filters", Map.of()); download(request);
        verify(mapper).clients(argThat(q -> q.filters().useYn()==null && q.sort().equals("name") && q.direction().equals("ASC")));
    }
    Set<Path> spools() throws Exception {
        try (var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) { return paths.filter(p -> p.getFileName().toString().startsWith("sicost-export-")).collect(java.util.stream.Collectors.toSet()); }
    }
    @Test void auditInsertFailureReturnsOnlyJsonAndDeletesSpoolWithoutRetry() throws Exception {
        var before=spools(); doThrow(new IllegalStateException("private database error")).when(logs).insert(any());
        mvc.perform(post("/api/exports/master-data").contentType("application/json").content(json.writeValueAsString(clientRequest())))
            .andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database error"))));
        assertThat(spools()).isEqualTo(before); verify(logs,times(1)).insert(any()); verify(transactions).rollback(any());
    }
    @Test void failedQueryClosesCursorDeletesSpoolAndNeverAudits() throws Exception {
        var before=spools(); when(clients.iterator()).thenThrow(new IllegalStateException("query failure"));
        mvc.perform(post("/api/exports/master-data").contentType("application/json").content(json.writeValueAsString(clientRequest())))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(spools()).isEqualTo(before); verify(clients).close(); verifyNoInteractions(logs);
    }
    @Test void successfulTransmissionDeletesSpool() throws Exception {var before=spools(); download(clientRequest()); assertThat(spools()).isEqualTo(before);}

    @Test void peopleViewsAreRoutedToThePeopleExportAndExistingViewsAreNot() throws Exception {
        when(people.create(any(), any())).thenThrow(new ApiException(400, "VALIDATION_ERROR", "조건 오류", "columns", null));
        mvc.perform(post("/api/exports/master-data").contentType("application/json")
            .content("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"name\"]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("columns"));
        mvc.perform(post("/api/exports/master-data").contentType("application/json")
            .content("{\"view\":\"CANDIDATES\",\"filters\":{},\"columns\":[\"name\"]}")).andExpect(status().isBadRequest());
        verify(people, times(2)).create(any(), any());
        mvc.perform(post("/api/exports/master-data").contentType("application/json")
            .content("{\"view\":\"ZZZ\",\"filters\":{},\"columns\":[\"name\"]}")).andExpect(status().isBadRequest());
        verify(people, times(2)).create(any(), any());
    }

    @Test void assignmentViewIsRoutedToTheAssignmentExportOnly() throws Exception {
        when(assignments.create(any(), any())).thenThrow(new ApiException(400, "VALIDATION_ERROR", "조건 오류", "filters", null));
        mvc.perform(post("/api/exports/master-data").contentType("application/json")
            .content("{\"view\":\"ASSIGNMENTS\",\"filters\":{\"year\":2026},\"columns\":[\"projectName\"]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("filters"));
        verify(assignments).create(any(), any());verify(people, never()).create(any(), any());
    }
}
