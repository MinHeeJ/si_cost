package com.sicost.apiops;

import com.sicost.profitability.*;
import com.sicost.common.assignments.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL",matches=".+")
class ProjectAnalysisDatabaseTest {
    CostDatabaseSupport db;
    Map<String,List<String>> before;
    ProjectAnalysisRepository repository;
    @BeforeEach void existingReadOnlySource() throws Exception {
        for(String key:List.of("SICOST_COST_TEST_DB_SCHEMA","SICOST_COST_TEST_DB_USER","SICOST_COST_TEST_DB_PASSWORD"))
            Assumptions.assumeTrue(System.getenv(key)!=null&&!System.getenv(key).isBlank(),"기존자료 DB 연결 설정 필요");
        db=new CostDatabaseSupport();before=db.snapshot();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.context.getBean(DataSource.class));
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        var payroll=new EstimatedPayrollAdapter();var source=db.context.getBean(MonthlyCostRepository.class);
        var engine=new MonthlyCostService(source,new MonthlyCostCalculation(payroll),new TurnkeyRevenueCalculation(),payroll);
        var monthlyMapper=session.getMapper(AssignmentMonthlyMapper.class);
        var monthly=new AssignmentMonthlyService(monthlyMapper,null,null,Clock.systemUTC());
        repository=new ProjectAnalysisRepository(session.getMapper(ProjectAnalysisMapper.class),source,engine,monthly,monthlyMapper);
    }
    @AfterEach void exactSourceAndAuditPreservation() {
        if(db!=null)try{assertThat(db.snapshot()).isEqualTo(before);}finally{db.close();}
    }
    @Test void currentAndPreviousDecemberReadFullRangeAndEveryMapperMaterializesWithoutWrites() {
        var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        var current=db.read(()->repository.load(YearMonth.from(today)));
        assertThat(current.sources().period().fromMonth()).isEqualTo(current.sources().period().baseMonth());
        var cost=new ProjectCostCalculation(new TurnkeyRevenueCalculation());var status=new ProjectStatusCalculation();
        for(var project:current.sources().projects()) {
            var row=cost.row(current,project,YearMonth.from(today));
            assertThat(row).containsKeys("cumulativeCost","completeness","actualMm");
            if(Set.of("ING","PRE").contains(project.status())&&!project.toDate().isBefore(today))
                assertThat(status.row(current,project,row,today)).containsKeys("reasons","costUnknown","currentStep");
        }
        YearMonth december=YearMonth.of(today.getYear()-1,12);
        if(!december.isBefore(com.sicost.profitability.MonthlyCostDtos.parseMonth(current.sources().period().baseMonth()))) {
            var prior=db.read(()->repository.load(december));
            assertThat(prior.sources().period().toMonth()).isEqualTo(december.toString().replace("-",""));
            assertThat(prior.sources().period().fromMonth()).isEqualTo(current.sources().period().baseMonth());
        }
    }
}
