package com.sicost.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.notifications.PredictionNotificationAdapter;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

/** Real PostgreSQL/MyBatis/transaction tests in the repository's isolated test schema. */
@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnalysisAssistantDatabaseTest {
    PostgreSqlDatabase db;JdbcTemplate sql;AnalysisAssistantMapper mapper;com.sicost.common.history.AccessLogMapper access;ObjectMapper json;
    AnalysisAssistantService service;AnalysisDataQueries queries;PredictionNotificationAdapter notifications;
    final AtomicBoolean allowed=new AtomicBoolean(true);final Clock clock=Clock.fixed(Instant.parse("2026-11-16T01:00:00Z"),ZoneId.of("Asia/Seoul"));
    @BeforeAll void install()throws Exception {
        db=new PostgreSqlDatabase();db.install();sql=db.sql;json=JsonMapper.builder().findAndAddModules().build();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mappers/common/AccessLogMapper.xml"));var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(AnalysisAssistantMapper.class);configuration.addMapper(com.sicost.common.history.AccessLogMapper.class);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);var sessions=new SqlSessionTemplate(factory.getObject());mapper=sessions.getMapper(AnalysisAssistantMapper.class);access=sessions.getMapper(com.sicost.common.history.AccessLogMapper.class);
    }
    @BeforeEach void setup(){allowed.set(true);queries=mock(AnalysisDataQueries.class);notifications=mock(PredictionNotificationAdapter.class);service=create(mapper);when(queries.read(anyString(),any(),any(),any())).thenAnswer(call->result(call.getArgument(1)));}
    AnalysisAssistantService create(AnalysisAssistantMapper storage){return create(storage,clock);}
    AnalysisAssistantService create(AnalysisAssistantMapper storage,Clock requestClock){var users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");return new AnalysisAssistantService(users,requestClock,storage,new AnalysisQuestionInterpreter(),queries,json,new DataSourceTransactionManager(db.source),notifications,(user,target)->allowed.get(),new AnalysisModelAdapter("NONE","","",30,json));}
    Result result(Question question){var target=new Target("COMPANY","ALL","회사",List.of("salesAmt"));return new Result("1","검증용 조회 결과",List.of(new Table("t","검증 표","2026-12","매출","원","PLANNED","BASE",List.of(new Column("value","매출","DECIMAL","원")),List.of(new Row("r",target,List.of(new Cell("value","123456789012345",null)),null)),1,List.of(new Cell("value","123456789012345",null)),List.of(new Sort("value","ASC")),50,null)),List.of("시험대역 결과: 업무 계산 검증과 구분"),List.of(),List.of(),List.of(),question.context(),new Scope(List.of(target),List.of(),"2026-12","2027-05",LocalDate.of(2026,11,16),"2026-11"),"OK",null,List.of(),new Interpreter("SERVER_RULE",null,"서버 규칙으로 해석·외부 모델 미사용"),OffsetDateTime.now(clock));}
    Final ask(String question){return service.execute(service.accept(new Question(question,new Context("UI-26",null,null,null))),p->{});}
    @Test void repeatedQuestionsAppendWithoutChangingEarlierSnapshotOrAuditMetadata(){
        long before=db.count("TB_SY_AI_QNA");var first=ask("이번 달 매출");String original=mapper.find("admin",first.qnaId()).answer();var next=ask("이번 달 매출");
        assertThat(first.persisted()).isTrue();assertThat(next.qnaId()).isNotEqualTo(first.qnaId());assertThat(db.count("TB_SY_AI_QNA")).isEqualTo(before+2);assertThat(mapper.find("admin",first.qnaId()).answer()).isEqualTo(original);
        assertThat(sql.queryForMap("SELECT user_id,reg_user_id,mod_user_id,mod_dttm FROM tb_sy_ai_qna WHERE qna_id=?::numeric",first.qnaId())).containsEntry("user_id","admin").containsEntry("reg_user_id","admin").containsEntry("mod_user_id",null).containsEntry("mod_dttm",null);verifyNoInteractions(notifications);
    }
    @Test void reacceptingAcrossSeoulYearAndQuarterBoundaryReinterpretsRelativeButKeepsExplicitPeriod(){
        // A controlled application clock, not a change to the runtime/server clock.
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(db.source)).executeWithoutResult(tx->{
        tx.setRollbackOnly(); // Keep future-dated test records out of other tests' keyset pages.
        var now=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-12-31T14:59:59Z"));
        var moving=mock(Clock.class);when(moving.instant()).thenAnswer(call->now.get());when(moving.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(moving.withZone(any())).thenReturn(moving);
        service=create(mapper,moving);var first=ask("다음 분기 대기 인력");String original=mapper.find("admin",first.qnaId()).answer();
        var explicitFirst=ask("2026년 10월 회사 매출");now.set(Instant.parse("2026-12-31T15:00:00Z"));
        var next=ask("다음 분기 대기 인력");var explicitNext=ask("2026년 10월 회사 매출");
        var intents=org.mockito.ArgumentCaptor.forClass(AnalysisQuestionInterpreter.Intent.class);verify(queries,times(4)).read(eq("admin"),any(),intents.capture(),any());
        assertThat(intents.getAllValues()).extracting(i->i.from().toString()).containsExactly("2027-01","2026-10","2027-04","2026-10");
        assertThat(intents.getAllValues()).extracting(i->i.to().toString()).containsExactly("2027-03","2026-10","2027-06","2026-10");
        assertThat(next.qnaId()).isNotEqualTo(first.qnaId());assertThat(explicitNext.qnaId()).isNotEqualTo(explicitFirst.qnaId());
        assertThat(mapper.find("admin",first.qnaId()).answer()).isEqualTo(original);
        assertThat(mapper.find("admin",first.qnaId()).questionAt().toLocalDate()).isEqualTo(LocalDate.of(2026,12,31));
        assertThat(mapper.find("admin",next.qnaId()).questionAt().toLocalDate()).isEqualTo(LocalDate.of(2027,1,1));
        });
    }
    @Test void onlySuccessfulPredictionsPublishAfterInsertInSameTransaction(){
        doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();assertThat(mapper.find("admin",Long.toString(call.getArgument(0)))).isNotNull();return null;}).when(notifications).predictionCompleted(anyLong(),eq("admin"));
        var done=ask("앞으로 6개월 매출 전망");assertThat(done.status()).isEqualTo("OK");verify(notifications,times(1)).predictionCompleted(Long.parseLong(done.qnaId()),"admin");
    }
    @Test void notificationFailureRollsBackOkAndStoresOnlyAnErrorCompletion(){
        long before=db.count("TB_SY_AI_QNA");doThrow(new IllegalStateException("private error")).when(notifications).predictionCompleted(anyLong(),anyString());
        var done=ask("다음 분기 대기 인력");assertThat(done.status()).isEqualTo("ERR");assertThat(done.persisted()).isTrue();assertThat(db.count("TB_SY_AI_QNA")).isEqualTo(before+1);assertThat(mapper.find("admin",done.qnaId()).status()).isEqualTo("ERR");assertThat(mapper.find("admin",done.qnaId()).answer()).doesNotContain("private error");
    }
    @Test void ownersAndRevokedPoliciesAreCheckedOnStoredAnswersAndPageCursorIsStable()throws Exception {
        var mine=ask("이번 달 매출");String foreign=mapper.nextId();var row=mapper.find("admin",mine.qnaId());mapper.insert(new Insert(foreign,"other","다른 사용자의 질문",row.answer(),"", "202611","Y","OK",LocalDateTime.of(2030,1,1,0,0),LocalDateTime.of(2030,1,1,0,0)));
        assertThatThrownBy(()->service.detail(foreign)).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(404);assertThat(e.getMessage()).isEqualTo("질문 기록을 찾을 수 없습니다.");});
        assertThatThrownBy(()->service.detail("999999999999")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(404));
        for(int i=0;i<52;i++)mapper.insert(new Insert(mapper.nextId(),"admin","페이지 "+i,row.answer(),"","202611","Y","OK",LocalDateTime.of(2026,11,16,10,0),LocalDateTime.of(2026,11,16,10,0)));
        var page=service.page(null,50);var next=service.page(page.nextCursor(),50);assertThat(page.items().stream().map(item->new java.math.BigInteger(item.qnaId())).toList()).isSortedAccordingTo(Comparator.reverseOrder());assertThat(page.items()).hasSize(50).noneMatch(item->item.qnaId().equals(foreign));assertThat(page.hasMore()).isTrue();assertThat(next.items()).noneMatch(item->page.items().stream().anyMatch(previous->previous.qnaId().equals(item.qnaId())));
        allowed.set(false);assertThatThrownBy(()->service.detail(mine.qnaId())).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(404);assertThat(e.getMessage()).isEqualTo("질문 기록을 찾을 수 없습니다.");});
    }
    @Test void exhaustedPersistenceNeverReportsSuccessAndAppendOnlyBoundaryStaysClosed(){
        var broken=mock(AnalysisAssistantMapper.class,org.mockito.AdditionalAnswers.delegatesTo(mapper));doThrow(new IllegalStateException("SQL password=secret")).when(broken).insert(any());service=create(broken);long before=db.count("TB_SY_AI_QNA");var done=ask("이번 달 매출");assertThat(done.persisted()).isFalse();assertThat(done.qnaId()).isNull();assertThat(done.status()).isEqualTo("ERR");assertThat(done.serverErrorId()).isNotBlank();assertThat(done.message()).doesNotContain("SQL","secret");assertThat(db.count("TB_SY_AI_QNA")).isEqualTo(before);verifyNoInteractions(notifications);
        assertThatThrownBy(()->PersistenceBoundary.requireAllowed("UPDATE TB_SY_AI_QNA SET ANS_CN=? WHERE QNA_ID=?")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->PersistenceBoundary.requireAllowed("DELETE FROM TB_SY_AI_QNA WHERE QNA_ID=?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void realSavedWorkbookIncludesAllRowsAndCommitsDownloadAuditBeforeBytesWithoutReadingBusinessAgain()throws Exception {
        var saved=result(new Question("=SUM(1,1) 매출",new Context("UI-26",null,null,null)));
        var rows=java.util.stream.IntStream.range(0,52).mapToObj(i->new Row("row"+i,null,List.of(new Cell("name","=SUM(1,1) "+i,null),new Cell("amount","-1",null)),null)).toList();
        var table=new Table("all","전체 저장 표","2026-12","매출","원","PLANNED","BASE",List.of(new Column("name","이름","TEXT",""),new Column("amount","금액","DECIMAL","원")),rows,rows.size(),List.of(new Cell("amount","-52",null)),List.of(new Sort("name","ASC")),50,null);
        var answer=new Result(saved.schemaVersion(),saved.conclusion(),List.of(table),saved.assumptions(),saved.unknownReasons(),saved.usedData(),saved.nextActions(),saved.context(),saved.appliedScope(),saved.status(),saved.reason(),saved.screenLinks(),saved.interpreter(),saved.dataAsOf());
        String id=mapper.nextId();mapper.insert(new Insert(id,"admin","=SUM(1,1) 매출",json.writeValueAsString(answer),"전체 저장 표","202611","Y","OK",LocalDateTime.now(clock),LocalDateTime.now(clock)));
        var users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        var audit=new com.sicost.common.history.AccessLogWriter(access,users,clock);var excel=new com.sicost.common.excel.ExcelExportService(users,audit,clock,new DataSourceTransactionManager(db.source));
        var controller=new AnalysisAssistantController(service,excel);var request=new org.springframework.mock.web.MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");var response=new org.springframework.mock.web.MockHttpServletResponse();long before=db.count("TB_SY_ACCS_LOG");
        try {
            controller.excel(id,request,response);assertThat(response.getStatus()).isEqualTo(200);assertThat(db.count("TB_SY_ACCS_LOG")).isEqualTo(before+1);
            assertThat(sql.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE USER_ID='admin' AND ACT_TYPE_CD='DNLD' AND SCRN_ID='UI-26' AND TRGT_DESC=?",Long.class,id+" AI분석 52")).isEqualTo(1L);
            try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))){var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(53);assertThat(sheet.getRow(53).getCell(0).getStringCellValue()).isEqualTo("전체 합계");assertThat(sheet.getRow(53).getCell(1).getNumericCellValue()).isEqualTo(-52d);assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(-1d);assertThat(book.getSheet("다운로드정보")).isNotNull();}
            verifyNoInteractions(queries,notifications);allowed.set(false);var denied=new org.springframework.mock.web.MockHttpServletResponse();assertThatThrownBy(()->controller.excel(id,request,denied)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(404));assertThat(denied.getContentAsByteArray()).isEmpty();assertThat(db.count("TB_SY_ACCS_LOG")).isEqualTo(before+1);
        }finally{controller.close();}
    }
}
