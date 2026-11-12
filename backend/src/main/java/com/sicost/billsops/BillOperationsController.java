package com.sicost.billsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.billsops.BillOperationsDtos.*;

@RestController
public class BillOperationsController {
    private final BillOperationsService service;
    public BillOperationsController(BillOperationsService service) {this.service=service;}
    @GetMapping("/api/bills/estimate")
    public ApiResponse<BillEstimate> getBillEstimate(@RequestParam MultiValueMap<String,String> query) {
        BillOperationsInput.query(query,Set.of("projectId","month"));
        return ApiResponse.ok(service.getBillEstimate(BillOperationsInput.project(query.getFirst("projectId")),BillOperationsInput.month(query.getFirst("month"))));
    }
    @GetMapping("/api/bills/summary")
    public ApiResponse<BillsSummary> getBillsSummary(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getBillsSummary(BillOperationsInput.summary(query)));
    }
    @GetMapping("/api/bills/selection")
    public ApiResponse<BillSelection> getBillSelection(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getBillSelection(BillOperationsInput.selection(query)));
    }
    @GetMapping("/api/bills/{id}/copy-draft")
    public ApiResponse<BillCopyDraft> getBillCopyDraft(@PathVariable String id,@RequestParam MultiValueMap<String,String> query) {
        BillOperationsInput.query(query,Set.of("year"));
        return ApiResponse.ok(service.getBillCopyDraft(BillOperationsInput.id(id),BillOperationsInput.year(query.getFirst("year"))));
    }
    @PostMapping("/api/bills/{id}/cancel")
    public ApiResponse<BillMutationResult> saveBillCancellation(@PathVariable String id,@RequestBody(required=false) JsonNode body,
        @RequestParam MultiValueMap<String,String> query) {
        BillOperationsInput.query(query,Set.of());return ApiResponse.ok(service.saveBillCancellation(BillOperationsInput.id(id),BillOperationsInput.mutation(body,true)));
    }
    @PostMapping("/api/bills/{id}/next-month")
    public ResponseEntity<ApiResponse<BillMutationResult>> createNextMonthBill(@PathVariable String id,@RequestBody(required=false) JsonNode body,
        @RequestParam MultiValueMap<String,String> query) {
        BillOperationsInput.query(query,Set.of());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createNextMonthBill(BillOperationsInput.id(id),BillOperationsInput.mutation(body,false))));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(BillOperationsInput.invalid("body"),request);
    }
}
