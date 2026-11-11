package com.sicost.b8;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.persistence.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DeveloperImportDatabaseTest {
    PostgreSqlDatabase db; B8Service service;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES('HQ','본사','Y','Y','admin')");
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());var mapper=session.getMapper(B8Mapper.class);
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,JsonMapper.builder().findAndAddModules().build());
        service=new B8Service(new B8Repository(mapper,new MasterIdService(session.getMapper(MasterIdMapper.class)),history),new ExcelImportService(),new B8ImportDefinition(mapper),users,new DataSourceTransactionManager(db.source),clock);
    }
    @Test void validationWritesOnlyDefaultSequenceUploadAndNoPeopleOrReservedNumber() throws Exception {
        var before=db.sql.queryForMap("SELECT last_value,is_called FROM sq_tb_hr_dev_mng_no");var checked=service.validate(DeveloperWorkbook.valid());
        assertThat(checked.canApply()).isTrue();assertThat(db.count("tb_hr_dev")).isZero();assertThat(db.count("tb_hr_dev_prc")).isZero();assertThat(db.count("tb_hr_dev_skill")).isZero();assertThat(db.sql.queryForMap("SELECT last_value,is_called FROM sq_tb_hr_dev_mng_no")).isEqualTo(before);
        var upload=db.sql.queryForMap("SELECT * FROM tb_sy_upld WHERE upld_id=?",checked.uploadId());assertThat(upload).containsEntry("form_type_cd","B8").containsEntry("scrn_id","UI-02").containsEntry("upld_stat_cd","CHECKED");assertThat(upload.get("trgt_ym")).isNull();assertThat(upload.get("trgt_proj_id")).isNull();
    }
    @Test void applyPersistsDeveloperNullPayPriceOrderedSkillsNewHistoryAndRecoversSameNumbersOnRetry() throws Exception {
        var file=DeveloperWorkbook.valid();var checked=service.validate(file);var applied=service.apply(file,checked.uploadId());
        assertThat(service.apply(file,checked.uploadId())).isEqualTo(applied);assertThat(db.count("tb_hr_dev")).isEqualTo(1);assertThat(db.count("tb_hr_dev_prc")).isEqualTo(1);assertThat(db.count("tb_hr_dev_skill")).isEqualTo(2);
        assertThat(db.sql.queryForObject("SELECT use_yn FROM tb_hr_dev",String.class)).isEqualTo("Y");assertThat(db.sql.queryForObject("SELECT work_stat_cd FROM tb_hr_dev",String.class)).isEqualTo("IDLE");assertThat(db.sql.queryForObject("SELECT aply_bgn_ym FROM tb_hr_dev_prc",String.class)).isEqualTo("202002");assertThat(db.sql.queryForObject("SELECT mon_pay_amt FROM tb_hr_dev_prc",String.class)).isNull();
        assertThat(db.sql.queryForList("SELECT skill_nm FROM tb_hr_dev_skill ORDER BY sort_ord",String.class)).containsExactly("Java","SQL");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_HR_DEV' AND chg_type_cd='NEW' AND chg_rsn=?",Long.class,"B8 업로드:"+checked.uploadId())).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT aply_dttm IS NOT NULL FROM tb_sy_upld",Boolean.class)).isTrue();assertThat(db.count("tb_sy_accs_log")).isZero();
    }
    @Test void concurrentSameUploadProducesOneCompleteSet() throws Exception {
        var file=DeveloperWorkbook.valid();var checked=service.validate(file);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<B8Dtos.Applied> command=()->{assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();return service.apply(file,checked.uploadId());};
        try{var first=pool.submit(command);var second=pool.submit(command);start.countDown();assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo(second.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertThat(db.count("tb_hr_dev")).isEqualTo(1);assertThat(db.count("tb_hr_dev_prc")).isEqualTo(1);assertThat(db.count("tb_hr_dev_skill")).isEqualTo(2);
    }
    @Test void changedDatabaseCommitsCheckedDiagnosticsAnd422WithNoBusinessWrites() throws Exception {
        var file=DeveloperWorkbook.valid();var checked=service.validate(file);db.sql.update("UPDATE tb_bs_part SET use_yn='N' WHERE part_id='HQ'");
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(db.count("tb_hr_dev")).isZero();assertThat(db.count("tb_sy_upld_err")).isEqualTo(1);assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void historyFailureRollsBackEveryPersonChildAndAppliedTransition() throws Exception {
        var file=DeveloperWorkbook.valid();var checked=service.validate(file);db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_history CHECK(trgt_tbl_nm <> 'TB_HR_DEV_PRC')");
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_hr_dev")).isZero();assertThat(db.count("tb_hr_dev_prc")).isZero();assertThat(db.count("tb_hr_dev_skill")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void diagnosticReplacementFailurePreservesPreviousUploadAndProblems() throws Exception {
        var file=DeveloperWorkbook.valid();service.validate(file);var before=db.sql.queryForList("SELECT * FROM tb_sy_upld");db.sql.update("UPDATE tb_bs_part SET use_yn='N' WHERE part_id='HQ'");db.sql.execute("ALTER TABLE tb_sy_upld_err ADD CONSTRAINT reject_issue CHECK(rslt_sn<0)");
        long id=db.sql.queryForObject("SELECT upld_id FROM tb_sy_upld",Long.class);assertThatThrownBy(()->service.apply(file,id)).isInstanceOf(RuntimeException.class);assertThat(db.sql.queryForList("SELECT * FROM tb_sy_upld")).isEqualTo(before);assertThat(db.count("tb_hr_dev")).isZero();
    }
}
