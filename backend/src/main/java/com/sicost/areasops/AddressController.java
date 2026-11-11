package com.sicost.areasops;

import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.areasops.AddressDtos.*;

@RestController
public class AddressController {
    private final AddressService service;
    public AddressController(AddressService service) { this.service=service; }
    @GetMapping("/api/areas/sidos")
    public ApiResponse<Areas> getAreaSidos(@RequestParam MultiValueMap<String,String> query) {
        AddressInput.allowQuery(query,Set.of());return ApiResponse.ok(service.getSidos());
    }
    @GetMapping("/api/areas/children")
    public ApiResponse<Areas> getAreaChildren(@RequestParam MultiValueMap<String,String> query) {
        AddressInput.allowQuery(query,Set.of("parentCode","level"));
        return ApiResponse.ok(service.getChildren(query.getFirst("parentCode"),query.getFirst("level")));
    }
    @GetMapping("/api/areas/{code}/path")
    public ApiResponse<AreaPath> getAreaPath(@PathVariable String code,@RequestParam MultiValueMap<String,String> query) {
        AddressInput.allowQuery(query,Set.of());return ApiResponse.ok(service.getPath(code));
    }
}
