package com.sicost.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.clients.ClientDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ClientDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; ClientService service; ObjectMapper json;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(AggregateLockMapper.class);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ClientMapper.class,()->session.getMapper(ClientMapper.class));
        context.registerBean(com.sicost.clientsops.ClientActionsMapper.class,()->session.getMapper(com.sicost.clientsops.ClientActionsMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.registerBean(AccountKeyMapper.class,()->session.getMapper(AccountKeyMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AccountCipher.class,ClientRepository.class,ClientService.class);
        context.register(com.sicost.clientsops.ClientActionsRepository.class,com.sicost.clientsops.ClientActionsService.class,
                com.sicost.clientsops.ClientStatusService.class,com.sicost.clientsops.ClientBulkStatusService.class);
        context.refresh();service=context.getBean(ClientService.class);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Save request(Map<String,Object> body,boolean edit){return ClientInput.parse(json.valueToTree(body),edit);}
    Map<String,Object> customer(String name,String number){return new HashMap<>(Map.of("type","CUST","name",name,"businessNo",number,"taxEmail","tax@example.test"));}
    Map<String,Object> vendor(String name,String number){var body=customer(name,number);body.put("type","VNDR");body.put("bankCode",sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='BANK' AND use_yn='Y' AND del_yn='N' ORDER BY code LIMIT 1",String.class));body.put("accountNumber","001-2345678");body.put("depositor","예금주");return body;}
    Map<String,Object> edit(Client row){var body=new HashMap<String,Object>();body.put("type",row.type());body.put("name",row.name());body.put("taxEmail",row.taxEmail());body.put("accountAction",row.type().equals("CUST")?"CLEAR":"KEEP");body.put("expectedRevision",row.revision());return body;}
    long histories(String id){return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_BS_CLNT' AND trgt_key=?",Long.class,id);}
    @Test void seededAdminCanCreateAndReadNormalizedCustomerWithoutOtherSourceWrites() {
        var developers=sql.queryForList("SELECT * FROM tb_hr_dev");var projects=sql.queryForList("SELECT * FROM tb_pj_proj");var assignments=sql.queryForList("SELECT * FROM tb_pj_asgn");
        Result created=service.create(request(customer("신규거래처","1234567890"),false));Client detail=service.get(created.id());
        assertThat(detail.id()).matches("C-[0-9]{3,18}");assertThat(detail.businessNo()).isEqualTo("123-45-67890");assertThat(detail.useYn()).isEqualTo("Y");assertThat(detail.revision()).isEqualTo(created.client().revision());assertThat(histories(created.id())).isEqualTo(1);
        assertThat(sql.queryForList("SELECT * FROM tb_hr_dev")).isEqualTo(developers);assertThat(sql.queryForList("SELECT * FROM tb_pj_proj")).isEqualTo(projects);assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn")).isEqualTo(assignments);
    }
    @Test void filteredCountUsesSameTypeStatusAndBusinessNumberPredicatesAsRows() {
        Client customer=service.create(request(customer("검색고객","1234567890"),false)).client();
        service.create(request(vendor("검색협력","1234567891"),false));var both=vendor("검색겸업","1234567892");both.put("type","BOTH");service.create(request(both,false));
        sql.update("UPDATE tb_bs_clnt SET use_yn='N' WHERE clnt_id=?",customer.id());
        Page all=service.list(ClientInput.query(Map.of("type","CUST","q","123-45")));assertThat(all.total()).isEqualTo(2);assertThat(all.items()).hasSize(2);
        Page active=service.list(ClientInput.query(Map.of("type","CUST","q","123-45","useYn","Y")));assertThat(active.total()).isEqualTo(1);assertThat(active.items()).extracting(Client::type).containsExactly("BOTH");
        assertThat(service.list(ClientInput.query(Map.of("type","CUST","page","999"))).items()).isEmpty();
    }
    @Test void detailAndSelectionRevisionsCanBeUsedAcrossEditAndStatusOperations() {
        Client row=service.create(request(customer("연결검증","1234567890"),false)).client();
        var reads=context.getBean(com.sicost.clientsops.ClientActionsService.class);
        var statuses=context.getBean(com.sicost.clientsops.ClientBulkStatusService.class);
        var query=new org.springframework.util.LinkedMultiValueMap<String,String>();query.add("q","연결검증");
        var selected=reads.selection(query).items().get(0);
        assertThat(selected.revision()).isEqualTo(row.revision());
        var stopped=statuses.save(new com.sicost.clientsops.ClientActionsDtos.BulkRequest("N",List.of(
                new com.sicost.clientsops.ClientActionsDtos.Target(row.id(),row.revision()))),"integration-revision");
        assertThat(stopped.applied()).isEqualTo(1);
        var body=edit(service.get(row.id()));body.put("name","연결수정");
        body.put("expectedRevision",reads.selection(query).items().get(0).revision());
        assertThat(service.update(row.id(),request(body,true)).changed()).isTrue();
        assertThat(service.get(row.id()).useYn()).isEqualTo("N");
    }
    @Test void selectAllUsesTheSameSearchAndDisplayedTypeOrderAsTheList() {
        service.create(request(customer("목록고객","1234567890"),false));
        service.create(request(vendor("목록협력","1234567891"),false));
        var both=vendor("목록겸업","1234567892");both.put("type","BOTH");service.create(request(both,false));
        var reads=context.getBean(com.sicost.clientsops.ClientActionsService.class);
        for(String direction:List.of("ASC","DESC")) {
            var filters=Map.of("q","-","sort","type","direction",direction);
            var query=new org.springframework.util.LinkedMultiValueMap<String,String>();filters.forEach(query::add);
            assertThat(reads.selection(query).items()).extracting(com.sicost.clientsops.ClientActionsDtos.SelectionItem::id)
                    .containsExactlyElementsOf(service.list(ClientInput.query(filters)).items().stream().map(Client::id).toList());
        }
    }
    @Test void encryptedAccountKeepAndIdenticalReplacementPreserveCiphertextAndNoOpPrecision() {
        Client row=service.create(request(vendor("협력사","1234567890"),false)).client();
        String cipher=sql.queryForObject("SELECT acct_no_enc FROM tb_bs_clnt WHERE clnt_id=?",String.class,row.id());assertThat(cipher).startsWith("v1.").doesNotContain("001-2345678");assertThat(row.account()).contains("****5678").doesNotContain("001-2345678");
        var body=edit(row);Result same=service.update(row.id(),request(body,true));assertThat(same.changed()).isFalse();assertThat(same.client().revision()).isEqualTo(row.revision());assertThat(histories(row.id())).isEqualTo(1);
        body.put("accountAction","REPLACE");body.put("bankCode",row.bankCode());body.put("accountNumber","001-2345678");body.put("depositor",row.depositor());assertThat(service.update(row.id(),request(body,true)).changed()).isFalse();
        assertThat(sql.queryForObject("SELECT acct_no_enc FROM tb_bs_clnt WHERE clnt_id=?",String.class,row.id())).isEqualTo(cipher);assertThat(histories(row.id())).isEqualTo(1);
    }
    @Test void clearKeepsClientAndClearsOnlyAccountTripleWithMaskedHistory() {
        Client row=service.create(request(vendor("협력사","1234567890"),false)).client();var body=edit(row);body.put("type","CUST");body.put("taxEmail","tax@example.test");body.put("accountAction","CLEAR");
        Client changed=service.update(row.id(),request(body,true)).client();assertThat(changed.id()).isEqualTo(row.id());assertThat(changed.businessNo()).isEqualTo(row.businessNo());assertThat(changed.bankCode()).isNull();assertThat(changed.account()).isNull();assertThat(changed.depositor()).isNull();
        assertThat(sql.queryForList("SELECT bfr_val,aft_val FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_item_nm='ACCOUNT'",row.id())).singleElement().satisfies(value->assertThat(value.get("bfr_val")).isEqualTo("****5678"));
    }
    @Test void failedHistoryRollsBackCreateAndUpdate() {
        Client row=service.create(request(customer("보존","1234567890"),false)).client();var before=sql.queryForMap("SELECT * FROM tb_bs_clnt WHERE clnt_id=?",row.id());
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_client_history CHECK(trgt_tbl_nm <> 'TB_BS_CLNT') NOT VALID");
        assertThatThrownBy(()->service.create(request(customer("실패","1234567891"),false))).isInstanceOf(RuntimeException.class);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_bs_clnt WHERE biz_reg_no='123-45-67891'",Long.class)).isZero();
        var body=edit(row);body.put("name","실패수정");assertThatThrownBy(()->service.update(row.id(),request(body,true))).isInstanceOf(RuntimeException.class);
        assertThat(sql.queryForMap("SELECT * FROM tb_bs_clnt WHERE clnt_id=?",row.id())).isEqualTo(before);assertThat(histories(row.id())).isEqualTo(1);
    }
    @Test void stoppedBusinessNumberStillConflictsAndSameNameWarns() {
        Client row=service.create(request(customer("동일상호","1234567890"),false)).client();sql.update("UPDATE tb_bs_clnt SET use_yn='N' WHERE clnt_id=?",row.id());
        assertThatThrownBy(()->service.create(request(customer("다른상호","123-45-67890"),false))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("DUPLICATE_BUSINESS_NO"));
        assertThat(service.create(request(customer("동일상호","1234567891"),false)).warnings()).hasSize(1);
    }
    @Test void concurrentSameRevisionWritesOnce() throws Exception {
        Client row=service.create(request(customer("동시수정","1234567890"),false)).client();var body=edit(row);body.put("name","변경");Save request=request(body,true);CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->writeAfter(start,row.id(),request));var b=CompletableFuture.supplyAsync(()->writeAfter(start,row.id(),request));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(histories(row.id())).isEqualTo(2);
    }
    @Test void bothCountsUnionAndDistinctWithInclusiveDatesAndSourceStatusFilters() {
        var body=vendor("겸업거래처","1234567890");body.put("type","BOTH");Client row=service.create(request(body,false)).client();
        String part=sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE hq_yn='Y' ORDER BY part_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,blng_clnt_id,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES('D-901','M-901','시험개발자','FRE',?,'DEV','DRCT',?,'WORK','admin')",row.id(),part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,cust_clnt_id,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('P-901','시험프로젝트',?,'SI','ING',DATE '2026-01-01',DATE '2026-12-31',0,0,0,0,'admin')",row.id());
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES('A-901','P-901','D-901','DEV',DATE '2026-06-15',DATE '2026-06-15',1,'NORM',0,'ACT','admin')");
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES('A-902','P-901','D-901','DEV',DATE '2026-06-01',DATE '2026-06-15',1,'NORM',0,'ACT','admin')");
        Client counted=service.get(row.id());assertThat(counted.projectCount()).isEqualTo(1);assertThat(counted.personCount()).isEqualTo(1);assertThat(counted.basisDate()).isEqualTo(LocalDate.of(2026,6,15));
        sql.update("UPDATE tb_pj_asgn SET input_end_dt=DATE '2026-06-14'");assertThat(service.get(row.id()).personCount()).isZero();
        sql.update("UPDATE tb_pj_asgn SET input_end_dt=DATE '2026-06-16',asgn_stat_cd='END'");assertThat(service.get(row.id()).projectCount()).isZero();
        sql.update("UPDATE tb_pj_asgn SET asgn_stat_cd='ACT'");sql.update("UPDATE tb_pj_proj SET proj_stat_cd='END'");assertThat(service.get(row.id()).projectCount()).isZero();
    }
    String writeAfter(CountDownLatch start,String id,Save input){try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.update(id,input);return "OK";}catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
