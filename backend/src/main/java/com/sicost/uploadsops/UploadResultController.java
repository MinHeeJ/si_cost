package com.sicost.uploadsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b9.B9Dtos.Result;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class UploadResultController {
    private final UploadResultService service;
    private final ObjectMapper json;
    private java.util.List<UploadResultProvider> providers = java.util.List.of();
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setProviders(java.util.List<UploadResultProvider> providers) { this.providers=java.util.List.copyOf(providers); }
    public UploadResultController(UploadResultService service, ObjectMapper json) {
        this.service=service;this.json=json;
    }
    @GetMapping("/api/uploads/{id}/result")
    public ApiResponse<?> getUploadResult(@PathVariable String id,
            @RequestParam MultiValueMap<String,String> query, HttpServletRequest request) {
        if(!query.isEmpty())throw ProjectInputPolicy.invalid(query.keySet().iterator().next());
        rejectBody(request);
        if(!id.matches("[0-9]{1,19}"))throw ProjectInputPolicy.invalid("id");
        long number;
        try { number=Long.parseLong(id); }
        catch(NumberFormatException e) { throw ProjectInputPolicy.invalid("id"); }
        if(number<1)throw ProjectInputPolicy.invalid("id");
        for(var provider:providers)if(provider.supports(number))return ApiResponse.ok(provider.result(number));
        return ApiResponse.ok(service.get(number));
    }
    private void rejectBody(HttpServletRequest request) {
        try {
            var input=request.getInputStream();
            int first=input.read();
            if(first<0)return;
            byte[] tail=input.readNBytes(8192);
            byte[] bytes=new byte[tail.length+1];bytes[0]=(byte)first;
            System.arraycopy(tail,0,bytes,1,tail.length);
            var body=json.readTree(bytes);
            if(body!=null&&body.isObject()&&body.fieldNames().hasNext())
                throw ProjectInputPolicy.invalid(body.fieldNames().next());
            throw ProjectInputPolicy.invalid("body");
        } catch(IOException e) { throw ProjectInputPolicy.invalid("body"); }
    }
}
