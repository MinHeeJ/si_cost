package com.sicost.salespartsettlementsops;

import com.sicost.common.CostManagementApplication;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.salespartsettlements.SalesPartSettlementService;
import java.time.YearMonth;
import java.util.UUID;
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
class SettlementCloseDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        database=new PostgreSqlDatabase();
        registry.add("spring.datasource.url",database.source::getUrl);
        registry.add("spring.datasource.username",()->System.getenv("SICOST_TEST_DB_USER"));
        registry.add("spring.datasource.password",()->System.getenv("SICOST_TEST_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate sql;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SettlementClosingService close;
    @Autowired SalesPartSettlementService reads;
    @Autowired com.sicost.common.concurrent.DeveloperAggregateRevision sourceRevisions;
    @Test void autoSnapshotAndAllAuditsJoinCloseRollbackAndNeverOverwriteExistingConfirmation() {
        String part="T"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String month=YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString(),dbMonth=month.replace("-","");
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,part);
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES (?,'202601','admin',40,10,'admin')",part);
        sql.update("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,setl_stat_cd,reg_user_id) VALUES (?,?,5,'WORK','admin')",part,dbMonth);
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            assertThat(close.confirmForClose(part,month,"admin").status()).isEqualTo("AUTO");
            assertThat(reads.previewSettlement(part,month).summary().partProfitAmount()).isEqualTo("-5");
            status.setRollbackOnly();
        });
        assertThat(sql.queryForObject("SELECT setl_stat_cd FROM tb_co_part_setl WHERE part_id=?",String.class,part)).isEqualTo("WORK");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=?",Integer.class,part+":"+dbMonth)).isZero();
        tx.executeWithoutResult(status->close.confirmForClose(part,month,"admin"));
        String original=sql.queryForObject("SELECT row_to_json(s)::text FROM tb_co_part_setl s WHERE part_id=?",String.class,part);
        int audits=sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=?",Integer.class,part+":"+dbMonth);
        assertThat(audits).isGreaterThan(0);
        tx.executeWithoutResult(status->assertThat(close.confirmForClose(part,month,"different-actor").changed()).isFalse());
        assertThat(sql.queryForObject("SELECT row_to_json(s)::text FROM tb_co_part_setl s WHERE part_id=?",String.class,part)).isEqualTo(original);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=?",Integer.class,part+":"+dbMonth)).isEqualTo(audits);
    }
    @Test void sourceWriteWaitsUntilSettlementTransactionCommits() throws Exception {
        String part="T"+UUID.randomUUID().toString().replace("-","").substring(0,12),dev="D"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String month=YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString();
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,part);
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES (?,'202601','admin',40,10,'admin')",part);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FL','BE','DRCT',?,'WORK','admin')",dev,dev,dev,part);
        var tx=new TransactionTemplate(transactions);
        var attempted=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            tx.executeWithoutResult(status->{
                close.confirmForClose(part,month,"admin");
                future.set(pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(write->{
                    attempted.countDown();sourceRevisions.snapshot(dev,true);
                    sql.update("UPDATE tb_hr_dev SET dev_nm='수정 이름' WHERE dev_id=?",dev);
                })));
                try {
                    assertThat(attempted.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(()->future.get().get(200,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            });
            future.get().get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(sql.queryForObject("SELECT dev_nm FROM tb_hr_dev WHERE dev_id=?",String.class,dev)).isEqualTo("수정 이름");
        } finally {pool.shutdownNow();}
    }
}
