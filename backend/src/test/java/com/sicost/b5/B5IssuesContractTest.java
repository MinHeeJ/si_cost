package com.sicost.b5;

import com.sicost.common.api.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.uploadsops.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.uploadsops.UploadIssuesDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UploadIssuesController.class)
@ContextConfiguration(classes=UploadIssuesController.class)
@Import({UploadIssuesService.class,UploadIssuesRepository.class,ExcelExportService.class,CurrentUserProvider.class,AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class,B5IssuesContractTest.Time.class})
class B5IssuesContractTest {
    @TestConfiguration static class Time {@Bean Clock clock(){return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));}}
    @Autowired MockMvc mvc;
    @MockBean UploadIssuesMapper mapper;
    @MockBean CurrentUserMapper users;
    @MockBean AccessLogMapper logs;
    @MockBean PlatformTransactionManager transactions;
    @BeforeEach void setup(){when(users.findAdministrator()).thenReturn("admin");when(users.findAdministratorRoles()).thenReturn(List.of("ADMIN"));when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(logs.insert(any())).thenReturn(1);when(mapper.exists(17)).thenReturn(true);when(mapper.formType(17)).thenReturn("B5");when(mapper.issues(any())).thenReturn(List.of(new Issue(1,"WARN",2,"프로젝트ID","=SUM(1,1)","입력값 확인")));when(mapper.count(any())).thenReturn(1L);}
    @Test void b5IssuesDownloadUsesUi12AndSafeTextCellsWithoutChangingOtherBranches() throws Exception {var response=mvc.perform(get("/api/uploads/17/issues.xlsx").param("severity","WARN")).andExpect(status().isOk()).andReturn().getResponse();try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(1);assertThat(book.getSheetAt(0).getRow(1).getCell(3).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);assertThat(book.getSheetAt(0).getRow(1).getCell(3).getStringCellValue()).isEqualTo("=SUM(1,1)");}verify(logs).insert(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-12")&&e.description().equals("17 투입_업로드검사 1")));}
    @Test void issueListKeepsSameFilterCountPageAndDoesNotAuditDownload() throws Exception {mvc.perform(get("/api/uploads/17/issues").param("severity","WARN").param("size","20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].rowNumber").value(2));verify(mapper).count(argThat(q->q.severity().equals("WARN")&&q.limit()==20));verifyNoInteractions(logs);}
}
