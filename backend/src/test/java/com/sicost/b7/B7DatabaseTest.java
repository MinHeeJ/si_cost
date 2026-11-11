package com.sicost.b7;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.persistence.*;
import com.sicost.common.security.AccountCipher;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class B7DatabaseTest {
    PostgreSqlDatabase db; B7Service service; AccountCipher cipher;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());var mapper=session.getMapper(B7Mapper.class);
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,JsonMapper.builder().findAndAddModules().build());
        cipher=mock(AccountCipher.class);
        var repository=new B7Repository(mapper,new MasterIdService(session.getMapper(MasterIdMapper.class)),cipher,history);
        service=new B7Service(repository,new ExcelImportService(),new B7ImportDefinition(mapper),users,new DataSourceTransactionManager(db.source),clock);
    }
    @Test void seededAdminFirstValidationAndApplyCommitOnceAndRetryDoesNotRevalidateOwnRows() throws Exception {
        var file=B7Workbook.valid();var checked=service.validate(file);
        assertThat(checked.canApply()).isTrue();assertThat(db.count("tb_bs_clnt")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();
        assertThat(service.apply(file,checked.uploadId()).appliedCount()).isEqualTo(1);assertThat(service.apply(file,checked.uploadId()).appliedCount()).isEqualTo(1);
        assertThat(db.count("tb_bs_clnt")).isEqualTo(1);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT reg_user_id FROM tb_bs_clnt",String.class)).isEqualTo("admin");
        assertThat(db.sql.queryForObject("SELECT chg_rsn FROM tb_sy_chg_hist",String.class)).isEqualTo("B7 업로드:"+checked.uploadId());
        assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("APPLIED");
    }
    @Test void validationIssueFailureRollsBackParentAndEveryProblem() throws Exception {
        db.sql.execute("ALTER TABLE tb_sy_upld_err ADD CONSTRAINT reject_issue CHECK(rslt_sn<0)");
        var file=B7Workbook.file(b->B7Workbook.row(b,1,"고객사","이름","invalid"));
        assertThatThrownBy(()->service.validate(file)).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_sy_upld")).isZero();assertThat(db.count("tb_sy_upld_err")).isZero();assertThat(db.count("tb_bs_clnt")).isZero();
    }
    @Test void historyFailureRollsBackClientsSummaryReplacementAndAppliedTransition() throws Exception {
        var file=B7Workbook.valid();var checked=service.validate(file);
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_history CHECK(trgt_tbl_nm <> 'TB_BS_CLNT')");
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_bs_clnt")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void duplicateAfterCheckReplacesIssuesAndCommitsCheckedBefore422() throws Exception {
        var file=B7Workbook.valid();var checked=service.validate(file);
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('C-999','CUST','동시 등록','123-45-67890','admin')");
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(db.count("tb_bs_clnt")).isEqualTo(1);assertThat(db.count("tb_sy_chg_hist")).isZero();assertThat(db.count("tb_sy_upld_err")).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT err_cnt FROM tb_sy_upld",Integer.class)).isEqualTo(1);
    }
    @Test void replacementFailureRetainsPreviousCheck() throws Exception {
        var file=B7Workbook.valid();var checked=service.validate(file);var before=db.sql.queryForList("SELECT * FROM tb_sy_upld");
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('C-999','CUST','동시 등록','123-45-67890','admin')");
        db.sql.execute("ALTER TABLE tb_sy_upld_err ADD CONSTRAINT reject_issue CHECK(rslt_sn<0)");
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOf(RuntimeException.class);assertThat(db.sql.queryForList("SELECT * FROM tb_sy_upld")).isEqualTo(before);
    }
    @Test void concurrentSameUploadCommitsOneClientAndNewSet() throws Exception {
        var file=B7Workbook.valid();var checked=service.validate(file);var start=new CountDownLatch(1);
        Callable<Integer> apply=()->{assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();return service.apply(file,checked.uploadId()).appliedCount();};
        var pool=Executors.newFixedThreadPool(2);
        try {var first=pool.submit(apply);var second=pool.submit(apply);start.countDown();assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(1);assertThat(second.get(20,TimeUnit.SECONDS)).isEqualTo(1);}
        finally {pool.shutdownNow();}
        assertThat(db.count("tb_bs_clnt")).isEqualTo(1);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1);
    }
    @Test void secondRowEncryptionFailureRollsBackFirstRowAndAllNewHistory() throws Exception {
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"고객사","첫 거래처","1234567890","","","","","a@example.test");B7Workbook.row(b,2,"협력사","둘 거래처","1234567891","","","","","","KB국민은행","0100000000","예금주");});
        var checked=service.validate(file);assertThat(checked.canApply()).isTrue();when(cipher.encrypt(any())).thenThrow(new ApiException(503,"ENCRYPTION_CONFIG_ERROR","계좌 암호화 설정을 확인해 주세요."));
        assertThatThrownBy(()->service.apply(file,checked.uploadId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(503));
        assertThat(db.count("tb_bs_clnt")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
}
