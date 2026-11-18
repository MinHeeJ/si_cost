package com.sicost.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    /** Always chosen by the server; audit records use it so a caller-supplied request id never reaches the audit log. */
    public static final String SERVER_ATTRIBUTE = RequestIdFilter.class.getName() + ".serverRequestId";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String candidate = request.getHeader("X-Request-Id");
        String id = candidate != null && candidate.matches("[A-Za-z0-9_-]{1,64}")
                ? candidate : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        request.setAttribute(SERVER_ATTRIBUTE, UUID.randomUUID().toString());
        response.setHeader("X-Request-Id", id);
        chain.doFilter(request, response);
    }
}
