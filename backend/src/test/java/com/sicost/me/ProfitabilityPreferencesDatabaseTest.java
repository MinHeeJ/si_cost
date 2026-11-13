package com.sicost.me;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.api.RequestIdFilter;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Existing independent baseline only. No DDL/Flyway/seed replay; every owned write rolls back. */
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_BASELINE_CONFIRMED",matches="true")
@EnabledIfEnvironmentVariable(named="SICOST_PREF_TEST_WRITES_CONFIRMED",matches="true")
class ProfitabilityPreferencesDatabaseTest {
    @Test void realPreferenceKeysRoundTripColumnsAndRejectInvalidChangesWithoutTouchingExistingRows() throws Exception {
        String url=required("SICOST_COST_TEST_DB_URL"),schema=required("SICOST_COST_TEST_DB_SCHEMA");
        if(!schema.matches("[a-z][a-z0-9_]{0,62}")||url.equals(System.getenv("DB_URL"))||url.equals(System.getenv("SICOST_TEST_DB_URL")))
            throw new IllegalArgumentException("독립 기존자료 DB를 지정하세요.");
        var source=new DriverManagerDataSource(url,required("SICOST_COST_TEST_DB_USER"),required("SICOST_COST_TEST_DB_PASSWORD"));
        var properties=new Properties();properties.setProperty("currentSchema",schema);properties.setProperty("options","-c TimeZone=Asia/Seoul");source.setConnectionProperties(properties);
        var sql=new JdbcTemplate(source);
        assertThat(sql.queryForObject("SELECT current_schema()",String.class)).isEqualTo(schema);
        // Validate the real physical contract rather than inventing revision/order storage columns.
        assertThat(sql.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=? AND table_name='tb_sy_user_pref' AND column_name='pref_val'",Integer.class,schema)).isEqualTo(2000);
        assertThat(sql.queryForList("SELECT k.column_name FROM information_schema.table_constraints t JOIN information_schema.key_column_usage k ON t.constraint_schema=k.constraint_schema AND t.constraint_name=k.constraint_name WHERE t.table_schema=? AND t.table_name='tb_sy_user_pref' AND t.constraint_type='PRIMARY KEY' ORDER BY k.ordinal_position",String.class,schema))
            .containsExactly("user_id","pref_type_cd","pref_key");
        var config=new Configuration(new Environment("profitability-preferences",new SpringManagedTransactionFactory(),source));
        config.setArgNameBasedConstructorAutoMapping(true);config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        String resource="mappers/me/PreferenceMapper.xml";
        try(var in=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}
        var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        var mapper=session.getMapper(PreferenceMapper.class);
        var json=new ObjectMapper().findAndRegisterModules();
        // Test-owned actor avoids shared seed preference rows and all uniqueness collisions.
        String actor="pref_"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        var user=mock(CurrentUserProvider.class);when(user.userId()).thenReturn(actor);
        var validator=new PreferenceValidator(mapper,json,Clock.systemUTC());
        var service=new PreferenceService(mapper,user,new RevisionService(json),new CommitInvalidation(event->{}),validator,json);
        var mvc=MockMvcBuilders.standaloneSetup(new PreferenceController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        var existing=sql.queryForList("SELECT row_to_json(p)::text FROM tb_sy_user_pref p ORDER BY user_id,pref_type_cd,pref_key",String.class);
        tx.executeWithoutResult(transaction->{
            transaction.setRollbackOnly();
            try {
                // Copy valid account prerequisites, never read/print secrets or modify the source account.
                assertThat(sql.update("INSERT INTO tb_sy_user (user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT ?,user_nm,?,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,? FROM tb_sy_user ORDER BY user_id LIMIT 1",actor,actor+"@example.invalid",actor)).isEqualTo(1);
                JsonNode value=validator.defaultValue("COLS","UI-03");
                var view=(com.fasterxml.jackson.databind.node.ObjectNode)value.path("individualProfitability");
                var order=new ArrayList<String>();view.path("order").forEach(n->order.add(n.asText()));Collections.reverse(order);
                view.set("order",json.valueToTree(order));view.set("hidden",json.valueToTree(List.of("companyName","netPay")));
                String revision=save(mvc,json,"COLS","UI-03",value,null);
                save(mvc,json,"PAGE_SIZE","*",json.valueToTree(20),null);
                save(mvc,json,"DENSE","*",json.valueToTree("좁게"),null);
                save(mvc,json,"AMT_UNIT","*",json.valueToTree("백만원"),null);
                save(mvc,json,"COLS","UI-27",validator.defaultValue("COLS","UI-27"),null);
                save(mvc,json,"PERIOD","UI-03",validator.defaultValue("PERIOD","UI-03"),null);
                save(mvc,json,"PERIOD","UI-01",validator.defaultValue("PERIOD","UI-01"),null);
                var before=mapper.list(actor);
                var read=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
                assertThat(json.readTree(read.getResponse().getContentAsString()).at("/data/items"))
                    .anyMatch(n->n.path("type").asText().equals("COLS")&&n.path("key").asText().equals("UI-03")&&n.path("value").equals(value)&&n.path("revision").asText().equals(revision));
                for(String invalid:List.of("profit","duplicate")) {
                    var bad=value.deepCopy();
                    if(invalid.equals("profit"))((com.fasterxml.jackson.databind.node.ArrayNode)bad.path("individualProfitability").path("hidden")).add("profit");
                    else ((com.fasterxml.jackson.databind.node.ArrayNode)bad.path("individualProfitability").path("order")).set(0,json.valueToTree("name"));
                    mvc.perform(put("/api/me/preferences/COLS/UI-03").contentType("application/json").content(json.writeValueAsString(Map.of("value",bad,"expectedRevision",revision))))
                        .andExpect(status().isBadRequest());
                    assertThat(mapper.list(actor)).isEqualTo(before);
                }
                mvc.perform(delete("/api/me/preferences/COLS/UI-03").param("expectedRevision",revision)).andExpect(status().isNoContent());
                assertThat(mapper.list(actor)).noneMatch(p->p.type().equals("COLS")&&p.key().equals("UI-03"));
                assertThat(sql.queryForList("SELECT row_to_json(p)::text FROM tb_sy_user_pref p WHERE user_id<>? ORDER BY user_id,pref_type_cd,pref_key",String.class,actor)).isEqualTo(existing);
            }catch(Exception e){throw new RuntimeException(e);}
        });
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user_pref WHERE user_id=?",Integer.class,actor)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id=?",Integer.class,actor)).isZero();
    }
    private static String save(MockMvc mvc,ObjectMapper json,String type,String key,JsonNode value,String revision) throws Exception {
        var body=json.createObjectNode();body.set("value",value);if(revision==null)body.putNull("expectedRevision");else body.put("expectedRevision",revision);
        var response=mvc.perform(put("/api/me/preferences/{type}/{key}",type,key).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/preference/revision").asText();
    }
    private static String required(String key) {
        String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException(key+" 설정이 필요합니다.");return value;
    }
}
