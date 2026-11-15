package com.sicost.receivablesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@RestController
public class DunningDeliveryController {
    private final DunningDeliveryService service;
    private final DunningDeliveryCommands commands;
    public DunningDeliveryController(DunningDeliveryService service,DunningDeliveryCommands commands) {this.service=service;this.commands=commands;}
    @GetMapping("/api/receivables/{billId}/delivery-preview")
    public ApiResponse<DeliveryPreview> getReceivableDeliveryPreview(@PathVariable String billId,@RequestParam Map<String,String> query) {
        DunningDeliveryInput.allow(query,Set.of());return ApiResponse.ok(service.preview(DunningDeliveryInput.billId(billId)));
    }
    @GetMapping("/api/receivables/{billId}/deliveries")
    public ApiResponse<DeliveryPage> getReceivableDeliveries(@PathVariable String billId,@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.deliveries(DunningDeliveryInput.billId(billId),DunningDeliveryInput.page(query)));
    }
    @PostMapping("/api/receivables/{billId}/reminders")
    public ResponseEntity<ApiResponse<CommandResult>> createReceivableReminder(@PathVariable String billId,@RequestParam Map<String,String> query,@RequestBody JsonNode body) {
        DunningDeliveryInput.allow(query,Set.of());
        var result=commands.remind(DunningDeliveryInput.billId(billId),DunningDeliveryInput.reminder(body));
        return ResponseEntity.status(result.status()).body(ApiResponse.ok(result.result()));
    }
}
