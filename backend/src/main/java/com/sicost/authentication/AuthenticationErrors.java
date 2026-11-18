package com.sicost.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class AuthenticationErrors {
    private AuthenticationErrors() {}
    public static void write(ObjectMapper json,HttpServletRequest request,HttpServletResponse response,ApiException error) throws IOException {
        Object value=request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String id=value instanceof String text?text:UUID.randomUUID().toString();
        response.setStatus(error.status());response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id",id);response.setHeader("Cache-Control","no-store");
        json.writeValue(response.getOutputStream(),new ApiError(false,Map.of(),error.code(),error.getMessage(),error.field(),error.details(),id));
    }
}
