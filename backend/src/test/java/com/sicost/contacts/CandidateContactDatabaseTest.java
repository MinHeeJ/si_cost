package com.sicost.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.contacts.CandidateContactDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class CandidateContactDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; CandidateContactService service; CandidateRevisionService snapshots; TransactionTemplate tx;
    final String id="CAND-900001";
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        new ResourceDatabasePopulator(new ClassPathResource("contacts/contacts.sql")).execute(db.source);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(CandidateContactMapper.class,()->session.getMapper(CandidateContactMapper.class));
        context.registerBean(CandidateRevisionMapper.class,()->session.getMapper(CandidateRevisionMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,
                CandidateRevisionService.class,CandidateContactRepository.class,CandidateContactService.class);
        context.refresh();service=context.getBean(CandidateContactService.class);snapshots=context.getBean(CandidateRevisionService.class);
        tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(String target){return tx.execute(s->snapshots.revision(target,false));}
    Create request(String target,String status,String memo){return new Create(status,memo,revision(target));}
    List<Map<String,Object>> candidates(){return sql.queryForList("SELECT * FROM tb_hr_cand ORDER BY cand_id");}
    List<Map<String,Object>> contacts(){return sql.queryForList("SELECT * FROM tb_hr_cand_cntc ORDER BY cand_id,cntc_sn");}
    List<Map<String,Object>> histories(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");}
    @Test void requirement504ReadsEveryEventInDateThenSerialOrderWithoutWrites() {
        var before=candidates();var events=contacts();var audit=histories();
        var first=service.list(id,new Query(1,20));var second=service.list(id,new Query(2,20));
        assertThat(first.total()).isEqualTo(25);assertThat(second.total()).isEqualTo(25);assertThat(first.items()).hasSize(20);assertThat(second.items()).hasSize(5);
        var expected=sql.queryForList("SELECT cntc_sn FROM tb_hr_cand_cntc WHERE cand_id=? ORDER BY cntc_dt DESC,cntc_sn DESC",Integer.class,id);
        var actual=new ArrayList<Integer>();first.items().forEach(c->actual.add(c.sn()));second.items().forEach(c->actual.add(c.sn()));assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(first.items()).allSatisfy(c->{assertThat(c.actorId()).isEqualTo("admin");assertThat(c.actorName()).isNotBlank();});
        assertThat(second.items()).extracting(Contact::memo).contains("등록","정보 수정");
        assertThat(service.list(id,new Query(99,20)).items()).isEmpty();assertThat(service.list("CAND-900002",new Query(1,50)).items()).isEmpty();
        assertThat(candidates()).isEqualTo(before);assertThat(contacts()).isEqualTo(events);assertThat(histories()).isEqualTo(audit);
    }
    @Test void requirement400And505SameStatusCommitsNewCompositeKeyEventParentAndHistoryWithoutChangingAnotherCandidate() {
        var control=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id='CAND-900002'");
        var original=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id);String oldRevision=revision(id);
        Created created=service.create(id,request(id,"BEFORE","가".repeat(200)));
        assertThat(created.contact().sn()).isEqualTo(26);assertThat(created.contactStatus()).isEqualTo("BEFORE");assertThat(created.lastContactDate()).isEqualTo(LocalDate.parse("2026-06-16"));
        var event=sql.queryForMap("SELECT * FROM tb_hr_cand_cntc WHERE cand_id=? AND cntc_sn=26",id);
        assertThat(event.get("cntc_stat_cd")).isEqualTo("BEFORE");assertThat(event.get("cntc_memo")).isEqualTo("가".repeat(200));assertThat(event.get("cntc_dt").toString()).isEqualTo("2026-06-16");assertThat(event.get("reg_user_id")).isEqualTo("admin");
        var parent=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id);
        assertThat(parent.get("cntc_stat_cd")).isEqualTo(event.get("cntc_stat_cd"));assertThat(parent.get("last_cntc_dt")).isEqualTo(event.get("cntc_dt"));
        assertThat(parent.get("reg_dttm")).isEqualTo(original.get("reg_dttm"));assertThat(parent.get("reg_user_id")).isEqualTo(original.get("reg_user_id"));assertThat(parent.get("mod_user_id")).isEqualTo("admin");
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id='CAND-900002'")).isEqualTo(control);assertThat(revision(id)).isNotEqualTo(oldRevision);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_HR_CAND_CNTC' AND trgt_key=? AND chg_type_cd='NEW'",Long.class,id+":26")).isEqualTo(1);
        service.create(id,request(id,"BEFORE","같은 상태 다른 메모"));assertThat(service.list(id,new Query(1,20)).items().get(0).sn()).isEqualTo(27);
    }
    @ParameterizedTest @ValueSource(strings={"HOLD","CTRT"})
    void holdAndContractedAreNotIncludedInOverdueContactScope(String status) {
        assertThat(overdue()).isEqualTo(3);
        service.create(id,request(id,status,"진행 상태 변경"));
        sql.update("UPDATE tb_hr_cand SET last_cntc_dt=DATE '2026-01-01' WHERE cand_id=?",id);
        assertThat(overdue()).isEqualTo(2);
    }
    long overdue(){return sql.queryForObject("SELECT count(*) FROM tb_hr_cand WHERE cntc_stat_cd NOT IN ('HOLD','CTRT') AND DATE '2026-06-16'-COALESCE(last_cntc_dt,reg_dttm::date)>30",Long.class);}
    @Test void serialLimitLeavesCandidateEventsAndAuditUnchanged() {
        var parent=candidates();var events=contacts();var audit=histories();
        assertThatThrownBy(()->service.create("CAND-900003",request("CAND-900003","DONE","연락")))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SERIAL_LIMIT"));
        assertThat(candidates()).isEqualTo(parent);assertThat(contacts()).isEqualTo(events);assertThat(histories()).isEqualTo(audit);
    }
    @Test void staleAfterSkillChangeWithNullModificationDatesWritesNothing() {
        Create input=request(id,"DONE","연락");sql.update("UPDATE tb_hr_cand_skill SET skill_nm='Kotlin' WHERE cand_id=?",id);
        var parent=candidates();var events=contacts();var audit=histories();
        assertThatThrownBy(()->service.create(id,input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(candidates()).isEqualTo(parent);assertThat(contacts()).isEqualTo(events);assertThat(histories()).isEqualTo(audit);
    }
    @Test void revisionIncludesParentStoredValuesAndContactContentAndTimestampPrecision() {
        String original=revision(id);sql.update("UPDATE tb_hr_cand SET memo_cn='변경' WHERE cand_id=?",id);assertThat(revision(id)).isNotEqualTo(original);
        String parentChanged=revision(id);sql.update("UPDATE tb_hr_cand_cntc SET cntc_memo='자식 변경' WHERE cand_id=? AND cntc_sn=1",id);assertThat(revision(id)).isNotEqualTo(parentChanged);
        String childChanged=revision(id);sql.update("UPDATE tb_hr_cand SET reg_dttm=reg_dttm+INTERVAL '1 microsecond' WHERE cand_id=?",id);assertThat(revision(id)).isNotEqualTo(childChanged);
    }
    @Test void historyFailureRollsBackEventParentAndAllEarlierAuditWrites() {
        var parent=candidates();var events=contacts();var audit=histories();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_contact_parent_audit CHECK(trgt_tbl_nm <> 'TB_HR_CAND') NOT VALID");
        assertThatThrownBy(()->service.create(id,request(id,"DONE","롤백 확인"))).isInstanceOf(RuntimeException.class);
        assertThat(candidates()).isEqualTo(parent);assertThat(contacts()).isEqualTo(events);assertThat(histories()).isEqualTo(audit);
    }
    @Test void concurrentSameRevisionCreatesExactlyOneEvent() throws Exception {
        Create input=request(id,"DONE","동시 연락");var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<String> work=()->{assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();try{service.create(id,input);return "OK";}catch(ApiException e){return e.code();}};
        try {var first=pool.submit(work);var second=pool.submit(work);start.countDown();assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");}
        finally{pool.shutdownNow();}
        assertThat(service.list(id,new Query(1,50)).total()).isEqualTo(26);
    }
}
