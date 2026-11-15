package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class WithdrawalController {
    private final WithdrawalService service;
    public WithdrawalController(WithdrawalService service){this.service=service;}
    @GetMapping("/api/withdrawals")
    public ApiResponse<WithdrawalDtos.Data> getWithdrawals(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body){
        return ApiResponse.ok(service.list(WithdrawalInput.parse(query,body)));
    }
}
