package com.sicost.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.me.*;
import java.time.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PreferenceController.class)
@ContextConfiguration(classes=PreferenceController.class)
@Import({PreferenceService.class,PreferenceValidator.class,RevisionService.class,com.sicost.common.TimeConfiguration.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectPreferencesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PreferenceMapper mapper;
    @MockBean CurrentUserProvider user;
    @MockBean CommitInvalidation invalidation;
    PreferenceDtos.StoredPreference stored;
    @BeforeEach void setup() {
        when(user.userId()).thenReturn("admin");when(mapper.screens()).thenReturn(List.of("UI-01"));when(mapper.cashBaseMonth()).thenReturn("202601");
        when(mapper.list("admin")).thenReturn(List.of());when(mapper.lock(any())).thenAnswer(c->stored);
        when(mapper.insert(any())).thenAnswer(c->{PreferenceDtos.PreferenceWrite w=c.getArgument(0);stored=new PreferenceDtos.StoredPreference(w.id().userId(),w.id().type(),w.id().key(),w.value(),"admin",LocalDateTime.of(2026,7,1,0,0),null,null);return 1;});
    }
    ObjectNode body() {
        ObjectNode body=json.createObjectNode(), value=body.putObject("value").putObject("projects");
        value.set("order",json.valueToTree(List.of("id","name","customerName","contractType","contractPeriod","contractAmount","personCount","partName","status","distribution")));
        value.putArray("hidden");body.putNull("expectedRevision");return body;
    }
    @Test void projectsDefaultListsAllTenColumnsAndIsNotPersistedOnRead() throws Exception {
        String text=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var items=json.readTree(text).path("data").path("items");
        assertThat(items).anyMatch(n->n.path("type").asText().equals("COLS")&&n.path("key").asText().equals("UI-01")&&n.path("value").path("projects").path("order").size()==10);
        verify(mapper,never()).insert(any());
    }
    @Test void validProjectsOrderAndHiddenSetUseExistingPreferenceEndpoint() throws Exception {
        ObjectNode body=body();((ObjectNode)body.path("value").path("projects")).putArray("hidden").add("distribution");
        mvc.perform(put("/api/me/preferences/COLS/UI-01").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.preference.value.projects.hidden[0]").value("distribution"));
    }
    @ParameterizedTest @ValueSource(strings={"id","name","unknown"})
    void protectedOrUnknownHiddenColumnsRejectedWithoutWriting(String column) throws Exception {
        ObjectNode body=body();((ObjectNode)body.path("value").path("projects")).putArray("hidden").add(column);
        mvc.perform(put("/api/me/preferences/COLS/UI-01").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @Test void duplicateOrMissingOrderRejectedWithoutWriting() throws Exception {
        ObjectNode body=body();((ObjectNode)body.path("value").path("projects")).putArray("order").add("id").add("id");
        mvc.perform(put("/api/me/preferences/COLS/UI-01").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
}
