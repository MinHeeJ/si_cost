package com.sicost.shared;

import com.sicost.common.api.*;
import com.sicost.common.health.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@ContextConfiguration(classes=HealthController.class)
@Import({HealthService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class SharedHealthContractTest {
    @Autowired MockMvc mvc;
    @MockBean HealthMapper mapper;
    @Test void databaseSelectPrecedesSuccessEnvelope() throws Exception {
        when(mapper.selectDatabaseHealth()).thenReturn(1);
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap());
        verify(mapper).selectDatabaseHealth();verifyNoMoreInteractions(mapper);
    }
    @Test void databaseFailureReturns503WithMatchingSafeRequestIdAndNoSecrets() throws Exception {
        when(mapper.selectDatabaseHealth()).thenThrow(new DataAccessResourceFailureException("private SQL connection details"));
        var response=mvc.perform(get("/api/health").header("X-Request-Id","read-health"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"))
            .andExpect(jsonPath("$.requestId").value("read-health")).andExpect(header().string("X-Request-Id","read-health")).andReturn().getResponse();
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("데이터베이스").doesNotContain("private SQL","connection details");
    }
}
