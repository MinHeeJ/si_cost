package com.sicost.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Final JSON field projection, after existing business calculations and explicit exception views. */
@RestControllerAdvice
@Order(10)
public class PermissionFieldAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper json;
    private final PermissionProjectionService projection;
    public PermissionFieldAdvice(ObjectMapper json,PermissionProjectionService projection) {this.json=json;this.projection=projection;}
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(!(request instanceof ServletServerHttpRequest servlet) || !(servlet.getServletRequest().getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation op) || !op.policy().startsWith("UI-") || Set.of("UI-19","UI-24","UI-25","UI-27","UI-31","UI-32","UI-33").contains(op.policy()))return body;
        boolean full=projection.sensitive() && projection.billingRate();
        if(full && !op.policy().equals("UI-16"))return body;
        var tree=json.valueToTree(body);if(!(tree instanceof ObjectNode envelope) || !envelope.has("data") || !envelope.path("success").asBoolean())return body;
        var data=envelope.get("data");
        if(op.policy().equals("UI-16"))data=projection.dashboardView(data);
        if(!full)data=projection.publicView(op.policy(),data);
        if(data instanceof ObjectNode object) {object.put("projection","RESTRICTED");PermissionViews.available(object);}
        for(var row:data.path("items"))if(row instanceof ObjectNode object)PermissionViews.available(object);
        if(envelope.path("meta") instanceof ObjectNode meta)meta.remove("issues");
        return envelope;
    }
}
