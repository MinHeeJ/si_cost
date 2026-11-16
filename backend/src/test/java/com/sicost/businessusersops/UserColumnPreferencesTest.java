package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.me.PreferenceMapper;
import com.sicost.me.PreferenceValidator;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserColumnPreferencesTest {
    ObjectMapper json=JsonMapper.builder().findAndAddModules().build();
    PreferenceMapper mapper;
    PreferenceValidator validator;
    @BeforeEach void setup() {
        mapper=mock(PreferenceMapper.class);when(mapper.screens()).thenReturn(List.of("UI-23"));
        validator=new PreferenceValidator(mapper,json,Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
    }
    @Test void legacyOrderAndHiddenValuesAreAcceptedWithoutReadMutation() throws Exception {
        var value=json.readTree("{\"users\":{\"order\":[\"name\",\"userId\",\"email\",\"roles\",\"status\",\"lastLoginAt\"],\"hidden\":[\"email\"]}}");
        var before=value.deepCopy();validator.validate("COLS","UI-23",value);
        assertThat(value).isEqualTo(before);verify(mapper,never()).insert(any());verify(mapper,never()).update(any());
    }
    @Test void newAssignedPartsColumnCanBeHiddenAndRequiredIdentityCannot() throws Exception {
        var value=json.readTree("{\"users\":{\"order\":[\"userId\",\"name\",\"email\",\"roles\",\"status\",\"lastLoginAt\",\"assignedParts\"],\"hidden\":[\"assignedParts\"]}}");
        validator.validate("COLS","UI-23",value);
        ((com.fasterxml.jackson.databind.node.ArrayNode)value.path("users").path("hidden")).add("userId");
        assertThatThrownBy(()->validator.validate("COLS","UI-23",value)).isInstanceOf(ApiException.class);
    }
    @Test void defaultAddsOnlyAssignedPartsAtEnd() {
        var defaults=validator.defaultValue("COLS","UI-23");
        assertThat(defaults.path("users").path("order").findValuesAsText("unused")).isEmpty();
        assertThat(defaults.path("users").path("order").get(6).asText()).isEqualTo("assignedParts");
        assertThat(defaults.path("users").path("hidden").isEmpty()).isTrue();
    }
}
