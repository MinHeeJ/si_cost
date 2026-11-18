package com.sicost.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.authentication.AuthenticationService;
import com.sicost.common.history.AccessLogWriter;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Commit the required audit before the JSON converter can emit private values. */
@RestControllerAdvice
@org.springframework.core.annotation.Order(100)
public class PermissionSensitiveReadAdvice implements ResponseBodyAdvice<Object> {
    private static final Set<String> SCREENS=Set.of("UI-02","UI-03","UI-04","UI-19","UI-32");
    private static final Set<String> FIELDS=Set.of("monthlyPay","payAmount","basePayAmount","salary","pay","allowance","companyInsurance","retirementReserve","personalCost","totalCost","profit","netPay","laborAllocated","laborCost","monthlyLaborCost","costAmount","retirementAmount","profitAmount","profitShareAmount","personProfitShareAmount");
    private static final String AUDITED=PermissionSensitiveReadAdvice.class.getName();
    private final ObjectMapper json;
    private final PermissionService policies;
    private final AuthenticationService authentication;
    private final AccessLogWriter audit;
    public PermissionSensitiveReadAdvice(ObjectMapper json,PermissionService policies,AuthenticationService authentication,AccessLogWriter audit) {
        this.json=json;this.policies=policies;this.authentication=authentication;this.audit=audit;
    }
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(!(request instanceof ServletServerHttpRequest servlet) || !media.isCompatibleWith(MediaType.APPLICATION_JSON))return body;
        var incoming=servlet.getServletRequest();
        if(!"GET".equals(incoming.getMethod()) || incoming.getAttribute(AUDITED)!=null
                || !(incoming.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation operation)
                || !SCREENS.contains(operation.policy()))return body;
        JsonNode envelope=json.valueToTree(body);
        if(!envelope.path("success").asBoolean() || !containsPrivate(envelope.path("data")))return body;
        authentication.validate(incoming,true);
        policies.require(operation.policy(),operation.action());policies.requireSensitive();
        audit.appendSensitiveRead(operation.policy(),incoming);
        incoming.setAttribute(AUDITED,Boolean.TRUE);
        return body;
    }
    private boolean containsPrivate(JsonNode node) {
        if(node.isObject()) {
            var fields=node.fields();
            while(fields.hasNext()) {
                var field=fields.next();
                if(FIELDS.contains(field.getKey()) && !field.getValue().isNull())return true;
                if(containsPrivate(field.getValue()))return true;
            }
        } else if(node.isArray())for(var child:node)if(containsPrivate(child))return true;
        return false;
    }
}
