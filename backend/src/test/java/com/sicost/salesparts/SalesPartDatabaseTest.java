package com.sicost.salesparts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
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
import static com.sicost.salesparts.SalesPartDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class SalesPartDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    SalesPartService service;
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
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(SalesPartMapper.class,()->session.getMapper(SalesPartMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));
        context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,SalesPartRepository.class,SalesPartService.class);
        context.refresh();service=context.getBean(SalesPartService.class);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT 'leadercase','시험 파트장','leader@example.invalid',pwd_hash,0,'USE',0,'admin' FROM tb_sy_user WHERE user_id='admin'");
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES('leadercase','SALES_LDR','admin')");
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Create input(String name) {return new Create(name,"2026-06","leadercase",new BigDecimal("100"),new BigDecimal("999999999999999"));}
    List<Map<String,Object>> rows(String table){return sql.queryForList("SELECT * FROM "+table);}
    @Test void seededAdminCreatesAndReadbackMatchesTwoAtomicNewRows() {
        var result=service.create(input("시험 영업파트"));String id=result.part().part().id();
        assertThat(id).matches("PART-[0-9]{3,15}");assertThat(service.detail(id)).isEqualTo(result.part());
        assertThat(sql.queryForMap("SELECT hq_yn,use_yn,reg_user_id FROM tb_bs_part WHERE part_id=?",id)).containsEntry("hq_yn","N").containsEntry("reg_user_id","admin");
        assertThat(sql.queryForMap("SELECT aply_end_ym,prft_dstrb_rt,sales_exps_amt FROM tb_bs_part_std WHERE part_id=?",id)).containsEntry("aply_end_ym",null);
        assertThat(rows("tb_sy_chg_hist")).hasSize(2).allMatch(h->h.get("chg_type_cd").equals("NEW"));
    }
    @Test void firstStandardFailureRollsBackPartAndAllHistory() {
        sql.execute("ALTER TABLE tb_bs_part_std ADD CONSTRAINT reject_test_standard CHECK(part_ldr_user_id <> 'leadercase')");
        var before=rows("tb_bs_part");assertThatThrownBy(()->service.create(input("실패파트"))).isInstanceOf(RuntimeException.class);
        assertThat(rows("tb_bs_part")).isEqualTo(before);assertThat(rows("tb_bs_part_std")).isEmpty();assertThat(rows("tb_sy_chg_hist")).isEmpty();
    }
    @Test void secondNewFailureRollsBackBothRowsAndFirstNew() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_test_history CHECK(trgt_tbl_nm <> 'TB_BS_PART_STD')");
        var before=rows("tb_bs_part");assertThatThrownBy(()->service.create(input("이력실패파트"))).isInstanceOf(RuntimeException.class);
        assertThat(rows("tb_bs_part")).isEqualTo(before);assertThat(rows("tb_bs_part_std")).isEmpty();assertThat(rows("tb_sy_chg_hist")).isEmpty();
    }
    @Test void duplicateIncludesStoppedAndNoNewRows() {
        var created=service.create(input("중복파트")).part();service.patch(created.part().id(),new Patch(null,"N",created.revision()));
        var parts=rows("tb_bs_part");var history=rows("tb_sy_chg_hist");
        assertThatThrownBy(()->service.create(input("중복파트"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("DUPLICATE_PART_NAME"));
        assertThat(rows("tb_bs_part")).isEqualTo(parts);assertThat(rows("tb_sy_chg_hist")).isEqualTo(history);
    }
    @Test void stopAndNoOpPreserveStandardsSettlementCloseAndTimestamps() {
        var created=service.create(input("중지파트")).part();String id=created.part().id();
        var standards=rows("tb_bs_part_std");var settlements=rows("tb_co_part_setl");var closes=rows("tb_cl_close");
        var stopped=service.patch(id,new Patch(null,"N",created.revision()));var parts=rows("tb_bs_part");var history=rows("tb_sy_chg_hist");
        assertThat(service.patch(id,new Patch(null,"N",stopped.part().revision())).changed()).isFalse();
        assertThat(rows("tb_bs_part")).isEqualTo(parts);assertThat(rows("tb_sy_chg_hist")).isEqualTo(history);
        assertThat(rows("tb_bs_part_std")).isEqualTo(standards);assertThat(rows("tb_co_part_setl")).isEqualTo(settlements);assertThat(rows("tb_cl_close")).isEqualTo(closes);
    }
    @Test void onlyCloseNotWorkDeterminesMinimumAndRejectsPast() {
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES('202605','CLOSE','admin'),('202801','WORK','admin')");
        var detail=service.create(input("최소월파트")).part();assertThat(detail.minMonth()).isEqualTo("2026-06");
        Create invalid=new Create("이전월파트","2026-05","leadercase",BigDecimal.ZERO,BigDecimal.ZERO);
        assertThatThrownBy(()->service.create(invalid)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));
    }
    @Test void filterTotalAndLiteralSearchShareSameSnapshotAndHqExcluded() {
        service.create(input("검색_파트"));var second=service.create(input("다른파트")).part();service.patch(second.part().id(),new Patch(null,"N",second.revision()));
        assertThat(service.list(new Query("_",null,1,20,"name","ASC")).total()).isEqualTo(1);
        assertThat(service.list(new Query(null,"Y",1,20,"name","ASC")).items()).hasSize(1);
        assertThat(service.list(new Query(null,null,2,20,"name","ASC")).items()).isEmpty();
        assertThat(service.list(new Query(null,null,1,20,"name","ASC")).total()).isEqualTo(2);
    }
    void project(String id) {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES(?,?,'SI','ING','2026-01-01','2027-12-31',0,0,0,'admin')",id,id);
    }
    @Test void currentProjectPreventsStopAndCurrentCountUsesLatestMapping() {
        var target=service.create(input("현재연결파트")).part();String id=target.part().id();
        project("projectcase");sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES('projectcase','202601',?,'admin')",id);
        assertThat(service.detail(id).part().projectCount()).isEqualTo(1);
        var before=rows("tb_sy_chg_hist");
        assertThatThrownBy(()->service.patch(id,new Patch(null,"N",target.revision()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("REFERENCE_IN_USE"));
        assertThat(service.detail(id).part().useYn()).isEqualTo("Y");assertThat(rows("tb_sy_chg_hist")).isEqualTo(before);
    }
    @Test void futureProjectPreventsStopButDoesNotCountAsCurrent() {
        var target=service.create(input("미래연결파트")).part();String id=target.part().id();project("futurecase");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES('futurecase','202701',?,'admin')",id);
        assertThat(service.detail(id).part().projectCount()).isZero();
        assertThatThrownBy(()->service.patch(id,new Patch(null,"N",target.revision()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("REFERENCE_IN_USE"));
    }
    @Test void exclusivelyPastProjectConnectionAllowsStop() {
        var target=service.create(input("과거연결파트")).part();var other=service.create(input("현재담당파트")).part();String id=target.part().id();project("pastcase");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES('pastcase','202601',?,'admin'),('pastcase','202606',?,'admin')",id,other.part().id());
        assertThat(service.detail(id).part().projectCount()).isZero();assertThat(service.patch(id,new Patch(null,"N",target.revision())).changed()).isTrue();
    }
    @Test void futureOnlyTimelineHasNoCurrentAndRawChangeInvalidatesRevision() {
        Create future=new Create("예정파트","2027-01","leadercase",BigDecimal.ZERO,BigDecimal.ZERO);
        var detail=service.create(future).part();String id=detail.part().id();
        assertThat(detail.part().currentStandard()).isNull();assertThat(detail.part().scheduledChange().effectiveMonth()).isEqualTo("2027-01");
        sql.update("UPDATE tb_bs_part_std SET prft_dstrb_rt=1 WHERE part_id=?",id);
        assertThatThrownBy(()->service.patch(id,new Patch("이름변경",null,detail.revision()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
    @Test void stoppedDeveloperStillCountsAndPreventsStop() {
        var detail=service.create(input("인력연결파트")).part();String id=detail.part().id();
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,reg_user_id) VALUES('devcase','devcase','시험인력','OWN','DEV','DIRECT',?,'STOP','N','admin')",id);
        assertThat(service.detail(id).part().personCount()).isEqualTo(1);
        assertThatThrownBy(()->service.patch(id,new Patch(null,"N",detail.revision()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("REFERENCE_IN_USE"));
    }
}
