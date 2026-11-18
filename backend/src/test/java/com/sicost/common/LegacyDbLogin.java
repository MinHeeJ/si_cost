package com.sicost.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.authentication.AuthenticationMailService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real password/MFA/session/CSRF for legacy HTTP business tests in disposable schemas.
 * Only the synchronous mail transport is replaced; no authentication or policy bypass.
 */
public final class LegacyDbLogin {
    private LegacyDbLogin() {}
    public static RequestPostProcessor admin(MockMvc mvc,ObjectMapper json,JdbcTemplate sql,
            PlatformTransactionManager manager,AuthenticationMailService mail) throws Exception {
        String password="Aa1!"+UUID.randomUUID();
        var transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status->sql.update("UPDATE tb_sy_user SET pwd_hash=?,temp_pwd_yn='N',pwd_chg_dttm=CURRENT_TIMESTAMP,user_stat_cd='USE',pwd_err_cnt=0 WHERE user_id='admin'",new BCryptPasswordEncoder(10).encode(password)));
        AtomicReference<String> code=new AtomicReference<>();
        doNothing().when(mail).requireConfigured();
        when(mail.send(anyString(),anyString(),anyString())).thenAnswer(call->{
            var matcher=java.util.regex.Pattern.compile("[0-9]{6}").matcher((String)call.getArgument(2));
            assertThat(matcher.find()).isTrue();code.set(matcher.group());return "ACCEPTED";
        });
        var csrf=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrf.getRequest().getSession(false);
        var token=json.readTree(csrf.getResponse().getContentAsString()).path("data");
        var login=mvc.perform(post("/api/auth/login").session(session).header(token.path("headerName").asText(),token.path("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("userId","admin","password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("MFA")).andReturn();
        session=(MockHttpSession)login.getRequest().getSession(false);
        csrf=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        token=json.readTree(csrf.getResponse().getContentAsString()).path("data");
        mvc.perform(post("/api/auth/mfa/verify").session(session).header(token.path("headerName").asText(),token.path("token").asText())
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("code",code.get()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("AUTHENTICATED"));
        mvc.perform(get("/api/current-user").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("admin"))
            .andExpect(jsonPath("$.data.developmentMode").value(false))
            .andExpect(jsonPath("$.data.accessEnforcement").value(true));
        csrf=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        token=json.readTree(csrf.getResponse().getContentAsString()).path("data");
        final var authenticated=session;final String header=token.path("headerName").asText(),value=token.path("token").asText();
        return request->{request.setSession(authenticated);request.addHeader(header,value);return request;};
    }
}
