package com.sicost.apiops;

import java.io.InputStream;
import java.time.LocalDate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ActivityReadMapperTest {
    Configuration configuration() throws Exception {
        var c=new Configuration();for(String name:new String[]{"ActivityReadMapper","DashboardReadMapper"}) {
            String path="mappers/apiops/"+name+".xml";
            try(InputStream stream=getClass().getClassLoader().getResourceAsStream(path)){assertThat(stream).isNotNull();new XMLMapperBuilder(stream,c,path,c.getSqlFragments()).parse();}
        }
        return c;
    }
    @Test void recipientListAndCountUseTheSameScopeAndReadOnlyStatements() throws Exception {
        var c=configuration();String list=c.getMappedStatement(ActivityReadMapper.class.getName()+".notifications").getBoundSql(new ActivityReadDtos.RecipientQuery("current",20,20)).getSql();
        String count=c.getMappedStatement(ActivityReadMapper.class.getName()+".notificationCount").getBoundSql("current").getSql();
        for(String sql:new String[]{list,count})assertThat(sql).contains("rcvr_user_id=? AND send_mthd_cd IN ('SYS','BOTH')").doesNotContain("FOR UPDATE","INSERT","UPDATE ","DELETE ","nextval");
        assertThat(list).contains("ORDER BY noti_dttm DESC,noti_id DESC");
    }
    @Test void optionalJobPredicateAddsOnlyNonNullBindingToListAndCount() throws Exception {
        var c=configuration();for(String method:new String[]{"automationRuns","automationCount"}) {
            var statement=c.getMappedStatement(ActivityReadMapper.class.getName()+"."+method);
            var all=statement.getBoundSql(new ActivityQueryInput.Query(1,50,null,0));var filtered=statement.getBoundSql(new ActivityQueryInput.Query(1,50,"DAILY_OPERATIONS",0));
            assertThat(all.getSql()).doesNotContain("job_cd=?","IS NULL");assertThat(filtered.getSql()).contains("job_cd=?");
            assertThat(filtered.getParameterMappings()).extracting(p->p.getProperty()).contains("jobCode");
        }
    }
    @Test void currentBalanceDoesNotReuseTheOverdueOnlyPredicateOrCachedReceiptAmount() throws Exception {
        var sql=configuration().getMappedStatement(DashboardReadMapper.class.getName()+".receivables").getBoundSql(LocalDate.of(2026,7,15)).getSql();
        assertThat(sql).contains("b.tot_amt-COALESCE(r.amount,0)","WHERE cncl_yn='N' GROUP BY bill_id","WHERE balance>0","FILTER (WHERE pay_due_dt < ?)").doesNotContain("rcpt_sum_amt","unpaid_amt","UPDATE","nextval");
    }
}
