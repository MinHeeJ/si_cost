package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.businessusersops.BusinessUserProjectsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessUserProjectsController.class)
@ContextConfiguration(classes=BusinessUserProjectsController.class)
@Import({BusinessUserProjectsService.class,BusinessUserProjectsRepository.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class,BusinessUserProjectsContractTest.Time.class})
class BusinessUserProjectsContractTest {
    @TestConfiguration static class Time {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BusinessUserProjectsMapper mapper;
    @MockBean CurrentUserProvider users;
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(mapper.userExists("personone")).thenReturn(true);
        when(mapper.projects(any())).thenReturn(List.of());
    }
    ProjectRow row(String id,String type,String status) {
        return new ProjectRow(id,"담당 계약",type,LocalDate.parse("2025-01-01"),LocalDate.parse("2025-12-31"),
            new BigDecimal("999999999999999"),status,null,0,null,null,BigDecimal.ZERO,0);
    }
    @Test void publicReadUsesTemporaryAdministratorAndExactPublicFields() throws Exception {
        when(mapper.projects(any())).thenReturn(List.of(row("P-901","TK","END"),row("P-902","IN","LOSE")));
        mvc.perform(get("/api/business-users/personone/projects").header("X-Request-Id","read_projects"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","read_projects"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.userId").value("personone"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].contractAmount").value("999999999999999"))
            .andExpect(jsonPath("$.data.items[0].status").value("END"))
            .andExpect(jsonPath("$.data.items[0].distribution.hasTable").value(false))
            .andExpect(jsonPath("$.data.items[0].distribution.contractDifference").value("999999999999999"))
            .andExpect(jsonPath("$.data.items[1].customerName").value("(자체)"))
            .andExpect(jsonPath("$.data.items[1].contractAmount").isEmpty())
            .andExpect(jsonPath("$.data.revision").isString());
        verify(users).currentUser();
        verify(mapper).projects(new Query("personone",LocalDate.parse("2026-06-15"),"202606"));
    }
    @Test void existingUserWithoutConnectionsReturnsRealEmptyArray() throws Exception {
        mvc.perform(get("/api/business-users/personone/projects"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void missingUserIsNotAnEmptySuccessfulUser() throws Exception {
        mvc.perform(get("/api/business-users/missing/projects"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(mapper,never()).projects(any());
    }
    @ParameterizedTest @ValueSource(strings={"year","month","q","status","page","userId","regUserId"})
    void undeclaredQueriesAreInvalidInput(String field) throws Exception {
        mvc.perform(get("/api/business-users/personone/projects").param(field,"2026-99"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={" ","abcdefghijklmnopqrstuvwxyz12345"})
    void blankOrOverlongIdentityIsRejectedBeforeDatabase(String id) throws Exception {
        mvc.perform(get("/api/business-users/{userId}/projects",id))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(mapper,users);
    }
    @Test void requestBodyIsNotSilentlyIgnored() throws Exception {
        mvc.perform(get("/api/business-users/personone/projects").contentType("application/json").content("{\"year\":2026}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("body"));
        verifyNoInteractions(mapper,users);
    }
    @Test void unchangedReadsKeepRevisionAndDoNotCallMutationResources() throws Exception {
        var first=mvc.perform(get("/api/business-users/personone/projects")).andReturn();
        var second=mvc.perform(get("/api/business-users/personone/projects")).andReturn();
        assertThat(json.readTree(first.getResponse().getContentAsString()).at("/data/revision"))
            .isEqualTo(json.readTree(second.getResponse().getContentAsString()).at("/data/revision"));
        verify(mapper,times(2)).userExists("personone");verify(mapper,times(2)).projects(any());verifyNoMoreInteractions(mapper);
    }
    @Test void databaseErrorsAreSanitizedAndRequestIdMatches() throws Exception {
        when(mapper.projects(any())).thenThrow(new DataAccessResourceFailureException("SELECT pwd_hash FROM tb_sy_user"));
        var result=mvc.perform(get("/api/business-users/personone/projects"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        String body=result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT","pwd_hash","tb_sy_user","Exception");
        assertThat(json.readTree(body).get("requestId").asText()).isEqualTo(result.getResponse().getHeader("X-Request-Id"));
    }
}
