package com.sicost.automationrunsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.developers.DeveloperQueryService;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DailyAutomationDatabaseTest {
    PostgreSqlDatabase db;
    DailyAutomationMapper mapper;
    ChangeHistoryMapper audits;
    DailyAutomationRepository repository;
    final Clock clock=Clock.fixed(Instant.parse("2026-07-14T17:00:00Z"),ZoneId.of("Asia/Seoul"));
    final LocalDate today=LocalDate.of(2026,7,15);
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/automationrunsops/DailyAutomationMapper.xml"),new ClassPathResource("mappers/common/ChangeHistoryMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(DailyAutomationMapper.class);audits=session.getMapper(ChangeHistoryMapper.class);
        repository=repository(writer(audits));
    }
    ChangeHistoryWriter writer(ChangeHistoryMapper mapper) { return new ChangeHistoryWriter(mapper,mock(CurrentUserProvider.class),clock,JsonMapper.builder().findAndAddModules().build()); }
    DailyAutomationRepository repository(ChangeHistoryWriter history) {
        return new DailyAutomationRepository(mapper,new MonthCloseBoundary(db.source),mock(SettlementSourceLocks.class),history,clock,new DataSourceTransactionManager(db.source));
    }
    @AfterEach void cleanup() {
        if(db!=null) {
            String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
            if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    @Test void twoIndependentInstancesKeepSessionLockAcrossTransactionsAndReleaseIt() {
        var first=new DailyAutomationSessionLocks(db.source);var second=new DailyAutomationSessionLocks(db.source);
        var key=new Key("DAILY_OPERATIONS",today);
        try(var lease=first.tryAcquire(key)) {
            assertThat(lease).isNotNull();repository.prepare(key);repository.update(key,"RUNNING",1,null);
            assertThat(second.tryAcquire(key)).isNull();
            try(var other=second.tryAcquire(new Key("RECEIVABLE_DUNNING",today))) { assertThat(other).isNotNull(); }
        }
        try(var resumed=second.tryAcquire(key)) { assertThat(resumed).isNotNull(); }
    }
    @Test void realMapperMaterializesOriginalStartAndSameDayCompletedResultWithoutNewAudit() {
        var key=new Key("DAILY_OPERATIONS",today);var initial=repository.prepare(key);
        repository.update(key,"FAILED",3,"단계 4 / ASGN-1 / 처리 실패");repository.update(key,"RUNNING",3,null);repository.update(key,"SUCCEEDED",7,null);
        long count=db.count("tb_sy_chg_hist");var completed=repository.prepare(key);
        assertThat(completed.startedAt()).isEqualTo(initial.startedAt());assertThat(completed.lastStep()).isEqualTo(7);
        assertThat(completed.view().endedAt()).isNotNull();assertThat(db.count("tb_sy_chg_hist")).isEqualTo(count);
    }
    String project(String state,LocalDate end) {
        String id="P-"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO',?,'2026-01-01',?,1,0,0,'SYSTEM')",id,id,state,end);return id;
    }
    @Test void projectEndAndBusinessHistoryAndSystemAuditAreAtomicAndResumeIsNoOp() {
        String id=project("ING",today.minusDays(1));
        var actions=new DailyAutomationActions(mapper,repository,mock(DeveloperQueryService.class),writer(audits),clock);
        actions.process(3,id,today);long count=db.count("tb_sy_chg_hist");actions.process(3,id,today);
        assertThat(db.sql.queryForObject("SELECT proj_stat_cd FROM tb_pj_proj WHERE proj_id=?",String.class,id)).isEqualTo("END");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Long.class,id)).isEqualTo(1);
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(count);
    }
    @Test void mandatoryAuditFailureRollsBackOriginalProjectAndBusinessHistory() {
        String id=project("ING",today.minusDays(1));var failed=mock(ChangeHistoryMapper.class);when(failed.insert(any())).thenReturn(0);
        var actions=new DailyAutomationActions(mapper,repository(writer(failed)),mock(DeveloperQueryService.class),writer(failed),clock);
        assertThatThrownBy(()->actions.process(3,id,today)).isInstanceOf(RuntimeException.class);
        assertThat(db.sql.queryForObject("SELECT proj_stat_cd FROM tb_pj_proj WHERE proj_id=?",String.class,id)).isEqualTo("ING");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Long.class,id)).isZero();
    }
    @Test void endDateTodayAndPreAndLostProjectsNeverEndAutomatically() {
        String current=project("ING",today),pre=project("PRE",today.minusDays(1)),lost=project("LOSE",today.minusDays(1));
        var actions=new DailyAutomationActions(mapper,repository,mock(DeveloperQueryService.class),writer(audits),clock);
        for(String id:java.util.List.of(current,pre,lost))actions.process(3,id,today);
        assertThat(db.count("tb_pj_proj_chg")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();
    }
}
