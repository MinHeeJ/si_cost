package com.sicost.codemappings;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CodeMappingsMapperTest {
    private Configuration configuration() throws Exception {
        Configuration config=new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setArgNameBasedConstructorAutoMapping(true);
        String path="mappers/codemappings/CodeMappingsMapper.xml";
        try(var stream=new ClassPathResource(path).getInputStream()) {
            new XMLMapperBuilder(stream,config,path,config.getSqlFragments()).parse();
        }
        return config;
    }
    private String sql(Configuration config,String name,Object query) {
        return config.getMappedStatement("com.sicost.codemappings.CodeMappingsMapper."+name).getBoundSql(query).getSql().replaceAll("\\s+"," ").trim();
    }
    @Test void dynamicFilterAndCountShareExactPredicates() throws Exception {
        var config=configuration();
        var query=new CodeMappingsMapper.Query("표기","GROUP",20,40);
        String list=sql(config,"list",query), count=sql(config,"count",query);
        assertThat(list).contains("strpos(m.SRC_TEXT, ?) > 0","m.CODE_GRP_ID = ?","ORDER BY m.SRC_TEXT, m.CODE_GRP_ID, m.MAP_ID LIMIT ? OFFSET ?");
        assertThat(list.substring(list.indexOf("WHERE"),list.indexOf("ORDER BY")).trim())
                .isEqualTo(count.substring(count.indexOf("WHERE")));
        assertThat(config.getMappedStatement("com.sicost.codemappings.CodeMappingsMapper.list").getBoundSql(query).getParameterMappings())
                .extracting(p -> p.getProperty()).containsExactly("sourceText","groupId","size","offset");
    }
    @Test void absentFiltersNeverBindNullAndReadsNeverFlushWrites() throws Exception {
        var config=configuration(); var query=new CodeMappingsMapper.Query(null,null,50,0);
        assertThat(sql(config,"list",query)).doesNotContain("WHERE","IS NULL");
        assertThat(sql(config,"count",query)).doesNotContain("WHERE","IS NULL");
        assertThat(config.getMappedStatement("com.sicost.codemappings.CodeMappingsMapper.list").isFlushCacheRequired()).isFalse();
    }
    @Test void deleteLocksOnlySelectedPhysicalRowsAndInsertUsesGeneratedKey() throws Exception {
        var config=configuration();
        assertThat(sql(config,"lock",Map.of("mapId",1))).contains("m.MAP_ID=? FOR UPDATE OF m");
        assertThat(sql(config,"insert",new CodeMappingsMapper.Insert("표기","GROUP","A","admin",java.time.LocalDateTime.of(2026,1,1,0,0))))
                .contains("ON CONFLICT (SRC_TEXT,CODE_GRP_ID) DO NOTHING RETURNING MAP_ID");
        assertThat(sql(config,"activeCodeName",Map.of("groupId","GROUP","code","A")))
                .contains("c.USE_YN='Y' AND c.DEL_YN='N'","FOR SHARE OF c, g");
        assertThat(sql(config,"delete",Map.of("mapId",1))).isEqualTo("DELETE FROM TB_SY_CODE_MAP WHERE MAP_ID=?");
    }
}
