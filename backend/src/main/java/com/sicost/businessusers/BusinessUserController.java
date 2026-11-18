package com.sicost.businessusers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.businessusers.BusinessUserDtos.*;

@RestController
public class BusinessUserController {
    private final BusinessUserService service;
    private com.sicost.authentication.AuthenticationPasswordService passwords;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void authentication(com.sicost.authentication.AuthenticationPasswordService passwords,com.sicost.permission.PermissionService permissions) {this.passwords=passwords;this.permissions=permissions;}
    public BusinessUserController(BusinessUserService service) {this.service=service;}
    @GetMapping("/api/business-users")
    public ApiResponse<Page> getBusinessUsers(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.list(BusinessUserInput.filter(query)));
    }
    @GetMapping("/api/business-users/{id}")
    public ApiResponse<User> getBusinessUser(@PathVariable String id,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.get(id));
    }
    @PostMapping("/api/business-users")
    public ResponseEntity<ApiResponse<Result>> createBusinessUser(@RequestBody JsonNode body,@RequestParam Map<String,String> query,HttpServletRequest request) {
        BusinessUserInput.query(query,Set.of());
        Result registered=service.create(BusinessUserInput.create(body),request);
        com.sicost.authentication.AuthenticationPasswordService.Delivery delivery;
        try {delivery=passwords.initial(registered.user().userId(),permissions.userId());}
        catch(com.sicost.common.api.ApiException e) {
            delivery=new com.sicost.authentication.AuthenticationPasswordService.Delivery(false,"FAILED",false,
                com.sicost.authentication.AuthenticationService.maskedEmail(registered.user().email()),"비밀번호 발급 설정을 확인한 뒤 새 비밀번호 다시 발급을 이용해 주세요.");
        }
        String message="ACCEPTED".equals(delivery.mailStatus())?"사용자를 등록하고 임시 비밀번호를 발송했습니다.":"사용자는 등록됐지만 비밀번호 발송에 실패했습니다. 사용자 상세에서 새 비밀번호 다시 발급을 이용해 주세요.";
        return ResponseEntity.status(201).body(ApiResponse.ok(new Result(true,message,registered.user(),delivery)));
    }
    @PutMapping("/api/business-users/{id}")
    public ApiResponse<Result> putBusinessUser(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.update(id,BusinessUserInput.edit(body)));
    }
}
