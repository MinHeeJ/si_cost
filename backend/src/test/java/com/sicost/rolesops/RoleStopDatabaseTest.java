package com.sicost.rolesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class RoleStopDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    ObjectMapper json;
    RoleConfigurationRevision revisions;
    com.sicost.roles.RoleMapper roleReads;
    String id;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        roleReads=session.getMapper(com.sicost.roles.RoleMapper.class);
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(RoleStopMapper.class,()->session.getMapper(RoleStopMapper.class));
        context.registerBean(RoleConfigurationSnapshotMapper.class,()->session.getMapper(RoleConfigurationSnapshotMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,
            RevisionService.class,RoleConfigurationRevision.class,CommitInvalidation.class,WriteCoordinator.class,RoleStopRepository.class,RoleStopService.class);
        context.refresh();revisions=context.getBean(RoleConfigurationRevision.class);
        mvc=MockMvcBuilders.standaloneSetup(new RoleStopController(context.getBean(RoleStopService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        id="R-"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id,reg_dttm) VALUES (?,?,'PROJ','N','N',901,'Y','admin',TIMESTAMP '2026-01-01 00:00:00.123456')",id,"역할 "+id);
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,upd_yn,reg_user_id) SELECT ?,menu_id,'N','N','admin' FROM tb_sy_menu WHERE menu_lvl=2",id);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    String body() {return json.createObjectNode().put("expectedRevision",revisions.revision(id)).toString();}
    org.springframework.test.web.servlet.ResultActions stop(String body) throws Exception {
        return mvc.perform(post("/api/roles/{id}/stop",id).contentType("application/json").content(body));
    }
    Map<String,Object> role() {return sql.queryForMap("SELECT * FROM tb_sy_role WHERE role_id=?",id);}
    List<Map<String,Object>> permissions() {return sql.queryForList("SELECT * FROM tb_sy_role_menu_auth WHERE role_id=? ORDER BY menu_id",id);}
    long historyCount() {return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_ROLE' AND trgt_key=?",Long.class,id);}
    long accessCount() {return sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE trgt_desc=?",Long.class,id);}
    @Test void revisionFromRoleReadIsAcceptedByStopAndSettingsStayConsistent() throws Exception {
        RevisionService hashes=context.getBean(RevisionService.class);
        String readRevision=hashes.revision(roleReads.snapshot(id));
        assertThat(revisions.revision(id)).isEqualTo(readRevision);
        assertThat(revisions.settingsRevision()).isEqualTo(hashes.revision(roleReads.settingsSnapshot()));
        var response=stop(json.createObjectNode().put("expectedRevision",readRevision).toString())
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString()).get("data");
        assertThat(result.get("revision").asText()).isEqualTo(hashes.revision(roleReads.snapshot(id)));
        assertThat(result.get("value").get("settingsRevision").asText())
            .isEqualTo(hashes.revision(roleReads.settingsSnapshot()));
    }
    @Test void commitExcludesActiveSelectionButPreservesPermissionsIdSortAndPriorHistory() throws Exception {
        var before=role();var permissions=permissions();
        sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,aft_val,reg_user_id) VALUES ('TB_SY_ROLE',?,'NEW','ROLE_ID',?,'admin')",id,id);
        var past=sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_key=?",id);
        stop(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.value.use").value(false)).andExpect(jsonPath("$.data.changed").value(true));
        var after=role();assertThat(after.get("use_yn")).isEqualTo("N");
        for(String field:before.keySet())if(!Set.of("use_yn","mod_user_id","mod_dttm").contains(field))assertThat(after.get(field)).isEqualTo(before.get(field));
        assertThat(permissions()).isEqualTo(permissions);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_role WHERE role_id=? AND use_yn='Y'",Long.class,id)).isZero();
        assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_key=? ORDER BY hist_id",id)).containsAll(past);
        assertThat(historyCount()).isEqualTo(2);assertThat(accessCount()).isEqualTo(1);
        assertThat(sql.queryForMap("SELECT chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_type_cd='STOP'",id))
            .containsEntry("chg_item_nm","USE_YN").containsEntry("bfr_val","Y").containsEntry("aft_val","N").containsEntry("reg_user_id","admin");
        assertThat(sql.queryForMap("SELECT act_type_cd,scrn_id,user_id,reg_user_id FROM tb_sy_accs_log WHERE trgt_desc=?",id))
            .containsEntry("act_type_cd","AUTH_CHG").containsEntry("scrn_id","UI-24").containsEntry("user_id","admin").containsEntry("reg_user_id","admin");
        stop(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        assertThat(role()).isEqualTo(after);assertThat(historyCount()).isEqualTo(2);assertThat(accessCount()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"USE","LOCK","STOP"})
    void assignmentInEveryUserStateBlocksStop(String status) throws Exception {
        String user="user"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT ?,?,'test@example.org',pwd_hash,0,?,0,'admin' FROM tb_sy_user WHERE user_id='admin'",user,user,status);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'admin')",user,id);
        var before=role();var permissions=permissions();
        stop(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_IN_USE"))
            .andExpect(jsonPath("$.message").value("1명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요."));
        assertThat(role()).isEqualTo(before);assertThat(permissions()).isEqualTo(permissions);assertThat(historyCount()).isZero();assertThat(accessCount()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"history","access"})
    void failedRequiredRecordRollsBackBusinessAuditAndPermissions(String failing) throws Exception {
        var before=role();var permissions=permissions();String input=body();
        if(failing.equals("history"))sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_role_stop CHECK(chg_type_cd <> 'STOP')");
        else sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_role_access CHECK(act_type_cd <> 'AUTH_CHG')");
        stop(input).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(role()).isEqualTo(before);assertThat(permissions()).isEqualTo(permissions);assertThat(historyCount()).isZero();assertThat(accessCount()).isZero();
    }
    @Test void permissionAuditMicrosecondsAndNullAuditValuesAreRevisionMaterial() throws Exception {
        String prior=body();
        sql.update("UPDATE tb_sy_role_menu_auth SET mod_dttm=TIMESTAMP '2026-01-01 00:00:00.123456' WHERE role_id=?",id);
        assertThat(body()).isNotEqualTo(prior);var before=role();
        stop(prior).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        String next=body();sql.update("UPDATE tb_sy_role_menu_auth SET mod_dttm=TIMESTAMP '2026-01-01 00:00:00.123457' WHERE role_id=?",id);
        assertThat(body()).isNotEqualTo(next);stop(next).andExpect(status().isConflict());
        assertThat(role()).isEqualTo(before);assertThat(historyCount()).isZero();assertThat(accessCount()).isZero();
    }
}
