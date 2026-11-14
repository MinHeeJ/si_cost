package com.sicost.progress;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.progress.ProjectProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectProgressPersistenceTest {
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    PostgreSqlDatabase db;
    AnnotationConfigApplicationContext context;
    ProjectProgressService service;
    ProjectProgressQueryService queries;
    ProjectProgressAggregateRevision revisions;
    String id;
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00.123456Z"),ZoneId.of("Asia/Seoul"));
    @BeforeEach void connectIsolatedWritableSchema()throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.addMapper(AggregateLockMapper.class);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);context.registerBean(Clock.class,()->clock);
        context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(ProjectProgressMapper.class,()->session.getMapper(ProjectProgressMapper.class));
        context.registerBean(ProjectProgressRevisionMapper.class,()->session.getMapper(ProjectProgressRevisionMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        var costs=mock(ProjectProgressCostSource.class);when(costs.load(any(),any())).thenReturn(new Cost(BigDecimal.ZERO,new Completeness(true,true,false,List.of(),0,"2026-01","2026-07",null)));
        context.registerBean(ProjectProgressCostSource.class,()->costs);
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,ProjectProgressAggregateRevision.class,CommitInvalidation.class,WriteCoordinator.class,ChangeHistoryWriter.class,ProjectProgressRepository.class,ProjectProgressQueryService.class,ProjectProgressService.class,ProjectProgressLifecycle.class);
        context.refresh();service=context.getBean(ProjectProgressService.class);queries=context.getBean(ProjectProgressQueryService.class);revisions=context.getBean(ProjectProgressAggregateRevision.class);
        id="T"+UUID.randomUUID().toString().replace("-","").substring(0,19);
        db.sql.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,BDGT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,?,'TK','ING',DATE '2026-01-01',DATE '2026-07-19',1000,800,0,0,?)",id,"진행 검증",context.getBean(CurrentUserProvider.class).userId());
    }
    @AfterEach void removeOnlyOwnedSchema(){
        if(context!=null)context.close();
        if(db!=null){String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    Write token(){return new Write(2026,revisions.revision(id));}
    MemoInput memo(String text,Integer actual){return new MemoInput(2026,revisions.revision(id),text,null,null,actual,actual!=null);}
    Map<String,List<String>> financial(){var result=new LinkedHashMap<String,List<String>>();for(String table:List.of("tb_pj_proj","tb_pj_proj_chg","tb_co_mon_cost","tb_hr_mon_pay","tb_pj_asgn","tb_pj_asgn_mon","tb_pj_asgn_hist","tb_pj_tk_dstrb","tb_pj_drct_exps","tb_cl_close","tb_bl_bill","tb_bl_rcpt"))result.put(table,db.sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));return result;}
    @Test void readsAndPreviewNeverChangeStoredRowsAndGetDoesNotInitialize(){
        var before=financial();String raw=revisions.snapshot(id);var data=queries.getProjectProgress(id,2026);
        assertThat(data.steps()).isEmpty();assertThat(revisions.snapshot(id)).isEqualTo(raw);assertThat(financial()).isEqualTo(before);
        service.createProjectProgressSteps(id,token());String initialized=revisions.snapshot(id);long audits=db.count("tb_sy_chg_hist");
        var input=new Dates(2026,null,queries.getProjectProgress(id,2026).steps().stream().map(s->new EndDate(s.stepSn(),s.endDate())).toList());
        assertThat(queries.preview(id,input).steps()).hasSize(7);assertThat(revisions.snapshot(id)).isEqualTo(initialized);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(financial()).isEqualTo(before);
    }
    @Test void realMapperInitializesExactlyOnceAndRetainsCompletedDates(){
        var before=financial();var saved=service.createProjectProgressSteps(id,token());assertThat(saved.changed()).isTrue();assertThat(saved.progress().steps()).hasSize(7);
        assertThat(saved.progress().steps().stream().map(s->ProjectProgressCalculation.days(s.startDate(),s.endDate()))).containsExactly(10L,30L,40L,70L,30L,10L,10L);
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(28);service.saveProjectStepCompletion(id,2,token());String raw=revisions.snapshot(id);long audits=db.count("tb_sy_chg_hist");
        assertThat(service.createProjectProgressSteps(id,token()).changed()).isFalse();assertThat(service.saveProjectStepCompletion(id,2,token()).changed()).isFalse();assertThat(revisions.snapshot(id)).isEqualTo(raw);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(financial()).isEqualTo(before);
    }
    @Test void memoInsertUpdatePreservesRegistrationAndWritesOnlyActualFieldAudits(){
        var before=financial();var created=service.createProjectWeekMemo(id,memo("기록",0));var m=created.memo();
        assertThat(m.writtenDate()).isEqualTo(LocalDate.of(2026,7,1));assertThat(m.actualProgress()).isZero();assertThat(created.progress().status().actualProgress()).isEqualTo("0.0");
        var updated=service.putProjectWeekMemo(id,m.memoSn(),memo("수정",30));assertThat(updated.memo().registeredAt()).isEqualTo(m.registeredAt());assertThat(updated.memo().registeredBy()).isEqualTo(m.registeredBy());assertThat(updated.memo().writtenDate()).isEqualTo(m.writtenDate());assertThat(updated.memo().modifiedAt()).isNotNull();
        assertThat(updated.progress().status().lastUpdatedDate()).isEqualTo(m.writtenDate());assertThat(updated.progress().status().elapsedDays()).isZero();
        var entries=queries.getProjectProgressHistory(id,new HistoryQuery("TB_PJ_WEEK_MEMO",id+":"+m.memoSn(),1,50));
        assertThat(entries.items()).filteredOn(e->e.changeType().equals("UPD")).extracting(HistoryEntry::itemName).containsExactlyInAnyOrder("THIS_WEEK_CN","ACTL_PRGRS_RT");assertThat(financial()).isEqualTo(before);
    }
    @Test void dateSaveAuditFailureRollsBackWholePlanAndNoopLeavesTimestamps(){
        service.createProjectProgressSteps(id,token());var data=queries.getProjectProgress(id,2026);var ends=new ArrayList<>(data.steps().stream().map(s->new EndDate(s.stepSn(),s.endDate())).toList());
        String before=revisions.snapshot(id);long audits=db.count("tb_sy_chg_hist");
        assertThat(service.saveProjectStepDates(id,new Dates(2026,data.progressRevision(),ends)).changed()).isFalse();assertThat(revisions.snapshot(id)).isEqualTo(before);
        ends.set(0,new EndDate(1,LocalDate.of(2026,1,11)));
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_progress_dates CHECK (chg_item_nm <> 'PLAN_BGN_DT') NOT VALID");
        assertThatThrownBy(()->service.saveProjectStepDates(id,new Dates(2026,data.progressRevision(),ends))).isInstanceOf(RuntimeException.class);
        assertThat(revisions.snapshot(id)).isEqualTo(before);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audits);
    }
    @Test void sameRevisionConcurrentMemoCreatesCommitOne()throws Exception{
        var input=memo("동시 등록",0);var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<String> action=()->{start.await();try{service.createProjectWeekMemo(id,input);return "saved";}catch(ApiException e){return e.code();}};
            var one=executor.submit(action);var two=executor.submit(action);start.countDown();assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","STALE_WRITE");
            assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_week_memo WHERE proj_id=?",Integer.class,id)).isEqualTo(1);
        } finally {executor.shutdownNow();}
    }
    @Test void sameRevisionConcurrentInitializationCreatesOneSet()throws Exception{
        var input=token();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<String> action=()->{start.await();try{service.createProjectProgressSteps(id,input);return "saved";}catch(ApiException e){return e.code();}};
            var one=executor.submit(action);var two=executor.submit(action);start.countDown();assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","STALE_WRITE");
            assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_step WHERE proj_id=?",Integer.class,id)).isEqualTo(7);
        } finally {executor.shutdownNow();}
    }
}
