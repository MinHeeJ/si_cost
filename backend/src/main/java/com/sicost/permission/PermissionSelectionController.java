package com.sicost.permission;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Public workforce selection attributes, not developer detail or salary authority. */
@RestController
public class PermissionSelectionController {
    private final PermissionMapper mapper;
    private final PermissionService policies;
    public PermissionSelectionController(PermissionMapper mapper,PermissionService policies) {this.mapper=mapper;this.policies=policies;}
    public record People(List<PermissionMapper.PersonOption> items,long total,int page,int size) {}
    @GetMapping("/api/assignment-people/options")
    public ApiResponse<People> assignmentPeople(@RequestParam Map<String,String> query) {
        query.keySet().forEach(key->{if(!key.equals("q"))throw InputValidator.invalid(key);});
        policies.require("UI-12","INQ");if(!policies.allows("UI-12","REG") && !policies.allows("UI-12","UPD"))throw PermissionService.denied();
        String q=InputValidator.optionalText(query.get("q"),100,"q");if(q==null)q="";
        return ApiResponse.ok(new People(mapper.assignmentPeople(q),mapper.assignmentPeopleCount(q),1,50));
    }
}
