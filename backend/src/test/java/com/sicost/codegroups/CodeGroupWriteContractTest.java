package com.sicost.codegroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.codegroups.CodeGroupDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CodeGroupController.class)
@ContextConfiguration(classes=CodeGroupController.class)
@Import({CodeGroupService.class, CodeGroupValidator.class, CodeReferenceGuard.class, RevisionService.class,
        ChangeHistoryWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class CodeGroupWriteContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean CodeGroupMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    StoredGroup group;
    StoredCode code;

    @BeforeEach void setup() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: createCodeGroup", "operationId: saveGroupCodes", "operationId: deleteGroupCode");
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-02T00:00:00Z"));
        when(clock.getZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        group=new StoredGroup("EDUCATION","교육","N","N",1,"Y","admin",null,null,null);
        code=new StoredCode("EDUCATION","A","기존",1,"N","N",null,null,null,null,null,"admin",null,null,null);
        when(mapper.group(eq("EDUCATION"),anyBoolean())).thenReturn(group);
        when(mapper.codes(eq("EDUCATION"),anyBoolean())).thenReturn(List.of(code));
    }
    String revision() { return revisions.revision(new Snapshot(group,List.of(code))); }

    @Test void authHappyCreateUsesAdminAndCommitInvalidationThenGetCodeGroups() throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json")
            .content("{\"groupId\":\"NEW_GROUP\",\"name\":\" 신규 그룹 \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap());
        verify(mapper).insertGroup(argThat(w->w.actor().equals("admin") && w.sortOrder()==1 && w.group().name().equals("신규 그룹")));
        verify(history).insert(argThat(e->e.tableName().equals("TB_SY_CODE_GRP") && e.type().equals("NEW")
            && e.before()==null && e.userId().equals("admin")));
        verify(invalidation).afterCommit("code-groups");
        when(mapper.groups()).thenReturn(List.of(new Group("NEW_GROUP","신규 그룹","N","N","Y",1,0)));
        mvc.perform(get("/api/code-groups")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].groupId").value("NEW_GROUP"))
            .andExpect(jsonPath("$.data.items[0].codeCount").value(0));
    }
    @Test void authCreateRejectsForgedRegUserIdWithoutPersistence() throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json")
            .content("{\"groupId\":\"NEW_GROUP\",\"name\":\"신규\",\"regUserId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("regUserId"));
        verifyNoInteractions(mapper,history,invalidation);
    }
    @Test void authHappySaveRecordsCodeNmAndResumeWithoutUpdatingUnsentRows() throws Exception {
        StoredCode after=new StoredCode("EDUCATION","A","변경",1,"Y","N",null,null,null,null,null,
            "admin",null,"admin",LocalDateTime.of(2026,1,2,9,0));
        when(mapper.codes("EDUCATION",false)).thenReturn(List.of(after));
        mvc.perform(put("/api/code-groups/EDUCATION/codes").contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("expectedRevision",revision(),"changes",
                List.of(java.util.Map.of("code","A","isNew",false,"name","변경","useYn","Y"))))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.items[0].name").value("변경"))
            .andExpect(jsonPath("$.data.items[0].useYn").value("Y"));
        verify(mapper,times(1)).updateCode(argThat(w->w.actor().equals("admin") && w.row().code().equals("A")));
        verify(mapper,never()).insertCode(any());
        verify(history).insert(argThat(e->e.item().equals("CODE_NM") && e.type().equals("UPD") && e.before().equals("기존") && e.after().equals("변경")));
        verify(history).insert(argThat(e->e.item().equals("USE_YN") && e.type().equals("RESUME") && e.before().equals("N") && e.after().equals("Y")));
        verify(invalidation).afterCommit("codes:EDUCATION");
        mvc.perform(get("/api/code-groups/EDUCATION/codes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].name").value("변경"))
            .andExpect(jsonPath("$.data.groupRevision").isString());
    }
    @Test void businessNoChangeLeavesModDttmAndHistoryUntouched() throws Exception {
        mvc.perform(put("/api/code-groups/EDUCATION/codes").contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("expectedRevision",revision(),"changes",
                List.of(java.util.Map.of("code","A","isNew",false,"name","기존"))))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));
        verify(mapper,never()).updateCode(any()); verify(mapper,never()).insertCode(any());
        verifyNoInteractions(history,invalidation);
        assertThat(code.modDttm()).isNull();
    }
    @Test void authSaveRejectsForgedModUserIdBeforeWrite() throws Exception {
        mvc.perform(put("/api/code-groups/EDUCATION/codes").contentType("application/json")
            .content("{\"expectedRevision\":\"r\",\"changes\":[{\"code\":\"A\",\"isNew\":false,\"modUserId\":\"other\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("changes[0].modUserId"));
        verifyNoInteractions(mapper,history,invalidation);
    }
    @Test void authHappyDeleteLogicallyUpdatesTbSyCodeAndRecordsDelBeforeCommitRefresh() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",revision()))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(mapper).updateCode(argThat(w->w.actor().equals("admin") && w.row().delYn().equals("Y") && w.row().code().equals("A")));
        verify(history).insert(argThat(e->e.tableName().equals("TB_SY_CODE") && e.type().equals("DEL")
            && e.item().equals("DEL_YN") && e.before().equals("N") && e.after().equals("Y") && e.userId().equals("admin")));
        verify(invalidation).afterCommit("codes:EDUCATION");
        when(mapper.codes("EDUCATION",false)).thenReturn(List.of(new StoredCode("EDUCATION","A","기존",1,"N","Y",null,null,null,null,null,"admin",null,"admin",null)));
        mvc.perform(get("/api/code-groups/EDUCATION/codes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void validationDeleteRequiresRevisionAndRejectsForgedUserId() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",revision()).param("userId","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(mapper,history,invalidation);
    }
    @Test void businessReferencedDeleteHasNoWriteOrHistory() throws Exception {
        when(mapper.references("EDUCATION","A")).thenReturn(List.of(new Reference("표기 대응",1)));
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",revision()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_IN_USE"))
            .andExpect(jsonPath("$.details.references[0].count").value(1));
        verify(mapper,never()).updateCode(any()); verifyNoInteractions(history,invalidation);
    }
    @Test void validationPageAndBusinessMissingGroupHaveErrorBodies() throws Exception {
        mvc.perform(get("/api/code-groups/EDUCATION/codes").param("page","0"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));
        mvc.perform(get("/api/code-groups/MISSING/codes"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(mapper,never()).updateCode(any()); verifyNoInteractions(history,invalidation);
    }
}
