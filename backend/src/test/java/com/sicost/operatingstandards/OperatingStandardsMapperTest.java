package com.sicost.operatingstandards;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class OperatingStandardsMapperTest {
    Configuration configuration() throws Exception {
        Configuration config=new Configuration();
        try(var input=new ClassPathResource("mappers/operatingstandards/OperatingStandardsMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,config,"mappers/operatingstandards/OperatingStandardsMapper.xml",config.getSqlFragments()).parse();
        }
        return config;
    }
    String sql(String method,Map<String,Object> input) throws Exception {return configuration().getMappedStatement("com.sicost.operatingstandards.OperatingStandardsMapper."+method).getBoundSql(input).getSql().replaceAll("\\s+"," ");}
    @Test void allIsNonpagedAndOrdersKeysAndMonths() throws Exception {assertThat(sql("all",Map.of())).contains("ORDER BY std_cd,aply_bgn_ym DESC","reg_dttm","mod_dttm","AS \"unitNm\"").doesNotContain("LIMIT","OFFSET");}
    @Test void fullKeyHistoryIsLockedIncludingScheduledMonths() throws Exception {assertThat(sql("rows",Map.of("key","VAT_RT","lock",true))).contains("WHERE std_cd=? ORDER BY aply_bgn_ym DESC FOR UPDATE").doesNotContain("<=","LIMIT");}
    @Test void readHistoryDoesNotAcquireWriteLocks() throws Exception {assertThat(sql("rows",Map.of("key","VAT_RT","lock",false))).doesNotContain("FOR UPDATE");}
    @Test void closedPredicateUsesOnlyActualCloseAndShareLocks() throws Exception {assertThat(sql("closedMonths",Map.of())).contains("close_stat_cd='CLOSE'","FOR SHARE");}
    @Test void mutationIsExactlyOneKeyAndMonthAndCannotChangeMetadata() throws Exception {assertThat(sql("update",Map.of())).contains("SET std_val=?","mod_user_id=?","mod_dttm=?","WHERE std_cd=? AND aply_bgn_ym=?").doesNotContain("SET std_nm","DELETE","tb_cl_close");}
}
