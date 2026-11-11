package com.sicost.states;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import static com.sicost.states.DeveloperStateDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DeveloperStateDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;DeveloperStateService service;ObjectMapper json;
    static final String ID="DEV-000901";
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(DeveloperStateMapper.class,()->session.getMapper(DeveloperStateMapper.class));
        context.registerBean(DeveloperRevisionMapper.class,()->session.getMapper(DeveloperRevisionMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,DeveloperAggregateRevision.class,CommitInvalidation.class,DeveloperStateRepository.class,DeveloperStateService.class);
        context.refresh();service=context.getBean(DeveloperStateService.class);
        String part=sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE hq_yn='Y' ORDER BY part_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,jncmp_dt,work_stat_cd,reg_user_id) VALUES(?,?,'상태 검증','FT','DEV','DRCT',?,DATE '2026-01-01','IDLE','admin')",ID,"K-STATE-901",part);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(){return context.getBean(DeveloperAggregateRevision.class).revision(ID,false);}
    Created create(String state,String start,String end){return service.create(ID,new Create(state,LocalDate.parse(start),end==null?null:LocalDate.parse(end),null,revision()));}
    List<Map<String,Object>> snapshot(){return sql.queryForList("SELECT * FROM tb_hr_dev_stat WHERE dev_id=? ORDER BY stat_sn",ID);}
    long histories(){return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? OR trgt_key LIKE ?",Long.class,ID,ID+":%");}
    @Test void requirement398LeaveRoundTripAndRequirement478NullEndWithNoOp() {
        int sn=create("LEAVE","2026-06-01","2026-06-20").sn();
        assertThat(sql.queryForMap("SELECT dev_id,stat_sn,work_stat_cd,bgn_dt,end_dt,stat_rsn FROM tb_hr_dev_stat WHERE dev_id=? AND stat_sn=?",ID,sn)).containsEntry("work_stat_cd","LEAVE").containsEntry("dev_id",ID);
        var before=snapshot();long history=histories();
        assertThat(service.putEnd(ID,sn,new End(LocalDate.parse("2026-06-20"),revision())).changed()).isFalse();
        assertThat(snapshot()).isEqualTo(before);assertThat(histories()).isEqualTo(history);
        service.putEnd(ID,sn,new End(null,revision()));assertThat(service.get(ID).items().get(0).endDate()).isNull();
    }
    @Test void requirement490WorkClosesAndRequirement487CancellationRestoresAndNeverReusesSerial() {
        int leave=create("LEAVE","2026-06-01",null).sn();int work=create("WORK","2026-06-15",null).sn();
        assertThat(sql.queryForObject("SELECT close_leave_sn::integer FROM tb_hr_dev_stat WHERE dev_id=? AND stat_sn=?",Integer.class,ID,work)).isEqualTo(leave);
        assertThat(sql.queryForObject("SELECT orgnl_leave_end_dt FROM tb_hr_dev_stat WHERE dev_id=? AND stat_sn=?",LocalDate.class,ID,work)).isNull();
        service.cancel(ID,work,new Cancel(null,revision()));assertThat(snapshot()).hasSize(1);assertThat(service.get(ID).items().get(0).endDate()).isNull();
        assertThat(create("WORK","2026-06-16",null).sn()).isGreaterThan(work);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_type_cd='UPD' AND chg_item_nm='재직 취소'",Integer.class,ID+":"+work)).isEqualTo(1);
    }
    @Test void requirement481And486ActualRetirementCancellationResumesWithAtomicHistory() {
        create("LEAVE","2026-06-01",null);int retirement=create("RETIRE","2026-06-14",null).sn();
        assertThat(sql.queryForMap("SELECT retire_dt,work_stat_cd,use_yn FROM tb_hr_dev WHERE dev_id=?",ID)).containsEntry("use_yn","N").containsEntry("work_stat_cd","RETIRE");
        service.cancel(ID,retirement,new Cancel("잘못된 퇴사 등록",revision()));
        assertThat(sql.queryForMap("SELECT retire_dt,use_yn FROM tb_hr_dev WHERE dev_id=?",ID)).containsEntry("retire_dt",null).containsEntry("use_yn","Y");
        assertThat(service.get(ID).items()).hasSize(1);assertThat(service.get(ID).items().get(0).endDate()).isNull();
    }
    @Test void requirement479ChangedMonthsOnlyAndFailedHistoryRollsBackEveryRow() {
        int sn=create("LEAVE","2026-06-01","2026-07-31").sn();
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES('202606','CLOSE','admin') ON CONFLICT(close_ym) DO UPDATE SET close_stat_cd='CLOSE'");
        service.putEnd(ID,sn,new End(LocalDate.parse("2026-08-01"),revision()));
        var before=snapshot();long history=histories();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_state_history CHECK(trgt_tbl_nm <> 'TB_HR_DEV_STAT') NOT VALID");
        assertThatThrownBy(()->service.putEnd(ID,sn,new End(LocalDate.parse("2026-08-02"),revision()))).isInstanceOf(RuntimeException.class);
        assertThat(snapshot()).isEqualTo(before);assertThat(histories()).isEqualTo(history);
    }
    @Test void requirement491CancellationOrderRestoresSameNullOriginalWithRemainingRetirementCap() {
        create("LEAVE","2026-06-01",null);int work=create("WORK","2026-06-15",null).sn();int retire=create("RETIRE","2026-07-01",null).sn();
        service.cancel(ID,work,new Cancel(null,revision()));
        assertThat(service.get(ID).items().stream().filter(r->r.state().equals("LEAVE")).findFirst().orElseThrow().endDate()).isEqualTo(LocalDate.parse("2026-06-30"));
        service.cancel(ID,retire,new Cancel("취소",revision()));
        assertThat(service.get(ID).items()).hasSize(1);
        assertThat(service.get(ID).items().get(0).endDate()).isNull();
    }
    @Test void staleValueIncludesChildSnapshotAndRollsBackAllWrites() {
        int sn=create("LEAVE","2026-06-01",null).sn();String old=revision();
        sql.update("INSERT INTO tb_hr_dev_skill(dev_id,skill_nm,sort_ord,reg_user_id) VALUES(?,'SQL',1,'admin')",ID);
        var before=snapshot();long history=histories();
        assertThatThrownBy(()->service.putEnd(ID,sn,new End(LocalDate.parse("2026-06-30"),old))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(snapshot()).isEqualTo(before);assertThat(histories()).isEqualTo(history);
    }
}
