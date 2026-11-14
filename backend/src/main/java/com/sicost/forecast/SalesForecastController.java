package com.sicost.forecast;

import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class SalesForecastController {
    private final SalesRevenueForecastService forecasts;
    private final SalesForecastExportService exports;
    public SalesForecastController(SalesRevenueForecastService forecasts,SalesForecastExportService exports) {this.forecasts=forecasts;this.exports=exports;}
    @GetMapping("/api/sales-opportunities/forecast")
    public ApiResponse<SalesForecastDtos.Result> getSalesOpportunityForecast(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        SalesForecastInput.validate(query,false,body);
        return ApiResponse.ok(forecasts.forecast());
    }
    @GetMapping("/api/sales-opportunities/forecast/export")
    public void getSalesOpportunityForecastExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        String unit=SalesForecastInput.validate(query,true,body);
        try(var download=exports.export(unit,request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
