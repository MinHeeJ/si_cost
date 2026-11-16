package com.sicost.meops;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

class ProfileMapperTest {
    @Test void allMapperMethodsBindCurrentUserAndSelectOnlyNonSensitiveSourceColumns() throws Exception {
        var config=new Configuration();config.setArgNameBasedConstructorAutoMapping(true);
        String resource="mappers/meops/ProfileMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        var parameters=Map.of("userId","actual-user","month","202607");
        for(var method:ProfileMapper.class.getDeclaredMethods()) {
            var statement=config.getMappedStatement(ProfileMapper.class.getName()+"."+method.getName());
            assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
            var bound=statement.getBoundSql(parameters);
            assertThat(bound.getParameterMappings()).anySatisfy(p->assertThat(p.getProperty()).isEqualTo("userId"));
            bound.getParameterMappings().forEach(p->assertThat(parameters.get(p.getProperty())).isNotNull());
            assertThat(bound.getSql().toLowerCase()).doesNotContain("pwd_","password","mfa_","tb_hr_","dev_id","select *","for update","nextval(");
        }
        assertThat(config.getMappedStatement(ProfileMapper.class.getName()+".user").getResultMaps().get(0).getType()).isEqualTo(ProfileDtos.User.class);
        assertThat(config.getMappedStatement(ProfileMapper.class.getName()+".activeRoles").getResultMaps().get(0).getType()).isEqualTo(ProfileDtos.Role.class);
        assertThat(config.getMappedStatement(ProfileMapper.class.getName()+".parts").getResultMaps().get(0).getType()).isEqualTo(ProfileDtos.Part.class);
    }
    @Test void serviceUsesOneReadOnlyRepeatableReadBoundary() throws Exception {
        var transaction=ProfileService.class.getMethod("getMyProfile").getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
