package com.sicost.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.notifications.PredictionNotificationAdapter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.analysis.AnalysisAssistantDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisAssistantController.class)
@ContextConfiguration(classes=AnalysisAssistantController.class)
@Import({AnalysisAssistantService.class,AnalysisQuestionInterpreter.class,ExcelExportService.class,
    AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AnalysisAssistantContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean AnalysisAssistantMapper mapper;
    @MockBean AnalysisDataQueries queries;
    @MockBean AnalysisModelAdapter model;
    @MockBean AnalysisAccessPolicy policy;
    @MockBean PredictionNotificationAdapter notifications;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogMapper logs;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager transactions;
    final OffsetDateTime at=OffsetDateTime.parse("2026-11-16T10:00:00+09:00");
    Result result;

    @BeforeEach void setup() throws Exception {
        Clock fixed=Clock.fixed(at.toInstant(),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());
        when(clock.withZone(any())).thenReturn(fixed);
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(logs.insert(any())).thenReturn(1);
        var rows=IntStream.range(0,52).mapToObj(i->new Row("r"+i,null,
            List.of(new Cell("name","=SUM(1,1)",null),new Cell("amount","-1",null)),null)).toList();
        var table=new Table("t","저장 표","2026-11","매출","원","PROVISIONAL","BASE",
            List.of(new Column("name","이름","TEXT",""),new Column("amount","금액","DECIMAL","원")),
            rows,52,List.of(new Cell("amount","-52",null)),List.of(new Sort("name","ASC")),50,null);
        result=new Result("1","저장 결론",List.of(table),List.of("당시 가정"),List.of(),List.of(),List.of(),
            new Context("UI-26","2026-11",null,null),new Scope(List.of(),List.of(),"2026-11","2026-11",at.toLocalDate(),"2026-11"),
            "OK",null,List.of(),new Interpreter("SERVER_RULE",null,"서버 규칙"),at);
        when(mapper.find("admin","12")).thenReturn(stored("12","OK"));
    }
    Stored stored(String id,String status) throws Exception {
        return new Stored(id,"admin","이번 달 매출",json.writeValueAsString(result),"당시 원천","202611","Y",status,at.toLocalDateTime());
    }
    void noQuestionWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).nextId();verifyNoInteractions(queries,notifications);}

    @Test void durableContractDeclaresAllFourOperations() throws Exception {
        String contract=new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: createAnalysisQuestion","operationId: getAnalysisQuestions",
            "operationId: getAnalysisQuestion","operationId: getAnalysisQuestionExcel");
    }
    @Test void happyAuthAndSideEffectListReturnsOnlyOwnerSummariesWithoutWrites() throws Exception {
        when(mapper.page("admin",null,null,51)).thenReturn(List.of(stored("12","OK")));
        mvc.perform(get("/api/analysis/questions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items[0].qnaId").value("12"))
            .andExpect(jsonPath("$.data.items[0].provisional").value(true))
            .andExpect(jsonPath("$.data.hasMore").value(false)).andExpect(jsonPath("$.data.nextCursor").isEmpty());
        verify(mapper).page("admin",null,null,51);noQuestionWrites();verifyNoInteractions(logs);
    }
    @Test void happyListCursorCarriesTimestampAndNumericIdWithoutDuplicates() throws Exception {
        var first=IntStream.range(0,51).mapToObj(i->new Stored(Integer.toString(100-i),"admin","질문","{}","","202611","N","OK",at.toLocalDateTime())).toList();
        when(mapper.page("admin",null,null,51)).thenReturn(first);
        var response=mvc.perform(get("/api/analysis/questions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(50)).andExpect(jsonPath("$.data.hasMore").value(true)).andReturn();
        String cursor=json.readTree(response.getResponse().getContentAsString()).at("/data/nextCursor").asText();
        when(mapper.page("admin",at.toLocalDateTime(),"51",51)).thenReturn(List.of(first.get(50)));
        mvc.perform(get("/api/analysis/questions").param("cursor",cursor)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].qnaId").value("50"));
        noQuestionWrites();
    }
    @ParameterizedTest @ValueSource(strings={"0","51","bad"})
    void validationListRejectsInvalidLimits(String limit) throws Exception {
        mvc.perform(get("/api/analysis/questions").param("limit",limit)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(mapper,logs);
    }
    @Test void validationListRejectsCursorAndActorInjection() throws Exception {
        mvc.perform(get("/api/analysis/questions").param("cursor","not-a-cursor")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analysis/questions").param("userId","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(get("/api/analysis/questions").param("limit","1","2")).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper,logs);
    }
    @Test void happyAuthAndSideEffectDetailReplaysStoredSnapshotWithoutBusinessReads() throws Exception {
        mvc.perform(get("/api/analysis/questions/12")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.qnaId").value("12")).andExpect(jsonPath("$.data.result.tables[0].rows.length()").value(52))
            .andExpect(jsonPath("$.data.result.tables[0].rows[0].cells[1].value").value("-1"))
            .andExpect(jsonPath("$.data.baseMonth").value("2026-11"))
            .andExpect(jsonPath("$.data.snapshotNotice").value(org.hamcrest.Matchers.containsString("질문 당시 결과")));
        verify(policy).require("admin",result.appliedScope());noQuestionWrites();verifyNoInteractions(logs);
    }
    @Test void authMissingForeignAndRevokedDetailHaveIdentical404WithoutSensitiveBody() throws Exception {
        mvc.perform(get("/api/analysis/questions/13")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        doThrow(new ApiException(403,"ANALYSIS_ACCESS_DENIED","비공개")).when(policy).require(anyString(),any());
        mvc.perform(get("/api/analysis/questions/12")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("저장 결론"))));
        noQuestionWrites();verifyNoInteractions(logs);
    }
    @Test void validationDetailRejectsMalformedIdsAndQuery() throws Exception {
        mvc.perform(get("/api/analysis/questions/not-decimal")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("qnaId"));
        mvc.perform(get("/api/analysis/questions/1234567890123")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analysis/questions/12").param("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,logs);
    }
    @Test void happyAuthExcelUsesAllSnapshotRowsAndAccessLogWriterDnldCommitsBeforeBytes() throws Exception {
        byte[] bytes=mvc.perform(get("/api/analysis/questions/12/excel")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().exists("Content-Disposition")).andReturn().getResponse().getContentAsByteArray();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=workbook.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(53);
            assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(-1d);
            assertThat(sheet.getRow(53).getCell(1).getNumericCellValue()).isEqualTo(-52d);
            assertThat(workbook.getSheet("다운로드정보")).isNotNull();
        }
        var order=inOrder(mapper,transactions,logs);
        order.verify(mapper).find("admin","12");order.verify(transactions).commit(any());
        order.verify(logs).insert(argThat(e->e.userId().equals("admin")&&e.screenId().equals("UI-26")&&e.action().equals("DNLD")&&e.description().equals("12 AI분석 52")));
        order.verify(transactions).commit(any());noQuestionWrites();
    }
    @Test void businessExcelNonOkAndDnldFailureNeverSendFile() throws Exception {
        when(mapper.find("admin","12")).thenReturn(stored("12","NO_ANS"));
        mvc.perform(get("/api/analysis/questions/12/excel")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ANALYSIS_RESULT_NOT_EXPORTABLE")).andExpect(header().doesNotExist("Content-Disposition"));
        verifyNoInteractions(logs);
        when(mapper.find("admin","12")).thenReturn(stored("12","OK"));when(logs.insert(any())).thenReturn(0);
        mvc.perform(get("/api/analysis/questions/12/excel")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition"));
        verify(transactions,atLeastOnce()).rollback(any());noQuestionWrites();
    }
    @Test void authAndValidationExcelRejectMissingRevokedMalformedAndInjectedRequests() throws Exception {
        mvc.perform(get("/api/analysis/questions/13/excel")).andExpect(status().isNotFound());
        mvc.perform(get("/api/analysis/questions/bad/excel")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analysis/questions/12/excel").param("userId","other")).andExpect(status().isBadRequest());
        doThrow(new ApiException(403,"ANALYSIS_ACCESS_DENIED","비공개")).when(policy).require(anyString(),any());
        mvc.perform(get("/api/analysis/questions/12/excel")).andExpect(status().isNotFound()).andExpect(header().doesNotExist("Content-Disposition"));
        verifyNoInteractions(logs);noQuestionWrites();
    }
    @Test void happyPostStreamsProgressAndFinalOnlyAfterQuestionInsert() throws Exception {
        when(mapper.nextId()).thenReturn("14");
        when(model.classify(any(AnalysisQuestionInterpreter.Function.class),anyBoolean())).thenReturn(new Interpreter("SERVER_RULE",null,"서버 규칙"));
        when(queries.read(anyString(),any(),any(),any())).thenReturn(result);
        var pending=mvc.perform(post("/api/analysis/questions").contentType("application/json").content("{\"question\":\"이번 달 매출\"}"))
            .andExpect(request().asyncStarted()).andReturn();
        pending.getAsyncResult(5000);
        var completed=mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/event-stream")).andReturn();
        assertThat(completed.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("event:progress","event:final","\"persisted\":true","\"qnaId\":\"14\"");
        verify(mapper).insert(argThat(row->row.userId().equals("admin")&&row.qnaId().equals("14")));
        verify(transactions).commit(any());verifyNoInteractions(logs,notifications);
    }
    @Test void validationPostRejectsInvalidAndInjectedInputBeforeAcceptingWork() throws Exception {
        mvc.perform(post("/api/analysis/questions").contentType("application/json").content("{\"question\":\" \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("question"));
        mvc.perform(post("/api/analysis/questions").contentType("application/json").content("{\"question\":\"매출\",\"userId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,queries,logs,notifications);
    }
}
