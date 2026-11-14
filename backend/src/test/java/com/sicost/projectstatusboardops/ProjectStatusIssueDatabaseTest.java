package com.sicost.projectstatusboardops;

import com.sicost.profitability.CostDatabaseSupport;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL",matches=".+")
class ProjectStatusIssueDatabaseTest {
    @Test void existingEngineMaterializesExactFieldsExcludesExpiredAndLostAndPreservesAllSources()throws Exception {
        try(var db=new CostDatabaseSupport()) {
            var before=db.snapshot();
            var issueBefore=db.sql.queryForList("SELECT row_to_json(i)::text FROM TB_PJ_ISSUE i ORDER BY ISSUE_ID",String.class);
            var factory=new SqlSessionFactoryBean();factory.setDataSource(db.context.getBean(DataSource.class));
            var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/projectstatusboard/*.xml"));
            var mapper=new SqlSessionTemplate(factory.getObject()).getMapper(ProjectStatusIssueMapper.class);
            var day=db.sql.queryForObject("SELECT (current_timestamp AT TIME ZONE 'Asia/Seoul')::date",LocalDate.class);
            var rows=db.read(()->mapper.eligibleIssues(day));
            var expected=db.sql.queryForList("SELECT i.ISSUE_ID::bigint FROM TB_PJ_ISSUE i JOIN TB_PJ_PROJ p ON p.PROJ_ID=i.PROJ_ID WHERE p.PROJ_STAT_CD IN ('ING','PRE') AND p.CTRT_END_DT>=? ORDER BY CASE WHEN i.IMPR_CD='HIGH' THEN 0 ELSE 1 END,i.DDLN_DT,i.ISSUE_ID",Long.class,day);
            assertThat(rows.stream().map(ProjectStatusDtos.IssueSource::issueId).toList()).containsExactlyElementsOf(expected);
            for(var row:rows){assertThat(row.registeredId()).isNotBlank();assertThat(row.title()).isNotNull();assertThat(mapper.projectStatus(row.projectId(),day)).isEqualTo("ELIGIBLE");}
            assertThat(db.snapshot()).isEqualTo(before);assertThat(db.sql.queryForList("SELECT row_to_json(i)::text FROM TB_PJ_ISSUE i ORDER BY ISSUE_ID",String.class)).isEqualTo(issueBefore);
        }
    }
}
