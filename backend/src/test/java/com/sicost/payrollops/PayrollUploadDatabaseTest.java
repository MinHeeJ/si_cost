package com.sicost.payrollops;

import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.payrollops.PayrollUploadDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PayrollUploadDatabaseTest {
    PostgreSqlDatabase db;PayrollUploadService service;TransactionTemplate transaction;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        service=new PayrollUploadService(new PayrollUploadRepository(session.getMapper(PayrollUploadMapper.class)),
            new CurrentUserProvider(session.getMapper(CurrentUserMapper.class)));
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    long upload(String form,String screen,String month,String time,String status) {
        return db.sql.queryForObject("INSERT INTO tb_sy_upld(form_type_cd,scrn_id,file_nm,file_size,trgt_ym,tot_row_cnt,succ_cnt,warn_cnt,err_cnt,upld_stat_cd,reg_user_id,reg_dttm,aply_dttm) VALUES(?,?,?,10,?,7,4,2,1,?,'admin',?::timestamp,CASE WHEN ?='APPLIED' THEN ?::timestamp END) RETURNING upld_id",
            Long.class,form,screen,UUID.randomUUID()+".xlsx",month,status,time,status,time);
    }
    Page read(String month,int page,int size) {
        return transaction.execute(tx->service.getPayrollUploads(new Query(month,page,size,((long)page-1)*size)));
    }
    @Test void filtersCountOrderMaterializationAndReadOnlyAuditAreRealDatabaseValues() {
        long older=upload("B1","UI-02","202607","2026-07-01 09:00:00","APPLIED");
        long sameFirst=upload("B1","UI-02","202607","2026-07-02 09:00:00","CHECKED");
        long sameLast=upload("B1","UI-02","202607","2026-07-02 09:00:00","APPLIED");
        upload("B5","UI-02","202607","2026-07-03 09:00:00","CHECKED");
        upload("B1","UI-12","202607","2026-07-03 09:00:00","CHECKED");
        upload("B1","UI-02","202608","2026-07-03 09:00:00","CHECKED");
        db.sql.update("INSERT INTO tb_sy_upld_err(upld_id,rslt_sn,rslt_type_cd,row_no,col_nm,rsn_cn,reg_user_id) VALUES(?,1,'WARN',0,'[파일]','파일 경고','admin'),(?,2,'WARN',0,'[표]','표 경고','admin')",sameLast,sameLast);
        var before=snapshot();var result=read("202607",1,20);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting(PayrollUploadDtos.Record::uploadId).containsExactly(sameLast,sameFirst,older);
        var row=result.items().get(0);
        assertThat(row.fileName()).isEqualTo(db.sql.queryForObject("SELECT file_nm FROM tb_sy_upld WHERE upld_id=?",String.class,sameLast));
        assertThat(row.actor()).isEqualTo("admin");assertThat(row.month()).isEqualTo("2026-07");
        assertThat(row.summary().totalRows()).isEqualTo(7);assertThat(row.summary().successRows()).isEqualTo(4);
        assertThat(row.summary().warningRows()).isEqualTo(2);assertThat(row.summary().errorRows()).isEqualTo(1);
        assertThat(row.summary().fileWarningCount()).isEqualTo(1);assertThat(row.summary().tableWarningCount()).isEqualTo(1);
        assertThat(result.items().get(1).appliedAt()).isNull();
        assertThat(read("202607",2,20).items()).isEmpty();assertThat(read("202607",2,20).total()).isEqualTo(3);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void emptySourceDoesNotInventBusinessRows() {
        var before=snapshot();assertThat(read("202607",1,50).items()).isEmpty();assertThat(read("202607",1,50).total()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void laterUploadDoesNotRewriteEarlierReceiptOrAuditTimes() {
        long first=upload("B1","UI-02","202607","2026-07-01 09:00:00","APPLIED");
        var before=db.sql.queryForList("SELECT * FROM tb_sy_upld WHERE upld_id=?",first);
        var original=read("202607",1,50).items().get(0);
        upload("B1","UI-02","202607","2026-07-02 09:00:00","APPLIED");
        assertThat(read("202607",1,50).items().get(1)).isEqualTo(original);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_upld WHERE upld_id=?",first)).isEqualTo(before);
    }
    List<Object> snapshot() {
        return List.of(db.sql.queryForList("SELECT * FROM tb_sy_upld ORDER BY upld_id"),
            db.sql.queryForList("SELECT * FROM tb_sy_upld_err ORDER BY upld_id,rslt_sn"),
            db.sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id"),
            db.sql.queryForList("SELECT * FROM tb_hr_mon_pay ORDER BY pay_ym,dev_id"),
            db.sql.queryForList("SELECT * FROM tb_pj_asgn_hist ORDER BY asgn_id,hist_sn"));
    }
}
