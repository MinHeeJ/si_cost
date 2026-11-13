package com.sicost.corporatetaxesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@RestController
public class CorporateTaxController {
    private final CorporateTaxService service;
    public CorporateTaxController(CorporateTaxService service){this.service=service;}
    @GetMapping("/api/corporate-taxes/{year}/allocation")
    public ApiResponse<Allocation> getCorporateTaxAllocation(@PathVariable String year,@RequestParam Map<String,String> query){CorporateTaxInput.query(query);return ApiResponse.ok(service.getCorporateTaxAllocation(CorporateTaxInput.year(year)));}
    @PostMapping("/api/corporate-taxes/initialize")
    public ApiResponse<DefaultsResult> createCorporateTaxDefaults(@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query){CorporateTaxInput.query(query);return ApiResponse.ok(service.createCorporateTaxDefaults(CorporateTaxInput.initialize(body)));}
    @PostMapping("/api/corporate-taxes/{year}/payments/{payType}/confirmation")
    public ApiResponse<ConfirmationResult> saveCorporateTaxPaymentConfirmation(@PathVariable String year,@PathVariable String payType,@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query){CorporateTaxInput.query(query);return ApiResponse.ok(service.saveCorporateTaxPaymentConfirmation(CorporateTaxInput.year(year),CorporateTaxInput.payType(payType),CorporateTaxInput.confirmation(body)));}
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request){return new ApiExceptionHandler().application(CorporateTaxInput.invalid("body"),request);}
}
