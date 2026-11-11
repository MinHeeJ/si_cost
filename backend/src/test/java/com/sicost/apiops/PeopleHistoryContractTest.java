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
import static com.sicost.apiops.PeopleHistoryDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PeopleHistoryController.class)
@ContextConfiguration(classes=PeopleHistoryController.class)
@Import({PeopleHistoryService.class, PeopleHistoryRepository.class, ApiExceptionHandler.class,
    RequestIdFilter.class, JsonConfiguration.class})
class PeopleHistoryContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PeopleHistoryMapper mapper;
    @MockBean CurrentUserProvider users;

    @BeforeEach void setup() {
        when(mapper.exists(any())).thenReturn(true);
        when(mapper.list(any())).thenReturn(List.of());
    }
    Entry entry(String item, String before, String after) {
        return new Entry(9, "TB_HR_DEV", "DEV-000001", "UPD", item, before, after, null,
            "admin", "임시 관리자", "2026-11-11 09:10");
    }
    @ParameterizedTest @CsvSource({"DEVELOPER,DEV-000001", "CANDIDATE,CAND-000001"})
    void publicQueryUsesServerContextAndEnvelope(String type, String id) throws Exception {
        when(mapper.count(any())).thenReturn(1L);
        when(mapper.list(any())).thenReturn(List.of(entry("이름", "이전 이름", "변경 당시 이름")));
        mvc.perform(get("/api/people-history").param("targetType",type).param("targetId",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.items[0].afterValue").value("변경 당시 이름"))
            .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-11 09:10"));
        verify(users).currentUser();
        verify(mapper).list(new Query(type,id,1,50,0));
    }
    @Test void nullableRequiredKeysSurviveNonNullSerialization() throws Exception {
        when(mapper.list(any())).thenReturn(List.of(entry("등록",null,"등록 당시 값")));
        json.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        var response=mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001"))
            .andExpect(status().isOk()).andReturn();
        var row=json.readTree(response.getResponse().getContentAsString()).path("data").path("items").get(0);
        assertThat(row.size()).isEqualTo(11);
        assertThat(row.has("beforeValue")).isTrue();assertThat(row.get("beforeValue").isNull()).isTrue();
        assertThat(row.has("reason")).isTrue();
    }
    @Test void existingTargetWithoutHistoryReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void missingTargetIs404EvenIfOrphanHistoryExists() throws Exception {
        when(mapper.exists(any())).thenReturn(false);
        var result=mvc.perform(get("/api/people-history").param("targetType","CANDIDATE").param("targetId","absent"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
            .isEqualTo(json.readTree(result.getResponse().getContentAsString()).get("requestId").asText());
        verify(mapper,never()).count(any());verify(mapper,never()).list(any());
    }
    @ParameterizedTest @CsvSource({"targetType,USER", "targetType,''", "targetId,''", "page,0", "page,-1",
        "page,1.5", "page,2147483648", "page,''", "size,10", "size,0", "size,abc"})
    void rejectsInvalidQueryWithoutReadingOrWriting(String field,String value) throws Exception {
        var query=new LinkedMultiValueMap<String,String>();
        query.add("targetType","DEVELOPER");query.add("targetId","DEV-000001");query.set(field,value);
        mvc.perform(get("/api/people-history").params(query))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @CsvSource({"targetType", "targetId"})
    void requiresTarget(String field) throws Exception {
        var query=new LinkedMultiValueMap<String,String>();query.add("targetType","DEVELOPER");query.add("targetId","DEV-000001");query.remove(field);
        mvc.perform(get("/api/people-history").params(query)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @CsvSource({"actor", "sort", "direction", "q", "actorId", "regUserId", "status", "REG_DTTM", "MOD_DTTM"})
    void rejectsExtraFiltersAndForgedSystemFields(String field) throws Exception {
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001").param(field,"other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @CsvSource({"targetType", "targetId", "page", "size"})
    void rejectsRepeatedParameters(String field) throws Exception {
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001").param(field,"1","2"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @Test void trimsIdAndChecksLengthWithoutTruncating() throws Exception {
        mvc.perform(get("/api/people-history").param("targetType"," DEVELOPER ").param("targetId","  "+"a".repeat(20)+"  "))
            .andExpect(status().isOk());
        verify(mapper).exists(new Query("DEVELOPER","a".repeat(20),1,50,0));
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","a".repeat(21)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("targetId"));
    }
    @Test void outOfRangePageKeepsFilteredTotalAndUsesLongOffset() throws Exception {
        when(mapper.count(any())).thenReturn(21L);
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001")
            .param("page","2147483647").param("size","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(21));
        verify(mapper).list(new Query("DEVELOPER","DEV-000001",Integer.MAX_VALUE,100,214748364600L));
    }
    @ParameterizedTest @CsvSource({"TELNO", "phone", "EMAIL_ADDR", "email", "연락처", "전화번호", "이메일"})
    void masksContactBeforeAfterAndUnsafeReason(String item) throws Exception {
        var row=entry(item,"private-before","private-after");
        when(mapper.list(any())).thenReturn(List.of(new Entry(row.historyId(),row.targetTable(),row.targetKey(),row.changeType(),
            item,row.beforeValue(),row.afterValue(),"private-reason",row.actorId(),row.actorName(),row.createdAt())));
        var result=mvc.perform(get("/api/people-history").param("targetType","CANDIDATE").param("targetId","CAND-000001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].beforeValue").value("[비공개]"))
            .andExpect(jsonPath("$.data.items[0].afterValue").value("[비공개]")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-");
    }
    @Test void ordinaryAmountsAndSkillsAreNotMasked() throws Exception {
        when(mapper.list(any())).thenReturn(List.of(entry("MON_UPRC","100","200"),entry("보유 스킬","[Java]","[Java, SQL]")));
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].afterValue").value("200"))
            .andExpect(jsonPath("$.data.items[1].afterValue").value("[Java, SQL]"));
    }
    @Test void internalFailureReturnsSafeRootError() throws Exception {
        when(mapper.count(any())).thenThrow(new IllegalStateException("SQL password private"));
        var result=mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000001"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.meta").isMap()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SQL","password","private");
    }
}
