package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.financialitemsops.FinancialPreviewDtos.*;

@RestController
public class FinancialPreviewController {
    private final FinancialPreviewService service;
    public FinancialPreviewController(FinancialPreviewService service){this.service=service;}
    @GetMapping("/api/financial-items/year-status")
    public ApiResponse<YearStatus> getFinancialYearStatus(@RequestParam MultiValueMap<String,String> query){FinancialPreviewInput.query(query,true);return ApiResponse.ok(service.getFinancialYearStatus(FinancialPreviewInput.year(query.getFirst("year"))));}
    @PostMapping("/api/financial-items/preview")
    public ApiResponse<Preview> createFinancialPreview(@RequestBody(required=false) JsonNode body,@RequestParam MultiValueMap<String,String> query){FinancialPreviewInput.query(query,false);return ApiResponse.ok(service.createFinancialPreview(FinancialPreviewInput.preview(body)));}
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request){return new ApiExceptionHandler().application(FinancialPreviewInput.invalid("body"),request);}
}
