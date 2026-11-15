package com.sicost.withdrawalsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentsops.AssignmentActionsDtos.ExtensionResult;
import com.sicost.assignmentsops.AssignmentActionsService;
import com.sicost.common.api.ApiResponse;
import com.sicost.workforcerecommendations.WorkforceRecommendationDtos.Data;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalActionsController {
    private final AssignmentActionsService assignments;
    private final NextPositionRecommendationService recommendations;
    private final WithdrawalExportService exports;
    public WithdrawalActionsController(AssignmentActionsService assignments,NextPositionRecommendationService recommendations,WithdrawalExportService exports){this.assignments=assignments;this.recommendations=recommendations;this.exports=exports;}
    @PostMapping("/{assignmentId}/extension-possibility")
    public ApiResponse<ExtensionResult> saveWithdrawalExtensionPossibility(@PathVariable String assignmentId,@RequestParam MultiValueMap<String,String> query,@RequestBody JsonNode body){
        WithdrawalActionInput.noQuery(query);
        return ApiResponse.ok(assignments.extensionPossibility(WithdrawalActionInput.id(assignmentId),WithdrawalActionInput.extension(body)));
    }
    @PostMapping("/{assignmentId}/next-positions")
    public ApiResponse<Data> createNextPositionRecommendation(@PathVariable String assignmentId,@RequestParam MultiValueMap<String,String> query,@RequestBody JsonNode body){
        WithdrawalActionInput.noQuery(query);
        return ApiResponse.ok(recommendations.create(WithdrawalActionInput.id(assignmentId),WithdrawalActionInput.recommendation(body)));
    }
    @GetMapping("/export")
    public void getWithdrawalExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,HttpServletRequest request,HttpServletResponse response) throws IOException {
        try(var file=exports.export(WithdrawalActionInput.export(query,body),request)){
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(file.size());file.transferTo(response.getOutputStream());
        }
    }
}
