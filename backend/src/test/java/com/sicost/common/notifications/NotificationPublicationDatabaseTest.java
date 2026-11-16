package com.sicost.common.notifications;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class NotificationPublicationDatabaseTest {
    PostgreSqlDatabase db;
    NotificationPublicationMapper mapper;
    ChangeHistoryMapper audits;
    Clock clock=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneOffset.UTC);
    TransactionTemplate transaction;
    NotificationRecipients.Recipient recipient;
    final Event event=new Event(NotificationEventKeys.cash(LocalDate.of(2026,7,15)),"CASH_SHORT","자금 부족",null,null);
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/common/NotificationPublicationMapper.xml"),new ClassPathResource("mappers/common/ChangeHistoryMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        mapper=session.getMapper(NotificationPublicationMapper.class);audits=session.getMapper(ChangeHistoryMapper.class);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        var role=mapper.eligibleRoles().get(0);
        recipient=new NotificationRecipients.Recipient(role.userId(),role.name(),role.emailAddress(),List.of(role.role()));
    }
    @AfterEach void removeOnlyOwnedSchema() {
        if(db!=null) {
            String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
            if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    NotificationPublisher publisher(ChangeHistoryMapper historyMapper) {
        var writer=new ChangeHistoryWriter(historyMapper,mock(CurrentUserProvider.class),clock,JsonMapper.builder().findAndAddModules().build());
        return new NotificationPublisher(mapper,writer,clock);
    }
    @Test void realMapperMaterializesEmailAndSystemPublicationRetainsDedupAfterCleanup() {
        var publisher=publisher(audits);
        var tasks=transaction.execute(status->publisher.publish(event,List.of(recipient)));
        assertThat(tasks).hasSize(1);
        assertThat(db.count("tb_sy_noti")).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT state_cd FROM tb_sy_delivery_task WHERE task_id=?",String.class,tasks.get(0))).isEqualTo("SENT");
        assertThat(db.count("tb_sy_chg_hist")).isPositive();
        db.sql.update("DELETE FROM tb_sy_noti WHERE rcvr_user_id=?",recipient.userId());
        List<UUID> repeated=transaction.execute(status->publisher.publish(event,List.of(recipient)));
        assertThat(repeated).isEmpty();
        assertThat(db.count("tb_sy_noti")).isZero();assertThat(db.count("tb_sy_delivery_task")).isEqualTo(1);
    }
    @Test void requiredAuditFailureRollsBackBusinessRowNotificationAndSystemTaskTogether() {
        String key="P-"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2026-01-01','2026-12-31',1,1,0,0,?)",key,key,recipient.userId());
        var failed=mock(ChangeHistoryMapper.class);when(failed.insert(any())).thenReturn(0);
        var publisher=publisher(failed);
        assertThatThrownBy(()->transaction.executeWithoutResult(status->{
            db.sql.update("UPDATE tb_pj_proj SET proj_stat_cd='END' WHERE proj_id=?",key);
            publisher.publish(event,List.of(recipient));
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.sql.queryForObject("SELECT proj_stat_cd FROM tb_pj_proj WHERE proj_id=?",String.class,key)).isEqualTo("ING");
        assertThat(db.count("tb_sy_noti")).isZero();assertThat(db.count("tb_sy_delivery_task")).isZero();
    }
}
