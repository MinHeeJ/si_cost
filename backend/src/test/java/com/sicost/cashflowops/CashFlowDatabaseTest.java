package com.sicost.cashflowops;

import com.sicost.common.CostManagementApplication;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.cashflowops.CashFlowDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class CashFlowDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry)throws Exception {
        database=new PostgreSqlDatabase();registry.add("spring.datasource.url",database.source::getUrl);
        registry.add("spring.datasource.username",()->System.getenv("SICOST_TEST_DB_USER"));registry.add("spring.datasource.password",()->System.getenv("SICOST_TEST_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate sql;
    @Autowired CashFlowService service;
    @Autowired PlatformTransactionManager transactions;
    @Test void sourceQueriesUseActualReceiptBalancesExcludeCancellationAndNeverCreateOrRepairAnySource() {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            String id="C"+UUID.randomUUID().toString().replace("-","").substring(0,15),project="P"+UUID.randomUUID().toString().replace("-","").substring(0,15);
            String month=YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString(),dbMonth=month.replace("-","");
            sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','END',?::date,?::date,1000,0,0,0,'admin')",project,project,month+"-01",month+"-01");
            sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,?,?::date,100,0,0,100,77,88,?::date,'admin')",id,project,dbMonth,month+"-01",month+"-01");
            sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,?::date,20,'N','admin'),(?,?::date,30,'N','admin'),(?,?::date,99,'Y','admin')",id,month+"-01",id,month+"-01",id,month+"-01");
            var before=state();var page=service.getCashFlowSources(new SourceQuery(month,Item.CASH_SALES,1,100));
            var bill=page.items().stream().filter(s->s.sourceId().equals(id)).findFirst();
            boolean closed=sql.queryForObject("SELECT count(*) FROM tb_cl_close WHERE close_ym=? AND close_stat_cd='CLOSE'",Integer.class,dbMonth)>0;
            if(closed)assertThat(bill).isEmpty();else assertThat(bill.orElseThrow().amount()).isEqualTo("50");
            service.getCashFlowSources(new SourceQuery(month,Item.CASH_SALES,2,20));
            service.getCashFlowSources(new SourceQuery(month,Item.EXPENSE,1,50));
            assertThat(state()).isEqualTo(before);tx.setRollbackOnly();
        });
    }
    private Map<String,String> state() {
        Map<String,String> result=new TreeMap<>();
        for(String table:List.of("tb_bl_bill","tb_bl_rcpt","tb_hr_mon_pay","tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_fi_card_mon","tb_cl_close","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForObject("SELECT COALESCE(jsonb_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class));
        return result;
    }
}
