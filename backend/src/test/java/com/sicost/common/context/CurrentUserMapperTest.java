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
    @Test void principalSelectionUsesEligibleDatabaseAdminAndDeterministicFallbackWithoutCreatingAccounts() throws Exception {
        String sql=configuration().getMappedStatement(CurrentUserMapper.class.getName()+".findAdministrator")
            .getBoundSql(null).getSql().replaceAll("\\s+"," ");
        assertThat(sql).contains("u.user_stat_cd='USE'", "r.role_id='ADMIN'", "r.use_yn='Y'", "ur.user_id=u.user_id",
            "ORDER BY CASE WHEN u.user_id='admin' THEN 0 ELSE 1 END,u.user_id LIMIT 1").doesNotContain("INSERT", "UPDATE");
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
