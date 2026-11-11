package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessUserOptionsController.class)
@ContextConfiguration(classes = BusinessUserOptionsController.class)
@Import({BusinessUserOptionsService.class, BusinessUserOptionsRepository.class,
        ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class BusinessUserOptionsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BusinessUserOptionsMapper mapper;
    @MockBean CurrentUserProvider users;

    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "관리자", List.of("ADMIN"), true));
        when(mapper.activeRoles()).thenReturn(List.of(new RoleOption("PM", "프로젝트 담당"), new RoleOption("SALES_LDR", "파트 담당")));
        when(mapper.eligible(anyString())).thenReturn(List.of());
    }
    @Test void publicPmUsesAdminContextNotPmAuthorizationAndReturnsExactEnvelope() throws Exception {
        when(mapper.eligible("PM")).thenReturn(List.of(new UserOption("personb", "동명", "동명(personb)")));
        mvc.perform(get("/api/business-users/options").param("purpose", "PM"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items[0].userId").value("personb"))
            .andExpect(jsonPath("$.data.items[0].name").value("동명"))
            .andExpect(jsonPath("$.data.items[0].label").value("동명(personb)"))
            .andExpect(jsonPath("$.data.roleOptions[0].name").value("프로젝트 담당"))
            .andExpect(jsonPath("$.data.total").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].pwdHash").doesNotExist());
        verify(users).currentUser(); verify(mapper).eligible("PM");
    }
    @Test void partLeaderUsesSalesLeaderQualification() throws Exception {
        mvc.perform(get("/api/business-users/options").param("purpose", "PART_LEADER"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.roleOptions.length()").value(2));
        verify(mapper).eligible("SALES_LDR");
    }
    @Test void emptyPmReturnsArrayNotMissingOrAnInventedUser() throws Exception {
        mvc.perform(get("/api/business-users/options").param("purpose", "PM"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void missingPurposeIsValidationErrorBeforeDatabase() throws Exception {
        mvc.perform(get("/api/business-users/options"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value("purpose"));
        verifyNoInteractions(mapper, users);
    }
    @ParameterizedTest @ValueSource(strings = {"", "pm", "SALES_LDR", "OTHER", " PM ", "PM,PART_LEADER"})
    void invalidPurposeIsValidationError(String purpose) throws Exception {
        mvc.perform(get("/api/business-users/options").param("purpose", purpose))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value("purpose"));
        verifyNoInteractions(mapper, users);
    }
    @Test void repeatedPurposeIsRejected() throws Exception {
        mvc.perform(get("/api/business-users/options").param("purpose", "PM", "PART_LEADER"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("purpose"));
        verifyNoInteractions(mapper, users);
    }
    @ParameterizedTest @ValueSource(strings = {"page", "size", "q", "userId", "regUserId", "status", "roleId"})
    void unknownAndSystemQueryFieldsAreRejected(String field) throws Exception {
        var result = mvc.perform(get("/api/business-users/options").param("purpose", "PM").param(field, "forged"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(field)).andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText())
            .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        verifyNoInteractions(mapper, users);
    }
    @Test void reopeningReadsFreshDatabaseResults() throws Exception {
        when(mapper.eligible("PM")).thenReturn(List.of(), List.of(new UserOption("newperson", "신규", "신규(newperson)")));
        mvc.perform(get("/api/business-users/options").param("purpose", "PM")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/business-users/options").param("purpose", "PM")).andExpect(jsonPath("$.data.items[0].userId").value("newperson"));
        verify(mapper, times(2)).eligible("PM");
    }
    @Test void databaseFailureDoesNotExposeSqlAndHasMatchingRequestId() throws Exception {
        when(mapper.eligible("PM")).thenThrow(new DataAccessResourceFailureException("SELECT pwd_hash FROM tb_sy_user"));
        var result = mvc.perform(get("/api/business-users/options").param("purpose", "PM"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.success").value(false)).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("pwd_hash", "SELECT", "tb_sy_user");
        assertThat(json.readTree(body).get("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }
}
