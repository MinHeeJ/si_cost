package com.sicost.businessusersops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.businessusersops.BusinessUserProjectsDtos.*;

@RestController
public class BusinessUserProjectsController {
    private final BusinessUserProjectsService service;
    public BusinessUserProjectsController(BusinessUserProjectsService service) { this.service=service; }
    @GetMapping("/api/business-users/{userId}/projects")
    public ResponseEntity<ApiResponse<BusinessUserProjectsData>> getBusinessUserProjects(
            @PathVariable String userId, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty())
            throw ProjectInputPolicy.invalid(request.getParameterMap().keySet().iterator().next());
        if (request.getContentLengthLong()>0 || request.getHeader("Transfer-Encoding")!=null)
            throw ProjectInputPolicy.invalid("body");
        return ResponseEntity.ok().header("Cache-Control","no-store")
            .body(ApiResponse.ok(service.getBusinessUserProjects(userId)));
    }
}
