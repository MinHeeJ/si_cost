package com.sicost.salespartsettlements;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SalesPartSettlementMapperTest {
    @Test void everyMappedMethodIsAReadOnlyProjectionAndMonthBindingIsDeclared() throws Exception {
        var configuration=new Configuration();
        var resource=new ClassPathResource("mappers/salespartsettlements/SalesPartSettlementMapper.xml");
        try(var input=resource.getInputStream()) {new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();}
        for(String method:List.of("parts","standards","useHistory","parents","details","firstInputs","revisionSources")) {
            var statement=configuration.getMappedStatement(SalesPartSettlementMapper.class.getName()+"."+method);
            assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
            assertThat(statement.getBoundSql("202607").getParameterMappings()).allMatch(p->p.getProperty().equals("month"));
        }
    }
}
