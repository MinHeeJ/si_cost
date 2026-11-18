package com.sicost.basic147;

import com.sicost.common.CostManagementApplication;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reuses the real-database/SMTP journeys and adds exact commit/clock/concurrency boundaries. */
@SpringBootTest(classes={CostManagementApplication.class,AuthenticationCommitBoundaryTest.MutableTime.class})
@EnabledIfEnvironmentVariable(named="BASIC147_INTEGRATION",matches="true")
class AuthenticationCommitBoundaryTest extends AuthenticationIntegrationTest {
    @TestConfiguration static class MutableTime {
        @Bean @Primary AuthenticationFixtures.MutableClock boundaryClock() {return new AuthenticationFixtures.MutableClock(Instant.parse("2026-11-17T03:00:00Z"));}
    }
    @Autowired AuthenticationFixtures.MutableClock clock;
    @BeforeEach void resetClock() {clock.set(Instant.parse("2026-11-17T03:00:00Z"));}
    @Test void concurrentFifthPasswordFailureCommitsExactlyOneLockWithoutSuccessfulLogin() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        List<Token> tokens=new ArrayList<>();for(int i=0;i<5;i++)tokens.add(csrf(null));
        var executor=Executors.newFixedThreadPool(5);var start=new CountDownLatch(1);
        try {
            List<Future<Integer>> results=new ArrayList<>();
            for(Token token:tokens)results.add(executor.submit(()->{start.await();return login(token,"NotTheSyntheticPassword1!").getResponse().getStatus();}));
            start.countDown();for(var result:results)assertThat(result.get(45,TimeUnit.SECONDS)).isEqualTo(401);
        } finally {executor.shutdownNow();}
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isEqualTo(5);
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("LOCK");
        assertThat(sql.queryForObject("SELECT last_login_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isNull();
        assertThat(sql.queryForObject("SELECT mod_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isEqualTo(LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul")));
    }
    @Test void correctPasswordClearsFailuresWithoutFabricatingABusinessModification() throws Exception {
        sql.update("UPDATE tb_sy_user SET pwd_err_cnt=4 WHERE user_id=?",id);
        var before=sql.queryForObject("SELECT mod_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id);
        assertThat(login(csrf(null),password).getResponse().getStatus()).isEqualTo(200);
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        assertThat(sql.queryForObject("SELECT mod_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT last_login_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id)).isNotNull();
    }
    @Test void exactFiveMinuteMfaBoundaryDoesNotIncrementEitherFailureCounter() throws Exception {
        var token=administratorMfa();var expiry=sql.queryForObject("SELECT mfa_expr_dttm FROM tb_sy_user WHERE user_id=?",LocalDateTime.class,id);
        clock.set(expiry.atZone(ZoneId.of("Asia/Seoul")).toInstant());
        verify(token,"000000",401,"MFA_EXPIRED");
        assertThat(sql.queryForObject("SELECT mfa_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
    }
    @Test void malformedCodeIsNotAFailureAndFifthWrongCodeDiscardsOnlyTheMfaStage() throws Exception {
        var token=administratorMfa();verify(token,"12x",400,null);
        assertThat(sql.queryForObject("SELECT mfa_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        String hash=sql.queryForObject("SELECT mfa_code_hash FROM tb_sy_user WHERE user_id=?",String.class,id);
        String wrong=new BCryptPasswordEncoder(10).matches("000000",hash)?"000001":"000000";
        for(int i=0;i<5;i++)verify(token,wrong,401,null);
        assertThat(sql.queryForObject("SELECT mfa_code_hash IS NULL FROM tb_sy_user WHERE user_id=?",Boolean.class,id)).isTrue();
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("USE");
        assertThat(sql.queryForObject("SELECT pwd_err_cnt FROM tb_sy_user WHERE user_id=?",Integer.class,id)).isZero();
        mvc.perform(get("/api/auth/session").session(token.session())).andExpect(jsonPath("$.data.nextStep").value("LOGIN"));
    }
    @Test @EnabledIfEnvironmentVariable(named="BASIC147_LAST_ADMIN_ISOLATED",matches="true")
    void soleActiveAdministratorStillLocksOnFifthWrongPassword() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        assertThat(sql.queryForObject("SELECT count(DISTINCT u.user_id) FROM tb_sy_user u JOIN tb_sy_user_role ur ON ur.user_id=u.user_id JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE u.user_stat_cd='USE' AND r.role_id='ADMIN' AND r.use_yn='Y'",Long.class)).isEqualTo(1);
        for(int i=0;i<5;i++)assertThat(login(csrf(null),"NotTheSyntheticPassword1!").getResponse().getStatus()).isEqualTo(401);
        assertThat(sql.queryForObject("SELECT user_stat_cd FROM tb_sy_user WHERE user_id=?",String.class,id)).isEqualTo("LOCK");
    }
    private Token administratorMfa() throws Exception {
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",id);
        var result=login(csrf(null),password);assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("nextStep").asText()).isEqualTo("MFA");
        return csrf((MockHttpSession)result.getRequest().getSession(false));
    }
    private void verify(Token token,String code,int status,String expectedCode) throws Exception {
        var result=mvc.perform(post("/api/auth/mfa/verify").session(token.session()).header(token.header(),token.token()).contentType("application/json").content(json.writeValueAsBytes(Map.of("code",code)))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        if(expectedCode!=null)assertThat(json.readTree(result.getResponse().getContentAsByteArray()).path("code").asText()).isEqualTo(expectedCode);
    }
}
