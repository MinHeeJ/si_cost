package com.sicost.receipts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.receipts.ReceiptDtos.*;

@RestController
public class ReceiptController {
    private final ReceiptService service;
    public ReceiptController(ReceiptService service){this.service=service;}
    @GetMapping("/api/bills/{id}/receipts")
    public ApiResponse<BillReceipts> getBillReceipts(@PathVariable String id,@RequestParam Map<String,String> query){
        ReceiptInput.query(query,true);
        return ApiResponse.ok(service.getBillReceipts(ReceiptInput.id(id),ReceiptInput.queryYear(query)));
    }
    @PostMapping("/api/bills/{id}/receipts")
    public ResponseEntity<ApiResponse<BillReceiptResult>> createBillReceipt(@PathVariable String id,@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query){
        ReceiptInput.query(query,false);
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createBillReceipt(ReceiptInput.id(id),ReceiptInput.create(body))));
    }
    @PostMapping("/api/bills/{id}/receipts/{receiptId}/cancel")
    public ApiResponse<BillReceiptResult> saveBillReceiptCancellation(@PathVariable String id,@PathVariable String receiptId,
        @RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query){
        ReceiptInput.query(query,false);
        return ApiResponse.ok(service.saveBillReceiptCancellation(ReceiptInput.id(id),ReceiptInput.receiptId(receiptId),ReceiptInput.cancel(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request){
        return new ApiExceptionHandler().application(ReceiptInput.invalid("body"),request);
    }
}
