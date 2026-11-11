package com.sicost.common.installation;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class ConcurrentWriteDatabaseTest {
    final RevisionService revisions = new RevisionService(JsonMapper.builder().findAndAddModules().build());
    private String createGroup(PostgreSqlDatabase db) {
        String key = "T" + UUID.randomUUID().toString().replace("-", "").substring(0,20);
        db.sql.update("INSERT INTO tb_sy_code_grp (code_grp_id,code_grp_nm,sort_ord,reg_user_id) VALUES (?, ?, 1, 'admin')",key,"독립 시험");
        return key;
    }
    private Supplier<?> snapshot(PostgreSqlDatabase db, String key) {
        return () -> db.sql.queryForList("SELECT code_grp_id,code_grp_nm,sort_ord,sys_code_yn,val_code_yn,use_yn,reg_user_id,reg_dttm,mod_user_id,mod_dttm FROM tb_sy_code_grp WHERE code_grp_id=? ORDER BY code_grp_id FOR UPDATE",key);
    }
    private WriteCoordinator coordinator(PostgreSqlDatabase db, List<Object> events) {
        AggregateLockMapper locks = key -> db.sql.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))::text", String.class,key);
        return new WriteCoordinator(new DataSourceTransactionManager(db.source),locks,revisions,new CommitInvalidation(events::add));
    }
    private void change(PostgreSqlDatabase db,String key,String name) {
        db.sql.update("UPDATE tb_sy_code_grp SET code_grp_nm=?,mod_user_id='admin',mod_dttm=CURRENT_TIMESTAMP WHERE code_grp_id=?",name,key);
        db.sql.update("INSERT INTO tb_sy_chg_hist (trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id) VALUES ('TB_SY_CODE_GRP',?,'UPD','CODE_GRP_NM','독립 시험',?,'admin')",key,name);
    }
    @Test void sameRevisionTwoClientsCommitOneAndRejectTheOtherWithoutHistory() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install(); String key = createGroup(db);
        List<Object> events = new CopyOnWriteArrayList<>(); var coordinator = coordinator(db,events);
        String expected = revisions.revision(snapshot(db,key).get());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> coordinator.write(List.of(key),expected,snapshot(db,key), () -> {
            entered.countDown(); try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        },() -> true,() -> change(db,key,"선행")));
        assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
        var second = CompletableFuture.supplyAsync(() -> {
            try { coordinator.write(List.of(key),expected,snapshot(db,key),() -> {},() -> true,() -> change(db,key,"후행")); return "unexpected"; }
            catch (ApiException e) { assertThat(e.status()).isEqualTo(409); return e.code(); }
        });
        release.countDown();
        assertThat(first.get(15,TimeUnit.SECONDS).changed()).isTrue();
        assertThat(second.get(15,TimeUnit.SECONDS)).isEqualTo("STALE_WRITE");
        assertThat(db.sql.queryForObject("SELECT code_grp_nm FROM tb_sy_code_grp WHERE code_grp_id=?",String.class,key)).isEqualTo("선행");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=?",Long.class,key)).isEqualTo(1);
        assertThat(events).hasSize(1);
    }
    @Test void noOpDoesNotRunDmlOrChangeTimestampsOrAppendHistory() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install(); String key = createGroup(db);
        List<Object> events = new CopyOnWriteArrayList<>(); var before = snapshot(db,key).get();
        var result = coordinator(db,events).write(List.of(key),revisions.revision(before),snapshot(db,key),() -> {},() -> false,
                () -> { throw new AssertionError("No-op must not write"); });
        assertThat(result.changed()).isFalse(); assertThat(result.message()).isEqualTo("바뀐 내용이 없습니다.");
        assertThat(snapshot(db,key).get()).isEqualTo(before);
        assertThat(db.count("tb_sy_chg_hist")).isZero(); assertThat(events).isEmpty();
    }
    @Test void midwayFailureRollsBackDataAndHistoryAndInvalidation() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install(); String key = createGroup(db);
        List<Object> events = new CopyOnWriteArrayList<>(); var before = snapshot(db,key).get();
        assertThatThrownBy(() -> coordinator(db,events).write(List.of(key),revisions.revision(before),snapshot(db,key),() -> {},() -> true,
                () -> { change(db,key,"롤백"); throw new IllegalStateException("deliberate failure"); })).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot(db,key).get()).isEqualTo(before);
        assertThat(db.count("tb_sy_chg_hist")).isZero(); assertThat(events).isEmpty();
    }
}
