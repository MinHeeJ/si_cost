package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.apiops.FinancialReadDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class FinancialReadDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;FinancialReadService service;MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        ObjectMapper json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-12-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(FinancialReadMapper.class,()->session.getMapper(FinancialReadMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,FinancialReadRepository.class,FinancialReadService.class);context.refresh();service=context.getBean(FinancialReadService.class);
        mvc=MockMvcBuilders.standaloneSetup(new FinancialReadController(service)).setControllerAdvice(new ApiExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Period period(String month){return FinancialReadInput.period(month);}
    HistoryPage history(String month,String scope,int page,int size){var q=new LinkedMultiValueMap<String,String>();q.add("month",month);q.add("scope",scope);q.add("page",String.valueOf(page));q.add("size",String.valueOf(size));return service.getFinancialHistory(FinancialReadInput.history(q));}
    void item(String month,int id,String type,String amount,String vat,String date){sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,io_dt,reg_user_id) VALUES (?,?,?,'검증 원천',?,?,CAST(? AS date),'admin')",month,id,type,new java.math.BigDecimal(amount),new java.math.BigDecimal(vat),date);}
    void annual(String year,String amount){sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES (?,?,'admin')",year,new java.math.BigDecimal(amount));}
    void payment(String year,String type,String due,String amount,String paid,String yn){sql.update("INSERT INTO tb_fi_tax_pay(tax_yr,pay_type_cd,pay_due_dt,pay_amt,pay_dt,pay_yn,reg_user_id) VALUES (?,?,CAST(? AS date),?,CAST(? AS date),?,'admin')",year,type,due,amount==null?null:new java.math.BigDecimal(amount),paid,yn);}
    String audit(String table,String key,String type,String field,String before,String after,String time){return sql.queryForObject("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,chg_rsn,reg_user_id,reg_dttm) VALUES (?,?,?,?,?,?,'검증 사유','admin',CAST(? AS timestamp)) RETURNING hist_id::text",String.class,table,key,type,field,before,after,time);}
    @Test void storedSourcesContributeExactDecimalsAndSeparateProfitFromCash()throws Exception{
        item("208007",1,"SGA","999999999999999","1",null);item("208007",2,"SGA","999999999999999","2",null);
        item("208007",3,"NOPR_IN","7","3","2080-07-02");item("208007",4,"NOPR_OUT","11","4",null);
        sql.update("INSERT INTO tb_co_prev_adj(aply_ym,trgt_ym,adj_item_cd,adj_amt,adj_rsn,reg_user_id) VALUES ('208007','208006','COST',-5,'조정','admin')");
        sql.update("INSERT INTO tb_fi_card_mon(pay_ym,card_sum_amt,cash_amt,edit_yn,reg_user_id) VALUES ('208007',90,8,'Y','admin')");
        sql.update("INSERT INTO tb_fi_card(card_id,card_nm,card_no_l4,reg_user_id) VALUES ('K-TEST','검증 카드','0012','admin')");
        sql.update("INSERT INTO tb_fi_card_pay(pay_ym,card_id,pay_amt,reg_user_id) VALUES ('208007','K-TEST',90,'admin')");
        sql.update("INSERT INTO tb_fi_xfer(xfer_dt,xfer_cls_cd,item_nm,amt,reg_user_id) VALUES ('2080-07-01','EXP','경비',13,'admin'),('2080-07-02','SPCL','인건비',17,'admin')");
        sql.update("INSERT INTO tb_fi_loan(loan_dt,io_cd,amt,reg_user_id) VALUES ('2080-07-01','IN',19,'admin'),('2080-07-02','OUT',23,'admin')");
        annual("2080","20000000");payment("2080","MID","2080-07-31","29",null,"N");
        mvc.perform(get("/api/financial-contributions").param("month","2080-07")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sgaAmount").value("1999999999999998")).andExpect(jsonPath("$.data.sgaVatAmount").value("3"))
            .andExpect(jsonPath("$.data.nonOperatingIncome").value("7")).andExpect(jsonPath("$.data.nonOperatingIncomeVat").value("3"))
            .andExpect(jsonPath("$.data.nonOperatingExpense").value("11")).andExpect(jsonPath("$.data.nonOperatingExpenseVat").value("4"))
            .andExpect(jsonPath("$.data.adjustmentProfit").value("-5")).andExpect(jsonPath("$.data.cashFinancialExpense").value("2000000000000016"))
            .andExpect(jsonPath("$.data.cashMiscIncome").value("10")).andExpect(jsonPath("$.data.cashCardExpense").value("8"))
            .andExpect(jsonPath("$.data.cashTransferExpense").value("13")).andExpect(jsonPath("$.data.cashUnpaidLabor").value("17"))
            .andExpect(jsonPath("$.data.transferTotal").value("30")).andExpect(jsonPath("$.data.cashLoanIn").value("19"))
            .andExpect(jsonPath("$.data.cashLoanOut").value("23")).andExpect(jsonPath("$.data.cashLoanNet").value("-4"))
            .andExpect(jsonPath("$.data.cashTaxPayment").value("29"));
    }
    @Test void cashAttributionIncludesOtherFinancialMonthsAndUndatedIncomeIsNotCash(){
        item("208006",1,"SGA","31","4","2080-07-02");item("208007",1,"NOPR_IN","100","10",null);
        item("208007",2,"SGA","80","8","2080-08-01");item("208007",3,"NOPR_IN","7","1","2080-07-02");
        var result=service.getFinancialContributions(period("2080-07"));
        assertThat(result.sgaAmount()).isEqualTo("80");assertThat(result.nonOperatingIncome()).isEqualTo("107");
        assertThat(result.cashFinancialExpense()).isEqualTo("35");assertThat(result.cashMiscIncome()).isEqualTo("8");
    }
    @Test void confirmedPaymentsUseOnlyActualDateAndAmountWhilePendingUsesDisplayedEstimate(){
        annual("2079","100");payment("2079","MID","2079-08-31","30","2079-08-31","Y");
        payment("2079","CNFM","2080-03-31","65","2080-07-02","Y");
        annual("2080","200");payment("2080","MID","2080-07-31",null,null,"N");payment("2080","CNFM","2080-07-31",null,null,"N");
        assertThat(service.getFinancialContributions(period("2080-03")).cashTaxPayment()).isEqualTo("0");
        assertThat(service.getFinancialContributions(period("2080-07")).cashTaxPayment()).isEqualTo("265");
    }
    @Test void unknownAutomaticTaxAmountRemainsNullButExplicitZeroIsKnown(){
        payment("2079","CNFM","2080-07-31",null,null,"N");
        assertThat(service.getFinancialContributions(period("2080-07")).cashTaxPayment()).isNull();
        sql.update("UPDATE tb_fi_tax_pay SET pay_amt=0 WHERE tax_yr='2079' AND pay_type_cd='CNFM'");
        assertThat(service.getFinancialContributions(period("2080-07")).cashTaxPayment()).isEqualTo("0");
    }
    @Test void allocationPreservesClosedCopiesAndAssignsRemainderOnlyToLastOpenMonth(){
        annual("2080","20000000");
        for(int m=1;m<=6;m++){
            String month="2080"+String.format("%02d",m);
            sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES (?,'CLOSE','admin')",month);
            sql.update("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,corp_tax_amt,reg_user_id) VALUES (?,0,0,0,0,?,'admin')",month,m==6?3400000:3000000);
        }
        assertThat(service.getFinancialContributions(period("2080-06")).corporateTaxAllocation()).isEqualTo("3400000");
        assertThat(service.getFinancialContributions(period("2080-07")).corporateTaxAllocation()).isEqualTo("266666");
        assertThat(service.getFinancialContributions(period("2080-12")).corporateTaxAllocation()).isEqualTo("266670");
    }
    @Test void historyScopesExcludeOtherMonthsAndUnrelatedPaidYearsAndKeepDeletedRows()throws Exception{
        String item=audit("TB_FI_ITEM","208007:1","DEL","RMRK","가".repeat(500),null,"2080-07-02 10:00:00");
        String card=audit("TB_FI_CARD","K-TEST","STOP","USE_YN","Y","N","2080-07-02 10:00:00");
        audit("TB_FI_ITEM","208006:1","UPD","AMT","1","2","2080-07-03 10:00:00");
        audit("TB_FI_XFER","9100000001","DEL","XFER_DT","2080-07-03",null,"2080-07-02 10:00:00");
        audit("TB_FI_XFER","9100000001","DEL","AMT","77",null,"2080-07-02 10:00:00");
        payment("2079","CNFM","2080-03-31","30","2080-03-31","Y");payment("2078","CNFM","2079-03-31",null,null,"N");payment("2077","CNFM","2078-03-31","1","2078-03-31","Y");
        audit("TB_FI_TAX_PAY","2079:CNFM","UPD","PAY_DT",null,"2080-03-31","2080-07-01 10:00:00");
        audit("TB_FI_CORP_TAX","2078","UPD","EST_TAX_AMT","1","2","2080-07-01 10:00:00");
        audit("TB_FI_TAX_PAY","2077:CNFM","UPD","PAY_AMT","0","1","2080-07-01 10:00:00");
        assertThat(history("2080-07","MONTH",1,20).items()).extracting(HistoryEntry::targetTable).containsExactly("TB_FI_XFER","TB_FI_XFER","TB_FI_ITEM");
        assertThat(history("2080-07","MONTH",1,20).total()).isEqualTo(3);
        assertThat(history("2080-07","MONTH",2,20).items()).isEmpty();assertThat(history("2080-07","MONTH",2,20).total()).isEqualTo(3);
        assertThat(history("2080-07","TAX",1,20).items()).extracting(HistoryEntry::targetKey).containsExactly("2078","2079:CNFM");
        assertThat(history("2080-07","CARDS",1,20).items()).extracting(HistoryEntry::historyId).containsExactly(card);
        mvc.perform(get("/api/financial-history").param("month","2080-07").param("scope","MONTH"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[2].historyId").value(item)).andExpect(jsonPath("$.data.items[2].beforeValue").value("가".repeat(500)));
    }
    @Test void allMonthKeysAndCardStopResumeAreDistinguishable(){
        audit("TB_FI_CARD_PAY","208007:K-01","UPD","PAY_AMT","1","2","2080-07-03 00:00:00");
        audit("TB_FI_CARD_MON","208007","UPD","CASH_AMT","1","0","2080-07-03 00:00:00");
        audit("TB_CO_PREV_ADJ","9100000002","DEL","APLY_YM","208007",null,"2080-07-03 00:00:00");
        audit("TB_FI_LOAN","9100000003","DEL","LOAN_DT","2080-07-03",null,"2080-07-03 00:00:00");
        assertThat(history("2080-07","MONTH",1,20).items()).extracting(HistoryEntry::targetKey).containsExactly("9100000003","9100000002","208007","208007:K-01");
        audit("TB_FI_CARD","K-01","STOP","USE_YN","Y","N","2080-07-03 00:00:00");audit("TB_FI_CARD","K-01","RESUME","USE_YN","N","Y","2080-07-03 00:00:00");
        assertThat(history("2080-07","CARDS",1,20).items()).extracting(HistoryEntry::changeType).containsExactly("RESUME","STOP");
    }
    Map<String,List<Map<String,Object>>> snapshot(){
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_co_prev_adj","tb_fi_card","tb_fi_card_mon","tb_fi_card_pay","tb_fi_xfer","tb_fi_loan","tb_co_mon_pl","tb_cl_close","tb_sy_chg_hist","tb_sy_accs_log"))result.put(table,sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        result.put("sequence",sql.queryForList("SELECT last_value,is_called FROM sq_tb_sy_chg_hist"));return result;
    }
    @Test void bothReadsAndInvalidOrForgedQueriesLeaveEverySourceSnapshotAndAuditUnchanged()throws Exception{
        item("208007",1,"SGA","1","2",null);audit("TB_FI_ITEM","208007:1","NEW","AMT",null,"1","2080-07-01 00:00:00");var before=snapshot();
        for(String path:List.of("/api/financial-contributions","/api/financial-history")){
            var request=get(path).param("month","2080-07");if(path.endsWith("history"))request.param("scope","MONTH");mvc.perform(request).andExpect(status().isOk());
            for(String key:List.of("actor","regUserId","REG_USER_ID","unknown"))mvc.perform(get(path).param("month","2080-07").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        }
        mvc.perform(get("/api/financial-contributions").param("month","2080-13")).andExpect(status().isBadRequest());
        for(String field:List.of("scope","page","size"))mvc.perform(get("/api/financial-history").param("month","2080-07").param("scope","MONTH").param(field,field.equals("scope")?"ALL":field.equals("page")?"0":"30")).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void settlementHistoryDoesNotMixAdjacentPartsMonthsOrMalformedDetailKeys() {
        String parent=audit("TB_CO_PART_SETL","PART-001:208007","UPD","CORP_CARD_AMT","0","5","2080-07-01");
        String detail=audit("TB_CO_PART_SETL_DTL","PART-001:208007:DEV-001","DEL","PAY_AMT","10",null,"2080-07-02");
        for(String key:List.of("PART-0010:208007","PART-001:208006","PART-001:208007:DEV:EXTRA","PART-001:208007:"))
            audit(key.contains("DEV")||key.endsWith(":")?"TB_CO_PART_SETL_DTL":"TB_CO_PART_SETL",key,"UPD","PAY_AMT","0","5","2080-07-03");
        var query=new LinkedMultiValueMap<String,String>();query.add("month","2080-07");query.add("scope","PART_SETTLEMENT");query.add("partId","PART-001");
        var result=service.getFinancialHistory(FinancialReadInput.history(query));
        assertThat(result.items()).extracting(HistoryEntry::historyId).containsExactly(detail,parent);
        assertThat(result.total()).isEqualTo(2);
    }
}
