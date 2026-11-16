package com.sicost.meops;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.meops.ProfileDtos.*;

@RestController
public class ProfileController {
    private final ProfileService service;
    public ProfileController(ProfileService service) { this.service=service; }
    @GetMapping("/api/me/profile")
    public ApiResponse<Profile> getMyProfile(@RequestParam MultiValueMap<String,String> query,
                                           @RequestBody(required=false) String body) {
        if(!query.isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",query.keySet().iterator().next(),null);
        if(body!=null)throw new ApiException(400,"UNKNOWN_FIELD","내 정보 조회에는 본문을 보낼 수 없습니다.","body",null);
        return ApiResponse.ok(service.getMyProfile());
    }
}
