package com.sicost.authentication;

import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthenticationController {
    private final AuthenticationService service;
    private final AuthenticationPasswordService passwords;
    private final com.sicost.permission.PermissionService permissions;
    public AuthenticationController(AuthenticationService service,AuthenticationPasswordService passwords,com.sicost.permission.PermissionService permissions) {this.service=service;this.passwords=passwords;this.permissions=permissions;}
    public record Login(String userId,String password) { @Override public String toString(){return "Login[redacted]";} }
    public record Code(String code) { @Override public String toString(){return "Code[redacted]";} }
    public record Password(String currentPassword,String newPassword,String confirmPassword) { @Override public String toString(){return "Password[redacted]";} }
    public record RequiredPassword(String newPassword,String confirmPassword) { @Override public String toString(){return "RequiredPassword[redacted]";} }
    public record Recovery(String userId,String email) {}
    public record Reset(boolean confirmed) {}
    @GetMapping("/api/auth/csrf") public Object csrf(CsrfToken token) {return ApiResponse.ok(Map.of("headerName",token.getHeaderName(),"token",token.getToken()));}
    @PostMapping("/api/auth/login") public Object login(@RequestBody Login body,HttpServletRequest request,HttpServletResponse response) {return ApiResponse.ok(service.login(body.userId(),body.password(),request,response));}
    @GetMapping("/api/auth/session") public Object session(HttpServletRequest request) {return ApiResponse.ok(service.session(request));}
    @PostMapping("/api/auth/session/extend") public Object extend(@RequestBody(required=false) com.fasterxml.jackson.databind.JsonNode body,HttpServletRequest request) {empty(body);return ApiResponse.ok(service.extend(request));}
    @PostMapping("/api/auth/password/recommendation-dismissal") public Object dismissRecommendation(@RequestBody(required=false) com.fasterxml.jackson.databind.JsonNode body,HttpServletRequest request) {empty(body);return ApiResponse.ok(service.dismissRecommendation(request));}
    @PostMapping("/api/auth/mfa/verify") public Object verify(@RequestBody Code body,HttpServletRequest request,HttpServletResponse response) {return ApiResponse.ok(service.verify(body.code(),request,response));}
    @PostMapping("/api/auth/mfa/resend") public Object resend(@RequestBody(required=false) com.fasterxml.jackson.databind.JsonNode body,HttpServletRequest request) {empty(body);return ApiResponse.ok(service.resend(request));}
    @PostMapping("/api/auth/restart") public Object restart(@RequestBody(required=false) com.fasterxml.jackson.databind.JsonNode body,HttpServletRequest request,HttpServletResponse response) {empty(body);service.restart(request,response);return ApiResponse.ok(Map.of("nextStep","LOGIN"));}
    @PostMapping("/api/auth/logout") public Object logout(@RequestBody(required=false) com.fasterxml.jackson.databind.JsonNode body,HttpServletRequest request,HttpServletResponse response) {empty(body);service.logout(request,response);return ApiResponse.ok(Map.of("nextStep","LOGIN"));}
    @PostMapping("/api/auth/password/required") public Object required(@RequestBody RequiredPassword body,HttpServletRequest request,HttpServletResponse response) {return ApiResponse.ok(service.change(null,body.newPassword(),body.confirmPassword(),true,request,response));}
    @PostMapping("/api/auth/password/change") public Object change(@RequestBody Password body,HttpServletRequest request,HttpServletResponse response) {return ApiResponse.ok(service.change(body.currentPassword(),body.newPassword(),body.confirmPassword(),false,request,response));}
    @PostMapping("/api/auth/forgot-password") public Object recover(@RequestBody Recovery body) {passwords.recover(body.userId(),body.email());return ApiResponse.ok(Map.of("message","요청을 접수했습니다. 입력 정보가 맞으면 등록 이메일로 안내합니다. 메일이 오지 않으면 관리자에게 문의해 주세요."));}
    @PostMapping("/api/business-users/{id}/password-reset") public Object reset(@PathVariable String id,@RequestBody Reset body) {return ApiResponse.ok(passwords.reset(id,body.confirmed()));}
    @GetMapping("/api/current-user/capabilities") public Object capabilities() {return ApiResponse.ok(permissions.capabilities());}
    private void empty(com.fasterxml.jackson.databind.JsonNode body) {
        if(body!=null && (!body.isObject() || !body.isEmpty()))throw new com.sicost.common.api.ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.","body",null);
    }
}
