package com.sicost.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.lang.reflect.Type;

@RestControllerAdvice
public class PermissionBodyAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper json;
    private final PermissionBusinessPolicy business;
    private final PermissionProjectionService projection;
    public PermissionBodyAdvice(ObjectMapper json,PermissionBusinessPolicy business,PermissionProjectionService projection) {this.json=json;this.business=business;this.projection=projection;}
    @Override public boolean supports(MethodParameter method,Type type,Class<? extends HttpMessageConverter<?>> converter) {return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter parameter,Type target,Class<? extends HttpMessageConverter<?>> converter) {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request=attributes.getRequest();
            if(request.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation op && !java.util.Set.of("PUBLIC","AUTH").contains(op.policy())) {
                com.fasterxml.jackson.databind.JsonNode value=projection.input(op.policy(),json.valueToTree(body));
                business.check(request,value);request.setAttribute(PermissionBusinessPolicy.BODY,value);
                if(body instanceof com.fasterxml.jackson.databind.JsonNode)return value;
            }
        }
        return body;
    }
}
