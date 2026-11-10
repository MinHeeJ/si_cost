package com.sicost.common.health;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.api.JsonConfiguration;
import com.sicost.common.api.RequestIdFilter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({HealthService.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class HealthContractTest {
    @Autowired MockMvc mvc;
    @MockBean HealthMapper mapper;

    @Test void healthyDatabaseReturnsContractEnvelopeAfterSelect() throws Exception {
        when(mapper.selectDatabaseHealth()).thenReturn(1);
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
                .andExpect(jsonPath("$.data").doesNotExist()).andExpect(header().exists("X-Request-Id"));
        verify(mapper).selectDatabaseHealth();
        verifyNoMoreInteractions(mapper);
    }
    @Test void disconnectedDatabaseReturns503WithoutDriverOrSecretDetails() throws Exception {
        when(mapper.selectDatabaseHealth()).thenThrow(new DataAccessResourceFailureException("jdbc:postgresql://private/db password=never-expose SELECT secret"));
        var result = mvc.perform(get("/api/health").header("X-Request-Id", "health_case-1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("데이터베이스")))
                .andExpect(jsonPath("$.requestId").value("health_case-1"))
                .andExpect(header().string("X-Request-Id", "health_case-1")).andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("jdbc:", "password", "never-expose", "SELECT", "stackTrace");
        verify(mapper).selectDatabaseHealth();
        verifyNoMoreInteractions(mapper);
    }
    @Test void invalidRequestIdIsReplacedNotReflected() throws Exception {
        when(mapper.selectDatabaseHealth()).thenThrow(new DataAccessResourceFailureException("offline"));
        var response = mvc.perform(get("/api/health").header("X-Request-Id", "<script>bad</script>"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse();
        assertThat(response.getHeader("X-Request-Id")).matches("[A-Za-z0-9_-]{1,64}");
        assertThat(response.getContentAsString()).doesNotContain("<script>");
    }
    @Test void overlongRequestIdIsReplaced() throws Exception {
        when(mapper.selectDatabaseHealth()).thenReturn(1);
        var response = mvc.perform(get("/api/health").header("X-Request-Id", "a".repeat(65)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("X-Request-Id")).hasSizeLessThanOrEqualTo(64);
    }
    @Test void unsuccessfulSelectCannotReportHealthy() throws Exception {
        when(mapper.selectDatabaseHealth()).thenReturn(0);
        mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"));
    }
    @Test void approvedContractIsAvailableAsClasspathResource() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/health:", "operationId: getHealth", "DB_UNAVAILABLE");
    }
}
