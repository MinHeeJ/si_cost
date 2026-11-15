package com.sicost.apiops;

import com.sicost.cashflowops.CashFlowDtos.*;
import com.sicost.cashflowops.CashFlowService;
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
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class FinancialManagementReadDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        database=new PostgreSqlDatabase();
        registry.add("spring.datasource.url",database.source::getUrl);
        registry.add("spring.datasource.username",()->System.getenv("SICOST_TEST_DB_USER"));
        registry.add("spring.datasource.password",()->System.getenv("SICOST_TEST_DB_PASSWORD"));
    }
    @Autowired JdbcTemplate sql;
    @Autowired CashFlowService cash;
    @Autowired MonthCloseReadService close;
    @Test void annualReadDoesNotCreatePayrollTaxParentsOrAuditAndMatchesSourceContributions() {
        Map<String,String> before=state();
        Result annual=cash.calculateCashFlow(new Query(null,Unit.THOUSAND_WON));
        assertThat(annual.months()).hasSize(12);assertThat(annual.periodTotals()).hasSize(3);
        String month=YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString();
        for(Item item:Item.values()) {
            var source=cash.getCashFlowSources(new SourceQuery(month,item,1,100));
            assertThat(source.total()).isGreaterThanOrEqualTo(source.items().size());
        }
        assertThat(state()).isEqualTo(before);
    }
    @Test void emptyOrWorkMonthGetIsReadOnlyAndReturnsFullChecksAndSameRevision() {
        Map<String,String> before=state();
        var first=close.getMonthCloses(new MonthCloseReadDtos.Query(null,1,20));
        var second=close.getMonthCloses(new MonthCloseReadDtos.Query(first.month(),2,20));
        assertThat(first.checks()).hasSize(6);assertThat(second.revision()).isEqualTo(first.revision());
        assertThat(first.history().total()).isEqualTo(second.history().total());
        assertThat(state()).isEqualTo(before);
    }
    private Map<String,String> state() {
        Map<String,String> result=new TreeMap<>();
        for(String table:List.of("tb_cl_close","tb_cl_close_chk","tb_cl_close_hist","tb_pj_asgn_mon","tb_co_mon_cost","tb_co_mon_pl","tb_co_part_setl","tb_co_part_setl_dtl","tb_hr_mon_pay","tb_bl_bill","tb_bl_rcpt","tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForObject("SELECT COALESCE(jsonb_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class));
        return result;
    }
}
