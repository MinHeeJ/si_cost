package com.sicost.salespartsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PartStandardDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    PartStandardService service;
    PartStandardMapper mapper;
    ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AggregateLockMapper.class);factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(PartStandardMapper.class);
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(PartStandardMapper.class,()->mapper);
        context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,PartStandardRepository.class,PartStandardService.class);
        context.refresh();service=context.getBean(PartStandardService.class);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT 'part-leader','시험 파트장','part@example.test',pwd_hash,0,'USE',0,'admin' FROM tb_sy_user WHERE user_id='admin'");
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('part-leader','SALES_LDR','admin')");
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('PART-test','시험 영업파트','N','Y','admin')");
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES ('PART-test','202601','202611','part-leader',40,1500000,'admin'),('PART-test','202612',NULL,'part-leader',50,1500000,'admin')");
    }
    @AfterEach void close(){if(context!=null)context.close();}
    ObjectNode body(String month,String rate) {
        return json.createObjectNode().put("effectiveMonth",month).put("leaderUserId","part-leader").put("profitRate",rate).put("salesExpenseAmount","1500000")
            .put("expectedRevision",service.revision(mapper.header("PART-test",false),mapper.timeline("PART-test",false)));
    }
    List<Map<String,Object>> timeline(){return sql.queryForList("SELECT * FROM tb_bs_part_std WHERE part_id='PART-test' ORDER BY aply_bgn_ym");}
    List<Map<String,Object>> history(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_BS_PART_STD' ORDER BY hist_id");}
    void settlement(String month,String status) {sql.update("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,part_prft_amt,setl_stat_cd,reg_user_id) VALUES ('PART-test',?,123,456,?,'admin')",month,status);}
    @Test void timelineInsertPreservesFutureAndSettlementAndCloseRows() {
        settlement("202609","WORK");sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202612','WORK','admin')");
        var future=timeline().get(1);var settlements=sql.queryForList("SELECT * FROM tb_co_part_setl");var closes=sql.queryForList("SELECT * FROM tb_cl_close");
        var result=service.save("PART-test",body("2026-08","45"));assertThat(result.changed()).isTrue();
        var rows=timeline();assertThat(rows).hasSize(3);assertThat(rows.get(0).get("aply_end_ym")).isEqualTo("202607");assertThat(rows.get(1).get("aply_end_ym")).isEqualTo("202611");assertThat(rows.get(2)).isEqualTo(future);
        assertThat(history()).hasSize(2);assertThat(sql.queryForList("SELECT * FROM tb_co_part_setl")).isEqualTo(settlements);assertThat(sql.queryForList("SELECT * FROM tb_cl_close")).isEqualTo(closes);
    }
    @Test void savedDetailCountsBothInProgressAndPreparingProjectsLikeTheList() {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,cust_clnt_id,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('P-integration','집계 검증',NULL,'TM','PRE','2026-01-01','2026-12-31',0,0,0,'admin')");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES ('P-integration','202601','PART-test','admin')");
        assertThat(service.save("PART-test",body("2026-08","45")).part().part().projectCount()).isEqualTo(1);
    }
    @Test void failureAfterPreviousEndUpdateRollsBackEndAndHistory() {
        sql.execute("ALTER TABLE tb_bs_part_std ADD CONSTRAINT reject_august CHECK(aply_bgn_ym <> '202608')");var before=timeline();
        assertThatThrownBy(()->service.save("PART-test",body("2026-08","45"))).isInstanceOf(RuntimeException.class);
        assertThat(timeline()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void auditFailureRollsBackAllBusinessWrites() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_part_history CHECK(trgt_tbl_nm <> 'TB_BS_PART_STD')");var before=timeline();
        assertThatThrownBy(()->service.save("PART-test",body("2026-08","45"))).isInstanceOf(RuntimeException.class);assertThat(timeline()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void sameTripleIsNoOpButExistingMonthIsConflict() {
        var before=timeline();assertThat(service.save("PART-test",body("2026-08","40")).changed()).isFalse();
        assertThatThrownBy(()->service.save("PART-test",body("2026-01","40"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STANDARD_MONTH_EXISTS"));
        assertThat(timeline()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void bothAutoAndConfirmedProtectUnclosedMonthsBeyondNextStandard() {
        settlement("202701","AUTO");settlement("202702","CNFM");var before=timeline();
        assertThatThrownBy(()->service.save("PART-test",body("2026-08","45"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("PART_SETTLEMENT_CONFIRMED"));
        assertThat(timeline()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void onlyCloseRowsControlMinimumAndDefaultsAreMonthBounded() {
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202608','CLOSE','admin'),('202612','WORK','admin')");
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('PART_DSTRB_RT','202610','미래율',90,'%','admin')");
        assertThat(service.defaults(null).minMonth()).isEqualTo("2026-09");assertThat(service.defaults("2026-09").profitRate()).isEqualTo("40");
        assertThatThrownBy(()->service.save("PART-test",body("2026-08","45"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));
        assertThat(service.save("PART-test",body("2026-09","45")).changed()).isTrue();
    }
    @Test void optionsExcludeStoppedIncludeFutureAndHqWithoutWriting() {
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('PART-future','미래파트','N','Y','admin'),('PART-stopped','중지파트','N','N','admin')");
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES ('PART-future','202901','part-leader',40,1500000,'admin')");
        var items=service.options().items();assertThat(items).anyMatch(o->o.hqYn().equals("Y"));assertThat(items).anyMatch(o->o.id().equals("PART-future") && o.firstEffectiveMonth().equals("2029-01"));assertThat(items).noneMatch(o->o.id().equals("PART-stopped"));assertThat(history()).isEmpty();
    }
    @Test void concurrentRevisionAllowsOnlyOneInsert() throws Exception {
        ObjectNode input=body("2026-08","45");CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->write(start,input));var b=CompletableFuture.supplyAsync(()->write(start,input));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(history()).hasSize(2);
    }
    String write(CountDownLatch start,ObjectNode input){try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.save("PART-test",input);return "OK";}catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
