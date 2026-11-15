package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/** Reuses the public, non-paged dashboard read on the same thread/transaction, without a loopback HTTP connection. */
@Component
public class DashboardSnapshotReader {
    private final ObjectMapper json;
    public DashboardSnapshotReader(ObjectMapper json) { this.json = json; }

    public JsonNode read(String month, HttpServletRequest request, HttpServletResponse response) {
        var dispatcher = request.getRequestDispatcher("/api/dashboard?month=" + month);
        if (dispatcher == null) throw unavailable();
        var capture = new DashboardResponse(response);
        try {
            dispatcher.include(request, capture);
            capture.writer.flush();
            JsonNode envelope = json.readTree(capture.bytes.toByteArray());
            if (capture.status != 200 || envelope == null || !envelope.path("success").asBoolean()
                    || !envelope.path("data").isObject()) throw unavailable();
            JsonNode data = envelope.get("data");
            if (!month.equals(data.path("selectedMonth").asText())) throw unavailable();
            return data;
        } catch (jakarta.servlet.ServletException | IOException e) {
            throw unavailable();
        }
    }
    private static ApiException unavailable() {
        return new ApiException(500,"INTERNAL_ERROR","대시보드 전체 조회를 불러오지 못했습니다. 다시 시도해 주세요.");
    }
    private static final class DashboardResponse extends HttpServletResponseWrapper {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8));
        int status = 200;
        DashboardResponse(HttpServletResponse response) { super(response); }
        @Override public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override public void write(int value) { bytes.write(value); }
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) { throw new IllegalStateException("동기 조회만 지원합니다."); }
            };
        }
        @Override public PrintWriter getWriter() { return writer; }
        @Override public void setStatus(int value) { status = value; }
        @Override public int getStatus() { return status; }
        @Override public void sendError(int value) { status = value; }
        @Override public void sendError(int value, String message) { status = value; }
        @Override public void sendRedirect(String location) { status = 500; }
        @Override public void setHeader(String name, String value) { }
        @Override public void addHeader(String name, String value) { }
        @Override public void setContentType(String type) { }
        @Override public void setContentLength(int length) { }
        @Override public void setContentLengthLong(long length) { }
        @Override public void setCharacterEncoding(String encoding) { }
        @Override public String getCharacterEncoding() { return StandardCharsets.UTF_8.name(); }
        @Override public void flushBuffer() { writer.flush(); }
        @Override public boolean isCommitted() { return false; }
        @Override public void resetBuffer() { bytes.reset(); }
        @Override public void reset() { bytes.reset(); status = 200; }
    }
}
