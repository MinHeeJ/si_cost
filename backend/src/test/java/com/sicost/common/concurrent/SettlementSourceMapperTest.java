package com.sicost.common.concurrent;

import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class SettlementSourceMapperTest {
    Configuration configuration() throws Exception {
        var configuration = new Configuration();
        var resource = new ClassPathResource("mappers/common/SettlementSourceLockMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource.toString(), configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    String sql(String method, Object request) throws Exception {
        return configuration().getMappedStatement(SettlementSourceLockMapper.class.getName() + "." + method)
            .getBoundSql(request).getSql().replaceAll("\\s+", " ");
    }
    @Test void eachSourceUsesOnlyNonNullBoundPredicatesAndPersonMembership() throws Exception {
        for (var request : List.of(new SettlementSourceLocks.Sources(List.of("P"), List.of(), List.of(), List.of(), List.of()),
            new SettlementSourceLocks.Sources(List.of(), List.of("D"), List.of(), List.of(), List.of()),
            new SettlementSourceLocks.Sources(List.of(), List.of(), List.of("A"), List.of(), List.of()),
            new SettlementSourceLocks.Sources(List.of(), List.of(), List.of(), List.of("J"), List.of()),
            new SettlementSourceLocks.Sources(List.of(), List.of(), List.of(), List.of(), List.of("202607")))) {
            String sql = sql("partIds", request);
            assertThat(sql).doesNotContain("IS NULL", "IN ( )", "p.PART_ID=a.PART_ID").contains("ORDER BY p.PART_ID");
        }
        assertThat(sql("partIds", new SettlementSourceLocks.Sources(List.of(), List.of(), List.of(), List.of(), List.of())))
            .contains("FALSE");
    }
    @Test void lockStatementsAreOrderedRowLocksNotShareTableLocks() throws Exception {
        for (String name : List.of("lockParts", "lockHeaders", "lockStandards")) {
            assertThat(sql(name, java.util.Map.of("ids", List.of("P1", "P2"))))
                .contains("ORDER BY PART_ID", "FOR UPDATE").doesNotContain("LOCK TABLE", "SHARE");
        }
    }
    @Test void userQualificationsDiscoverAllLeaderTimelinesAndStoredPartConnections() throws Exception {
        String sql=sql("partIds",new SettlementSourceLocks.Sources(List.of(),List.of(),List.of(),List.of(),List.of(),List.of("ab12")));
        assertThat(sql).contains("TB_BS_PART_STD", "PART_LDR_USER_ID IN", "TB_SY_USER_SCOPE", "SCOPE_TYPE_CD='PART'", "s.USER_ID IN")
            .doesNotContain("USE_YN=", "APLY_BGN_YM", "APLY_END_YM", "FALSE");
    }
}
