package com.sicost.codegroups;

import com.sicost.common.api.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CodeGroupController.class)
@org.springframework.test.context.ContextConfiguration(classes=CodeGroupController.class)
@Import({ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class CodeGroupContractTest {
    @Autowired MockMvc mvc;
    @MockBean CodeGroupService service;

    @Test void groupsArePublicAndWrapped() throws Exception {
        when(service.groups()).thenReturn(new CodeGroupDtos.Groups(java.util.List.of()));
        mvc.perform(get("/api/code-groups")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items").isArray());
    }
    @Test void unknownQueryIsRejectedWithoutPersistence() throws Exception {
        mvc.perform(get("/api/code-groups").param("userId","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(service);
    }
    @Test void createReturns201JsonWithoutInventingData() throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json").content("{\"groupId\":\"EDUCATION\",\"name\":\"교육\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).create(any());
    }
    @Test void deleteRequiresQueryRevision() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));
        verifyNoInteractions(service);
    }
    @Test void deleteReturnsEmpty204() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision","current"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete("EDUCATION","A","current");
    }
    @Test void contractIsLoadedFromClasspath() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: getCodeGroups", "operationId: saveGroupCodes", "operationId: deleteGroupCode");
    }
}
