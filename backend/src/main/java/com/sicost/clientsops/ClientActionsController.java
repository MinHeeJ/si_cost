package com.sicost.clientsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.MasterDataValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.clientsops.ClientActionsDtos.*;

@RestController
public class ClientActionsController {
    private final ClientActionsService service;
    private final ClientAccountService accounts;
    private final ClientBulkStatusService bulk;
    public ClientActionsController(ClientActionsService service, ClientAccountService accounts, ClientBulkStatusService bulk) {
        this.service=service; this.accounts=accounts; this.bulk=bulk;
    }
    @GetMapping("/api/clients/options")
    public ApiResponse<Options> getClientOptions(@RequestParam MultiValueMap<String,String> query) {
        ClientActionsInput.query(query,Set.of("purpose","q"));
        String purpose=MasterDataValidator.enumValue(query.getFirst("purpose"),Set.of("CUSTOMER","VENDOR"),"purpose");
        return ApiResponse.ok(service.options(new OptionQuery(purpose,MasterDataValidator.search(query.getFirst("q")))));
    }
    @GetMapping("/api/clients/selection")
    public ApiResponse<Selection> getClientSelection(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.selection(query));
    }
    @GetMapping("/api/clients/{id}/account")
    public ResponseEntity<ApiResponse<Account>> getClientAccount(@PathVariable String id,
            @RequestParam MultiValueMap<String,String> query,HttpServletRequest request) {
        ClientActionsInput.query(query,Set.of());
        return ResponseEntity.ok().header("Cache-Control","no-store")
            .body(ApiResponse.ok(accounts.view(ClientActionsInput.id(id),request)));
    }
    @GetMapping("/api/clients/{id}/assignments")
    public ApiResponse<Assignments> getClientAssignments(@PathVariable String id,@RequestParam MultiValueMap<String,String> query) {
        ClientActionsInput.query(query,Set.of("page","size"));
        return ApiResponse.ok(service.assignments(ClientActionsInput.id(id),ClientActionsInput.page(query.getFirst("page")),
            ClientActionsInput.size(query.getFirst("size"))));
    }
    @PostMapping("/api/clients/bulk-status")
    public ApiResponse<BulkResult> saveClientBulkStatus(@RequestBody JsonNode body,
            @RequestParam MultiValueMap<String,String> query,HttpServletRequest request) {
        ClientActionsInput.query(query,Set.of());
        return ApiResponse.ok(bulk.save(ClientActionsInput.bulk(body),(String)request.getAttribute(RequestIdFilter.ATTRIBUTE)));
    }
}
