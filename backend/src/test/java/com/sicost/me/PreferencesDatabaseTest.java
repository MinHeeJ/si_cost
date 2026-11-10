package com.sicost.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PreferencesDatabaseTest {
    @Test void realApiAndMapperPersistResetAndPreserveHistoryAndOtherKeys() throws Exception {
        String url=System.getenv("SICOST_TEST_DB_URL"),user=System.getenv("SICOST_TEST_DB_USER"),password=System.getenv("SICOST_TEST_DB_PASSWORD");
        String schema="sicost_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement()){statement.execute("CREATE SCHEMA "+schema);}
        var source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema+"&options=-c%20TimeZone=Asia%2FSeoul",user,password);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").group(true).cleanDisabled(true).load().migrate();
        var sql=new JdbcTemplate(source);
        var config=new Configuration(new Environment("preferences",new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),source));
        config.setArgNameBasedConstructorAutoMapping(true);
        for(String resource:new String[]{"mappers/me/PreferenceMapper.xml","mappers/common/CurrentUserMapper.xml"}){
            try(var in=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
        }
        var factory=new SqlSessionFactoryBuilder().build(config);
        {
            var session=new org.mybatis.spring.SqlSessionTemplate(factory);
            var mapper=session.getMapper(PreferenceMapper.class);var current=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));
            ObjectMapper json=new ObjectMapper().findAndRegisterModules();
            var service=new PreferenceService(mapper,current,new RevisionService(json),new CommitInvalidation(event->{}),new PreferenceValidator(mapper,json,java.time.Clock.systemUTC()),json);
            var mvc=MockMvcBuilders.standaloneSetup(new PreferenceController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
            var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            long history=sql.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST",Long.class);
            tx.executeWithoutResult(status->{try {
                var response=mvc.perform(put("/api/me/preferences/PAGE_SIZE/*").contentType("application/json").content("{\"value\":50,\"expectedRevision\":null}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andReturn();
                String token=json.readTree(response.getResponse().getContentAsString()).at("/data/preference/revision").asText();
                mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json").content("{\"value\":\"좁게\",\"expectedRevision\":null}")).andExpect(status().isOk());
                assertThat(sql.queryForObject("SELECT PREF_VAL FROM TB_SY_USER_PREF WHERE USER_ID='admin' AND PREF_TYPE_CD='PAGE_SIZE'",String.class)).isEqualTo("50");
                mvc.perform(delete("/api/me/preferences/PAGE_SIZE/*").param("expectedRevision",token)).andExpect(status().isNoContent());
                assertThat(sql.queryForObject("SELECT count(*) FROM TB_SY_USER_PREF WHERE PREF_TYPE_CD='PAGE_SIZE'",Long.class)).isZero();
                assertThat(sql.queryForObject("SELECT PREF_VAL FROM TB_SY_USER_PREF WHERE PREF_TYPE_CD='DENSE'",String.class)).isEqualTo("\"좁게\"");
                assertThat(sql.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST",Long.class)).isEqualTo(history);
                mvc.perform(get("/api/me/preferences")).andExpect(status().isOk());
            }catch(Exception e){throw new RuntimeException(e);}});
        }
    }
}
