package com.sicost.b8;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@Import({UploadIssuesService.class,UploadIssuesRepository.class,ExcelExportService.class,CurrentUserProvider.class,AccessLogWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class,DeveloperImportIssuesTest.Time.class})
class DeveloperImportIssuesTest {
    @TestConfiguration static class Time {@Bean Clock clock(){return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));}}
    @Autowired MockMvc mvc;
    @MockBean UploadIssuesMapper mapper;
    @MockBean CurrentUserMapper users;
    @MockBean AccessLogMapper logs;
    @MockBean PlatformTransactionManager transactions;
    @BeforeEach void setup(){when(users.findAdministrator()).thenReturn("admin");when(users.findAdministratorRoles()).thenReturn(List.of("ADMIN"));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(logs.insert(any())).thenReturn(1);when(mapper.exists(17)).thenReturn(true);}
    @ParameterizedTest @ValueSource(strings={"B7","B8"})
    void metadataSelectsKindScreenAndFilenameWithoutChangingB7(String form) throws Exception {
        when(mapper.formType(17)).thenReturn(form);when(mapper.issues(any())).thenReturn(List.of(new Issue(1,"WARN",1,"[파일]","=SUM(1,1)","알 수 없는 열")));
        var response=mvc.perform(get("/api/uploads/17/issues.xlsx")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))){assertThat(book.getSheetAt(0).getRow(1).getCell(3).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);assertThat(book.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);}
        String screen=form.equals("B8")?"UI-02":"UI-15";verify(logs).insert(argThat(e->e.screenId().equals(screen)&&e.action().equals("DNLD")));
        if(form.equals("B8"))assertThat(response.getHeader("Content-Disposition")).contains("B8_");
    }
    @Test void severityPaginationUsesSameFilterForRowsAndTotalAndNoAudit() throws Exception {
        when(mapper.count(any())).thenReturn(71L);when(mapper.issues(any())).thenReturn(List.of());
        mvc.perform(get("/api/uploads/17/issues").param("severity","WARN").param("page","2").param("size","50")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(71));
        verify(mapper).issues(argThat(q->q.severity().equals("WARN")&&q.offset()==50));verify(mapper).count(argThat(q->q.severity().equals("WARN")));verifyNoInteractions(logs);
    }
}
