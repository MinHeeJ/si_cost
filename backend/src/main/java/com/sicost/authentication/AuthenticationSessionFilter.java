package com.sicost.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AuthenticationSessionFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC=Set.of("GET /api/health","GET /api/auth/csrf","GET /api/auth/session","POST /api/auth/login","POST /api/auth/forgot-password","POST /api/auth/logout","POST /api/auth/restart");
    private static final Set<String> PENDING=Set.of("POST /api/auth/mfa/verify","POST /api/auth/mfa/resend","POST /api/auth/password/required");
    private final AuthenticationService service;
    private final ObjectMapper json;
    public AuthenticationSessionFilter(AuthenticationService service,ObjectMapper json) {this.service=service;this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(!path.startsWith("/api/")) {chain.doFilter(request,response);return;}
        String key=request.getMethod()+" "+path;
        AuthenticationState state=null;
        try {
            if(!PUBLIC.contains(key))state=service.validate(request,!PENDING.contains(key));
        } catch(ApiException e) {AuthenticationErrors.write(json,request,response,e);return;}
        response.setHeader("Cache-Control","no-store");
        var timed=new TimingResponse(request,response,state!=null && !background(request));
        try {chain.doFilter(request,timed);timed.prepare();}
        catch(ApiException failure) {
            if(response.isCommitted())throw failure;
            response.resetBuffer();response.setHeader("Content-Disposition",null);response.setHeader("Content-Length",null);
            AuthenticationErrors.write(json,request,response,failure);
        }
    }
    /** Publish timing before the first JSON/stream/file byte, not after a committed response. */
    private final class TimingResponse extends HttpServletResponseWrapper {
        private final HttpServletRequest request;
        private final boolean activity;
        private final java.util.concurrent.atomic.AtomicBoolean prepared=new java.util.concurrent.atomic.AtomicBoolean();
        private ServletOutputStream stream;
        private java.io.PrintWriter writer;
        TimingResponse(HttpServletRequest request,HttpServletResponse response,boolean activity) {super(response);this.request=request;this.activity=activity;}
        void prepare() {
            if(!prepared.compareAndSet(false,true) || getStatus()>=400 || isCommitted())return;
            var current=service.state(request);
            if(current==null || current.revoked || current.step!=AuthenticationState.Step.AUTHENTICATED)return;
            current=service.validate(request,true);
            if(activity)service.activity(current);
            setHeader("X-Session-Server-Time",service.serverTime().toString());
            setHeader("X-Session-Expires-At",service.expires(current).toString());
        }
        @Override public ServletOutputStream getOutputStream() throws IOException {
            if(stream==null) {
                var output=super.getOutputStream();
                stream=new ServletOutputStream() {
                    @Override public boolean isReady() {return output.isReady();}
                    @Override public void setWriteListener(WriteListener listener) {output.setWriteListener(listener);}
                    @Override public void write(int value) throws IOException {prepare();output.write(value);}
                    @Override public void write(byte[] bytes,int offset,int length) throws IOException {prepare();output.write(bytes,offset,length);}
                    @Override public void flush() throws IOException {prepare();output.flush();}
                    @Override public void close() throws IOException {prepare();output.close();}
                };
            }
            return stream;
        }
        @Override public java.io.PrintWriter getWriter() throws IOException {
            if(writer==null)writer=new java.io.PrintWriter(new java.io.OutputStreamWriter(getOutputStream(),getCharacterEncoding()));
            return writer;
        }
        @Override public void flushBuffer() throws IOException {prepare();if(writer!=null)writer.flush();super.flushBuffer();}
    }
    private boolean background(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/auth/"))return true;
        return "GET".equals(request.getMethod()) && "true".equals(request.getHeader("X-Background-Request"))
            && (path.equals("/api/notifications") || path.equals("/api/notifications/unread-count") || path.equals("/api/automation-runs") || path.matches("/api/analysis/questions/[^/]+") || path.matches("/api/receivables/[^/]+/deliveries"));
    }
}
