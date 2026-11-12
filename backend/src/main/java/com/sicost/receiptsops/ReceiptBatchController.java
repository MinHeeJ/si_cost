package com.sicost.receiptsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;

@RestController
public class ReceiptBatchController {
    private final ReceiptBatchService service;
    public ReceiptBatchController(ReceiptBatchService service) {this.service=service;}
    @PostMapping("/api/receipts/batch")
    public ResponseEntity<ApiResponse<Result>> createBatchReceipts(@RequestBody(required=false) JsonNode body,
                                                                  @RequestParam Map<String,String> query) {
        if(!query.isEmpty())throw ReceiptBatchInput.invalid(query.keySet().iterator().next());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createBatchReceipts(ReceiptBatchInput.parse(body))));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ReceiptBatchInput.invalid("body"),request);
    }
}
