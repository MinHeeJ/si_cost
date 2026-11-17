package com.sicost.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticationSecurityConfiguration {
    @Bean org.springframework.security.core.userdetails.UserDetailsService noGeneratedUsers() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("AuthenticationService is required"); };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AuthenticationService sessions,ObjectMapper json,
            @org.springframework.beans.factory.annotation.Value("${sicost.auth.trusted-origins:}") String origins) throws Exception {
        var trusted=java.util.Arrays.stream(origins.split(",")).map(String::strip).filter(value->!value.isEmpty()).collect(java.util.stream.Collectors.toSet());
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .securityContext(context -> context.requireExplicitSave(true).securityContextRepository(new HttpSessionSecurityContextRepository()))
            .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/health","/api/auth/csrf","/api/auth/session").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/login","/api/auth/forgot-password","/api/auth/logout","/api/auth/restart","/api/auth/mfa/verify","/api/auth/mfa/resend","/api/auth/password/required").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/","/UI-*","/index.html","/assets/**","/favicon.ico").permitAll()
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,error)->AuthenticationErrors.write(json,request,response,new ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.")))
                .accessDeniedHandler((request,response,error)->AuthenticationErrors.write(json,request,response,
                    error instanceof org.springframework.security.web.csrf.CsrfException?new ApiException(403,"CSRF_INVALID","보안 토큰을 새로 받아 다시 시도해 주세요."):new ApiException(403,"FORBIDDEN","이 작업을 수행할 권한이 없습니다."))));
        http.addFilterAfter(new AuthenticationOriginFilter(json,trusted),CsrfFilter.class);
        http.addFilterAfter(new AuthenticationSessionFilter(sessions,json),AuthenticationOriginFilter.class);
        return http.build();
    }
}
