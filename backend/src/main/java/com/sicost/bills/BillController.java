package com.sicost.bills;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.bills.BillDtos.*;

@RestController
public class BillController {
    private final BillService service;
    public BillController(BillService service) {this.service=service;}
    @GetMapping("/api/bills")
    public ApiResponse<BillsPage> getBills(@RequestParam Map<String,String> query) {return ApiResponse.ok(service.getBills(BillInput.list(query)));}
    @GetMapping("/api/bills/{id}")
    public ApiResponse<BillDetail> getBill(@PathVariable String id,@RequestParam Map<String,String> query) {return ApiResponse.ok(service.getBill(BillInput.id(id),BillInput.detailQuery(query)));}
    @PostMapping("/api/bills")
    public ResponseEntity<ApiResponse<BillMutationResult>> createBill(@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query) {
        BillInput.query(query,Set.of());return ResponseEntity.status(201).body(ApiResponse.ok(service.createBill(BillInput.create(body))));
    }
    @PutMapping("/api/bills/{id}")
    public ApiResponse<BillMutationResult> putBill(@PathVariable String id,@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query) {
        BillInput.query(query,Set.of());return ApiResponse.ok(service.putBill(BillInput.id(id),BillInput.update(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {return new ApiExceptionHandler().application(BillInput.invalid("body"),request);}
}
