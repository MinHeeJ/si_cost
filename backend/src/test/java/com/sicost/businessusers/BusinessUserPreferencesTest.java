package com.sicost.businessusers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.me.*;
import java.util.*;
import org.junit.jupiter.api.*;
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
class BusinessUserPreferencesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PreferenceValidator validator;
    @MockBean PreferenceMapper mapper;
    @MockBean CurrentUserProvider user;
    @MockBean CommitInvalidation invalidation;
    @BeforeEach void setup() {
        when(user.userId()).thenReturn("admin");when(mapper.screens()).thenReturn(List.of("UI-15","UI-23","UI-27"));
        when(mapper.list("admin")).thenReturn(List.of());
    }
    @Test void defaultsAreScreenKeyedAndNeverWrite() throws Exception {
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        for(var item:items)if(item.path("type").asText().equals("COLS")) {
            var value=item.path("value");
            if(item.path("key").asText().equals("UI-23"))assertThat(value.fieldNames()).toIterable().containsExactly("users");
            if(item.path("key").asText().equals("UI-15"))assertThat(value.fieldNames()).toIterable().containsExactly("clients");
            if(item.path("key").asText().equals("UI-27"))assertThat(value.size()).isEqualTo(5);
        }
        verify(mapper,never()).insert(any());
    }
    @Test void requiredUserColumnsCannotBeHidden() throws Exception {
        var value=validator.defaultValue("COLS","UI-23");((com.fasterxml.jackson.databind.node.ArrayNode)value.at("/users/hidden")).add("userId");
        var body=json.createObjectNode();body.set("value",value);body.putNull("expectedRevision");
        mvc.perform(put("/api/me/preferences/COLS/UI-23").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @Test void otherScreenViewsAndUnknownViewsAreRejected() throws Exception {
        for(String view:List.of("clients","codes","unknown")) {
            var value=json.createObjectNode().set(view,validator.defaultValue("COLS","UI-23").get("users"));
            var body=json.createObjectNode();body.set("value",value);body.putNull("expectedRevision");
            mvc.perform(put("/api/me/preferences/COLS/UI-23").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        }
        verify(mapper,never()).insert(any());
    }
    @Test void incompleteOrderIsRejected() throws Exception {
        var value=validator.defaultValue("COLS","UI-23");((com.fasterxml.jackson.databind.node.ArrayNode)value.at("/users/order")).remove(0);
        var body=json.createObjectNode();body.set("value",value);body.putNull("expectedRevision");
        mvc.perform(put("/api/me/preferences/COLS/UI-23").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
}
