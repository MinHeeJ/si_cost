package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.apiops.MasterHistoryDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MasterHistoryController.class)
@ContextConfiguration(classes=MasterHistoryController.class)
@Import({MasterHistoryService.class, MasterHistoryRepository.class, ApiExceptionHandler.class,
    RequestIdFilter.class, JsonConfiguration.class})
class MasterHistoryContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MasterHistoryMapper mapper;
    @MockBean CurrentUserProvider users;

    @BeforeEach void setup() {
        when(mapper.exists(any())).thenReturn(true);
        when(mapper.list(any())).thenReturn(List.of());
    }
    Entry entry(String item, String before, String after) {
        return new Entry(9, "TB_SY_USER", "admin", "UPD", item, before, after, null,
            "admin", "임시 관리자", "2026-11-11 09:10");
    }
    @ParameterizedTest @CsvSource({"USER,admin", "CLIENT,C-001", "PART,PART-001"})
    void publicQueryUsesServerContextAndEnvelope(String type, String id) throws Exception {
        when(mapper.count(any())).thenReturn(1L);
        when(mapper.list(any())).thenReturn(List.of(entry("이름", "이전 이름", "변경 당시 이름")));
        mvc.perform(get("/api/master-history").param("targetType",type).param("targetId",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.items[0].afterValue").value("변경 당시 이름"))
            .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-11 09:10"));
        verify(users).currentUser();
        verify(mapper).list(new Query(type,id,1,50,0));
    }
    @Test void existingTargetWithoutHistoryReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void missingTargetIs404EvenIfOrphanHistoryExists() throws Exception {
        when(mapper.exists(any())).thenReturn(false);
        var result=mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","absent"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
            .isEqualTo(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText());
        verify(mapper,never()).count(any()); verify(mapper,never()).list(any());
    }
    @ParameterizedTest @CsvSource({"targetType,UNKNOWN", "targetType,''", "targetId,''", "page,0", "page,-1",
        "page,1.5", "page,2147483648", "page,''", "size,10", "size,0", "size,abc"})
    void rejectsInvalidQuery(String field,String value) throws Exception {
        var query=new LinkedMultiValueMap<String,String>();
        query.add("targetType","USER");query.add("targetId","admin");query.set(field,value);
        mvc.perform(get("/api/master-history").params(query))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @CsvSource({"targetType", "targetId"})
    void requiresTarget(String field) throws Exception {
        var query=new LinkedMultiValueMap<String,String>();query.add("targetType","USER");query.add("targetId","admin");query.remove(field);
        mvc.perform(get("/api/master-history").params(query)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field));
    }
    @ParameterizedTest @CsvSource({"sort", "direction", "q", "actorId", "regUserId", "useYn"})
    void rejectsExtraFiltersAndForgedSystemFields(String field) throws Exception {
        mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","admin").param(field,"x"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @Test void rejectsRepeatedParameters() throws Exception {
        mvc.perform(get("/api/master-history").param("targetType","USER","CLIENT").param("targetId","admin"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("targetType"));
    }
    @Test void trimsIdAndChecksLengthWithoutTruncating() throws Exception {
        mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","  "+"a".repeat(100)+"  "))
            .andExpect(status().isOk());
        verify(mapper).exists(new Query("USER","a".repeat(100),1,50,0));
        mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","a".repeat(101)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("targetId"));
    }
    @Test void outOfRangePageKeepsFilteredTotalAndUsesLongOffset() throws Exception {
        when(mapper.count(any())).thenReturn(21L);
        mvc.perform(get("/api/master-history").param("targetType","PART").param("targetId","PART-001")
            .param("page","2147483647").param("size","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(21));
        verify(mapper).list(new Query("PART","PART-001",Integer.MAX_VALUE,100,214748364600L));
    }
    @Test void masksUnsafeAccountAndSuppressesSecretsWithoutDecrypting() throws Exception {
        when(mapper.list(any())).thenReturn(List.of(entry("accountNumber","1234567890","****7890"),
            entry("ACCT_NO_ENC","v1.encrypted","v1.changed"),entry("PWD_HASH","not-public","also-private")));
        mvc.perform(get("/api/master-history").param("targetType","CLIENT").param("targetId","C-001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].beforeValue").value("****"))
            .andExpect(jsonPath("$.data.items[0].afterValue").value("****7890"))
            .andExpect(jsonPath("$.data.items[1].afterValue").value("****"))
            .andExpect(jsonPath("$.data.items[2].beforeValue").isEmpty());
    }
    @Test void internalFailureReturnsSafeRootError() throws Exception {
        when(mapper.count(any())).thenThrow(new IllegalStateException("SQL password private"));
        var result=mvc.perform(get("/api/master-history").param("targetType","USER").param("targetId","admin"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.meta").isMap()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL","password","private");
    }
}
