package com.sicost.permission;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerMapping;

/** Additional business qualifications; the operation registry still supplies INQ + action. */
@Service
public class PermissionBusinessPolicy {
    public static final String BODY=PermissionBusinessPolicy.class.getName()+".body";
    private final PermissionService permissions;
    private final PermissionRowPolicy rows;
    private final PermissionProjectionService projection;
    public PermissionBusinessPolicy(PermissionService permissions,PermissionRowPolicy rows,PermissionProjectionService projection) {this.permissions=permissions;this.rows=rows;this.projection=projection;}
    public void check(HttpServletRequest request,JsonNode body) {
        if(!(request.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation op))return;
        rows.check(request,body);
        projection.input(op.policy(),body);
        if(op.path().equals("/api/assignments/substitutions") && body.path("mode").asText().equals("TOGETHER"))permissions.require("UI-12","UPD");
        if(op.path().equals("/api/clients") && op.action().equals("REG"))permissions.requireClientWrite(null,body.path("type").asText());
        if(op.path().equals("/api/clients/{id}") && op.action().equals("UPD")) {
            var variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String id=variables instanceof Map<?,?> values?(String)values.get("id"):null;
            permissions.requireClientWrite(id,body.path("type").asText());
        }
        if(op.path().equals("/api/clients/bulk-status"))checkClients(body);
    }
    private void checkClients(JsonNode node) {
        if(node.isObject()) {
            node.fields().forEachRemaining(entry->{
                if(entry.getKey().equals("id") && entry.getValue().isTextual())permissions.requireClientWrite(entry.getValue().asText(),null);
                else if(entry.getKey().equals("ids") && entry.getValue().isArray())for(var id:entry.getValue())if(id.isTextual())permissions.requireClientWrite(id.asText(),null);
                else if(entry.getValue().isContainerNode())checkClients(entry.getValue());
            });
        } else if(node.isArray())for(var item:node)checkClients(item);
    }
}
