package com.sicost.structure;

import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.structure.ProfitStructureDtos.*;

@RestController
public class ProfitStructureController {
    private final ProfitStructureService service;
    private final ProfitStructureExportService exports;
    public ProfitStructureController(ProfitStructureService service,ProfitStructureExportService exports) {this.service=service;this.exports=exports;}
    @GetMapping("/api/profit-loss/structure")
    public ApiResponse<Data> getProfitLossStructure(@RequestParam MultiValueMap<String,String> values,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProfitLossStructure(ProfitStructureInput.parse(values,false)));
    }
    @GetMapping("/api/profit-loss/structure/export")
    public void getProfitLossStructureExport(@RequestParam MultiValueMap<String,String> values,@RequestBody(required=false) String body,
        HttpServletRequest request,HttpServletResponse response)throws IOException {
        rejectBody(body);
        try(var download=exports.create(ProfitStructureInput.parse(values,true),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    private void rejectBody(String body) {if(body!=null&&!body.isBlank())throw ProfitStructureInput.invalid("body");}
}
