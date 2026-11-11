package com.sicost.uploadsops;

import com.sicost.common.api.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
@Import({UploadIssuesService.class, UploadIssuesRepository.class, ExcelExportService.class, CurrentUserProvider.class,
    AccessLogWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class, UploadIssuesContractTest.Time.class})
class UploadIssuesContractTest {
    @TestConfiguration static class Time {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneId.of("Asia/Seoul")); }
    }
    @Autowired MockMvc mvc;
    @MockBean UploadIssuesMapper mapper;
    @MockBean CurrentUserMapper users;
    @MockBean AccessLogMapper logs;
    @MockBean PlatformTransactionManager transactions;
    final List<Issue> stored = List.of(
        new Issue(1,"WARN",1,"알 수 없는 열","","알 수 없는 열입니다."),
        new Issue(2,"ERR",2,"계좌번호","[비공개]","계좌번호 형식을 확인해 주세요."),
        new Issue(3,"WARN",3,"상호","저장된 발췌…","같은 상호입니다."));
    @BeforeEach void setup() {
        when(users.findAdministrator()).thenReturn("admin");
        when(users.findAdministratorRoles()).thenReturn(List.of("ADMIN"));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(logs.insert(any())).thenReturn(1);
        when(mapper.exists(17)).thenReturn(true);
        when(mapper.issues(any())).thenAnswer(call -> {
            Query q=call.getArgument(0);
            return stored.stream().filter(i->q.severity()==null || q.severity().equals(i.severity())).skip(q.offset()).limit(q.limit()).toList();
        });
        when(mapper.count(any())).thenAnswer(call -> {
            Query q=call.getArgument(0);return stored.stream().filter(i->q.severity()==null || q.severity().equals(i.severity())).count();
        });
    }
    @Test void noAuthenticationDownloadsRealFiveColumnWorkbookAllIssuesAndOneAdminAudit() throws Exception {
        byte[] bytes=mvc.perform(get("/api/uploads/17/issues.xlsx"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().exists("X-Request-Id")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
            .andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var data=book.getSheetAt(0);assertThat(data.getLastRowNum()).isEqualTo(3);
            assertThat(java.util.stream.IntStream.range(0,5).mapToObj(i->data.getRow(0).getCell(i).getStringCellValue()).toList()).containsExactly("유형","행","열","입력값","이유");
            assertThat(data.getRow(2).getCell(3).getStringCellValue()).isEqualTo("[비공개]");
            assertThat(data.getRow(3).getCell(3).getStringCellValue()).isEqualTo("저장된 발췌…");
            assertThat(book.getSheetName(book.getNumberOfSheets()-1)).isEqualTo("다운로드정보");
            assertThat(book.getSheetAt(1).getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
            assertThat(book.getSheetAt(1).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(3);
        }
        verify(logs).insert(argThat(e->e.action().equals("DNLD") && e.userId().equals("admin") && e.screenId().equals("UI-15") && e.description().equals("17 거래처_업로드검사 3")));
    }
    @Test void filteredPageCountMatchesFilterAndOutOfRangePageIsEmpty() throws Exception {
        mvc.perform(get("/api/uploads/17/issues").param("severity","WARN").param("size","20").param("page","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.page").value(2));
        mvc.perform(get("/api/uploads/17/issues")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].rowNumber").value(1))
            .andExpect(jsonPath("$.data.items[1].inputValue").value("[비공개]")).andExpect(jsonPath("$.data.size").value(50));
        verifyNoInteractions(logs);
    }
    @Test void errDownloadContainsAllRowsAcrossChunksWithoutWarnOrPageCap() throws Exception {
        doAnswer(call->{Query q=call.getArgument(0);return java.util.stream.IntStream.range((int)q.offset(),(int)Math.min(1201,q.offset()+q.limit()))
            .mapToObj(i->new Issue(i+1,"ERR",i+2,"상호","=SUM(1,1)","중복")).toList();}).when(mapper).issues(any());
        var response=mvc.perform(get("/api/uploads/17/issues.xlsx").param("severity","ERR")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(1201);
            for(var row:book.getSheetAt(0)) if(row.getRowNum()>0) {
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("ERR");
                assertThat(row.getCell(3).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            }
        }
        verify(mapper,times(2)).issues(argThat(q->q.severity().equals("ERR") && q.limit()==1000));
        verify(logs).insert(argThat(e->e.description().endsWith("1201")));
    }
    @Test void warnDownloadIncludesFileWarningAndNoErrors() throws Exception {
        var response=mvc.perform(get("/api/uploads/17/issues.xlsx").param("severity","WARN")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);
            assertThat(book.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(book.getSheetAt(0).getRow(2).getCell(3).getStringCellValue()).isEqualTo("저장된 발췌…");
        }
    }
    @Test void zeroIssuesStillDownloadsHeaderAndInformation() throws Exception {
        doReturn(List.of()).when(mapper).issues(any());
        var response=mvc.perform(get("/api/uploads/17/issues.xlsx")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {assertThat(book.getSheetAt(0).getLastRowNum()).isZero();}
        verify(logs).insert(argThat(e->e.description().endsWith(" 0")));
    }
    @ParameterizedTest @ValueSource(strings={"/issues","/issues.xlsx"})
    void invalidSeverityHasKoreanFieldErrorAndNoReadOrAudit(String suffix) throws Exception {
        mvc.perform(get("/api/uploads/17"+suffix).param("severity","ALL"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value("severity")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));
        verifyNoInteractions(mapper,logs);
    }
    @ParameterizedTest @ValueSource(strings={"/issues","/issues.xlsx"})
    void unknownProcessorRejectedWithoutFileOrWrites(String suffix) throws Exception {
        var response=mvc.perform(get("/api/uploads/17"+suffix).param("processor","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("processor")).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).isNull();verifyNoInteractions(mapper,logs);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","x","9223372036854775808"})
    void invalidId(String id) throws Exception {
        mvc.perform(get("/api/uploads/"+id+"/issues")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
    }
    @Test void invalidPageSizeDuplicateSeverityAndDownloadPageAreRejected() throws Exception {
        mvc.perform(get("/api/uploads/17/issues").param("page","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/uploads/17/issues").param("size","10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/uploads/17/issues").param("severity","ERR","WARN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/uploads/17/issues.xlsx").param("page","2")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,logs);
    }
    @ParameterizedTest @ValueSource(strings={"/issues","/issues.xlsx"})
    void missingUploadIs404(String suffix) throws Exception {
        mvc.perform(get("/api/uploads/99"+suffix)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(logs);
    }
    Set<Path> spools() throws Exception {
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p->p.getFileName().toString().startsWith("sicost-export-")).collect(java.util.stream.Collectors.toSet());
        }
    }
    @Test void auditFailureReturnsOnlyJsonAndDeletesSpoolWithoutRetry() throws Exception {
        Set<Path> before=spools();when(logs.insert(any())).thenThrow(new IllegalStateException("private database detail"));
        var response=mvc.perform(get("/api/uploads/17/issues.xlsx")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).isNull();assertThat(response.getContentAsString()).doesNotContain("private database detail");
        assertThat(spools()).isEqualTo(before);verify(logs,times(1)).insert(any());verify(transactions).rollback(any());
    }
    @Test void generationFailureDeletesSpoolAndNeverAudits() throws Exception {
        Set<Path> before=spools();doThrow(new IllegalStateException()).when(mapper).issues(any());
        mvc.perform(get("/api/uploads/17/issues.xlsx")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(spools()).isEqualTo(before);verifyNoInteractions(logs);
    }
}
