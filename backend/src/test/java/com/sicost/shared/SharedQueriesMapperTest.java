package com.sicost.shared;

import com.sicost.shared.SharedQueryDtos.HistoryQuery;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class SharedQueriesMapperTest {
    Configuration configuration() throws Exception {
        Configuration config=new Configuration();
        config.setArgNameBasedConstructorAutoMapping(true);
        try(var input=new ClassPathResource("mappers/shared/SharedQueriesMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,config,"mappers/shared/SharedQueriesMapper.xml",config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void allReadStatementsResolveRecordTypesAndNamedBindings() throws Exception {
        var config=configuration();
        for(String method:new String[]{"menus","cashBaseMonth"}) assertThat(config.getMappedStatement(SharedQueriesMapper.class.getName()+"."+method).getBoundSql(null).getSql()).containsIgnoringCase("SELECT");
        var code=config.getMappedStatement(SharedQueriesMapper.class.getName()+".codes");
        assertThat(code.getBoundSql(Map.of("group","JOB","includeInactive",false)).getSql()).contains("USE_YN='Y'","DEL_YN='N'","ORDER BY SORT_ORD,CODE");
        assertThat(code.getBoundSql(Map.of("group","JOB","includeInactive",true)).getSql()).doesNotContain("USE_YN='Y'").contains("DEL_YN='N'");
    }
    @Test void listAndCountUseExactlySameNonNullFilterBindings() throws Exception {
        var config=configuration();
        var query=new HistoryQuery("CODE_MAP","JOB",20,20);
        var list=config.getMappedStatement(SharedQueriesMapper.class.getName()+".history").getBoundSql(query);
        var count=config.getMappedStatement(SharedQueriesMapper.class.getName()+".historyCount").getBoundSql(query);
        assertThat(list.getParameterMappings().stream().map(p->p.getProperty()).toList()).containsExactly("targetType","groupId","size","offset");
        assertThat(count.getParameterMappings().stream().map(p->p.getProperty()).toList()).containsExactly("targetType","groupId");
        assertThat(list.getSql()).contains("q.\"groupId\"=?","q.\"target\"=?","q.\"occurredAt\" DESC,q.\"histId\" DESC","split_part(h.TRGT_KEY,':',1)");
        var all=config.getMappedStatement(SharedQueriesMapper.class.getName()+".history").getBoundSql(new HistoryQuery(null,null,50,0));
        assertThat(all.getParameterMappings()).hasSize(2);
    }
}
