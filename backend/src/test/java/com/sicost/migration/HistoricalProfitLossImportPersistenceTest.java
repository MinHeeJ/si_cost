package com.sicost.migration;

import com.sicost.common.persistence.PersistenceBoundary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class HistoricalProfitLossImportPersistenceTest {
    @Test void productionMapperBindingsUseNullableAmountsAndGuardedHistoricalWrites()throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        try(var input=new ClassPathResource("mappers/migration/HistoricalProfitLossImportMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,configuration,"mappers/migration/HistoricalProfitLossImportMapper.xml",configuration.getSqlFragments()).parse();
        }
        Map<String,BigDecimal> amounts=new LinkedHashMap<>();HistoricalProfitLossImportDefinition.FIELDS.forEach(k->amounts.put(k,null));amounts.put("salesAmt",BigDecimal.ZERO);
        var write=new HistoricalProfitLossImportDtos.ProfitLossWrite("207901","208007",amounts,"admin",LocalDateTime.of(2080,1,1,0,0));
        for(String id:List.of("insertProfit","updateProfit")) {
            var statement=configuration.getMappedStatement("com.sicost.migration.HistoricalProfitLossImportMapper."+id);var bound=statement.getBoundSql(write);
            assertThatCode(()->PersistenceBoundary.requireAllowed(bound.getSql())).doesNotThrowAnyException();
            for(var binding:bound.getParameterMappings())assertThatCode(()->configuration.newMetaObject(write).getValue(binding.getProperty())).doesNotThrowAnyException();
        }
        assertThat(configuration.getMappedStatement("com.sicost.migration.HistoricalProfitLossImportMapper.snapshot").getBoundSql(null).getSql()).contains("to_jsonb(p)","CASH_BASE_YM");
    }
    @Test void deletesOperatingSnapshotsAndUnboundedChangesStayForbidden() {
        for(String sql:List.of("DELETE FROM tb_co_mon_pl WHERE pl_ym=?","UPDATE tb_co_mon_pl SET sales_amt=? WHERE pl_ym=?",
            "UPDATE tb_co_mon_pl SET fix_yn='Y' WHERE pl_ym=?","INSERT INTO tb_co_mon_pl(pl_ym,sales_amt) VALUES(?,?)"))
            assertThatThrownBy(()->PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
    }
}
