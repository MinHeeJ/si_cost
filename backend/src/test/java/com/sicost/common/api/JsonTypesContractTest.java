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
