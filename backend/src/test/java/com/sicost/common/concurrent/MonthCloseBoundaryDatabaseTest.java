package com.sicost.common.concurrent;

import com.sicost.common.CostManagementApplication;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class MonthCloseBoundaryDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        database=new PostgreSqlDatabase();
        registry.add("spring.datasource.url",database.source::getUrl);
        registry.add("spring.datasource.username",()->System.getenv("SICOST_TEST_DB_USER"));
        registry.add("spring.datasource.password",()->System.getenv("SICOST_TEST_DB_PASSWORD"));
    }
    @Autowired PlatformTransactionManager transactions;
    @Autowired MonthCloseBoundary boundary;
    @Autowired MasterLockMapper mapper;
    @Autowired ChangeHistoryWriter history;
    @Autowired JdbcTemplate sql;
    @Test void firstOrdinarySourceQueryHoldsShareUntilCommitAndExclusiveWaits() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var attempted=new CountDownLatch(1);
        var future=new AtomicReference<Future<?>>();var tx=new TransactionTemplate(transactions);
        try {
            tx.executeWithoutResult(status->{
                mapper.isClosed("999912");
                future.set(pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(close->{attempted.countDown();boundary.exclusive();})));
                awaitBlocked(attempted,future.get());
            });
            future.get().get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
    }
    @Test void exclusiveCommitForcesFollowingWriterToReadCommittedClosedState() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var attempted=new CountDownLatch(1);
        var future=new AtomicReference<Future<Boolean>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                boundary.exclusive();
                sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('999912','CLOSE','admin')");
                future.set(pool.submit(()->new TransactionTemplate(transactions).execute(write->{attempted.countDown();return mapper.isClosed("999912");})));
                awaitBlocked(attempted,future.get());
            });
            assertThat(future.get().get(10,TimeUnit.SECONDS)).isTrue();
        } finally {pool.shutdownNow();sql.update("DELETE FROM tb_cl_close WHERE close_ym='999912'");}
    }
    @Test void sharedTransactionsCanProgressConcurrentlyWithoutPromotion() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                boundary.shared();
                try {assertThat(pool.submit(()->new TransactionTemplate(transactions).execute(other->{boundary.shared();return mapper.isClosed("999912");})).get(5,TimeUnit.SECONDS)).isFalse();}
                catch(Exception error){throw new IllegalStateException(error);}
            });
        } finally {pool.shutdownNow();}
    }
    @Test void requiredAuditReadbackAndDataJoinRollbackWithoutAnyNewTableOrColumn() {
        String id=UUID.randomUUID().toString();String key="999911:1";
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            boundary.exclusive();
            history.appendMonthClose(new ChangeHistoryWriter.Change("TB_CL_CLOSE_HIST",key,"NEW","operationId",null,id,null),"admin",LocalDateTime.of(2026,7,1,0,0));
            assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? AND aft_val=?",Integer.class,key,id)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? AND aft_val=?",Integer.class,key,id)).isZero();
    }
    private static void awaitBlocked(CountDownLatch attempted,Future<?> future) {
        try {
            assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->future.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }
}
