package com.sicost.cashflowops;

import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.cashflowops.CashFlowDtos.*;

@RestController("cashFlowEvidenceController")
public class CashFlowController {
    private final CashFlowService service;
    public CashFlowController(CashFlowService service){this.service=service;}
    @GetMapping("/api/cash-flow/sources")
    public ApiResponse<SourcesPage> getCashFlowSources(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getCashFlowSources(CashFlowInput.sources(query)));
    }
    @GetMapping("/api/cash-flow/export")
    public void getCashFlowExport(@RequestParam MultiValueMap<String,String> query,HttpServletRequest request,HttpServletResponse response) throws IOException {
        Query input=CashFlowInput.export(query);
        try(var download=service.getCashFlowExport(input,request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
