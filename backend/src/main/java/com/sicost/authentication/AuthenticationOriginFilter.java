package com.sicost.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** CSRF possession does not exempt a browser write from the Origin policy. */
final class AuthenticationOriginFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final Set<String> trusted;
    AuthenticationOriginFilter(ObjectMapper json,Set<String> trusted) {this.json=json;this.trusted=Set.copyOf(trusted);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String origin=request.getHeader("Origin");
        if(origin!=null && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            try {
                URI incoming=URI.create(origin);
                int port=request.getServerPort();
                String host=request.getServerName();
                if(host.contains(":") && !host.startsWith("["))host="["+host+"]";
                String expected=request.getScheme()+"://"+host+(port==defaultPort(request.getScheme())?"":":"+port);
                if(!valid(incoming) || !(same(incoming,URI.create(expected)) || trusted.contains(origin))) {
                    AuthenticationErrors.write(json,request,response,new ApiException(403,"ORIGIN_FORBIDDEN","허용되지 않은 요청 출처입니다."));return;
                }
            } catch(IllegalArgumentException invalid) {
                AuthenticationErrors.write(json,request,response,new ApiException(403,"ORIGIN_FORBIDDEN","허용되지 않은 요청 출처입니다."));return;
            }
        }
        chain.doFilter(request,response);
    }
    private static boolean valid(URI value) {return ("http".equals(value.getScheme()) || "https".equals(value.getScheme())) && value.getHost()!=null && value.getPort()<=65535 && value.getUserInfo()==null && value.getRawQuery()==null && value.getRawFragment()==null && (value.getRawPath()==null || value.getRawPath().isEmpty());}
    private static int defaultPort(String scheme) {return "https".equalsIgnoreCase(scheme)?443:80;}
    private static boolean same(URI left,URI right) {return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost()) && (left.getPort()<0?defaultPort(left.getScheme()):left.getPort())==(right.getPort()<0?defaultPort(right.getScheme()):right.getPort());}
}
