package com.sicost.uploadsops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.uploadsops.UploadIssuesDtos.*;

@RestController
public class UploadIssuesController {
    private final UploadIssuesService service;
    public UploadIssuesController(UploadIssuesService service) { this.service = service; }
    @GetMapping("/api/uploads/{id}/issues")
    public ApiResponse<Page> getUploadIssues(@PathVariable String id, @RequestParam MultiValueMap<String, String> query) {
        UploadIssuesInput.allow(query, true);
        int page = InputValidator.page(UploadIssuesInput.integer(query.getFirst("page"), 1, "page"));
        int size = InputValidator.size(UploadIssuesInput.integer(query.getFirst("size"), 50, "size"));
        return ApiResponse.ok(service.list(UploadIssuesInput.id(id), UploadIssuesInput.severity(query.getFirst("severity")), page, size));
    }
    @GetMapping("/api/uploads/{id}/issues.xlsx")
    public void getUploadIssuesXlsx(@PathVariable String id, @RequestParam MultiValueMap<String, String> query,
                                   HttpServletRequest request, HttpServletResponse response) throws IOException {
        UploadIssuesInput.allow(query, false);
        try (var download = service.download(UploadIssuesInput.id(id), UploadIssuesInput.severity(query.getFirst("severity")), request)) {
            long size = download.size();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(size);
            download.transferTo(response.getOutputStream());
        }
    }
}
