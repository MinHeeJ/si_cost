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
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(FinancialMapper.class,()->session.getMapper(FinancialMapper.class));
        context.registerBean(FinancialPreviewMapper.class,()->session.getMapper(FinancialPreviewMapper.class));
        context.register(FinancialPreviewRepository.class,FinancialPreviewService.class);
        context.registerBean(com.sicost.b2.FinancialImportMapper.class,()->session.getMapper(com.sicost.b2.FinancialImportMapper.class));
        context.register(com.sicost.b2.FinancialImportRepository.class);
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(CorporateTaxMapper.class,()->session.getMapper(CorporateTaxMapper.class));context.registerBean(FinancialReadMapper.class,()->session.getMapper(FinancialReadMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,FinancialRepository.class,FinancialService.class,FinancialCommands.class,FinancialWriteService.class,FinancialEditorSources.class,CorporateTaxRepository.class,CorporateTaxViews.class,CorporateTaxAllocationService.class,CorporateTaxService.class,CorporateTaxCommands.class,FinancialReadRepository.class,FinancialReadService.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();service=context.getBean(FinancialService.class);writes=context.getBean(FinancialWriteService.class);
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

    ObjectNode sequenceRow(FinancialDtos.Kind kind,String month,String code,String amount){
        ObjectNode row=json.createObjectNode().put("amount",amount);
        switch(kind){
            case TRANSFERS -> row.put("date",month+"-15").put("classCode",code).put("itemName","이체 "+amount);
            case LOANS -> row.put("date",month+"-15").put("direction",code);
            case ADJUSTMENTS -> row.put("targetMonth","2080-05").put("itemCode",code).put("reason","차액 "+amount);
            default -> throw new IllegalArgumentException(kind.name());
        }
        return row;
    }
    String sequence(FinancialDtos.Kind kind){
        return switch(kind){
            case TRANSFERS -> "sq_tb_fi_xfer";
            case LOANS -> "sq_tb_fi_loan";
            case ADJUSTMENTS -> "sq_tb_co_prev_adj";
            default -> throw new IllegalArgumentException(kind.name());
        };
    }
    void closeAdjustmentTarget(){
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208005','CLOSE','admin')");
    }
    List<String> savedIds(FinancialDtos.Kind kind){
        return sql.queryForList("SELECT "+kind.idColumn+"::text FROM "+kind.table+" ORDER BY "+kind.idColumn,String.class);
    }
    @ParameterizedTest
    @CsvSource({"TRANSFERS,2,EXP","TRANSFERS,3,EXP","TRANSFERS,2,SPCL","TRANSFERS,3,SPCL",
        "LOANS,2,IN","LOANS,3,OUT","ADJUSTMENTS,2,COST","ADJUSTMENTS,3,SGA"})
    void sameKindAdditionsReceiveFreshSequenceIds(FinancialDtos.Kind kind,int count,String code)throws Exception{
        if(kind==FinancialDtos.Kind.ADJUSTMENTS)closeAdjustmentTarget();
        ObjectNode request=body();var additions=request.putObject(kind.collection()).putArray("add");
        for(int i=1;i<=count;i++)additions.add(sequenceRow(kind,"2080-07",code,Integer.toString(i)));
        mvc.perform(put("/api/financial-items").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        List<String> expected=java.util.stream.IntStream.rangeClosed(1,count).mapToObj(Integer::toString).toList();
        assertThat(savedIds(kind)).containsExactlyElementsOf(expected);
        assertThat(sql.queryForObject("SELECT last_value FROM "+sequence(kind),Integer.class)).isEqualTo(count);
        JsonNode editor=json.valueToTree(service.getFinancialItems("2080-07"));
        assertThat(editor.path(kind.collection())).hasSize(count);
        for(int i=0;i<count;i++){
            assertThat(editor.path(kind.collection()).get(i).path(kind.id).isTextual()).isTrue();
            assertThat(editor.path(kind.collection()).get(i).path(kind.id).asText()).isEqualTo(expected.get(i));
            assertThat(editor.path(kind.collection()).get(i).path("amount").asText()).isEqualTo(expected.get(i));
        }
        assertThat(history().stream().filter(h->h.get("trgt_tbl_nm").equals(kind.table))
            .map(h->h.get("trgt_key")).distinct().toList()).containsExactlyElementsOf(expected);
    }
    @Test void mixedTransferClassesShareOneSequence()throws Exception{
        ObjectNode request=body();var additions=request.putObject("transfers").putArray("add");
        additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,"2080-07","EXP","10"));
        additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,"2080-07","SPCL","20"));
        mvc.perform(put("/api/financial-items").contentType("application/json").content(request.toString())).andExpect(status().isOk());
        assertThat(savedIds(FinancialDtos.Kind.TRANSFERS)).containsExactly("1","2");
        assertThat(sql.queryForList("SELECT xfer_cls_cd FROM tb_fi_xfer ORDER BY xfer_sn",String.class)).containsExactly("EXP","SPCL");
    }
    @Test void mixedKindsEachUseTheirOwnSequence(){
        closeAdjustmentTarget();ObjectNode request=body();request.putObject("items").putArray("add").add(item());
        for(var kind:List.of(FinancialDtos.Kind.ADJUSTMENTS,FinancialDtos.Kind.TRANSFERS,FinancialDtos.Kind.LOANS)){
            String code=kind==FinancialDtos.Kind.ADJUSTMENTS?"COST":kind==FinancialDtos.Kind.TRANSFERS?"EXP":"IN";
            var additions=request.putObject(kind.collection()).putArray("add");
            additions.add(sequenceRow(kind,"2080-07",code,"10")).add(sequenceRow(kind,"2080-07",code,"20"));
        }
        assertThat(save(request).changed()).isTrue();assertThat(items()).hasSize(1);
        for(var kind:List.of(FinancialDtos.Kind.ADJUSTMENTS,FinancialDtos.Kind.TRANSFERS,FinancialDtos.Kind.LOANS)){
            assertThat(savedIds(kind)).containsExactly("1","2");
            assertThat(sql.queryForObject("SELECT last_value FROM "+sequence(kind),Integer.class)).isEqualTo(2);
        }
    }
    @Test void secondRowFailureRollsBackBusinessAndHistoryButSequenceIdsAreNotReused(){
        sql.execute("ALTER TABLE tb_fi_xfer ADD CONSTRAINT reject_second_transfer CHECK (amt <> 13)");
        ObjectNode request=body();request.putObject("items").putArray("add").add(item());
        var additions=request.putObject("transfers").putArray("add");
        additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,"2080-07","EXP","10"));
        additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,"2080-07","SPCL","13"));
        assertThatThrownBy(()->save(request)).hasStackTraceContaining("reject_second_transfer");
        assertThat(items()).isEmpty();assertThat(savedIds(FinancialDtos.Kind.TRANSFERS)).isEmpty();assertThat(history()).isEmpty();
        assertThat(sql.queryForObject("SELECT last_value FROM sq_tb_fi_xfer",Integer.class)).isEqualTo(2);
        ((ObjectNode)additions.get(1)).put("amount","20");
        assertThat(save(request).changed()).isTrue();
        assertThat(savedIds(FinancialDtos.Kind.TRANSFERS)).containsExactly("3","4");assertThat(items()).hasSize(1);
    }
    @Test void concurrentRequestsInDifferentMonthsAllocateDistinctIds()throws Exception{
        ObjectNode july=body();ObjectNode june=json.createObjectNode().put("month","2080-06")
            .put("expectedRevision",service.getFinancialItems("2080-06").get("revision").toString());
        for(ObjectNode request:List.of(june,july)){
            String month=request.path("month").asText();var additions=request.putObject("transfers").putArray("add");
            additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,month,"EXP","10"));
            additions.add(sequenceRow(FinancialDtos.Kind.TRANSFERS,month,"SPCL","20"));
        }
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try{
            List<Future<FinancialDtos.Commit>> results=new ArrayList<>();
            for(ObjectNode request:List.of(june,july))results.add(pool.submit(()->{
                ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");return save(request);
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            for(var result:results)assertThat(result.get(20,TimeUnit.SECONDS).changed()).isTrue();
        }finally{start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        assertThat(savedIds(FinancialDtos.Kind.TRANSFERS)).containsExactly("1","2","3","4");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_xfer WHERE xfer_dt >= '2080-06-01' AND xfer_dt < '2080-07-01'",Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_xfer WHERE xfer_dt >= '2080-07-01' AND xfer_dt < '2080-08-01'",Integer.class)).isEqualTo(2);
        assertThat(history().stream().filter(h->h.get("trgt_tbl_nm").equals("TB_FI_XFER"))
            .map(h->h.get("trgt_key")).distinct().toList()).hasSize(4);
    }
    @ParameterizedTest
    @EnumSource(value=FinancialDtos.Kind.class,names={"ADJUSTMENTS","TRANSFERS","LOANS"})
    void singleAdditionPreservesExistingSequenceAndIdFormat(FinancialDtos.Kind kind){
        if(kind==FinancialDtos.Kind.ADJUSTMENTS)closeAdjustmentTarget();
        sql.execute("SELECT setval('"+sequence(kind)+"',42,true)");
        ObjectNode request=body();String code=kind==FinancialDtos.Kind.ADJUSTMENTS?"COST":kind==FinancialDtos.Kind.TRANSFERS?"EXP":"IN";
        request.putObject(kind.collection()).putArray("add").add(sequenceRow(kind,"2080-07",code,"10"));
        assertThat(save(request).changed()).isTrue();assertThat(savedIds(kind)).containsExactly("43");
        JsonNode id=json.valueToTree(service.getFinancialItems("2080-07")).path(kind.collection()).get(0).path(kind.id);
        assertThat(id.isTextual()).isTrue();assertThat(id.asText()).isEqualTo("43");
        assertThat(sql.queryForObject("SELECT last_value FROM "+sequence(kind),Integer.class)).isEqualTo(43);
    }
}
