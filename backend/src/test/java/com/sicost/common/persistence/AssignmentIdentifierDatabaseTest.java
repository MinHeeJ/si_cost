package com.sicost.common.persistence;

import com.sicost.common.api.ApiException;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AssignmentIdentifierDatabaseTest {
    PostgreSqlDatabase db;
    MasterIdMapper mapper;
    MasterIdService ids;
    TransactionTemplate transaction;
    String projectId,developerId;
    List<Map<String,Object>> applied;

    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(db.source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("5").load().migrate();
        applied=db.sql.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        String key=UUID.randomUUID().toString().replace("-","").substring(0,14);
        projectId="P-"+key;developerId="D-"+key;
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2026-01-01','2026-12-31',1,1,0,0,'admin')",projectId,projectId);
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) SELECT ?,?,?,'FT','DEV','DRCT',part_id,'IDLE','admin' FROM tb_bs_part ORDER BY part_id LIMIT 1",developerId,"M"+key,developerId);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(MasterIdMapper.class);
        ids=new MasterIdService(mapper);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
    }
    void assignment(String id) {
        db.sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV',DATE '2026-01-01',DATE '2026-12-31',1,1,'NEW',0,'ACT','admin')",id,projectId,developerId);
    }
    @Test void emptyInstallationStartsAtOneAndPreservesSeedAndAllPriorHistory() {
        var standards=db.sql.queryForList("SELECT * FROM tb_sy_oper_std ORDER BY std_cd,aply_bgn_ym");
        var codes=db.sql.queryForList("SELECT * FROM tb_sy_code ORDER BY code_grp_id,code");
        db.install();
        String first=transaction.execute(status -> ids.nextAssignmentId());
        assertThat(first).isEqualTo("A-0001");
        assertThat(db.count("tb_pj_asgn")).isZero();
        db.install();
        assertThat(db.sql.queryForList("SELECT * FROM flyway_schema_history WHERE version IN ('1','2','3','4','5') ORDER BY installed_rank")).isEqualTo(applied);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_oper_std ORDER BY std_cd,aply_bgn_ym")).isEqualTo(standards);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_code ORDER BY code_grp_id,code")).isEqualTo(codes);
    }
    @Test void initializationUsesExactNumericMaximumAndIgnoresNonNumericIds() {
        BigInteger maximum=new BigInteger("9007199254740993");
        assignment("A-0000");assignment("A-0099");assignment("A-"+maximum);assignment("LEGACY-999999");
        var before=db.sql.queryForList("SELECT * FROM tb_pj_asgn ORDER BY asgn_id");
        db.install();
        assertThat(mapper.nextAssignment()).isEqualTo(maximum.add(BigInteger.ONE));
        assertThat(db.sql.queryForList("SELECT * FROM tb_pj_asgn ORDER BY asgn_id")).isEqualTo(before);
    }
    @Test void rollbackRestartAndSameSessionNeverReuseAllocatedSuffixes() {
        assignment("A-0007");db.install();
        transaction.executeWithoutResult(status -> {
            assertThat(ids.nextAssignmentId()).isEqualTo("A-0008");
            assertThat(ids.nextAssignmentId()).isEqualTo("A-0009");
            status.setRollbackOnly();
        });
        db.install();
        String next=transaction.execute(status -> ids.nextAssignmentId());
        assertThat(next).isEqualTo("A-0010");
        assertThat(db.count("tb_pj_asgn")).isEqualTo(1);
    }
    @Test void existingAllocatorIsNeverResetEvenWhenMigrationScriptIsRepeated() throws Exception {
        db.install();db.sql.execute("SELECT setval('sq_tb_pj_asgn_id',100,true)");
        String script=new ClassPathResource("db/migration/V6__assignment_identifier_sequence.sql").getContentAsString(StandardCharsets.UTF_8);
        db.sql.execute(script);
        assertThat(mapper.nextAssignment()).isEqualTo(BigInteger.valueOf(101));
        assertThat(db.sql.queryForObject("SELECT max_value FROM pg_sequences WHERE schemaname=current_schema() AND sequencename='sq_tb_pj_asgn_id'",BigInteger.class)).isEqualTo(new BigInteger("999999999999999999"));
        assertThat(db.sql.queryForObject("SELECT cycle FROM pg_sequences WHERE schemaname=current_schema() AND sequencename='sq_tb_pj_asgn_id'",Boolean.class)).isFalse();
    }
    @Test void maximumExistingIdExhaustsWithoutReuseOrUnsafeLength() {
        assignment("A-999999999999999999");db.install();
        assertThatThrownBy(() -> transaction.execute(status -> ids.nextAssignmentId())).isInstanceOfSatisfying(ApiException.class,e -> {
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("ID_EXHAUSTED");
        });
        assertThat(db.count("tb_pj_asgn")).isEqualTo(1);
    }
    @Test void parallelAllocationsHaveNoDuplicateIdentifiers() throws Exception {
        db.install();var pool=Executors.newFixedThreadPool(4);
        try {
            Callable<String> allocate=() -> transaction.execute(status -> ids.nextAssignmentId());
            var futures=pool.invokeAll(java.util.Collections.nCopies(20,allocate));
            var values=new java.util.HashSet<String>();
            for(var future:futures)assertThat(values.add(future.get())).isTrue();
            assertThat(values).hasSize(20).allMatch(id -> id.matches("A-[0-9]{4,18}"));
        } finally {pool.shutdownNow();}
    }
}
