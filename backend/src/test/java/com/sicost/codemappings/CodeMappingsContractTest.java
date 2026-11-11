package com.sicost.codemappings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CodeMappingsController.class)
@ContextConfiguration(classes = CodeMappingsController.class)
@Import({CodeMappingsService.class, RevisionService.class, ChangeHistoryWriter.class,
        ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class CodeMappingsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean CodeMappingsMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;

    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(mapper.activeCodeName(anyString(), anyString())).thenReturn("선택 코드");
        when(mapper.insert(any())).thenReturn(9L);
        when(mapper.delete(anyLong())).thenReturn(1);
    }
    private CodeMappingRow row(long id) {
        return new CodeMappingRow(id,"표기","GROUP","A","선택 코드","admin",
                LocalDateTime.of(2026,1,1,0,0),null,null);
    }
    private void postBody(String body, int status, String field) throws Exception {
        var result = mvc.perform(post("/api/code-mappings").contentType("application/json").content(body))
                .andExpect(status().is(status));
        if (field != null) result.andExpect(jsonPath("$.field").value(field));
    }
    @Test void readsPageWithRowRevisionAndSeparateTotal() throws Exception {
        when(mapper.list(any())).thenReturn(List.of(row(1)));
        when(mapper.count(any())).thenReturn(61L);
        mvc.perform(get("/api/code-mappings").param("sourceText","표").param("groupId","GROUP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.items[0].revision").isString())
                .andExpect(jsonPath("$.data.total").value(61)).andExpect(jsonPath("$.data.size").value(50));
        verify(mapper).list(argThat(q -> q.sourceText().equals("표") && q.groupId().equals("GROUP") && q.offset()==0));
        verifyNoInteractions(history, invalidation);
    }
    @Test void emptyPageDoesNotWriteDefaults() throws Exception {
        when(mapper.list(any())).thenReturn(List.of());
        mvc.perform(get("/api/code-mappings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50));
        verifyNoInteractions(history, invalidation);
        verify(mapper,never()).insert(any());
    }
    @Test void rejectsZeroPage() throws Exception {
        mvc.perform(get("/api/code-mappings").param("page","0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));
        verifyNoInteractions(mapper,history);
    }
    @Test void rejectsUnsupportedSize() throws Exception {
        mvc.perform(get("/api/code-mappings").param("size","30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));
        verifyNoInteractions(mapper,history);
    }
    @Test void rejectsUnknownQuery() throws Exception {
        mvc.perform(get("/api/code-mappings").param("userId","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,history);
    }
    @Test void createsPubliclyWithServerActorAndLosslessNewHistory() throws Exception {
        postBody("{\"sourceText\":\"  표기  \",\"groupId\":\"GROUP\",\"code\":\"A\"}",201,null);
        verify(mapper).insert(argThat(r -> r.sourceText().equals("표기") && r.userId().equals("admin")));
        var entry = org.mockito.ArgumentCaptor.forClass(com.sicost.common.history.ChangeHistoryEntry.class);
        verify(history).insert(entry.capture());
        assertThat(entry.getValue().before()).isNull();
        assertThat(entry.getValue().type()).isEqualTo("NEW");
        assertThat(entry.getValue().targetKey()).isEqualTo("GROUP:9");
        assertThat(json.readTree(entry.getValue().after()).get("codeName").asText()).isEqualTo("선택 코드");
        assertThat(entry.getValue().userId()).isEqualTo("admin");
        verify(invalidation).afterCommit("code-mappings");
    }
    @Test void authHappyCreateThenGetCodeMappingsUsesTrimmedSourceAndAdmin() throws Exception {
        mvc.perform(post("/api/code-mappings").contentType("application/json")
            .content("{\"sourceText\":\"  표기  \",\"groupId\":\"GROUP\",\"code\":\"A\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap());
        verify(mapper).insert(argThat(w->w.userId().equals("admin") && w.sourceText().equals("표기")));
        verify(history).insert(argThat(e->e.tableName().equals("TB_SY_CODE_MAP") && e.targetKey().equals("GROUP:9")
            && e.type().equals("NEW") && e.before()==null && e.after().contains("codeName") && e.userId().equals("admin")));
        when(mapper.list(any())).thenReturn(List.of(row(9))); when(mapper.count(any())).thenReturn(1L);
        mvc.perform(get("/api/code-mappings").param("groupId","GROUP"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].mapId").value(9))
            .andExpect(jsonPath("$.data.items[0].sourceText").value("표기"))
            .andExpect(jsonPath("$.data.items[0].revision").isString()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void authCreateRejectsLiteralRegUserIdBeforePersistence() throws Exception {
        mvc.perform(post("/api/code-mappings").contentType("application/json")
            .content("{\"sourceText\":\"표기\",\"groupId\":\"GROUP\",\"code\":\"A\",\"regUserId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("regUserId"));
        verifyNoInteractions(mapper,history,invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"", " ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void rejectsInvalidSourceText(String source) throws Exception {
        postBody(json.writeValueAsString(java.util.Map.of("sourceText",source,"groupId","GROUP","code","A")),400,"sourceText");
        verify(mapper,never()).insert(any()); verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"memo","regUserId","actor","primary","useYn","effectiveMonth"})
    void rejectsEveryExtraField(String field) throws Exception {
        postBody("{\"sourceText\":\"표기\",\"groupId\":\"GROUP\",\"code\":\"A\",\""+field+"\":\"other\"}",400,field);
        verifyNoInteractions(mapper,history);
    }
    @ParameterizedTest @ValueSource(strings={"groupId","code"})
    void rejectsMissingRequiredReference(String field) throws Exception {
        var body=json.createObjectNode().put("sourceText","표기").put("groupId","GROUP").put("code","A"); body.remove(field);
        postBody(body.toString(),400,field); verifyNoInteractions(mapper,history);
    }
    @Test void rejectsInactiveDeletedOrMissingReference() throws Exception {
        when(mapper.activeCodeName("GROUP","A")).thenReturn(null);
        postBody("{\"sourceText\":\"표기\",\"groupId\":\"GROUP\",\"code\":\"A\"}",400,"code");
        verify(mapper,never()).insert(any()); verifyNoInteractions(history);
    }
    @Test void duplicateEvenWithAnotherCodeIs409() throws Exception {
        when(mapper.insert(any())).thenReturn(null);
        mvc.perform(post("/api/code-mappings").contentType("application/json").content("{\"sourceText\":\"표기\",\"groupId\":\"GROUP\",\"code\":\"B\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_MAPPING"));
        verifyNoInteractions(history,invalidation);
    }
    @Test void deletesAllInNumericKeyOrderAndRecordsBeforeJson() throws Exception {
        var one=row(2); var two=row(10);
        when(mapper.lock(2)).thenReturn(one); when(mapper.lock(10)).thenReturn(two);
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(deleteBody(two,one)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var order=inOrder(mapper); order.verify(mapper).lock(2); order.verify(mapper).lock(10);
        order.verify(mapper).delete(2); order.verify(mapper).delete(10);
        verify(history,times(2)).insert(argThat(e -> e.type().equals("DEL") && e.before().contains("codeName") && e.after()==null && e.userId().equals("admin")));
    }
    @Test void authHappyDeleteUsesAdminAndCommitInvalidationBeforeGetCodeMappings() throws Exception {
        var one=row(2);var two=row(10);
        when(mapper.lock(2)).thenReturn(one);when(mapper.lock(10)).thenReturn(two);
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(deleteBody(one,two)))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(mapper).delete(2); verify(mapper).delete(10);
        verify(history,times(2)).insert(argThat(e->e.tableName().equals("TB_SY_CODE_MAP") && e.type().equals("DEL")
            && e.targetKey().startsWith("GROUP:") && e.before().contains("sourceText") && e.after()==null && e.userId().equals("admin")));
        verify(invalidation).afterCommit("code-mappings");
        when(mapper.list(any())).thenReturn(List.of());
        mvc.perform(get("/api/code-mappings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void authDeleteRejectsForgedActorBeforeAnyDeleteOrHistory() throws Exception {
        var request=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(deleteBody(row(2)));
        request.put("actor","other");
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(request.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("actor"));
        verifyNoInteractions(mapper,history,invalidation);
    }
    private String deleteBody(CodeMappingRow... rows) throws Exception {
        var items=json.createArrayNode();
        for(var r:rows) items.addObject().put("mapId",r.mapId()).put("expectedRevision",revisions.revision(r));
        return json.createObjectNode().set("items",items).toString();
    }
    @Test void missingOneSelectionDeletesNothing() throws Exception {
        when(mapper.lock(2)).thenReturn(row(2));
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(deleteBody(row(2),row(10))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).delete(anyLong()); verifyNoInteractions(history,invalidation);
    }
    @Test void changedOneSelectionDeletesNothing() throws Exception {
        when(mapper.lock(2)).thenReturn(row(2)); when(mapper.lock(10)).thenReturn(row(11));
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(deleteBody(row(2),row(10))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).delete(anyLong()); verifyNoInteractions(history,invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"items\":[]}","{\"items\":[{}]}","{\"items\":[{\"mapId\":1}]}","{\"items\":[{\"mapId\":1,\"expectedRevision\":null}]}","{\"items\":[],\"actor\":\"other\"}"})
    void invalidDeleteDoesNotTouchData(String body) throws Exception {
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper,history,invalidation);
    }
    @Test void oversizedHistoryRejectsBeforeWriting() throws Exception {
        when(mapper.activeCodeName(anyString(),anyString())).thenReturn("\"".repeat(100));
        postBody(json.writeValueAsString(java.util.Map.of("sourceText","\"".repeat(100),"groupId","G".repeat(30),"code","A".repeat(20))),400,"history");
        verify(mapper,never()).insert(any()); verifyNoInteractions(history);
    }
    @Test void approvedOperationsAreOnClasspath() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("operationId: getCodeMappings","operationId: createCodeMapping","operationId: deleteCodeMappings");
    }
}
