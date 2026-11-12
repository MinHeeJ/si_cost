package com.sicost.common.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JsonTypesContractTest.InputController.class)
@Import({JsonTypesContractTest.InputController.class, JsonConfiguration.class, ApiExceptionHandler.class, RequestIdFilter.class})
class JsonTypesContractTest {
    @RestController
    static class InputController {
        record Input(@NotNull String value, @NotNull Boolean enabled) {}
        @PostMapping("/validation-input") ApiResponse<Input> validate(@Valid @RequestBody Input input) { return ApiResponse.ok(input); }
    }
    @Autowired MockMvc mvc;
    @Test void allExcelMultipartLimitsUseTheSameSafeErrorEnvelopeAndRequestId() {
        for (String path : new String[]{"/api/excel/B7/validate","/api/excel/B7/apply","/api/excel/B8/validate","/api/excel/B8/apply",
                "/api/excel/B9/validate","/api/excel/B9/apply","/api/excel/B6/validate","/api/excel/B6/apply"}) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST",path);
            request.setAttribute(RequestIdFilter.ATTRIBUTE,"upload-request");
            var response = new ApiExceptionHandler().uploadSize(new org.springframework.web.multipart.MaxUploadSizeExceededException(10485760),request);
            org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(413);
            org.assertj.core.api.Assertions.assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("upload-request");
            org.assertj.core.api.Assertions.assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
            org.assertj.core.api.Assertions.assertThat(response.getBody().field()).isEqualTo("file");
            org.assertj.core.api.Assertions.assertThat(response.getBody().requestId()).isEqualTo("upload-request");
        }
    }
    @Test void decimalStringAndBooleanAreAcceptedWithoutConversion() throws Exception {
        mvc.perform(post("/validation-input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1.2300\",\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("1.2300"));
    }
    @Test void numericJsonCannotBeCoercedIntoDecimalString() throws Exception {
        mvc.perform(post("/validation-input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":1.23,\"enabled\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void stringBooleanIsRejected() throws Exception {
        mvc.perform(post("/validation-input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\",\"enabled\":\"true\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void actorInjectionIsRejectedAsUnknownField() throws Exception {
        mvc.perform(post("/validation-input").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\",\"enabled\":true,\"userId\":\"forged\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
    }
}
