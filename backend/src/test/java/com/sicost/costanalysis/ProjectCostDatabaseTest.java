package com.sicost.costanalysis;

import com.sicost.common.assignments.*;
import com.sicost.projects.*;
import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL",matches=".+")
class ProjectCostDatabaseTest {
    @Test void realMapperFullRangeAndCalculationsPreserveEveryBusinessRow() throws Exception {
        try(var db=new CostDatabaseSupport()) {
            var before=db.snapshot();
            var factory=new SqlSessionFactoryBean();factory.setDataSource(db.context.getBean(javax.sql.DataSource.class));
            var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
            var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
            var payroll=new EstimatedPayrollAdapter();var turnkey=new TurnkeyRevenueCalculation();
            var monthly=new MonthlyCostService(db.context.getBean(MonthlyCostRepository.class),new MonthlyCostCalculation(payroll),turnkey,payroll);
            var repository=new ProjectCostRepository(db.context.getBean(MonthlyCostRepository.class),monthly,
                new ProjectRepository(session.getMapper(ProjectMapper.class),null),session.getMapper(ProjectCostMapper.class),
                new AssignmentMonthlyService(session.getMapper(AssignmentMonthlyMapper.class),null,null,db.context.getBean(Clock.class)));
            var service=new ProjectCostService(repository,new ProjectCostCalculation(turnkey));
            db.read(()->{
                String id=db.sql.queryForObject("SELECT proj_id FROM tb_pj_proj ORDER BY ctrt_bgn_dt,proj_id LIMIT 1",String.class);
                assertThat(id).as("기존 프로젝트 fixture 필요").isNotNull();
                var detail=service.getProjectCostAnalysis(id,null);var months=service.getProjectCostMonths(id,detail.month());
                var assignments=service.getProjectCostAssignments(id,detail.month());service.getProjectCostCauses(id,detail.month());
                for(var m:months.items())if(m.completeness().costComplete()) {
                    BigDecimal expected=new BigDecimal(m.laborAllocated()).add(new BigDecimal(m.directExpense())).add(new BigDecimal(m.additionalDirectExpense()));
                    assertThat(new BigDecimal(m.totalCost())).isEqualByComparingTo(expected);
                }
                assertThat(months.totalCost()).isEqualTo(detail.row().cumulativeCost());assertThat(assignments.projectId()).isEqualTo(id);
                if(detail.simulationDefaults().enabled()) {
                    var defaults=detail.simulationDefaults();
                    if(defaults.c()!=null&&("TK".equals(detail.row().contractType())||defaults.r()!=null))
                        assertThat(service.createProjectCostSimulation(id,new ProjectCostDtos.SimulationInput(detail.month(),0,defaults.c(),1,defaults.r())).saved()).isFalse();
                }
                return true;
            });
            assertThat(db.snapshot()).isEqualTo(before);
        }
    }
}
