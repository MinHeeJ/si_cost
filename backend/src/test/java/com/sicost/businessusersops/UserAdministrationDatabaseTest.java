package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.businessusers.*;
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
import static com.sicost.businessusersops.UserAdministrationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UserAdministrationDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BusinessUserService users;
    UserAdministrationService service;
    ObjectMapper json;
    MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.addMapper(com.sicost.authentication.AuthenticationMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BusinessUserMapper.class,()->session.getMapper(BusinessUserMapper.class));
        context.registerBean(UserAdministrationMapper.class,()->session.getMapper(UserAdministrationMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(com.sicost.authentication.AuthenticationMapper.class,()->session.getMapper(com.sicost.authentication.AuthenticationMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,RevisionService.class,
            CommitInvalidation.class,SettlementSourceLocks.class,WriteCoordinator.class,BusinessUserRepository.class,BusinessUserService.class,
            UserAdministrationRepository.class,UserAdministrationService.class);
        context.refresh();users=context.getBean(BusinessUserService.class);service=context.getBean(UserAdministrationService.class);
        mvc=MockMvcBuilders.standaloneSetup(new UserAdministrationController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    String user(String state) {
        String id="u"+UUID.randomUUID().toString().replace("-","").substring(0,15);
        users.create(new BusinessUserDtos.Create(id,"사용자 "+id,id+"@example.org",List.of("PM")),new org.springframework.mock.web.MockHttpServletRequest());
        sql.update("UPDATE tb_sy_user SET user_stat_cd=?,pwd_err_cnt=7 WHERE user_id=?",state,id);return id;
    }
    String revision(String id) {return users.get(id).revision();}
    long history(String id) {return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_USER' AND trgt_key=?",Long.class,id);}
    long accessCount() {return sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log",Long.class);}
    @Test void stopResumePreserveEveryAuthenticationAndChildValueAndNoOpHasNoWrites() throws Exception {
        String id=user("USE");var original=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id);
        var roles=sql.queryForList("SELECT * FROM tb_sy_user_role WHERE user_id=?",id);var scopes=sql.queryForList("SELECT * FROM tb_sy_user_scope WHERE user_id=?",id);
        long events=accessCount(),initial=history(id);
        var stopped=service.change(id,revision(id),"STOP");assertThat(stopped.user().status()).isEqualTo("STOP");
        var resumed=service.change(id,stopped.user().revision(),"RESUME");assertThat(resumed.user().status()).isEqualTo("USE");
        var after=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id);
        for(String key:List.of("pwd_hash","temp_pwd_yn","pwd_chg_dttm","pwd_err_cnt","mfa_use_yn","mfa_code_hash","mfa_expr_dttm","mfa_err_cnt","last_login_dttm"))
            assertThat(Objects.equals(original.get(key),after.get(key))).as(key+" preserved").isTrue();
        assertThat(sql.queryForList("SELECT * FROM tb_sy_user_role WHERE user_id=?",id)).isEqualTo(roles);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_user_scope WHERE user_id=?",id)).isEqualTo(scopes);
        assertThat(service.change(id,resumed.user().revision(),"RESUME").changed()).isFalse();
        assertThat(history(id)).isEqualTo(initial+2);assertThat(accessCount()).isEqualTo(events);
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id).equals(after)).isTrue();
    }
    @Test void unlockChangesOnlyStateErrorCountAndModAndAuditsOnlyState() {
        String id=user("LOCK");var before=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id);long events=accessCount(),initial=history(id);
        service.change(id,revision(id),"UNLOCK");var after=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id);
        assertThat(after.get("user_stat_cd")).isEqualTo("USE");assertThat(((Number)after.get("pwd_err_cnt")).intValue()).isZero();
        for(String key:before.keySet())if(!Set.of("user_stat_cd","pwd_err_cnt","mod_user_id","mod_dttm").contains(key))assertThat(Objects.equals(before.get(key),after.get(key))).as(key+" preserved").isTrue();
        assertThat(history(id)).isEqualTo(initial+1);assertThat(accessCount()).isEqualTo(events);
        assertThat(sql.queryForList("SELECT chg_item_nm FROM tb_sy_chg_hist WHERE trgt_key=? AND bfr_val='LOCK'",String.class,id)).containsExactly("USER_STAT_CD");
    }
    @Test void failedAuditRollsBackStateAndUnlockErrorCount() {
        for(String action:List.of("STOP","RESUME","UNLOCK")) {
            String id=user(action.equals("STOP")?"USE":action.equals("RESUME")?"STOP":"LOCK");
            var before=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id);String revision=revision(id);long initial=history(id);
            sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_state_history CHECK (trgt_key <> '"+id+"') NOT VALID");
            assertThatThrownBy(()->service.change(id,revision,action)).isInstanceOf(RuntimeException.class);
            assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id=?",id).equals(before)).isTrue();assertThat(history(id)).isEqualTo(initial);
            sql.execute("ALTER TABLE tb_sy_chg_hist DROP CONSTRAINT reject_state_history");
        }
    }
    @Test void bulkSecondAuditFailureRollsBackOnlyThatItem() {
        String first=user("USE"),second=user("USE");var ids=new ArrayList<>(List.of(first,second));Collections.sort(ids);first=ids.get(0);second=ids.get(1);
        String a=first,b=second;sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_second_state CHECK (trgt_key <> '"+b+"') NOT VALID");
        var result=service.bulk(UserAdministrationInput.bulk(json.valueToTree(Map.of("action","STOP","items",List.of(new StatusItem(b,revision(b)),new StatusItem(a,revision(a)))))));
        assertThat(result.applied()).isEqualTo(1);assertThat(result.rejected()).isEqualTo(1);
        assertThat(users.get(a).status()).isEqualTo("STOP");assertThat(users.get(b).status()).isEqualTo("USE");
    }
    @Test void snapshotIncludesFullChildrenStatusAndMicrosecondAuditButNotLabelsOrLastLogin() {
        String id=user("USE"),revision=revision(id);
        sql.update("UPDATE tb_sy_user SET last_login_dttm=CURRENT_TIMESTAMP WHERE user_id=?",id);assertThat(revision(id)).isEqualTo(revision);
        sql.update("UPDATE tb_sy_user_role SET mod_dttm=TIMESTAMP '2026-06-15 00:00:00.000001' WHERE user_id=?",id);assertThat(revision(id)).isNotEqualTo(revision);
        String changed=revision(id);sql.update("UPDATE tb_sy_user_role SET mod_dttm=TIMESTAMP '2026-06-15 00:00:00.000002' WHERE user_id=?",id);assertThat(revision(id)).isNotEqualTo(changed);
        assertThatThrownBy(()->service.change(id,revision,"STOP")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
    @Test void realActivityUsesActorNotRegistrationTargetAndRetainsNullLegacyFields() throws Exception {
        String actor=user("USE"),target=user("USE");long before=accessCount();
        sql.update("INSERT INTO tb_sy_accs_log(user_id,act_type_cd,scrn_id,trgt_desc,act_dttm,reg_user_id) VALUES (?,'USER_REG',NULL,NULL,TIMESTAMP '2026-06-15 09:00',?)",actor,actor);
        sql.update("INSERT INTO tb_sy_accs_log(user_id,act_type_cd,scrn_id,trgt_desc,act_dttm,reg_user_id) VALUES (?,'AUTH_CHG','UI-23',?,TIMESTAMP '2026-06-15 09:00',?)",actor,target,actor);
        mvc.perform(get("/api/business-users/"+actor+"/access-logs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].actionType").value("AUTH_CHG")).andExpect(jsonPath("$.data.items[1].screenId").value(""))
            .andExpect(jsonPath("$.data.items[1].targetDescription").value(""));
        assertThat(service.activities(new ActivityQuery(target,1,50,0)).items()).isEmpty();assertThat(accessCount()).isEqualTo(before+2);
    }
    @Test void scopesUseCurrentSeoulMonthUnionAndFutureRowsProtectStop() {
        String id=user("USE"),part="p"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,'미래 중지 파트','N','N',?)",part,id);
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES (?,'202607',NULL,?,40,0,?)",part,id,id);
        assertThat(service.scopes(id).assignedParts()).isEmpty();
        assertThatThrownBy(()->service.change(id,revision(id),"STOP")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("USER_IS_PART_LEADER"));
        sql.update("UPDATE tb_bs_part_std SET aply_bgn_ym='202606' WHERE part_id=?",part);
        assertThat(service.scopes(id).assignedParts()).extracting(BusinessUserDtos.AssignedPart::partId).containsExactly(part);
        sql.update("UPDATE tb_bs_part_std SET aply_bgn_ym='202601',aply_end_ym='202605' WHERE part_id=?",part);
        assertThat(service.scopes(id).assignedParts()).isEmpty();assertThat(service.change(id,revision(id),"STOP").changed()).isTrue();
    }
}
