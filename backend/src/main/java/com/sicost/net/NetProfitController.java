package com.sicost.net;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.operating.OperatingProfitInput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class NetProfitController {
    private final NetProfitService service;
    private final NetProfitExportService exports;
    public NetProfitController(NetProfitService service,NetProfitExportService exports) {this.service=service;this.exports=exports;}
    @GetMapping("/api/profit-loss/net")
    public ApiResponse<NetProfitDtos.Data> getNetProfitLoss(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getNetProfitLoss(OperatingProfitInput.parse(query,false)));
    }
    @GetMapping("/api/profit-loss/net/export")
    public void getNetProfitLossExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,
        HttpServletRequest request,HttpServletResponse response)throws IOException {
        rejectBody(body);
        try(var download=exports.create(OperatingProfitInput.parse(query,true),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    private void rejectBody(String body) {if(body!=null&&!body.isBlank())throw new ApiException(400,"UNKNOWN_FIELD","조회 요청 본문은 허용되지 않습니다.","body",null);}
}
