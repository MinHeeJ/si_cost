package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardSnapshotReaderTest {
    final ObjectMapper json=new ObjectMapper();
    final DashboardSnapshotReader reader=new DashboardSnapshotReader(json);
    @Test void includesExactNonPagedReadInSameThreadAndKeepsInnerHeadersAndBytesPrivate() throws Exception {
        var request=mock(MockHttpServletRequest.class);var dispatcher=mock(RequestDispatcher.class);
        var response=new MockHttpServletResponse();
        when(request.getRequestDispatcher("/api/dashboard?month=2026-07")).thenReturn(dispatcher);
        Thread thread=Thread.currentThread();
        doAnswer(c->{
            assertThat(Thread.currentThread()).isSameAs(thread);
            HttpServletResponse inner=c.getArgument(1);
            inner.setHeader("Content-Disposition","not-export");inner.setContentType("application/json");
            inner.getOutputStream().write("{\"success\":true,\"meta\":{},\"data\":{\"selectedMonth\":\"2026-07\",\"value\":null}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return null;
        }).when(dispatcher).include(eq(request),any());
        assertThat(reader.read("2026-07",request,response).get("value").isNull()).isTrue();
        assertThat(response.getContentAsByteArray()).isEmpty();assertThat(response.getHeaderNames()).isEmpty();
    }
    @Test void malformedOrErrorResponseAndWrongMonthNeverBecomeWorkbookData() throws Exception {
        for(String payload:java.util.List.of("<html>error</html>","{\"success\":false}","{\"success\":true,\"data\":{\"selectedMonth\":\"2026-06\"}}")) {
            var request=mock(MockHttpServletRequest.class);var dispatcher=mock(RequestDispatcher.class);
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
            doAnswer(c->{((HttpServletResponse)c.getArgument(1)).getWriter().write(payload);return null;}).when(dispatcher).include(eq(request),any());
            assertThatThrownBy(()->reader.read("2026-07",request,new MockHttpServletResponse())).isInstanceOf(ApiException.class);
        }
    }
}
