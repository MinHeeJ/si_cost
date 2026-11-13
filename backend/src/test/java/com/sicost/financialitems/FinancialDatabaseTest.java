package com.sicost.financialitems;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.corporatetaxesops.*;
import com.sicost.financialitemsops.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class FinancialDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;FinancialService service;FinancialWriteService writes;MockMvc mvc;ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(FinancialMapper.class,()->session.getMapper(FinancialMapper.class));
        context.registerBean(FinancialPreviewMapper.class,()->session.getMapper(FinancialPreviewMapper.class));
        context.register(FinancialPreviewRepository.class,FinancialPreviewService.class);
        context.registerBean(com.sicost.b2.FinancialImportMapper.class,()->session.getMapper(com.sicost.b2.FinancialImportMapper.class));
        context.register(com.sicost.b2.FinancialImportRepository.class);
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(CorporateTaxMapper.class,()->session.getMapper(CorporateTaxMapper.class));context.registerBean(FinancialReadMapper.class,()->session.getMapper(FinancialReadMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,FinancialRepository.class,FinancialService.class,FinancialCommands.class,FinancialWriteService.class,FinancialEditorSources.class,CorporateTaxRepository.class,CorporateTaxViews.class,CorporateTaxAllocationService.class,CorporateTaxService.class,CorporateTaxCommands.class,FinancialReadRepository.class,FinancialReadService.class);context.refresh();service=context.getBean(FinancialService.class);writes=context.getBean(FinancialWriteService.class);
        mvc=MockMvcBuilders.standaloneSetup(new FinancialController(service,writes),new FinancialPreviewController(context.getBean(FinancialPreviewService.class))).setControllerAdvice(new ApiExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();}
    ObjectNode body(){return json.createObjectNode().put("month","2080-07").put("expectedRevision",service.getFinancialItems("2080-07").get("revision").toString());}
    ObjectNode item(){return json.createObjectNode().put("finClass","SGA").put("itemName","경비").put("amount","10").put("vatAmount","1");}
    FinancialDtos.Commit save(ObjectNode body){return writes.saveFinancialItems(FinancialInput.save(body));}
    void seedTax(){sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES ('2080',20,'admin')");sql.update("INSERT INTO tb_fi_tax_pay(tax_yr,pay_type_cd,pay_due_dt,pay_amt,reg_user_id) VALUES ('2080','MID','2080-08-31',10,'admin'),('2080','CNFM','2081-03-31',NULL,'admin')");}
    List<Map<String,Object>> history(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm LIKE 'TB_FI_%' OR trgt_tbl_nm='TB_CO_PREV_ADJ' ORDER BY hist_id");}
    List<Map<String,Object>> items(){return sql.queryForList("SELECT * FROM tb_fi_item ORDER BY fin_ym,item_sn");}
    @Test void getReadsAllNineTablesStringIdentifiersSortedAndNeverWrites()throws Exception{
        seedTax();sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,reg_user_id) VALUES ('208007',10,'SGA','경비',10,1,'admin'),('208007',2,'NOPR_IN','수익',0,0,'admin')");
        sql.update("INSERT INTO tb_fi_card(card_id,card_nm,card_no_l4,reg_user_id) VALUES ('K-01','법인카드','0001','admin')");sql.update("INSERT INTO tb_fi_card_mon(pay_ym,card_sum_amt,cash_amt,reg_user_id) VALUES ('208007',3,3,'admin')");sql.update("INSERT INTO tb_fi_card_pay(pay_ym,card_id,pay_amt,reg_user_id) VALUES ('208007','K-01',3,'admin')");
        sql.update("INSERT INTO tb_fi_xfer(xfer_dt,xfer_cls_cd,item_nm,amt,reg_user_id) VALUES ('2080-07-02','EXP','이체',1,'admin'),('2080-07-01','EXP','이체',2,'admin')");sql.update("INSERT INTO tb_fi_loan(loan_dt,io_cd,amt,reg_user_id) VALUES ('2080-07-01','OUT',1,'admin')");sql.update("INSERT INTO tb_co_prev_adj(aply_ym,trgt_ym,adj_item_cd,adj_amt,adj_rsn,reg_user_id) VALUES ('208007','208006','COST',-1,'차액','admin')");
        var before=items();var audits=history();String revision=service.getFinancialItems("2080-07").get("revision").toString();
        mvc.perform(get("/api/financial-items").param("month","2080-07")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].itemSn").value("2")).andExpect(jsonPath("$.data.transfers[0].date").value("2080-07-01")).andExpect(jsonPath("$.data.taxPayments[1].payAmount").isEmpty()).andExpect(jsonPath("$.data.cards[0].last4").value("0001")).andExpect(jsonPath("$.data.revision").value(revision));assertThat(items()).isEqualTo(before);assertThat(history()).isEqualTo(audits);
    }
    @Test void emptyGetDoesNotCreateTaxCardMonthHistoryOrUseSequences(){var audits=history();var sequence=sql.queryForMap("SELECT last_value,is_called FROM sq_tb_fi_loan");service.getFinancialItems("2080-07");assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_corp_tax",Integer.class)).isZero();assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_card_mon",Integer.class)).isZero();assertThat(history()).isEqualTo(audits);assertThat(sql.queryForMap("SELECT last_value,is_called FROM sq_tb_fi_loan")).isEqualTo(sequence);}
    @Test void editorRevisionWorksForPreviewAndSaveAndBecomesStaleAfterCommit()throws Exception{
        seedTax();
        String response=mvc.perform(get("/api/financial-items").param("month","2080-07")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String revision=json.readTree(response).path("data").path("revision").asText();
        ObjectNode request=json.createObjectNode().put("month","2080-07").put("expectedRevision",revision);
        request.putObject("items").putArray("add").add(item());
        var before=items();var audits=history();
        for(String action:List.of("CALCULATE","LOAD_PREVIOUS")){
            request.put("action",action);
            mvc.perform(post("/api/financial-items/preview").contentType("application/json").content(request.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(revision)).andExpect(jsonPath("$.data.contributions.sgaAmount").value("10"));
        }
        assertThat(items()).isEqualTo(before);assertThat(history()).isEqualTo(audits);
        request.remove("action");
        mvc.perform(put("/api/financial-items").contentType("application/json").content(request.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        var saved=items();var savedAudits=history();request.put("action","CALCULATE");
        mvc.perform(post("/api/financial-items/preview").contentType("application/json").content(request.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(items()).isEqualTo(saved);assertThat(history()).isEqualTo(savedAudits);
    }
    @Test void bothTabsCommitOmittedRowsRemainAndNoopPreservesTimesAndHistory(){ObjectNode body=body();body.putObject("items").putArray("add").add(item());body.putObject("loans").putArray("add").addObject().put("date","2080-07-15").put("direction","IN").put("amount","2");assertThat(save(body).changed()).isTrue();var before=items();var audits=history();assertThat(save(body()).changed()).isFalse();assertThat(items()).isEqualTo(before);assertThat(history()).isEqualTo(audits);ObjectNode other=body();other.putObject("items").putArray("add").add(item());save(other);assertThat(items()).hasSize(2);}
    @Test void staleRollbackHasNoAuditOrBusinessChanges(){ObjectNode body=body();body.putObject("items").putArray("add").add(item());save(body);var before=items();var audits=history();assertThatThrownBy(()->save(body)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(items()).isEqualTo(before);assertThat(history()).isEqualTo(audits);}
    @Test void requiredAuditFailureRollsBackBothTabs(){sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_financial_loan CHECK(trgt_tbl_nm <> 'TB_FI_LOAN')");ObjectNode body=body();body.putObject("items").putArray("add").add(item());body.putObject("loans").putArray("add").addObject().put("date","2080-07-15").put("direction","IN").put("amount","2");assertThatThrownBy(()->save(body)).isInstanceOf(RuntimeException.class);assertThat(items()).isEmpty();assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_loan",Integer.class)).isZero();assertThat(history()).isEmpty();}
    @Test void parentCardMonthExistsBeforeCardPaymentAndExplicitManualSameSumStaysY(){ObjectNode body=body(),cards=body.putObject("cards");cards.putArray("add").addObject().put("clientKey","card").put("cardName","카드").put("last4","0001").put("amount","10");cards.putObject("cashOverride").put("mode","MANUAL").put("amount","10");save(body);assertThat(sql.queryForObject("SELECT edit_yn FROM tb_fi_card_mon WHERE pay_ym='208007'",String.class)).isEqualTo("Y");assertThat(sql.queryForObject("SELECT pay_amt FROM tb_fi_card_pay WHERE pay_ym='208007'",String.class)).isEqualTo("10");ObjectNode restore=body();restore.putObject("cards").putObject("cashOverride").put("mode","AUTO");save(restore);assertThat(sql.queryForObject("SELECT edit_yn FROM tb_fi_card_mon",String.class)).isEqualTo("N");}
    @Test void explicitTaxNumberEqualToAutomaticIsNotNullAndOnlyNullRestores(){seedTax();ObjectNode body=body();body.putArray("taxSchedules").addObject().put("year","2080").put("payType","CNFM").put("dueDate","2081-03-31").put("payAmount","10");save(body);assertThat(sql.queryForObject("SELECT pay_amt FROM tb_fi_tax_pay WHERE pay_type_cd='CNFM'",String.class)).isEqualTo("10");ObjectNode restore=body();restore.putArray("taxSchedules").addObject().put("year","2080").put("payType","CNFM").put("dueDate","2081-03-31").putNull("payAmount");save(restore);assertThat(sql.queryForObject("SELECT pay_amt FROM tb_fi_tax_pay WHERE pay_type_cd='CNFM'",String.class)).isNull();}
    @Test void nullClosedTaxSnapshotBlocksAnnualChange(){seedTax();sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208006','CLOSE','admin')");var audits=history();assertThatThrownBy(()->save(body().put("estimatedTax","21"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("PERIOD_READ_ONLY"));assertThat(history()).isEqualTo(audits);assertThat(sql.queryForObject("SELECT est_tax_amt FROM tb_fi_corp_tax WHERE tax_yr='2080'",String.class)).isEqualTo("20");}
    @Test void readDraftIsNotPersistedAndFebruaryClampsLeapDay(){sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,io_dt,rpt_yn,reg_user_id) VALUES ('208001',1,'SGA','반복',1,0,'2080-01-31','Y','admin')");var before=items();JsonNode result=json.valueToTree(service.getFinancialItems("2080-02"));assertThat(result.path("automaticDrafts").get(0).path("ioDate").asText()).isEqualTo("2080-02-29");assertThat(items()).isEqualTo(before);assertThat(history()).isEmpty();}
    @Test void explicitDeleteAuditsBeforeValueAndBoundedMonth(){ObjectNode body=body();body.putObject("items").putArray("add").add(item());save(body);ObjectNode deletion=body();deletion.putObject("items").putArray("deleteIds").add("1");save(deletion);assertThat(items()).isEmpty();assertThat(history()).anySatisfy(h->{assertThat(h.get("chg_type_cd")).isEqualTo("DEL");assertThat(h.get("chg_item_nm")).isEqualTo("AMT");assertThat(h.get("bfr_val")).isEqualTo("10");assertThat(h.get("aft_val")).isNull();});}
}
