package com.sicost.common.api;

import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationErrorBoundaryTest {
    @RestController @org.springframework.context.annotation.Profile("error-boundary-test-only") static class Endpoint {
        @GetMapping("/api/assignments") Object read() {return Map.of("ok",true);}
        @GetMapping("/api/download") Object download(HttpServletResponse response) {
            response.setHeader("Content-Disposition","attachment; filename=private.xlsx");
            response.setHeader("Content-Length","9999");
            throw new ApiException(403,"FORBIDDEN","이 작업을 수행할 권한이 없습니다.");
        }
    }
    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter()).build();
    }
    @Test void unsupportedMethodsAreClientErrorsWithJsonAndAllow() throws Exception {
        for(String method : new String[]{"DELETE","PATCH","POST"})
            mvc().perform(request(org.springframework.http.HttpMethod.valueOf(method),"/api/assignments"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(header().string("Allow",org.hamcrest.Matchers.containsString("GET")));
    }
    @Test void callerCannotChooseAuditCorrelationId() throws Exception {
        var result=mvc().perform(get("/api/assignments").header("X-Request-Id","forged-audit-id")).andReturn();
        Object server=result.getRequest().getAttribute(RequestIdFilter.SERVER_ATTRIBUTE);
        assertThat(server).isNotEqualTo("forged-audit-id");assertThat(java.util.UUID.fromString((String)server)).isNotNull();
    }
    @Test void lateDenialDropsAttachmentAndFileLength() throws Exception {
        mvc().perform(get("/api/download")).andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(header().doesNotExist("Content-Length"))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
