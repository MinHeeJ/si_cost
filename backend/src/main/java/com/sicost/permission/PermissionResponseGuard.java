package com.sicost.permission;

import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Final JSON release gate, including revocation while mandatory read auditing was blocked. */
@RestControllerAdvice
@Order(110)
public class PermissionResponseGuard implements ResponseBodyAdvice<Object> {
    private final ObjectProvider<PermissionOperationRegistry> operations;
    private final ObjectProvider<PermissionService> permissions;
    public PermissionResponseGuard(ObjectProvider<PermissionOperationRegistry> operations,ObjectProvider<PermissionService> permissions) {this.operations=operations;this.permissions=permissions;}
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(!(request instanceof ServletServerHttpRequest servlet) || !(response instanceof ServletServerHttpResponse outgoing))return body;
        HttpServletRequest incoming=servlet.getServletRequest();
        if(!"GET".equals(incoming.getMethod()) || !(incoming.getAttribute(PermissionOperationRegistry.ACCEPTED) instanceof PermissionService.Capabilities accepted))return body;
        boolean success=body instanceof ApiResponse<?> || body instanceof com.fasterxml.jackson.databind.JsonNode node && node.path("success").asBoolean();
        if(!success)return body;
        operations.getObject().preHandle(incoming,outgoing.getServletResponse(),incoming.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE));
        // A field/scope loss after projection must not release the earlier full JSON.
        // A changed request is not retried; the common 403 handler reloads capabilities.
        if(!accepted.equals(permissions.getObject().capabilities()))throw PermissionService.denied();
        return body;
    }
}
