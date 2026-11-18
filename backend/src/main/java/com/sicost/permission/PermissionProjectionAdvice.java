package com.sicost.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Omit denied values, including derived values from which candidate cost can be recovered. */
@RestControllerAdvice
@org.springframework.core.annotation.Order(0)
public class PermissionProjectionAdvice implements ResponseBodyAdvice<Object> {
    private final PermissionProjectionService projection;
    private final ObjectMapper json;
    public PermissionProjectionAdvice(PermissionProjectionService projection,ObjectMapper json) {this.projection=projection;this.json=json;}
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {
        if(method.getContainingClass()==com.sicost.apiops.FinancialReadController.class && method.getMethod()!=null && method.getMethod().getName().equals("getFinancialHistory"))return true;
        return method.getContainingClass()==com.sicost.progress.ProjectProgressController.class || method.getContainingClass()==com.sicost.apiops.ProjectAnalysisController.class && method.getMethod()!=null && method.getMethod().getName().equals("getProjectStatusBoard") || method.getContainingClass()==com.sicost.salespartsettlements.SalesPartSettlementController.class || method.getContainingClass()==com.sicost.salespartsettlementsops.SettlementCommandController.class || method.getContainingClass()==com.sicost.apiops.RateRenegotiationController.class || method.getContainingClass()==com.sicost.candidatesops.CandidateMatchingController.class && method.getMethod()!=null && method.getMethod().getName().equals("getCandidatePositions");
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(method.getContainingClass()==com.sicost.apiops.FinancialReadController.class) {
            if(projection.sensitive())return body;
            ObjectNode result=json.valueToTree(body);
            for(var item:result.path("data").path("items"))if(item instanceof ObjectNode row) {row.remove("reason");PermissionViews.available(row);}
            return result;
        }
        if(method.getContainingClass()==com.sicost.progress.ProjectProgressController.class || method.getContainingClass()==com.sicost.apiops.ProjectAnalysisController.class) {
            ObjectNode result=json.valueToTree(body);var data=result.path("data");
            for(var row:data.path("items"))if(row instanceof ObjectNode object && object.has("costUsage"))PermissionViews.available(object);
            if(data.path("status") instanceof ObjectNode status)PermissionViews.available(status);
            if(data.path("progress").path("status") instanceof ObjectNode status)PermissionViews.available(status);
            return result;
        }
        boolean rate=method.getContainingClass()==com.sicost.apiops.RateRenegotiationController.class;
        boolean settlement=method.getContainingClass()==com.sicost.salespartsettlements.SalesPartSettlementController.class || method.getContainingClass()==com.sicost.salespartsettlementsops.SettlementCommandController.class;
        if(rate || settlement ? projection.sensitive():projection.candidateRate())return body;
        ObjectNode result=json.valueToTree(body);
        if(!(result.path("data") instanceof ObjectNode data))return body;
        if(rate) {PermissionViews.rate(data);return result;}
        if(settlement && body instanceof com.sicost.common.api.ApiResponse<?> envelope) {PermissionViews.settlement(data,envelope.data(),json);return result;}
        var items=result.path("data").path("items");
        for(var value:items)if(value instanceof ObjectNode row) {
            if(!rate)row.remove(List.of("candidateCost","profitRate","belowApprovalThreshold"));
            row.put("projection","RESTRICTED");
            var fields=row.putArray("availableFields");row.fieldNames().forEachRemaining(field->{if(!field.equals("availableFields"))fields.add(field);});
        }
        data.put("projection","RESTRICTED");PermissionViews.available(data);
        // Private pricing diagnostics are not a separate channel for hidden cost/threshold evidence.
        if(result.path("meta") instanceof ObjectNode meta)meta.remove("issues");
        return result;
    }

}
