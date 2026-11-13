package com.sicost.financialitems;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.financialitems.FinancialDtos.*;

@RestController
public class FinancialController {
    private final FinancialService service;
    private final FinancialWriteService writes;
    public FinancialController(FinancialService service,FinancialWriteService writes){this.service=service;this.writes=writes;}
    @GetMapping("/api/financial-items")
    public ApiResponse<Map<String,Object>> getFinancialItems(@RequestParam MultiValueMap<String,String> query){
        query.forEach((key,values)->{if(!key.equals("month"))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);if(values.size()!=1)throw FinancialInput.invalid(key);});
        return ApiResponse.ok(service.getFinancialItems(query.getFirst("month")));
    }
    @PutMapping("/api/financial-items")
    public ApiResponse<Commit> saveFinancialItems(@RequestBody(required=false) JsonNode body,@RequestParam MultiValueMap<String,String> query){
        query.keySet().forEach(key->{throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);});
        return ApiResponse.ok(writes.saveFinancialItems(FinancialInput.save(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request){return new ApiExceptionHandler().application(FinancialInput.invalid("body"),request);}
}
