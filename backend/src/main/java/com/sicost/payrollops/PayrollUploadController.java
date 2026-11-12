package com.sicost.payrollops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.AssignmentInputPolicy;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.payrollops.PayrollUploadDtos.*;

@RestController
public class PayrollUploadController {
    private final PayrollUploadService service;
    public PayrollUploadController(PayrollUploadService service) {this.service=service;}
    @GetMapping("/api/payroll/uploads")
    public ApiResponse<Page> getPayrollUploads(@RequestParam MultiValueMap<String,String> query,HttpServletRequest request) {
        query.forEach((key,values)->{
            if(!Set.of("month","page","size").contains(key)||values.size()!=1)throw ProjectInputPolicy.invalid(key);
        });
        try {if(request.getInputStream().read()!=-1)throw ProjectInputPolicy.invalid("body");}
        catch(IOException e) {throw ProjectInputPolicy.invalid("body");}
        String month=DatabaseMonthCodec.toDatabase(ProjectInputPolicy.month(query.getFirst("month"),"month").toString());
        int page=AssignmentInputPolicy.page(integer(query.getFirst("page"),1,"page"));
        int size=AssignmentInputPolicy.size(integer(query.getFirst("size"),50,"size"));
        return ApiResponse.ok(service.getPayrollUploads(new Query(month,page,size,((long)page-1)*size)));
    }
    private int integer(String raw,int fallback,String field) {
        if(raw==null)return fallback;
        String value=ProjectInputPolicy.text(raw,10,field);
        if(!value.matches("[0-9]+"))throw ProjectInputPolicy.invalid(field);
        try {return Integer.parseInt(value);}
        catch(NumberFormatException e) {throw ProjectInputPolicy.invalid(field);}
    }
}
