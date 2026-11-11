package com.sicost.me;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class PreferenceMapperTest {
    @Test void xmlBindsRecordPropertiesAndTargetsOnlyCurrentUserCompositeKey() throws Exception {
        var config=new Configuration();config.setArgNameBasedConstructorAutoMapping(true);
        String resource="mappers/me/PreferenceMapper.xml";
        try(var input=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();}
        var id=new PreferenceDtos.PreferenceId("admin","PAGE_SIZE","*");
        for(String operation:new String[]{"insert","update","delete","lock"}) {
            Object parameter=operation.equals("insert")||operation.equals("update")?new PreferenceDtos.PreferenceWrite(id,"50"):id;
            var bound=config.getMappedStatement("com.sicost.me.PreferenceMapper."+operation).getBoundSql(parameter);
            var properties=org.apache.ibatis.reflection.SystemMetaObject.forObject(parameter);
            bound.getParameterMappings().forEach(p->assertThat(properties.getValue(p.getProperty())).isNotNull());
            assertThat(bound.getSql()).contains("TB_SY_USER_PREF").doesNotContain("TB_SY_CHG_HIST","ON CONFLICT");
        }
        assertThat(config.getMappedStatement("com.sicost.me.PreferenceMapper.lock").getBoundSql(id).getSql()).contains("FOR UPDATE");
    }
}
