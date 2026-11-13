package com.sicost.issues;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.issues.ProjectIssueDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectIssueDatabaseTest {
    PostgreSqlDatabase db;
    ProjectIssueRepository repository;
    ProjectIssueService service;
    ProjectProgressAggregateRevision aggregate;
    TransactionTemplate tx;
    String id;
    CurrentUserProvider users;
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00.123456Z"),ZoneId.of("Asia/Seoul"));
    @BeforeEach void installDisposableWritableSchema() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.addMapper(AggregateLockMapper.class);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));
        var revisions=new RevisionService(JsonMapper.builder().findAndAddModules().build());
        aggregate=new ProjectProgressAggregateRevision(session.getMapper(ProjectProgressRevisionMapper.class),revisions);
        var beans=new StaticListableBeanFactory();
        beans.addBean("progress",(ProjectProgressView)(project,year)->Map.of("projectId",project,"year",year,"progressRevision",aggregate.revision(project)));
        repository=new ProjectIssueRepository(session.getMapper(ProjectIssueMapper.class),beans.getBeanProvider(ProjectProgressView.class));
        var manager=new DataSourceTransactionManager(db.source);tx=new TransactionTemplate(manager);
        var writes=new WriteCoordinator(manager,session.getMapper(AggregateLockMapper.class),revisions,new CommitInvalidation(event->{}));
        var audit=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,JsonMapper.builder().findAndAddModules().build());
        service=new ProjectIssueService(repository,writes,aggregate,users,audit,clock);
        id="I"+UUID.randomUUID().toString().replace("-","").substring(0,19);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES(?,?,'TK','ING',DATE '2026-01-01',DATE '2026-12-31',100,0,0,?)",id,"이슈 검증",users.userId());
    }
    @AfterEach void removeOnlyOwnedSchema() {
        if(db!=null){String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema==null || !schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    Input input(String status) {return new Input(2026,aggregate.revision(id),"제목",LocalDate.of(2026,6,30),"HIGH",status,"가".repeat(500));}
    Map<String,List<String>> sources() {
        var result=new LinkedHashMap<String,List<String>>();
        for(String table:List.of("tb_pj_proj","tb_pj_week_memo","tb_pj_step","tb_pj_proj_chg","tb_co_mon_cost","tb_hr_mon_pay","tb_pj_asgn","tb_pj_asgn_mon","tb_pj_tk_dstrb","tb_bl_bill","tb_cl_close"))result.put(table,db.sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return result;
    }
    @Test void installedActorCreatesDefaultSequenceIssueAndRawAuditWithoutChangingOtherSources() {
        var before=sources();
        Result result=tx.execute(s->service.createProjectIssue(id,input("OPEN")));
        assertThat(result.issue().issueId()).isPositive();assertThat(result.issue().registeredBy().userId()).isEqualTo(users.userId());
        Row row=repository.issues(id).get(0);assertThat(row.regDttm().getNano()).isEqualTo(123456000);assertThat(row.action()).hasSize(500);
        assertThat(db.sql.queryForList("SELECT aft_val FROM tb_sy_chg_hist WHERE chg_item_nm='ACTN_CN'",String.class)).containsExactly("가".repeat(500));
        assertThat(service.getProjectIssues(id,new Filter("OVERDUE",null,1,50)).openCount()).isEqualTo(1);
        assertThat(sources()).isEqualTo(before);
    }
    @Test void closeAndReopenKeepOriginalAuditAndNoOpStaleProtectsAllRows() {
        Result result=tx.execute(s->service.createProjectIssue(id,input("OPEN")));long issueId=result.issue().issueId();
        Row before=repository.issues(id).get(0);
        tx.execute(s->service.putProjectIssue(id,issueId,input("CLOSE")));
        assertThat(service.getProjectIssues(id,new Filter("ALL",null,1,50)).openCount()).isZero();
        tx.execute(s->service.putProjectIssue(id,issueId,input("ACT")));
        Row after=repository.issues(id).get(0);assertThat(after.regDttm()).isEqualTo(before.regDttm());assertThat(after.regUserId()).isEqualTo(before.regUserId());assertThat(after.modUserId()).isEqualTo(users.userId());
        String snapshot=aggregate.snapshot(id);long audits=db.count("tb_sy_chg_hist");
        assertThat(tx.execute(s->service.putProjectIssue(id,issueId,input("ACT"))).changed()).isFalse();assertThat(aggregate.snapshot(id)).isEqualTo(snapshot);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audits);
        var stale=new Input(2026,result.progressRevision(),"제목",before.dueDate(),"HIGH","ACT",before.action());
        assertThatThrownBy(()->tx.execute(s->service.putProjectIssue(id,issueId,stale))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(aggregate.snapshot(id)).isEqualTo(snapshot);
    }
    @Test void failingMandatoryAuditRollsBackIssueAndOtherSources() {
        var before=sources();String raw=aggregate.snapshot(id);
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_issue_audit CHECK(trgt_tbl_nm <> 'TB_PJ_ISSUE')");
        assertThatThrownBy(()->tx.execute(s->service.createProjectIssue(id,input("OPEN")))).isInstanceOf(RuntimeException.class);
        assertThat(aggregate.snapshot(id)).isEqualTo(raw);assertThat(db.count("tb_sy_chg_hist")).isZero();assertThat(sources()).isEqualTo(before);
    }
    @Test void numericIdentifierExhaustionUsesExistingSequenceAndRollsBack() {
        db.sql.execute("ALTER SEQUENCE sq_tb_pj_issue RESTART WITH 10000000000");
        assertThatThrownBy(()->tx.execute(s->service.createProjectIssue(id,input("OPEN")))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ID_EXHAUSTED"));
        assertThat(db.count("tb_pj_issue")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();
    }
}
