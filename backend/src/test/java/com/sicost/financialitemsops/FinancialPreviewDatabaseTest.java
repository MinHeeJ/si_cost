package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.context.*;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.financialitems.FinancialMapper;
import com.sicost.financialitems.FinancialRepository;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
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
class FinancialPreviewDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;FinancialPreviewService service;FinancialPreviewRepository repository;FinancialReadService reads;MockMvc mvc;ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(FinancialPreviewMapper.class,()->session.getMapper(FinancialPreviewMapper.class));context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(FinancialReadMapper.class,()->session.getMapper(FinancialReadMapper.class));
        context.registerBean(FinancialMapper.class,()->session.getMapper(FinancialMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(FinancialRepository.class,ChangeHistoryWriter.class);
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,FinancialPreviewRepository.class,FinancialPreviewService.class,FinancialReadRepository.class,FinancialReadService.class);context.refresh();
        service=context.getBean(FinancialPreviewService.class);repository=context.getBean(FinancialPreviewRepository.class);reads=context.getBean(FinancialReadService.class);
        mvc=MockMvcBuilders.standaloneSetup(new FinancialPreviewController(service)).setControllerAdvice(new ApiExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();}
    ObjectNode body(String action){return json.createObjectNode().put("month","2080-07").put("expectedRevision",service.revision(YearMonth.of(2080,7))).put("action",action);}
    void item(String month,int id,String name,String amount){sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,reg_user_id) VALUES (?,?,'SGA',?,?,0,'admin')",month,id,name,new java.math.BigDecimal(amount));}
    Map<String,Object> state(){Map<String,Object> result=new LinkedHashMap<>();for(String table:List.of("tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_fi_card","tb_fi_card_mon","tb_fi_card_pay","tb_fi_xfer","tb_fi_loan","tb_co_prev_adj","tb_sy_chg_hist","tb_sy_upLd","tb_sy_upLd_err","tb_co_mon_pl","tb_cl_close"))result.put(table,sql.queryForList("SELECT to_jsonb(t)::text AS value FROM "+table+" t ORDER BY to_jsonb(t)::text"));for(String sequence:List.of("sq_tb_fi_xfer","sq_tb_fi_loan","sq_tb_co_prev_adj","sq_tb_sy_chg_hist","sq_tb_sy_upld"))result.put(sequence,sql.queryForMap("SELECT last_value,is_called FROM "+sequence));return result;}
    @Test void realYearCountsExcludeTaxCardsAndDraftsAndNeverChangeAnyRows()throws Exception{
        item("208001",1,"저장 재무","1");item("208001",2,"저장 재무","2");
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208001','CLOSE','admin')");
        sql.update("INSERT INTO tb_fi_card_mon(pay_ym,card_sum_amt,cash_amt,reg_user_id) VALUES ('208002',1,1,'admin')");
        sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES ('2080',18400000,'admin')");
        var before=state();mvc.perform(get("/api/financial-items/year-status").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.months[0].itemCount").value(2)).andExpect(jsonPath("$.data.months[0].closeStatus").value("CLOSE")).andExpect(jsonPath("$.data.months[1].itemCount").value(0)).andExpect(jsonPath("$.data.months[7].itemCount").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.months[7].selectable").value(false));assertThat(state()).isEqualTo(before);
    }
    @Test void realPreviewAndStoredReadHaveTheSameCalculationsWithoutPreviewSideEffects(){
        item("208007",1,"기존","7");sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES ('2080',18400000,'admin')");
        ObjectNode b=body("CALCULATE");b.putObject("items").putArray("add").addObject().put("finClass","SGA").put("itemName","추가").put("amount","100").put("vatAmount","20");b.putObject("transfers").putArray("add").addObject().put("date","2080-07-01").put("classCode","EXP").put("itemName","경비").put("amount","9");
        var before=state();var preview=service.createFinancialPreview(FinancialPreviewInput.preview(b));assertThat(state()).isEqualTo(before);
        sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,reg_user_id) VALUES ('208007',2,'SGA','추가',100,20,'admin')");sql.update("INSERT INTO tb_fi_xfer(xfer_dt,xfer_cls_cd,item_nm,amt,reg_user_id) VALUES ('2080-07-01','EXP','경비',9,'admin')");
        var actual=reads.getFinancialContributions(FinancialReadInput.period("2080-07"));assertThat(preview.contributions()).containsEntry("sgaAmount",actual.sgaAmount()).containsEntry("sgaVatAmount",actual.sgaVatAmount()).containsEntry("cashFinancialExpense",actual.cashFinancialExpense()).containsEntry("transferTotal",actual.transferTotal()).containsEntry("corporateTaxAllocation",actual.corporateTaxAllocation());
    }
    @Test void previousCopiesAllSameKeyRowsWithoutPersistingOrConsumingSequences(){item("208006",1,"동일키","1");item("208006",2,"동일키","2");var before=state();var result=service.createFinancialPreview(FinancialPreviewInput.preview(body("LOAD_PREVIOUS")));assertThat(result.copiedCount()).isEqualTo(2);assertThat(result.addedDrafts()).extracting(FinancialPreviewDtos.Draft::sourceItemSn).containsExactly("1","2");assertThat(state()).isEqualTo(before);}
    @Test void spoofedActorAndInvalidAmountDoNotChangeBusinessOrAudit()throws Exception{var before=state();mvc.perform(post("/api/financial-items/preview").contentType("application/json").content(body("CALCULATE").put("REG_USER_ID","forged").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));assertThat(state()).isEqualTo(before);}
    @Test void completeRevisionIncludesAuditTimesAndAllSavedTabRows(){item("208007",1,"기존","1");String before=body("CALCULATE").path("expectedRevision").asText();sql.update("UPDATE tb_fi_item SET mod_dttm='2080-07-01 00:00:00.123456' WHERE fin_ym='208007' AND item_sn=1");String after=body("CALCULATE").path("expectedRevision").asText();assertThat(after).isNotEqualTo(before);sql.update("INSERT INTO tb_fi_loan(loan_dt,io_cd,amt,reg_user_id) VALUES ('2080-07-01','IN',1,'admin')");assertThat(body("CALCULATE").path("expectedRevision").asText()).isNotEqualTo(after);}
}
