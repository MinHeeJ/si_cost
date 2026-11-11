package com.sicost.shared;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.InputValidator;
import com.sicost.shared.SharedQueryDtos.*;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SharedQueriesController {
    private final SharedQueriesService service;
    private final MenuService menus;
    private final CurrentUserProvider users;
    public SharedQueriesController(SharedQueriesService service,MenuService menus,CurrentUserProvider users) {
        this.service=service; this.menus=menus; this.users=users;
    }
    @GetMapping("/current-user")
    public ApiResponse<CurrentUserProvider.CurrentUser> getCurrentUser(@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of()); return ApiResponse.ok(users.currentUser());
    }
    @GetMapping("/menus")
    public ApiResponse<MenusData> getMenus(@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of()); return ApiResponse.ok(menus.getMenus());
    }
    @GetMapping("/codes/{group}")
    public ApiResponse<Items<CodeLookup>> getCodes(@PathVariable String group,@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of("includeInactive"));
        String inactive=query.getFirst("includeInactive");
        if(inactive!=null && !inactive.equals("true") && !inactive.equals("false")) throw InputValidator.invalid("includeInactive");
        return ApiResponse.ok(service.getCodes(group,"true".equals(inactive)));
    }
    @GetMapping("/params/{key}")
    public ApiResponse<Parameter> getParameter(@PathVariable String key,@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of("month")); return ApiResponse.ok(service.getParameter(key,query.getFirst("month")));
    }
    @GetMapping("/period-context")
    public ApiResponse<PeriodContext> getPeriodContext(@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of()); return ApiResponse.ok(service.getPeriodContext());
    }
    @GetMapping("/month-close")
    public ApiResponse<Items<MonthClose>> getMonthClose(@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of("from","to")); return ApiResponse.ok(service.getMonthClose(query.getFirst("from"),query.getFirst("to")));
    }
    @GetMapping("/common-code-history")
    public ApiResponse<HistoryPage> getCommonCodeHistory(@RequestParam MultiValueMap<String,String> query) {
        validate(query,Set.of("targetType","groupId","page","size"));
        return ApiResponse.ok(service.getCommonCodeHistory(query.getFirst("targetType"),query.getFirst("groupId"),integer(query,"page",1),integer(query,"size",50)));
    }
    private void validate(MultiValueMap<String,String> query,Set<String> allowed) {
        query.forEach((field,values) -> {
            if(!allowed.contains(field)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",field,null);
            if(values.size()!=1) throw InputValidator.invalid(field);
        });
    }
    private int integer(MultiValueMap<String,String> query,String field,int fallback) {
        if(!query.containsKey(field)) return fallback;
        try { return Integer.parseInt(query.getFirst(field)); }
        catch(NumberFormatException e) { throw InputValidator.invalid(field); }
    }
}
