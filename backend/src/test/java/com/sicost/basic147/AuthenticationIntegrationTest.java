package com.sicost.basic147;

import com.fasterxml.jackson.databind.*;
import com.sicost.common.CostManagementApplication;
import com.sicost.permission.PermissionOperationRegistry;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs only against an explicitly supplied disposable database and real SMTP collector. */
@SpringBootTest(classes={CostManagementApplication.class,AuthenticationIntegrationTest.Time.class})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named="BASIC147_INTEGRATION",matches="true")
class AuthenticationIntegrationTest {
    @TestConfiguration static class Time { @Bean @Primary Clock integrationClock(){return Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"),ZoneId.of("Asia/Seoul"));} }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired javax.sql.DataSource database;
    @org.springframework.boot.test.mock.mockito.SpyBean com.sicost.authentication.AuthenticationMailService authenticationMail;
    @org.springframework.boot.test.mock.mockito.SpyBean com.sicost.authentication.AuthenticationAuditService authenticationAudit;
    @Autowired PermissionOperationRegistry registry;
    @Autowired RequestMappingHandlerMapping mappings;
    String id,password;
    @BeforeEach void fixture() {
        id="b147"+UUID.randomUUID().toString().replace("-","").substring(0,16);password="Aa1!"+UUID.randomUUID();
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_chg_dttm,temp_pwd_yn,pwd_err_cnt,user_stat_cd,mfa_use_yn,mfa_err_cnt,reg_user_id,reg_dttm) VALUES (?,?,?,?,'2026-11-16','N',0,'USE','N',0,'SYSTEM',CURRENT_TIMESTAMP)",id,"인증 합성 사용자",id+"@example.test",new BCryptPasswordEncoder(10).encode(password));
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'PM','SYSTEM')",id);
    }
    record Token(MockHttpSession session,String header,String token) {}
    Token csrf(MockHttpSession session) throws Exception {
        var request=get("/api/auth/csrf");if(session!=null)request.session(session);
        var result=mvc.perform(request).andExpect(status().isOk()).andReturn();
        var body=json.readTree(result.getResponse().getContentAsString()).path("data");
        return new Token((MockHttpSession)result.getRequest().getSession(false),body.path("headerName").asText(),body.path("token").asText());
    }
    MvcResult login(Token token,String value) throws Exception {return mvc.perform(post("/api/auth/login").session(token.session()).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("userId",id,"password",value)))).andReturn();}
    @Test void anonymousAndCsrfUseJsonWithoutBusinessData() throws Exception {
        mvc.perform(get("/api/current-user")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED")).andExpect(jsonPath("$.requestId").isString());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}" )).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("LOGIN")).andExpect(jsonPath("$.data.userId").doesNotExist());
    }
    @Test void realPasswordCreatesOnlyItsOwnPrincipalAndLogoutDiscardsIt() throws Exception {
        Token token=csrf(null);var logged=login(token,password);assertThat(logged.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(logged.getResponse().getContentAsString()).path("data").path("nextStep").asText()).isEqualTo("AUTHENTICATED");
        var session=(MockHttpSession)logged.getRequest().getSession(false);
        var current=mvc.perform(get("/api/current-user").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(id)).andExpect(jsonPath("$.data.developmentMode").value(false)).andExpect(jsonPath("$.data.accessEnforcement").value(true)).andExpect(jsonPath("$.data.capabilities.userId").value(id)).andReturn();
        var keys=new HashSet<String>();json.readTree(current.getResponse().getContentAsString()).path("data").fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("userId","name","roleIds","developmentMode","accessEnforcement","capabilities");
        mvc.perform(get("/api/menus").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").isString()).andExpect(jsonPath("$.data.developmentMode").value(false)).andExpect(jsonPath("$.data.accessEnforcement").value(true));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isEqualTo(1);
        var refreshed=csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).header(refreshed.header(),refreshed.token())).andExpect(status().isOk());
        assertThat(session.isInvalid()).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGOUT'",Long.class,id)).isEqualTo(1);
    }
    @Test void passwordFailuresCommitAndFifthLocksIncludingAnAdministrator() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        for(int count=1;count<=5;count++) {
            var result=login(csrf(null),"Wrong123!");assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isEqualTo(count);
        }
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("LOCK");
        assertThat(sql.queryForObject("SELECT last_login_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isNull();
    }
    @Test void requiredLoginAuditFailureCannotInstallABusinessPrincipal() throws Exception {
        org.mockito.Mockito.doThrow(new com.sicost.common.api.ApiException(503,"SYNTHETIC_AUDIT_UNAVAILABLE","기록을 확인할 수 없습니다."))
            .when(authenticationAudit).append(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("LOGIN"),org.mockito.ArgumentMatchers.any());
        var result=login(csrf(null),password);assertThat(result.getResponse().getStatus()).isEqualTo(503);
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").doesNotExist());
        assertThat(sql.queryForObject("SELECT last_login_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isNull();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isZero();
    }
    @Test void failedAttemptAuditCannotRollBackTheFifthFailureOrAdministratorLock() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        org.mockito.Mockito.doThrow(new com.sicost.common.api.ApiException(503,"SYNTHETIC_AUDIT_UNAVAILABLE","기록을 확인할 수 없습니다."))
            .when(authenticationAudit).append(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("LOGIN_FAIL"),org.mockito.ArgumentMatchers.any());
        for(int count=1;count<=5;count++) {
            assertThat(login(csrf(null),"Wrong123!").getResponse().getStatus()).isEqualTo(503);
            assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isEqualTo(count);
        }
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("LOCK");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isZero();
    }
    @Test void logoutDiscardsTheSessionEvenWhenItsRequiredAuditFails() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);var token=csrf(session);
        org.mockito.Mockito.doThrow(new com.sicost.common.api.ApiException(503,"SYNTHETIC_AUDIT_UNAVAILABLE","기록을 확인할 수 없습니다."))
            .when(authenticationAudit).append(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("LOGOUT"),org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/auth/logout").session(session).header(token.header(),token.token())).andExpect(status().isServiceUnavailable());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/current-user")).andExpect(status().isUnauthorized());
    }
    @Test void temporaryPasswordIsNotABusinessSessionAndChangingItRotatesProof() throws Exception {
        sql.update("UPDATE tb_sy_user SET temp_pwd_yn='Y' WHERE user_id=?",id);
        var logged=login(csrf(null),password);assertThat(logged.getResponse().getStatus()).isEqualTo(200);
        var session=(MockHttpSession)logged.getRequest().getSession(false);
        mvc.perform(get("/api/menus").session(session)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INCOMPLETE")).andExpect(jsonPath("$.details.nextStep").doesNotExist());
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isZero();
        String oldId=session.getId();var token=csrf(session);String next="Bb2!"+UUID.randomUUID();
        mvc.perform(post("/api/auth/password/required").session(session).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("newPassword",next,"confirmPassword",next))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("AUTHENTICATED"));
        assertThat(session.getId()).isNotEqualTo(oldId);
        assertThat(sql.queryForObject("SELECT temp_pwd_yn FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("N");
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isOk());
    }
    @Test void administratorMustUseRealSmtpCodeBeforeTemporaryPasswordChange() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        sql.update("UPDATE tb_sy_user SET temp_pwd_yn='Y' WHERE user_id=?",id);
        var logged=login(csrf(null),password);assertThat(logged.getResponse().getStatus()).isEqualTo(200);
        var body=json.readTree(logged.getResponse().getContentAsString()).path("data");assertThat(body.path("nextStep").asText()).isEqualTo("MFA");assertThat(body.path("stageExpiresAt").asText()).isNotBlank();assertThat(body.has("mailStatus")).isFalse();
        var session=(MockHttpSession)logged.getRequest().getSession(false);
        mvc.perform(get("/api/menus").session(session)).andExpect(status().isUnauthorized());
        var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(System.getenv("BASIC147_MAIL_COLLECTOR")+"/api/v2/messages")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        var messages=json.readTree(response.body()).path("items");String code=null;
        for(JsonNode message:messages) {
            String recipient=message.path("Content").path("Headers").path("To").toString();
            if(recipient.contains(id+"@")) {
                String content=message.path("Content").path("Body").asText();
                if(message.path("Content").path("Headers").path("Content-Transfer-Encoding").toString().contains("base64"))
                    content=new String(java.util.Base64.getMimeDecoder().decode(content),java.nio.charset.StandardCharsets.UTF_8);
                var matcher=java.util.regex.Pattern.compile("[0-9]{6}").matcher(content);
                if(matcher.find()) {code=matcher.group();break;}
            }
        }
        assertThat(code).isNotNull();var token=csrf(session);
        String committedHash=sql.queryForObject("SELECT mfa_code_hash FROM tb_sy_user WHERE user_id=?",String.class,id);
        int committedMailCount=json.readTree(response.body()).path("total").asInt();assertThat(json.readTree(response.body()).has("total")).isTrue();
        for(String path:List.of("mfa/resend","restart")) {
            mvc.perform(post("/api/auth/"+path).session(session).header(token.header(),token.token()).contentType("application/json").content("{\"recipient\":\"synthetic-other@example.test\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
            assertThat(session.isInvalid()).isFalse();
            assertThat(sql.queryForObject("SELECT mfa_code_hash FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo(committedHash);
            mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("MFA"));
        }
        var after=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(System.getenv("BASIC147_MAIL_COLLECTOR")+"/api/v2/messages")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(json.readTree(after.body()).path("total").asInt()).isEqualTo(committedMailCount);
        String wrong=code.equals("000000")?"111111":"000000";
        mvc.perform(post("/api/auth/mfa/verify").session(session).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("code",wrong))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_INVALID"));
        assertThat(sql.queryForObject("SELECT mfa_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isEqualTo(1);
        mvc.perform(post("/api/auth/mfa/verify").session(session).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("code",code))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("CHANGE_PASSWORD"));
        assertThat(sql.queryForObject("SELECT mfa_code_hash FROM tb_sy_user WHERE user_id=?",String.class,id)).isNull();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isZero();
    }
    @Test void expiredMfaUses401WithoutIncrementingFailures() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        var result=login(csrf(null),password);assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var session=(MockHttpSession)result.getRequest().getSession(false);var token=csrf(session);
        sql.update("UPDATE tb_sy_user SET mfa_expr_dttm='2026-11-17 11:59:59' WHERE user_id=?",id);
        mvc.perform(post("/api/auth/mfa/verify").session(session).header(token.header(),token.token()).contentType("application/json").content("{\"code\":\"000000\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MFA_EXPIRED"));
        assertThat(sql.queryForObject("SELECT mfa_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("MFA"));
    }
    @Test void passwordRecoveryReleasesTheDatabaseRowBeforeActualSmtpAndRevokesOldSessions() throws Exception {
        var oldSession=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        var checked=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(invocation->{
            try(var connection=database.getConnection()) {
                connection.setAutoCommit(false);
                try(var statement=connection.prepareStatement("SELECT temp_pwd_yn,pwd_err_cnt FROM tb_sy_user WHERE user_id=? FOR UPDATE NOWAIT")) {
                    statement.setString(1,id);
                    try(var row=statement.executeQuery()) {assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("Y");assertThat(row.getInt(2)).isZero();}
                } finally {connection.rollback();}
            }
            checked.set(true);return invocation.callRealMethod();
        }).when(authenticationMail).send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        var token=csrf(null);
        mvc.perform(post("/api/auth/forgot-password").session(token.session()).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("userId",id,"email",id+"@example.test"))))
            .andExpect(status().isOk());
        assertThat(checked.get()).isTrue();
        var delivered=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(System.getenv("BASIC147_MAIL_COLLECTOR")+"/api/v2/messages")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        boolean received=false;for(var message:json.readTree(delivered.body()).path("items"))if(message.path("Content").path("Headers").path("To").toString().contains(id+"@example.test"))received=true;
        assertThat(received).isTrue();
        assertThat(new BCryptPasswordEncoder(10).matches(password,sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id))).isFalse();
        mvc.perform(get("/api/current-user").session(oldSession)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        assertThat(oldSession.isInvalid()).isTrue();
    }
    @Test void scopedCandidateUsersCanReadSharedLockMetadataButNotCloseActions() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        mvc.perform(get("/api/month-close").session(session).param("from","2026-11").param("to","2026-11")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
        mvc.perform(get("/api/month-closes").session(session)).andExpect(status().isForbidden());
    }
    @Test void storedNotificationsAreReprojectedForCurrentTargetScopeAndMenuWithoutRewritingHistory() throws Exception {
        String own=id.substring(0,18)+"A",other=id.substring(0,18)+"B";
        for(String project:List.of(own,other))sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'SI','PROG','2026-01-01','2026-12-31',1000,0,0,'SYSTEM')",project,project);
        for(String bill:List.of(own,other))sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'202611','2026-11-01',100,0,0,100,0,100,'2026-11-30','N','SYSTEM')",bill,bill);
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) VALUES (?,?,'PROJ','N','N',999,'Y','SYSTEM')",id,"합성 알림 조회 "+id);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'SYSTEM')",id,id);
        sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='PM'",id);
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) SELECT ?,menu_id,'Y','N','N','N','N','N','N','SYSTEM' FROM tb_sy_menu WHERE scrn_id='UI-13' AND menu_lvl=2",id);
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES (?,1,'PROJ',?,'SYSTEM')",id,own);
        String foreign=id+"x";
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_use_yn,mfa_err_cnt,reg_user_id) VALUES (?,? ,?,?,0,'USE','N',0,'SYSTEM')",foreign,"합성 다른 수신자",foreign+"@example.test",new BCryptPasswordEncoder(10).encode(UUID.randomUUID().toString()));
        var visible=notification(id,"DUN","허용 고객 731000","UI-13",own);
        var denied=notification(id,"DUN","범위 밖 고객 991234","UI-13",other);
        notification(id,"AI_DONE","예전 질문 원가 667890","UI-26","7");
        var notOwned=notification(foreign,"DUN","다른 수신자 556789","UI-13",own);
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        var first=mvc.perform(get("/api/notifications").session(session)).andExpect(status().isOk()).andReturn();
        var items=json.readTree(first.getResponse().getContentAsByteArray()).path("data").path("items");
        assertThat(items.size()).isEqualTo(3);
        assertThat(items.toString()).contains("허용 고객 731000","현재 대상 조회 권한이 없는 알림입니다","AI 예측 완료").doesNotContain("991234","667890","556789");
        var token=csrf(session);
        var marked=mvc.perform(post("/api/notifications/"+denied+"/read").session(session).header(token.header(),token.token())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.notification.link").isEmpty()).andReturn();
        assertThat(json.readTree(marked.getResponse().getContentAsByteArray()).path("data").path("notification").path("content").asText()).isEqualTo("현재 대상 조회 권한이 없는 알림입니다");
        mvc.perform(post("/api/notifications/"+notOwned+"/read").session(session).header(token.header(),token.token())).andExpect(status().isNotFound());
        sql.update("DELETE FROM tb_sy_role_menu_auth WHERE role_id=?",id);
        var refreshed=mvc.perform(get("/api/notifications").session(session)).andExpect(status().isOk()).andReturn();
        assertThat(refreshed.getResponse().getContentAsString()).doesNotContain("731000","991234","667890","556789");
        assertThat(sql.queryForObject("SELECT noti_cn FROM tb_sy_noti WHERE noti_id=?",String.class,visible)).isEqualTo("허용 고객 731000");
        assertThat(sql.queryForObject("SELECT noti_cn FROM tb_sy_noti WHERE noti_id=?",String.class,denied)).isEqualTo("범위 밖 고객 991234");
    }
    private java.math.BigDecimal notification(String recipient,String type,String content,String screen,String key) {
        return sql.queryForObject("INSERT INTO tb_sy_noti(rcvr_user_id,noti_type_cd,noti_cn,send_mthd_cd,read_yn,noti_dttm,link_scrn_id,link_key,reg_user_id) VALUES (?,?,?,'SYS','N','2026-11-17 12:00:00',?,?,'SYSTEM') RETURNING noti_id",java.math.BigDecimal.class,recipient,type,content,screen,key);
    }
    @Test void userManagementRoleLabelsDoNotRequireFullRoleMatrixPermission() throws Exception {
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) VALUES (?,?,'PROJ','N','N',999,'Y','SYSTEM')",id,"합성 사용자 선택 "+id);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'SYSTEM')",id,id);
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) SELECT ?,menu_id,'Y','Y','Y','N','N','N','N','SYSTEM' FROM tb_sy_menu WHERE scrn_id='UI-23' AND menu_lvl=2",id);
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        mvc.perform(get("/api/business-users/role-options").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].roleId").isString()).andExpect(jsonPath("$.data.items[0].menuPermissions").doesNotExist());
        mvc.perform(get("/api/roles").session(session)).andExpect(status().isForbidden());
    }
    @Test void explicitOperatorProcessCannotCreatePromoteOrResumeAndPreservesCredentialsOnUnlock() throws Exception {
        String next="Operator1!"+UUID.randomUUID();
        String original=sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id);
        assertThat(operator("reset",id,next)).isEqualTo(1);
        assertThat(Objects.equals(original,sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id))).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user_role WHERE user_id=? AND role_id='ADMIN'",Long.class,id)).isZero();
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        assertThat(operator("reset",id,next)).isZero();
        String issued=sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id);
        assertThat(new BCryptPasswordEncoder(10).matches(next,issued)).isTrue();
        assertThat(sql.queryForObject("SELECT temp_pwd_yn FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("Y");
        assertThat(sql.queryForObject("SELECT mfa_use_yn FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("Y");
        sql.update("UPDATE tb_sy_user SET user_stat_cd='LOCK',pwd_err_cnt=5 WHERE user_id=?",id);
        assertThat(operator("unlock",id,null)).isZero();
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("USE");
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        assertThat(Objects.equals(issued,sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id))).isTrue();
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id=?",id);
        assertThat(operator("unlock",id,null)).isEqualTo(1);assertThat(operator("reset",id,next)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("STOP");
        assertThat(Objects.equals(issued,sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id=?",String.class,id))).isTrue();
        String missing=id+"x";assertThat(operator("reset",missing,next)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id=?",Long.class,missing)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_USER' AND trgt_key=? AND chg_item_nm='운영자 복구' AND reg_user_id='SYSTEM'",Long.class,id)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=?",Long.class,id)).isZero();
        assertThat(registry.operations().stream().anyMatch(operation->operation.handler().contains("AuthenticationAdminCli"))).isFalse();
    }
    private int operator(String action,String target,String secret) throws Exception {
        var command=new ArrayList<>(List.of(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"com.sicost.authentication.AuthenticationAdminCli",action,target));
        if(action.equals("reset"))command.add(target+"@example.test");
        var builder=new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("SPRING_FLYWAY_ENABLED","false");
        if(secret!=null)builder.environment().put("AUTH_INITIAL_PASSWORD",secret);else builder.environment().remove("AUTH_INITIAL_PASSWORD");
        var process=builder.start();
        try {
            assertThat(process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)).as("operator process terminates").isTrue();
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            if(secret!=null)assertThat(output.contains(secret)).as("operator never prints its hidden environment input").isFalse();
            return process.exitValue();
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    @Test void hashAndStatusRevocationInvalidateRealBusinessSessions() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        sql.update("UPDATE tb_sy_user SET pwd_hash=? WHERE user_id=?",new BCryptPasswordEncoder(10).encode("New1!"+UUID.randomUUID()),id);
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        assertThat(session.isInvalid()).isTrue();
        sql.update("UPDATE tb_sy_user SET pwd_hash=? WHERE user_id=?",new BCryptPasswordEncoder(10).encode(password),id);
        session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id=?",id);
        mvc.perform(get("/api/menus").session(session)).andExpect(status().isUnauthorized());assertThat(session.isInvalid()).isTrue();
    }
    @Test void newlyGrantedAdministratorCannotReuseANonMfaSession() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INCOMPLETE"));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isEqualTo(1);
        sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='ADMIN'",id);
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.roleIds[0]").value("PM"));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGIN'",Long.class,id)).isEqualTo(1);
    }
    @Test void expiredSessionIsNeverExtendedByStatusPollingOrLoggedAsAnExplicitLogout() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        var state=session.getAttribute(com.sicost.authentication.AuthenticationState.KEY);
        var activity=state.getClass().getDeclaredField("activity");activity.setAccessible(true);
        var old=Instant.parse("2026-11-17T02:59:00Z");activity.set(state,old);
        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());assertThat(activity.get(state)).isEqualTo(old);
        activity.set(state,Instant.parse("2026-11-16T00:00:00Z"));var token=csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).header(token.header(),token.token())).andExpect(status().isOk());assertThat(session.isInvalid()).isTrue();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='LOGOUT'",Long.class,id)).isZero();
    }
    @Test void registeredBackgroundPollingDoesNotExtendButAUserReadDoes() throws Exception {
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        var state=session.getAttribute(com.sicost.authentication.AuthenticationState.KEY);
        var activity=state.getClass().getDeclaredField("activity");activity.setAccessible(true);
        var old=Instant.parse("2026-11-17T02:59:00Z");activity.set(state,old);
        mvc.perform(get("/api/notifications/unread-count").header("X-Background-Request","true").session(session)).andExpect(status().isOk());
        assertThat(activity.get(state)).isEqualTo(old);
        mvc.perform(get("/api/notifications/unread-count").session(session)).andExpect(status().isOk());
        assertThat(activity.get(state)).isEqualTo(Instant.parse("2026-11-17T03:00:00Z"));
        activity.set(state,old);
        mvc.perform(get("/api/current-user").header("X-Background-Request","true").session(session)).andExpect(status().isOk());
        assertThat(activity.get(state)).isEqualTo(Instant.parse("2026-11-17T03:00:00Z"));
    }
    @Test void projectListScopeIsAppliedInSqlBeforeTheCountAndPage() throws Exception {
        String own=id.substring(0,19)+"A",other=id.substring(0,19)+"B";
        for(String project:List.of(own,other))sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'SI','PROG','2026-01-01','2026-12-31',1000,0,0,'SYSTEM')",project,project);
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES (?,1,'PROJ',?,'SYSTEM')",id,own);
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        mvc.perform(get("/api/projects").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(own));
    }
    @Test void pmCandidateJsonAndActualWorkbookHaveNoDesiredRate() throws Exception {
        String candidate=sql.queryForObject("SELECT 'CAND-'||lpad(nextval('SQ_TB_HR_CAND_ID')::text,15,'0')",String.class);
        sql.update("INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,cand_src_cd,reg_user_id) VALUES (?,?,'FL','DEV','MD',7312345,'2026-12-01','BEFORE','SITE','SYSTEM')",candidate,candidate);
        String exportRole=id;
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) VALUES (?,?,'PROJ','N','N',999,'Y','SYSTEM')",exportRole,"합성 후보 다운로드 "+id);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'SYSTEM')",id,exportRole);
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) SELECT ?,menu_id,'Y','N','N','N','N','Y','N','SYSTEM' FROM tb_sy_menu WHERE scrn_id='UI-33' AND menu_lvl=2",exportRole);
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        mvc.perform(get("/api/candidates/"+candidate).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredRate").doesNotExist()).andExpect(jsonPath("$.data.availableFields").isArray());
        var token=csrf(session);
        var exported=mvc.perform(post("/api/exports/master-data").session(session).header(token.header(),token.token()).contentType("application/json").content("{\"view\":\"CANDIDATES\",\"filters\":{},\"columns\":[\"name\",\"desiredRate\"],\"amountUnit\":\"원\"}")).andExpect(status().isOk()).andReturn();
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(exported.getResponse().getContentAsByteArray()))) {
            StringBuilder text=new StringBuilder();for(var sheet:workbook)for(var row:sheet)for(var cell:row)text.append(new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell)).append('|');
            assertThat(text.toString()).contains(candidate).doesNotContain("희망 단가","7312345");
        }
        mvc.perform(get("/api/candidates").param("sort","desiredRate").session(session)).andExpect(status().isBadRequest());
    }
    @Test void everyActualControllerMappingHasAnExactServerPolicy() {
        var declared=new HashSet<String>();registry.operations().forEach(operation->declared.add(operation.method()+" "+operation.path()+" "+operation.handler()));
        var actual=new HashSet<String>();
        mappings.getHandlerMethods().forEach((mapping,handler)->mapping.getPatternValues().stream().filter(path->path.startsWith("/api/")).forEach(path->mapping.getMethodsCondition().getMethods().forEach(method->
            actual.add(method.name()+" "+path+" "+handler.getBeanType().getSimpleName()+"."+handler.getMethod().getName()))));
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
    }
    @Test void callerScreenRefererIdentityAndSystemHeadersNeverReplaceTheServerPolicy() throws Exception {
        mvc.perform(get("/api/current-user").header("X-User-ID","SYSTEM").header("X-Role","ADMIN").header("X-System","true"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED")).andExpect(jsonPath("$.data").doesNotExist());
        var session=(MockHttpSession)login(csrf(null),password).getRequest().getSession(false);
        mvc.perform(get("/api/current-user").session(session).header("X-User-ID","admin").header("X-Role","ADMIN").header("X-System","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(id)).andExpect(jsonPath("$.data.roleIds[0]").value("PM"));
        mvc.perform(get("/api/roles").session(session).header("X-Screen-ID","UI-33").header("Referer","http://localhost/UI-33").header("X-User-ID","SYSTEM").header("X-Role","ADMIN").header("X-System","true").param("screenId","UI-33"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN")).andExpect(jsonPath("$.data").doesNotExist());
    }
}
