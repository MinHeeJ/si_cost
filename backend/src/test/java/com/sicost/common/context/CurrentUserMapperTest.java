package com.sicost.common.context;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CurrentUserMapperTest {
    Configuration configuration() throws Exception {
        var configuration=new Configuration();
        var resource=new ClassPathResource("mappers/common/CurrentUserMapper.xml");
        try(var input=resource.getInputStream()) {
            new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
    @Test void automaticAdministratorSelectionIsNotMapped() throws Exception {
        var config=configuration();
        assertThat(config.hasStatement(CurrentUserMapper.class.getName()+".findAdministrator")).isFalse();
        assertThat(config.hasStatement(CurrentUserMapper.class.getName()+".findAdministratorRoles")).isFalse();
    }
    @Test void nameAndRolesAreBoundToTheChosenUserAndRolesRemainActiveOnly() throws Exception {
        var configuration=configuration();
        for(String method:new String[]{"findName","findActiveRoles"}) {
            var bound=configuration.getMappedStatement(CurrentUserMapper.class.getName()+"."+method).getBoundSql(Map.of("userId","fallback"));
            assertThat(bound.getParameterMappings()).hasSize(1);
            assertThat(bound.getParameterMappings().get(0).getProperty()).isEqualTo("userId");
            assertThat(bound.getSql()).doesNotContain("'admin'", "fallback");
        }
        assertThat(configuration.getMappedStatement(CurrentUserMapper.class.getName()+".findActiveRoles")
            .getBoundSql(Map.of("userId","fallback")).getSql()).contains("r.use_yn='Y'", "ORDER BY r.role_id");
    }
}
