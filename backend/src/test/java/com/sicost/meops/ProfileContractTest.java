package com.sicost.meops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.meops.ProfileDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@ContextConfiguration(classes=ProfileController.class)
@Import({ProfileService.class,ProfileRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProfileContractTest {
    @Autowired MockMvc mvc;
    @MockBean ProfileMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;

    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-31T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("actual-user","임시 관리자",List.of("ADMIN"),true));
        when(mapper.user("actual-user")).thenReturn(new User("actual-user","실제 사용자"));
        when(mapper.activeRoles("actual-user")).thenReturn(List.of(new Role("FIN","재무"),new Role("PM","프로젝트 관리자")));
        when(mapper.parts("actual-user","202608")).thenReturn(List.of(new Part("P1","가나다"),new Part("HQ","본사")));
    }
    @Test void returnsDatabaseIdentityAndActiveRoleNamesWithSeoulServerMonthWithoutRoleGate() throws Exception {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("actual-user","대체 이름",List.of(),true));
        mvc.perform(get("/api/me/profile")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.userId").value("actual-user")).andExpect(jsonPath("$.data.userName").value("실제 사용자"))
            .andExpect(jsonPath("$.data.activeRoles[0].roleName").value("재무"))
            .andExpect(jsonPath("$.data.parts[1].partName").value("본사"))
            .andExpect(jsonPath("$.data.basisMonth").value("2026-08"))
            .andExpect(jsonPath("$.data.editGuidance").value("이름·역할은 관리자, 보유 스킬·거주지는 개발자 풀에서 수정합니다."))
            .andExpect(jsonPath("$.data.emailAddress").doesNotExist())
            .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.salary").doesNotExist())
            .andExpect(jsonPath("$.data.developerId").doesNotExist()).andExpect(header().exists("X-Request-Id"));
        verify(mapper).user("actual-user");verify(mapper).activeRoles("actual-user");verify(mapper).parts("actual-user","202608");
        verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"userId","DEV_ID","devId","actor","month","asOfDate","page","size","sort","unknown"})
    void rejectsAllQueriesBeforeIdentityOrPersistenceReads(String key) throws Exception {
        var result=mvc.perform(get("/api/me/profile").param(key,"injected")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(key)).andReturn();
        String id=result.getResponse().getHeader("X-Request-Id");
        assertThat(result.getResponse().getContentAsString()).contains(id);
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"userId\":\"other\"}","{\"DEV_ID\":\"other\"}","null","[]","broken"," "})
    void rejectsEveryNonemptyBodyRatherThanSilentlyIgnoringIt(String body) throws Exception {
        mvc.perform(get("/api/me/profile").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("body"));
        verifyNoInteractions(mapper,users);
    }
    @Test void missingRealUserIs404AndDoesNotUseTemporaryDisplayName() throws Exception {
        when(mapper.user("actual-user")).thenReturn(null);
        mvc.perform(get("/api/me/profile")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.message").value("현재 사용자 정보를 찾을 수 없습니다."));
        verify(mapper).user("actual-user");verifyNoMoreInteractions(mapper);
    }
    @Test void noAffiliationAndNoActiveRolesAreRealEmptyArraysNotInventedCompanyData() throws Exception {
        when(mapper.activeRoles("actual-user")).thenReturn(List.of());
        when(mapper.parts("actual-user","202608")).thenReturn(List.of());
        mvc.perform(get("/api/me/profile")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activeRoles").isArray()).andExpect(jsonPath("$.data.activeRoles").isEmpty())
            .andExpect(jsonPath("$.data.parts").isArray()).andExpect(jsonPath("$.data.parts").isEmpty());
    }
    @Test void databaseFailureKeepsLocalizedEnvelopeWithoutSqlOrStack() throws Exception {
        when(mapper.activeRoles("actual-user")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT sensitive_column FROM private_table"));
        var response=mvc.perform(get("/api/me/profile")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.message").value("자료 처리 중 오류가 발생했습니다."))
            .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SELECT","sensitive_column","private_table","Exception");
    }
    @Test void repeatedReadsOnlyCallTheThreeReadMethodsForTheCurrentActor() throws Exception {
        mvc.perform(get("/api/me/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/me/profile")).andExpect(status().isOk());
        verify(mapper,times(2)).user("actual-user");verify(mapper,times(2)).activeRoles("actual-user");verify(mapper,times(2)).parts("actual-user","202608");
        verifyNoMoreInteractions(mapper);
    }
}
