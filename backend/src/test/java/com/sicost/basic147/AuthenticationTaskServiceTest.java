package com.sicost.basic147;

import com.sicost.authentication.*;
import com.sicost.common.api.ApiException;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationTaskServiceTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"),ZoneId.of("Asia/Seoul"));
    AuthenticationService sessions=mock(AuthenticationService.class);
    AuthenticationMapper mapper=mock(AuthenticationMapper.class);
    AuthenticationTaskService tasks=new AuthenticationTaskService(sessions,mapper,clock);
    @BeforeEach void permissions() {
        var policy=mock(com.sicost.permission.PermissionService.class);
        when(policy.capabilities()).thenReturn(new com.sicost.permission.PermissionService.Capabilities(java.util.Map.of(),new com.sicost.permission.PermissionService.Scope(false,java.util.Set.of(),java.util.Set.of()),false,java.util.Map.of(),java.util.Map.of(),java.util.Set.of()));
        tasks.setPermissions(policy,mock(org.springframework.transaction.PlatformTransactionManager.class));
    }
    AuthenticationMapper.Account account(String hash,String status) {return new AuthenticationMapper.Account("accepted-user","synthetic@example.test",hash,"N",0,status,null,null,null,0);}
    AuthenticationTaskService.Proof proof() {
        var request=new MockHttpServletRequest();
        when(sessions.validate(request,true)).thenReturn(new AuthenticationState("accepted-user","opaque-password-proof",AuthenticationState.Step.AUTHENTICATED,clock.instant()));
        when(sessions.expires(any())).thenReturn(clock.instant().plusSeconds(1800));
        when(mapper.find("accepted-user")).thenReturn(account("opaque-password-proof","USE"));
        return tasks.capture(request);
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void establishesOnlyTheAcceptedPrincipalAndRestoresTheWorkerContext() {
        var proof=proof();var previous=SecurityContextHolder.createEmptyContext();previous.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("previous-worker",null,java.util.List.of()));SecurityContextHolder.setContext(previous);
        assertThat(tasks.run(proof,()->{tasks.validateCurrent();return SecurityContextHolder.getContext().getAuthentication().getName();})).isEqualTo("accepted-user");
        assertThat(SecurityContextHolder.getContext()).isSameAs(previous);assertThat(proof.toString()).doesNotContain("opaque-password-proof","accepted-user");
    }
    @Test void changedPasswordAndNewAdministratorWithoutMfaCannotStartWork() {
        var proof=proof();when(mapper.find("accepted-user")).thenReturn(account("a-new-proof","USE"));
        assertThatThrownBy(()->tasks.run(proof,()->"must-not-run")).isInstanceOf(ApiException.class);
        when(mapper.find("accepted-user")).thenReturn(account("opaque-password-proof","USE"));when(mapper.administrator("accepted-user")).thenReturn(true);
        assertThatThrownBy(()->tasks.run(proof,()->"must-not-run")).isInstanceOf(ApiException.class);
    }
    @Test void revocationDuringWorkPreventsReturningTheFinalValueAndRestoresContext() {
        var proof=proof();var previous=SecurityContextHolder.getContext();
        assertThatThrownBy(()->tasks.run(proof,()->{when(mapper.find("accepted-user")).thenReturn(account("opaque-password-proof","STOP"));return "private-result";})).isInstanceOf(ApiException.class);
        assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
    }
}
