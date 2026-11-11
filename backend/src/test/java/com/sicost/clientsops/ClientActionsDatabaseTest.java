package com.sicost.clientsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.security.*;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.clientsops.ClientActionsDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ClientActionsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ClientActionsService reads;
    ClientBulkStatusService bulk;
    ClientActionsMapper mapper;
    ClientAccountService accounts;
    RevisionService revisions;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ClientActionsMapper.class,()->session.getMapper(ClientActionsMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.registerBean(AccountKeyMapper.class,()->session.getMapper(AccountKeyMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,RevisionService.class,
            AccountCipher.class,CommitInvalidation.class,ClientActionsRepository.class,ClientActionsService.class,ClientAccountService.class,
            ClientStatusService.class,ClientBulkStatusService.class);
        context.refresh();reads=context.getBean(ClientActionsService.class);bulk=context.getBean(ClientBulkStatusService.class);
        mapper=context.getBean(ClientActionsMapper.class);accounts=context.getBean(ClientAccountService.class);revisions=context.getBean(RevisionService.class);
    }
    @AfterEach void close() { if(context!=null) context.close(); }
    String client(int number,String type,String use) {
        String id="C-"+number;
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,use_yn,reg_user_id) VALUES (?,?,?,? ,?,'admin')",
            id,type,"검증 "+number,String.format("%03d-90-%05d",number%1000,number%100000),use);
        return id;
    }
    Target target(String id) { return new Target(id,revisions.revision(mapper.client(id,false))); }
    BulkResult change(String use,Target... items) { return bulk.save(new BulkRequest(use,List.of(items)),"database-request"); }
    String status(String id) { return sql.queryForObject("SELECT use_yn FROM tb_bs_clnt WHERE clnt_id=?",String.class,id); }
    long histories(String id) { return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_BS_CLNT' AND trgt_key=?",Long.class,id); }
    void project(String id,String client,String state,String name) {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,cust_clnt_id,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,?,'TM',?,'2026-01-01','2026-12-31',0,0,0,'admin')",id,name,client,state);
    }
    void developer(String id,String client,String state) {
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('PART-900901','검증 파트','N','Y','admin') ON CONFLICT DO NOTHING");
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,blng_clnt_id,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,reg_user_id) VALUES (?,?,?,'OS',?,'BE','DRCT','PART-900901',?,'N','admin')",id,id,id,client,state);
    }
    void assignment(String id,String project,String developer,String state,String begin,String end) {
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'BE',?::date,?::date,1,'NEW',0,?,'admin')",id,project,developer,begin,end,state);
    }
    @Test void seededAdminStopsAndResumesWithAppendOnlyHistoryAndNoDeletion() {
        String id=client(900901,"CUST","Y");
        assertThat(change("N",target(id)).applied()).isEqualTo(1);assertThat(status(id)).isEqualTo("N");
        assertThat(change("Y",target(id)).applied()).isEqualTo(1);assertThat(status(id)).isEqualTo("Y");
        assertThat(sql.queryForList("SELECT chg_type_cd FROM tb_sy_chg_hist WHERE trgt_key=? ORDER BY hist_id",String.class,id)).containsExactly("STOP","RESUME");
    }
    @Test void mixedTransactionsKeepSuccessAndRollBackHistoryFailureIndependently() {
        String good=client(900901,"CUST","Y"),failed=client(900902,"VNDR","Y");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_one_client CHECK(trgt_key <> 'C-900902')");
        var result=change("N",target(good),target(failed));assertThat(result.applied()).isEqualTo(1);assertThat(result.rejected()).isEqualTo(1);
        assertThat(status(good)).isEqualTo("N");assertThat(histories(good)).isEqualTo(1);
        assertThat(status(failed)).isEqualTo("Y");assertThat(histories(failed)).isZero();
        sql.execute("ALTER TABLE tb_sy_chg_hist DROP CONSTRAINT reject_one_client");
        assertThat(change("N",target(failed)).applied()).isEqualTo(1);
    }
    @Test void staleEvenSameStateAndNoChangeHaveNoTimestampOrHistoryChanges() {
        String id=client(900901,"CUST","N");var before=sql.queryForMap("SELECT * FROM tb_bs_clnt WHERE clnt_id=?",id);
        assertThat(change("N",new Target(id,"stale")).items().get(0).error().code()).isEqualTo("STALE_WRITE");
        assertThat(change("N",target(id)).noChange()).isEqualTo(1);assertThat(histories(id)).isZero();
        assertThat(sql.queryForMap("SELECT * FROM tb_bs_clnt WHERE clnt_id=?",id)).isEqualTo(before);
    }
    @Test void projectWithNoAssignmentsAndRetiredDeveloperBothBlockStop() {
        String customer=client(900901,"CUST","Y"),vendor=client(900902,"VNDR","Y");
        project("P-900901",customer,"ING","프로젝트");developer("D-900901",vendor,"RETIRE");
        var result=change("N",target(customer),target(vendor));assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.items()).allMatch(item->item.error().code().equals("REFERENCE_IN_USE"));
        assertThat(status(customer)).isEqualTo("Y");assertThat(status(vendor)).isEqualTo("Y");assertThat(histories(customer)+histories(vendor)).isZero();
    }
    @Test void all123SelectionMatchesFiltersAndExcludesDisplayValuesFromRevision() {
        for(int i=900901;i<901024;i++) client(i,i%2==0?"BOTH":"CUST",i%3==0?"N":"Y");
        var query=new LinkedMultiValueMap<String,String>();query.add("type","CUST");
        var selection=reads.selection(query);assertThat(selection.total()).isEqualTo(123);assertThat(selection.items()).hasSize(123);
        assertThat(selection.items()).allMatch(item->item.revision().equals(revisions.revision(mapper.client(item.id(),false))));
        query.add("useYn","Y");var filtered=reads.selection(query);assertThat(filtered.total()).isEqualTo(82);
        query.add("q","901-90");assertThat(reads.selection(query).items()).allMatch(item->mapper.client(item.id(),false).businessNo().startsWith("901-90"));
    }
    @Test void optionsIncludeBothOnlyActiveAndLiteralSubstringNames() {
        String c=client(900901,"CUST","Y"),both=client(900902,"BOTH","Y");client(900903,"VNDR","Y");client(900904,"BOTH","N");
        assertThat(reads.options(new OptionQuery("CUSTOMER","검증")).items()).extracting(Option::id).containsExactly(c,both);
        assertThat(reads.options(new OptionQuery("VENDOR","%" )).items()).isEmpty();
    }
    @Test void assignmentsUseBothUnionWithoutDuplicationAndExactActiveInclusivePredicates() {
        String id=client(900901,"BOTH","Y");project("P-900901",id,"ING","나 프로젝트");project("P-900902",id,"PRE","가 프로젝트");project("P-900903",id,"END","종료");
        developer("D-900901",id,"RETIRE");
        assignment("A-900901","P-900901","D-900901","ACT","2026-06-16","2026-06-16");
        assignment("A-900902","P-900901","D-900901","ACT","2026-06-01","2026-06-30");
        assignment("A-900903","P-900902","D-900901","ACT","2026-06-01","2026-06-30");
        assignment("A-900904","P-900903","D-900901","ACT","2026-06-01","2026-06-30");
        assignment("A-900905","P-900901","D-900901","PLAN","2026-06-01","2026-06-30");
        assignment("A-900906","P-900901","D-900901","ACT","2026-06-17","2026-06-30");
        var result=reads.assignments(id,1,50);assertThat(result.total()).isEqualTo(3);assertThat(result.basisDate()).isEqualTo(LocalDate.of(2026,6,16));
        assertThat(result.items()).extracting(Assignment::assignmentId).containsExactly("A-900903","A-900901","A-900902");
        assertThat(reads.assignments(id,2,50).items()).isEmpty();
        assertThat(result.items().stream().map(Assignment::projectId).distinct().count()).isEqualTo(2);
        assertThat(result.items().stream().map(Assignment::managementNo).distinct().count()).isEqualTo(1);
    }
    @Test void accessLogCommitsBeforeAccountReturnsAndFailureNeverReturnsAnAccount() {
        String id=client(900901,"VNDR","Y");String number="001-23-45678";
        String encrypted=context.getBean(AccountCipher.class).encrypt(number);
        sql.update("UPDATE tb_bs_clnt SET acct_no_enc=?,bank_cd='004',dpstr_nm='검증 예금주' WHERE clnt_id=?",encrypted,id);
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        var response=accounts.view(id,request);assertThat(response.accountNumber()).isEqualTo(number);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE act_type_cd='ACCT_VIEW' AND trgt_desc=? AND user_id='admin' AND reg_user_id='admin'",Long.class,id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT trgt_desc FROM tb_sy_accs_log WHERE trgt_desc=?",String.class,id)).isEqualTo(id);
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_account_view CHECK(act_type_cd <> 'ACCT_VIEW') NOT VALID");
        assertThatThrownBy(()->accounts.view(id,request)).isInstanceOf(RuntimeException.class);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log",Long.class)).isEqualTo(1);
    }
    @Test void corruptedTagFailsBeforeAccessLogInsert() {
        String id=client(900901,"VNDR","Y");var cipher=context.getBean(AccountCipher.class);
        String stored=cipher.encrypt("001-23-45678");byte[] payload=Base64.getDecoder().decode(stored.substring(3));payload[payload.length-1]^=1;
        sql.update("UPDATE tb_bs_clnt SET acct_no_enc=? WHERE clnt_id=?","v1."+Base64.getEncoder().encodeToString(payload),id);
        assertThatThrownBy(()->accounts.view(id,new MockHttpServletRequest())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ENCRYPTION_CONFIG_ERROR"));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log",Long.class)).isZero();
    }
    @Test void concurrentSameRevisionStatusLocksExactlyOneAppliedAndOneStale() throws Exception {
        String id=client(900901,"CUST","Y");Target target=target(id);CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->after(start,target));var b=CompletableFuture.supplyAsync(()->after(start,target));start.countDown();
        var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        assertThat(results).containsExactlyInAnyOrder("applied","STALE_WRITE");assertThat(histories(id)).isEqualTo(1);
    }
    String after(CountDownLatch start,Target target) {
        try { if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");var item=change("N",target).items().get(0);return item.error()==null?item.result():item.error().code(); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
    }
}
