package com.sicost.common.notifications;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.B7Mapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UploadNotificationDatabaseTest {
    PostgreSqlDatabase db;
    B7Mapper upload;
    com.sicost.salesopportunitiesops.SalesCommandMapper sales;
    TransactionTemplate transaction;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var provider=mock(ObjectProvider.class);
        var quoteProvider=mock(ObjectProvider.class);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary(),new UploadNotificationInterceptor(provider),new QuoteNotificationInterceptor(quoteProvider));
        factory.setMapperLocations(new ClassPathResource("mappers/b7/B7Mapper.xml"),new ClassPathResource("mappers/salescommands/SalesCommandMapper.xml"),new ClassPathResource("mappers/common/NotificationPublicationMapper.xml"),new ClassPathResource("mappers/common/ChangeHistoryMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());upload=session.getMapper(B7Mapper.class);
        sales=session.getMapper(com.sicost.salesopportunitiesops.SalesCommandMapper.class);
        var mapper=session.getMapper(NotificationPublicationMapper.class);
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),mock(CurrentUserProvider.class),clock,JsonMapper.builder().findAndAddModules().build());
        when(provider.getObject()).thenReturn(new UploadNotificationService(mapper,new NotificationPublisher(mapper,history,clock)));
        when(quoteProvider.getObject()).thenReturn(new QuoteNotificationService(mapper,new NotificationPublisher(mapper,history,clock)));
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        db.sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,user_stat_cd,reg_user_id) VALUES ('uploader','업로더','local@example.invalid','unusable',0,0,'USE','SYSTEM')");
        db.sql.update("INSERT INTO tb_sy_upld(upld_id,form_type_cd,scrn_id,file_nm,file_size,tot_row_cnt,succ_cnt,warn_cnt,err_cnt,upld_stat_cd,reg_user_id) VALUES (123,'B7','UI-15','test.xlsx',1,1,1,0,0,'CHECKED','uploader')");
    }
    @AfterEach void cleanup() {
        if(db!=null) {String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리");db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    void apply() {assertThat(upload.markApplied(123,"uploader",LocalDateTime.now(clock))).isEqualTo(1);}
    @Test void appliedIsAtomicAndExplicitRolelessUploaderGetsExactlyOneNotification() {
        assertThat(db.count("tb_sy_noti")).isZero();
        transaction.executeWithoutResult(s->apply());
        assertThat(db.sql.queryForMap("SELECT rcvr_user_id,noti_type_cd,send_mthd_cd,link_scrn_id,link_key FROM tb_sy_noti")).containsEntry("rcvr_user_id","uploader").containsEntry("noti_type_cd","UPLD_DONE").containsEntry("send_mthd_cd","SYS").containsEntry("link_key","123");
        transaction.executeWithoutResult(s->assertThat(upload.markApplied(123,"uploader",LocalDateTime.now(clock))).isEqualTo(1));
        assertThat(db.count("tb_sy_noti")).isEqualTo(1);assertThat(db.count("tb_sy_delivery_task")).isEqualTo(1);
    }
    @Test void notificationFailureRollsBackBusinessAppliedAndAuditThenSameUploadCanRetry() {
        db.sql.execute("ALTER TABLE tb_sy_noti ADD CONSTRAINT codex_reject_upload CHECK (noti_type_cd <> 'UPLD_DONE')");
        try {
            assertThatThrownBy(()->transaction.executeWithoutResult(s->{
                db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_nm,clnt_type_cd,biz_reg_no,use_yn,reg_user_id) VALUES ('TEST-UPLD','시험 고객','CUST','0123456789','Y','uploader')");apply();
            })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("codex_reject_upload");
            assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld WHERE upld_id=123",String.class)).isEqualTo("CHECKED");
            assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_bs_clnt WHERE clnt_id='TEST-UPLD'",Integer.class)).isZero();
            assertThat(db.count("tb_sy_noti")).isZero();assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();
        } finally {db.sql.execute("ALTER TABLE tb_sy_noti DROP CONSTRAINT codex_reject_upload");}
        transaction.executeWithoutResult(s->apply());assertThat(db.count("tb_sy_noti")).isEqualTo(1);
    }
    @Test void actualQuoteRejectionReplacesOldDirectInsertAndDeduplicatesByHistory() {
        String part=db.sql.queryForObject("SELECT part_id FROM tb_bs_part ORDER BY part_id LIMIT 1",String.class);
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_nm,clnt_type_cd,biz_reg_no,use_yn,reg_user_id) VALUES ('QUOTE-C','견적 고객','CUST','0123456789','Y','uploader')");
        db.sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,expct_prft_rt,aprv_stat_cd,reg_user_id) VALUES ('QUOTE-O','견적','QUOTE-C',?,'PROP',30,100,'202611','ING',10,'REJ','uploader')",part);
        transaction.executeWithoutResult(s->{
            db.sql.update("INSERT INTO tb_sl_aprv_hist(opp_id,hist_sn,aprv_act_cd,prft_rt,reg_user_id) VALUES ('QUOTE-O',1,'REJ',10,'uploader')");
            var notification=new com.sicost.salesopportunitiesops.SalesCommandDtos.Notification("QUOTE-O","uploader","기존 알림","uploader",LocalDateTime.now(clock));
            assertThat(sales.notification(notification)).isEqualTo(1);assertThat(sales.notification(notification)).isEqualTo(1);
        });
        assertThat(db.count("tb_sy_noti")).isEqualTo(1);assertThat(db.count("tb_sy_delivery_task")).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT event_key FROM tb_sy_delivery_task",String.class)).isEqualTo("QUOTE:QUOTE-O:1:REJ");
    }
}
