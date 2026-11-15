package com.sicost.workforcerecommendationsops;

import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;
import static org.assertj.core.api.Assertions.*;

class RecommendationBrowseMapperTest {
    Configuration configuration() throws Exception {
        Configuration c=new Configuration();
        for(String resource:List.of("mappers/salescommands/SalesCommandMapper.xml","mappers/recommendationbrowse/RecommendationBrowseMapper.xml")){
            try(var in=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse();}
        }
        return c;
    }
    @Test void historyCountAndListBindTheSameFiltersAndKeepNumericRoundOrdering() throws Exception {
        Configuration c=configuration();var q=new HistoryQuery("PROJECT","P-001","OLD","AI",2,20);
        var list=c.getMappedStatement(RecommendationBrowseMapper.class.getName()+".history").getBoundSql(Map.of("query",q,"offset",20L));
        var count=c.getMappedStatement(RecommendationBrowseMapper.class.getName()+".historyCount").getBoundSql(q);
        for(String clause:List.of("r.proj_id=?","r.job_cd=?","r.rcmd_type_cd=?")){
            assertThat(list.getSql()).contains(clause);assertThat(count.getSql()).contains(clause);
        }
        assertThat(list.getSql()).contains("DISTINCT ON (r.rcmd_id)","r.reg_dttm DESC NULLS LAST,r.rcmd_id DESC","LIMIT ? OFFSET ?");
        assertThat(count.getSql()).contains("count(DISTINCT r.rcmd_id)");
        assertThat(list.getAdditionalParameter("targetId")).isEqualTo("P-001");
        assertThat(list.getAdditionalParameter("job")).isEqualTo("OLD");
    }
    @Test void noFiltersProduceNoNullBindingsAndAllOperationsAreSelects() throws Exception {
        Configuration c=configuration();var q=new HistoryQuery(null,null,null,null,1,50);
        var sql=c.getMappedStatement(RecommendationBrowseMapper.class.getName()+".history").getBoundSql(Map.of("query",q,"offset",0L));
        assertThat(sql.getParameterMappings()).extracting(p->p.getProperty()).containsExactly("query.size","offset");
        for(var method:RecommendationBrowseMapper.class.getDeclaredMethods())assertThat(c.getMappedStatement(RecommendationBrowseMapper.class.getName()+"."+method.getName()).getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
    }
    @Test void targetsHaveEligibilityAndOnlyActOccupancyPredicates() throws Exception {
        Configuration c=configuration();
        assertThat(c.getMappedStatement(RecommendationBrowseMapper.class.getName()+".targets").getBoundSql(java.time.LocalDate.of(2026,7,1)).getSql())
            .contains("opp_stat_cd='ING'","proj_stat_cd='ING'","ctrt_type_cd <> 'IN'","ctrt_end_dt >= ?");
        assertThat(c.getMappedStatement(RecommendationBrowseMapper.class.getName()+".needs").getBoundSql("O-001").getSql()).contains("t.tent_stat_cd='ACT'","ORDER BY n.need_sn");
    }
}
