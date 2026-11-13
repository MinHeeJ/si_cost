package com.sicost.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.progress.ProjectProgressDtos.*;

@RestController
@RequestMapping("/api/projects/{id}")
public class ProjectProgressController {
    private final ProjectProgressQueryService queries;
    private final ProjectProgressService service;
    public ProjectProgressController(ProjectProgressQueryService queries,ProjectProgressService service){this.queries=queries;this.service=service;}
    @GetMapping("/progress")
    public ApiResponse<Data> getProjectProgress(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(queries.getProjectProgress(ProjectProgressInput.id(id),ProjectProgressInput.year(query)));
    }
    @GetMapping("/progress/history")
    public ApiResponse<HistoryPage> getProjectProgressHistory(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(queries.getProjectProgressHistory(ProjectProgressInput.id(id),ProjectProgressInput.history(query)));
    }
    @PostMapping("/progress/steps/initialize")
    public ApiResponse<MutationResult> createProjectProgressSteps(@PathVariable String id,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.createProjectProgressSteps(ProjectProgressInput.id(id),ProjectProgressInput.write(body)));
    }
    @PostMapping("/progress/steps/preview")
    public ApiResponse<Preview> createProjectStepDatesPreview(@PathVariable String id,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(queries.preview(ProjectProgressInput.id(id),ProjectProgressInput.dates(body,true)));
    }
    @PutMapping("/progress/steps")
    public ApiResponse<MutationResult> saveProjectStepDates(@PathVariable String id,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.saveProjectStepDates(ProjectProgressInput.id(id),ProjectProgressInput.dates(body,false)));
    }
    @PostMapping("/progress/steps/{stepSn}/complete")
    public ApiResponse<MutationResult> saveProjectStepCompletion(@PathVariable String id,@PathVariable String stepSn,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.saveProjectStepCompletion(ProjectProgressInput.id(id),ProjectProgressInput.serial(stepSn,99,"stepSn"),ProjectProgressInput.write(body)));
    }
    @PostMapping("/progress/week-memos")
    public ApiResponse<MutationResult> createProjectWeekMemo(@PathVariable String id,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.createProjectWeekMemo(ProjectProgressInput.id(id),ProjectProgressInput.memo(body)));
    }
    @PutMapping("/progress/week-memos/{memoSn}")
    public ApiResponse<MutationResult> putProjectWeekMemo(@PathVariable String id,@PathVariable String memoSn,@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.putProjectWeekMemo(ProjectProgressInput.id(id),ProjectProgressInput.serial(memoSn,99999,"memoSn"),ProjectProgressInput.memo(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
    private static void noQuery(MultiValueMap<String,String> query){ProjectProgressInput.query(query,Set.of());}
    private static void rejectBody(String body){if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
