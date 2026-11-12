package com.sicost.uploadsops;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b9.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectRegistrationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.b9.B9Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UploadResultController.class)
@ContextConfiguration(classes=UploadResultController.class)
@Import({UploadResultService.class,UploadResultRepository.class,B9Repository.class,RevisionService.class,
    CurrentUserProvider.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class UploadResultContractTest {
    @Autowired MockMvc mvc;
    @MockBean UploadResultMapper mapper;
    @MockBean B9Mapper projects;
    @MockBean CurrentUserMapper users;
    @MockBean ChangeHistoryWriter history;
    @MockBean ProjectRegistrationService registration;
    @MockBean AggregateLockMapper locks;

    static Upload upload(String form,String status) {
        return new Upload(17,form,"UI-01","검사.xlsx",100,"admin",status,2,1,1,0,
            form.equals("B6")?"P-result":null,form.equals("B6")?"CTRT":null,
            status.equals("APPLIED")?LocalDateTime.of(2026,6,15,9,0):null);
    }
    @BeforeEach void setup() {
        when(users.findAdministrator()).thenReturn("admin");
        when(users.findAdministratorRoles()).thenReturn(List.of("ADMIN"));
        when(mapper.find(17)).thenReturn(upload("B9","APPLIED"));
        when(projects.issues(17)).thenReturn(List.of(new Issue(1,"WARN",1,"[파일]","","추가 열"),
            new Issue(2,"WARN",1,"[표]","","합계 차이"),new Issue(3,"WARN",3,"이름","","행 경고")));
        when(projects.results("B9 업로드:17;행:")).thenReturn(List.of(
            new Receipt("{\"id\":\"P-b\",\"name\":\"최초 이름 B\"}","B9 업로드:17;행:9"),
            new Receipt("{\"id\":\"P-a\",\"name\":\"최초 이름 A\"}","B9 업로드:17;행:3")));
        when(projects.context(17)).thenReturn("{\"year\":2026,\"contentSha256\":\"saved-digest\"}");
    }
    @Test void publicRequestUsesAdminAndReturnsOriginalAuditRowsInPhysicalRowOrder() throws Exception {
        mvc.perform(get("/api/uploads/17/result").header("X-Request-Id","upload-result-request"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","upload-result-request"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.uploadId").value(17)).andExpect(jsonPath("$.data.formType").value("B9"))
            .andExpect(jsonPath("$.data.status").value("APPLIED")).andExpect(jsonPath("$.data.projectId").isEmpty())
            .andExpect(jsonPath("$.data.step").isEmpty()).andExpect(jsonPath("$.data.results.length()").value(2))
            .andExpect(jsonPath("$.data.results[0].rowNumber").value(3)).andExpect(jsonPath("$.data.results[0].id").value("P-a"))
            .andExpect(jsonPath("$.data.results[0].name").value("최초 이름 A"))
            .andExpect(jsonPath("$.data.results[1].rowNumber").value(9))
            .andExpect(jsonPath("$.data.summary.totalRows").value(2)).andExpect(jsonPath("$.data.summary.successRows").value(1))
            .andExpect(jsonPath("$.data.summary.warningRows").value(1)).andExpect(jsonPath("$.data.summary.errorRows").value(0))
            .andExpect(jsonPath("$.data.summary.fileWarningCount").value(1)).andExpect(jsonPath("$.data.summary.tableWarningCount").value(1))
            .andExpect(jsonPath("$.data.revision").isString()).andExpect(jsonPath("$.data.changed").value(true));
        verify(users).findAdministrator();verifyNoInteractions(history,registration,locks);
        verify(projects,never()).lockUpload(anyLong());
    }
    @Test void b6UsesStoredTargetStageAndMetadataNotLiveProject() throws Exception {
        when(mapper.find(17)).thenReturn(upload("B6","APPLIED"));
        when(mapper.warnings(17)).thenReturn(new UploadResultRepository.WarningCounts(2,3));
        when(mapper.context(17,"B6")).thenReturn("{\"year\":2026,\"contentSha256\":\"saved-digest\"}");
        String first=mvc.perform(get("/api/uploads/17/result")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.formType").value("B6")).andExpect(jsonPath("$.data.projectId").value("P-result"))
            .andExpect(jsonPath("$.data.step").value("CTRT")).andExpect(jsonPath("$.data.results").isEmpty())
            .andExpect(jsonPath("$.data.summary.fileWarningCount").value(2)).andExpect(jsonPath("$.data.summary.tableWarningCount").value(3))
            .andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(get("/api/uploads/17/result")).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        verifyNoInteractions(projects,history,registration,locks);
    }
    @ParameterizedTest @ValueSource(strings={"B9","B6"})
    void checkedHasNoFabricatedResultsRevisionOrMutation(String form) throws Exception {
        when(mapper.find(17)).thenReturn(upload(form,"CHECKED"));
        when(mapper.warnings(17)).thenReturn(new UploadResultRepository.WarningCounts(0,0));
        mvc.perform(get("/api/uploads/17/result")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results").isEmpty()).andExpect(jsonPath("$.data.revision").isEmpty())
            .andExpect(jsonPath("$.data.changed").value(false));
        verify(projects,never()).results(anyString());verifyNoInteractions(history,registration,locks);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","x","1.5","1e2","9223372036854775808","12345678901234567890"})
    void invalidIdentifierIs400WithFieldAndNoDatabaseAccess(String id) throws Exception {
        mvc.perform(get("/api/uploads/"+id+"/result")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("id"));
        verifyNoInteractions(mapper,projects,history,registration,locks,users);
    }
    @ParameterizedTest @ValueSource(strings={"year","month","page","actor","projectId"})
    void queryFieldsAreNotPartOfThisGetContract(String field) throws Exception {
        mvc.perform(get("/api/uploads/17/result").param(field,"2026-99"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,projects,history,registration,locks);
    }
    @Test void unknownJsonFieldIsRejectedBeforeReading() throws Exception {
        mvc.perform(get("/api/uploads/17/result").contentType("application/json").content("{\"year\":2026}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"))
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(mapper,projects);
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","broken"})
    void unexpectedOrMalformedBodyIs400(String body) throws Exception {
        mvc.perform(get("/api/uploads/17/result").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("body"));
        verifyNoInteractions(mapper,projects);
    }
    @Test void missingUploadIs404AndRequestIdMatchesHeader() throws Exception {
        var response=mvc.perform(get("/api/uploads/99/result")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND")).andReturn().getResponse();
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-Id"));
        verifyNoInteractions(projects,history,registration,locks);
    }
    @Test void corruptReceiptNeverLeaksPrivateSource() throws Exception {
        when(projects.results(anyString())).thenReturn(List.of(new Receipt("private malformed JSON","B9 업로드:17;행:3")));
        var response=mvc.perform(get("/api/uploads/17/result")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private","Exception","SELECT");
        verifyNoInteractions(history,registration,locks);
    }
    @Test void databaseFailureUsesSafeEnvelopeAndRequestId() throws Exception {
        when(mapper.find(17)).thenThrow(new org.springframework.dao.DataRetrievalFailureException("SELECT private_table"));
        mvc.perform(get("/api/uploads/17/result").header("X-Request-Id","safe-id"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").value("safe-id"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_table"))));
    }
}
