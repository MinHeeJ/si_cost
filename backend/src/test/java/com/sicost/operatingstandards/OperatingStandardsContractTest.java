package com.sicost.operatingstandards;

import com.sicost.common.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperatingStandardsController.class)
@org.springframework.test.context.ContextConfiguration(classes=OperatingStandardsController.class)
@Import({ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class OperatingStandardsContractTest {
    @Autowired MockMvc mvc;
    @MockBean OperatingStandardsService service;
    @Test void publicListHasSuccessMetaAndItems() throws Exception {
        when(service.list(null)).thenReturn(new OperatingStandardsDtos.Items(List.of()));
        mvc.perform(get("/api/operating-standards")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items").isArray());
    }
    @Test void invalidMonthIsRejected() throws Exception {
        mvc.perform(get("/api/operating-standards").param("month","2026-00"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
            .andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(service);
    }
    @Test void longHistoryKeyIsRejected() throws Exception {
        mvc.perform(get("/api/operating-standards/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/history"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("key"));
        verifyNoInteractions(service);
    }
    @Test void forgedActorIsRejected() throws Exception {
        mvc.perform(put("/api/operating-standards").contentType("application/json")
            .content("{\"regUserId\":\"other\",\"changes\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(service);
    }
}
