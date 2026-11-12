package com.sicost.projects;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.projects.ProjectDtos.*;

class ProjectMapperTest {
    Configuration configuration;
    @BeforeEach void parseRealMapper() throws Exception {
        configuration=new Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);
        try(InputStream xml=getClass().getResourceAsStream("/mappers/projects/ProjectMapper.xml")) {
            assertThat(xml).isNotNull();new XMLMapperBuilder(xml,configuration,"mappers/projects/ProjectMapper.xml",configuration.getSqlFragments()).parse();
        }
    }
    String sql(String statement,Object input) { return configuration.getMappedStatement("com.sicost.projects.ProjectMapper."+statement).getBoundSql(input).getSql(); }
    Query query(Map<String,String> values) {return new Query(ProjectInput.filter(values),LocalDate.of(2026,7,1),"202607",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),0);}
    @Test void optionalPredicatesOnlyBindPresentValuesAndUnassignedCountOmitsPartFilter() {
        for(String operation:List.of("list","count","unassignedCount")) {
            var statement=configuration.getMappedStatement("com.sicost.projects.ProjectMapper."+operation);
            assertThat(statement.getBoundSql(query(Map.of())).getParameterMappings()).noneMatch(p->p.getProperty().startsWith("filter.q") || p.getProperty().startsWith("filter.partId"));
        }
        var input=query(Map.of("partId","HQ","q","A%_","status","PRE"));
        assertThat(sql("list",input)).contains("p.part_id=?","strpos(p.proj_nm,?)");assertThat(sql("count",input)).contains("p.part_id=?","p.effective_status=?");
        assertThat(sql("unassignedCount",input)).doesNotContain("p.part_id=?").contains("p.part_id IS NULL","p.effective_status=?");
    }
    @Test void everyApprovedSortBuildsWithoutRawInterpolationAndKeepsStableTieBreak() {
        for(String sort:ProjectInput.SORTS) assertThat(sql("list",query(Map.of("sort",sort,"direction","ASC")))).contains("NULLS LAST,p.proj_id ASC").doesNotContain("${");
    }
    @Test void assignmentAndActualMmQueriesRequireAllThreeJoinIdentifiersAndExcludeHistoryMultiplication() {
        String assignments=sql("assignments",Map.of("id","P-1234","includeCancelled",false));
        assertThat(assignments).contains("m.asgn_id=a.asgn_id AND m.proj_id=a.proj_id AND m.dev_id=a.dev_id").doesNotContain("asgn_hist");
        assertThat(sql("actualMmTotal",Map.of("id","P-1234"))).contains("m.asgn_id=a.asgn_id AND m.proj_id=a.proj_id AND m.dev_id=a.dev_id","a.asgn_stat_cd='ACT'");
    }
    @Test void periodGuardChecksEveryStageAndActWithoutDeletingAnything() {
        assertThat(sql("periodUses",new PeriodCheck("P-1234",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),"202601")))
            .contains("tb_pj_tk_dstrb","tb_pj_proj_part","asgn_stat_cd='ACT'").doesNotContain("dstrb_step_cd=","DELETE","UPDATE");
    }
}
