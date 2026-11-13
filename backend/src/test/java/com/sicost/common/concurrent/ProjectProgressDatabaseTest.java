package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.ProjectBudgetMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.validation.ProjectProgressPolicy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.annotations.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class ProjectProgressDatabaseTest {
    public interface Children {
        @Insert("INSERT INTO tb_pj_step(proj_id,step_sn,proj_step_cd,step_wght_rt,plan_bgn_dt,plan_end_dt,reg_user_id,reg_dttm) VALUES(#{projectId},#{sn},#{code},#{weight},#{start},#{end},#{actor},#{now})")
        int step(Map<String,Object> row);
        @Insert("INSERT INTO tb_pj_week_memo(proj_id,memo_sn,wrt_dt,this_week_cn,actl_prgrs_rt,reg_user_id,reg_dttm) VALUES(#{projectId},#{sn},#{start},#{text},#{actual},#{actor},#{now})")
        int memo(Map<String,Object> row);
        @Insert("INSERT INTO tb_pj_issue(proj_id,issue_ttl,impr_cd,ddln_dt,issue_stat_cd,actn_cn,reg_user_id,reg_dttm) VALUES(#{projectId},#{text},#{importance},#{end},#{status},#{action},#{actor},#{now})")
        @Options(useGeneratedKeys=true, keyProperty="issueId", keyColumn="issue_id")
        int issue(Map<String,Object> row);
        @Update("UPDATE tb_pj_step SET cmpl_dt=#{complete},mod_user_id=#{actor},mod_dttm=#{now} WHERE proj_id=#{projectId} AND step_sn=#{sn}")
        int complete(Map<String,Object> row);
        @Update("UPDATE tb_pj_week_memo SET this_week_cn=#{text},actl_prgrs_rt=#{actual},mod_user_id=#{actor},mod_dttm=#{now} WHERE proj_id=#{projectId} AND memo_sn=#{sn}")
        int editMemo(Map<String,Object> row);
        @Update("UPDATE tb_pj_issue SET issue_stat_cd=#{status},actn_cn=#{action},mod_user_id=#{actor},mod_dttm=#{now} WHERE issue_id=#{issueId} AND proj_id=#{projectId}")
        int editIssue(Map<String,Object> row);
    }
    PostgreSqlDatabase db;
    SqlSessionTemplate session;
    ProjectProgressAggregateRevision aggregate;
    WriteCoordinator writes;
    ChangeHistoryWriter history;
    CurrentUserProvider users;
    Children children;
    final Clock clock = Clock.fixed(Instant.parse("2026-06-30T15:00:00.123456Z"), ZoneId.of("Asia/Seoul"));
    final RevisionService revisions = new RevisionService(JsonMapper.builder().findAndAddModules().build());
    final List<Object> events = new CopyOnWriteArrayList<>();
    String id;

    @BeforeEach void installDisposableWritableSchema() throws Exception {
        db = new PostgreSqlDatabase(); db.install();
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var config = new org.apache.ibatis.session.Configuration();
        config.setArgNameBasedConstructorAutoMapping(true); config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addMapper(AggregateLockMapper.class); config.addMapper(Children.class);
        factory.setConfiguration(config); factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        users = new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));
        assertThat(users.currentUser().roleIds()).contains("ADMIN");
        history = new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class), users, clock, JsonMapper.builder().findAndAddModules().build());
        aggregate = new ProjectProgressAggregateRevision(session.getMapper(ProjectProgressRevisionMapper.class), revisions);
        writes = new WriteCoordinator(new DataSourceTransactionManager(db.source), session.getMapper(AggregateLockMapper.class), revisions, new CommitInvalidation(events::add));
        children = session.getMapper(Children.class);
        id = "T" + UUID.randomUUID().toString().replace("-", "").substring(0,19);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES(?,?,'TK','ING',DATE '2026-01-01',DATE '2026-12-31',100,0,0,?)", id, "진행 검증", users.userId());
    }
    @AfterEach void removeOnlyOwnedSchema() {
        if (db != null) {
            String schema = db.sql.queryForObject("SELECT current_schema()", String.class);
            if (schema == null || !schema.matches("sicost_test_[a-f0-9]{32}")) throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
    Map<String,Object> row(int sn) {
        var row = new HashMap<String,Object>();
        row.put("projectId", id); row.put("sn", sn); row.put("actor", users.userId());
        row.put("now", LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
        row.put("code", "TK_KICK"); row.put("weight", new java.math.BigDecimal("50.00"));
        row.put("start", LocalDate.of(2026,1,1)); row.put("end", LocalDate.of(2026,12,31));
        row.put("text", "내용"); row.put("actual", 0); row.put("importance", "HIGH"); row.put("status", "OPEN");
        return row;
    }
    WriteCoordinator.WriteResult write(String expected, Runnable action) {
        return writes.write(List.of("PROJECT:" + id), expected, () -> aggregate.snapshot(id),
            () -> ProjectProgressPolicy.requireEditable(2026, "ING", LocalDate.of(2026,12,31), clock), () -> true, action);
    }
    void audit(String table, String key, String type, String field, String before, String after) {
        history.append(new ChangeHistoryWriter.Change(table, key, type, field, before, after, null));
    }
    Map<String,List<String>> sources() {
        var result = new LinkedHashMap<String,List<String>>();
        for (String table : List.of("tb_pj_proj", "tb_pj_proj_chg", "tb_co_mon_cost", "tb_hr_mon_pay", "tb_pj_asgn", "tb_pj_asgn_mon",
            "tb_pj_asgn_hist", "tb_pj_tk_dstrb", "tb_pj_drct_exps", "tb_cl_close", "tb_bl_bill", "tb_bl_rcpt")) {
            result.put(table, db.sql.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        }
        return result;
    }
    @Test void installedAdministratorCanCreateAllThreeChildrenWithoutTouchingFinancialSources() {
        var before = sources(); var memo = row(1); var issue = row(1);
        var saved = write(aggregate.revision(id), () -> {
            children.step(row(1)); audit("TB_PJ_STEP", id + ":1", "NEW", "PLAN_END_DT", null, "2026-12-31");
            children.memo(memo); audit("TB_PJ_WEEK_MEMO", id + ":1", "NEW", "ACTL_PRGRS_RT", null, "0");
            children.issue(issue); audit("TB_PJ_ISSUE", issue.get("issueId").toString(), "NEW", "ISSUE_TTL", null, "내용");
        });
        assertThat(saved.changed()).isTrue(); assertThat(saved.revision()).isEqualTo(aggregate.revision(id));
        assertThat(db.count("tb_pj_step")).isEqualTo(1); assertThat(db.count("tb_pj_week_memo")).isEqualTo(1); assertThat(db.count("tb_pj_issue")).isEqualTo(1);
        assertThat(db.sql.queryForList("SELECT DISTINCT reg_user_id FROM tb_sy_chg_hist", String.class)).containsExactly(users.userId());
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(3); assertThat(events).hasSize(1); assertThat(sources()).isEqualTo(before);
    }
    @Test void updatesPreserveOriginalRegistrationAndMemoDateAndIssueAssociation() {
        var issue = row(1);
        write(aggregate.revision(id), () -> { children.step(row(1)); children.memo(row(1)); children.issue(issue); });
        var original = db.sql.queryForMap("SELECT reg_user_id,reg_dttm,wrt_dt FROM tb_pj_week_memo WHERE proj_id=?", id);
        var update = row(1); update.put("now", LocalDateTime.of(2026,7,2,0,0,0,654321000));
        update.put("text", "수정"); update.put("actual", 1); update.put("complete", LocalDate.of(2026,7,1));
        issue.put("status", "ACT"); issue.put("action", "조치"); issue.put("now", update.get("now"));
        var before = sources();
        write(aggregate.revision(id), () -> {
            children.complete(update); audit("TB_PJ_STEP", id + ":1", "UPD", "CMPL_DT", null, "2026-07-01");
            children.editMemo(update); audit("TB_PJ_WEEK_MEMO", id + ":1", "UPD", "THIS_WEEK_CN", "내용", "수정");
            children.editIssue(issue); audit("TB_PJ_ISSUE", issue.get("issueId").toString(), "UPD", "ISSUE_STAT_CD", "OPEN", "ACT");
        });
        assertThat(db.sql.queryForMap("SELECT reg_user_id,reg_dttm,wrt_dt FROM tb_pj_week_memo WHERE proj_id=?", id)).isEqualTo(original);
        assertThat(db.sql.queryForObject("SELECT proj_id FROM tb_pj_issue WHERE issue_id=?", String.class, issue.get("issueId"))).isEqualTo(id);
        assertThat(sources()).isEqualTo(before);
    }
    @Test void rawRevisionUsesNumericPrimaryKeyOrderEveryChildAndTimestampPrecision() throws Exception {
        write(aggregate.revision(id), () -> { children.step(row(9)); children.step(row(2)); children.memo(row(20)); children.memo(row(3)); children.issue(row(1)); });
        var json = JsonMapper.builder().findAndAddModules().build();
        var raw = json.readTree(aggregate.snapshot(id));
        assertThat(raw.path("steps").get(0).path("step_sn").intValue()).isEqualTo(2);
        assertThat(raw.path("memos").get(0).path("memo_sn").intValue()).isEqualTo(3);
        String token = aggregate.revision(id);
        db.sql.update("UPDATE tb_pj_week_memo SET reg_dttm=reg_dttm + INTERVAL '1 microsecond' WHERE proj_id=? AND memo_sn=20", id);
        assertThat(aggregate.revision(id)).isNotEqualTo(token); token = aggregate.revision(id);
        db.sql.update("UPDATE tb_pj_issue SET actn_cn='다른 값' WHERE proj_id=?", id);
        assertThat(aggregate.revision(id)).isNotEqualTo(token); token = aggregate.revision(id);
        db.sql.update("UPDATE tb_pj_proj SET proj_nm='표시명',ctrt_amt=200 WHERE proj_id=?", id);
        assertThat(aggregate.revision(id)).isEqualTo(token);
        db.sql.update("UPDATE tb_pj_proj SET proj_stat_cd='PRE' WHERE proj_id=?", id);
        assertThat(aggregate.revision(id)).isNotEqualTo(token);
    }
    @Test void ordinaryProjectRevisionAddsWholeStepSetButPreservesPreviousChildren() throws Exception {
        var mapper = session.getMapper(ProjectBudgetMapper.class);
        String before = mapper.snapshot(id);
        write(aggregate.revision(id), () -> children.step(row(1)));
        String after = mapper.snapshot(id);
        assertThat(after).isNotEqualTo(before);
        var json = JsonMapper.builder().findAndAddModules().build();
        var old = json.readTree(before); var current = json.readTree(after);
        for (String key : List.of("project", "parts", "plans", "expenses", "distribution", "pm")) assertThat(current.get(key)).isEqualTo(old.get(key));
        assertThat(current.path("steps").size()).isEqualTo(1);
    }
    @Test void mandatoryAuditFailureRollsBackEveryChildAndEvents() {
        var before = aggregate.snapshot(id); var sourceBefore = sources();
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_progress_audit CHECK (trgt_tbl_nm <> 'TB_PJ_ISSUE')");
        assertThatThrownBy(() -> write(aggregate.revision(id), () -> {
            children.step(row(1)); audit("TB_PJ_STEP", id + ":1", "NEW", "PLAN_END_DT", null, "2026-12-31");
            children.memo(row(1)); children.issue(row(1));
            audit("TB_PJ_ISSUE", "1", "NEW", "ISSUE_TTL", null, "실패");
        })).isInstanceOf(RuntimeException.class);
        assertThat(aggregate.snapshot(id)).isEqualTo(before); assertThat(sources()).isEqualTo(sourceBefore);
        assertThat(db.count("tb_sy_chg_hist")).isZero(); assertThat(events).isEmpty();
    }
    @Test void latestNoOpIsUnchangedButStaleNoOpConflictsBeforeDomainValidation() {
        String expected = aggregate.revision(id); var before = aggregate.snapshot(id);
        var result = writes.write(List.of("PROJECT:" + id), expected, () -> aggregate.snapshot(id), () -> {}, () -> false,
            () -> { throw new AssertionError("no-op DML"); });
        assertThat(result.changed()).isFalse(); assertThat(result.message()).isEqualTo("바뀐 내용이 없습니다.");
        assertThat(aggregate.snapshot(id)).isEqualTo(before); assertThat(events).isEmpty();
        write(expected, () -> children.memo(row(1)));
        assertThatThrownBy(() -> writes.write(List.of("PROJECT:" + id), expected, () -> aggregate.snapshot(id),
            () -> { throw new AssertionError("stale must precede validation"); }, () -> false, () -> {}))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(db.count("tb_pj_week_memo")).isEqualTo(1);
    }
    @Test void yearAndStatusConflictLeaveCompleteStoredRowsUnchanged() {
        String before = aggregate.snapshot(id);
        for (Runnable validation : List.<Runnable>of(
            () -> ProjectProgressPolicy.requireEditable(2025, "ING", LocalDate.of(2026,12,31), clock),
            () -> ProjectProgressPolicy.requireEditable(2026, "LOSE", LocalDate.of(2026,12,31), clock),
            () -> ProjectProgressPolicy.requireEditable(2026, "ING", LocalDate.of(2026,6,30), clock))) {
            assertThatThrownBy(() -> writes.write(List.of("PROJECT:" + id), aggregate.revision(id), () -> aggregate.snapshot(id),
                validation, () -> true, () -> children.memo(row(1))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(409));
            assertThat(aggregate.snapshot(id)).isEqualTo(before);
        }
        assertThat(db.count("tb_sy_chg_hist")).isZero(); assertThat(events).isEmpty();
    }
    @Test void concurrentNewMemosWithSameRevisionCommitOnce() throws Exception {
        String token = aggregate.revision(id); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> write(token, () -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            children.memo(row(1)); audit("TB_PJ_WEEK_MEMO", id + ":1", "NEW", "THIS_WEEK_CN", null, "내용");
        }));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = CompletableFuture.supplyAsync(() -> {
                try { write(token, () -> children.memo(row(2))); return "unexpected"; }
                catch (ApiException e) { return e.code(); }
            });
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).changed()).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo("STALE_WRITE");
            assertThat(db.count("tb_pj_week_memo")).isEqualTo(1); assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1); assertThat(events).hasSize(1);
        } finally { release.countDown(); }
    }
}
