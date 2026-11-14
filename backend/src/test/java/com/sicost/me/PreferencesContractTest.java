package com.sicost.me;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PreferenceController.class)
@org.springframework.test.context.ContextConfiguration(classes=PreferenceController.class)
@Import({PreferenceService.class, PreferenceValidator.class, RevisionService.class, com.sicost.common.TimeConfiguration.class,
        ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class PreferencesContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PreferenceMapper mapper;
    @MockBean CurrentUserProvider user;
    @MockBean CommitInvalidation invalidation;
    private final Map<String, PreferenceDtos.StoredPreference> rows = new HashMap<>();
    private LocalDateTime time;

    @BeforeEach void setup() {
        rows.clear(); time = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(user.userId()).thenReturn("admin");
        when(mapper.screens()).thenReturn(java.util.stream.IntStream.rangeClosed(1, 33)
                .filter(i -> i != 20).mapToObj(i -> String.format("UI-%02d", i)).toList());
        when(mapper.cashBaseMonth()).thenReturn("202601");
        when(mapper.list("admin")).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(mapper.lock(any())).thenAnswer(i -> rows.get(((PreferenceDtos.PreferenceId)i.getArgument(0)).type() + "/" + ((PreferenceDtos.PreferenceId)i.getArgument(0)).key()));
        when(mapper.insert(any())).thenAnswer(i -> { var w = (PreferenceDtos.PreferenceWrite)i.getArgument(0); store(w); return 1; });
        when(mapper.update(any())).thenAnswer(i -> { store(i.getArgument(0)); return 1; });
        when(mapper.delete(any())).thenAnswer(i -> { var id = (PreferenceDtos.PreferenceId)i.getArgument(0); return rows.remove(id.type()+"/"+id.key()) == null ? 0 : 1; });
    }
    private void store(PreferenceDtos.PreferenceWrite w) {
        var id = w.id(); time = time.plusSeconds(1);
        rows.put(id.type()+"/"+id.key(), new PreferenceDtos.StoredPreference(id.userId(), id.type(), id.key(), w.value(), id.userId(), time, null, null));
    }
    private String save(String type, String key, String value, String revision) throws Exception {
        var body = "{\"value\":"+value+",\"expectedRevision\":"+(revision == null ? "null" : "\""+revision+"\"")+"}";
        var response = mvc.perform(put("/api/me/preferences/{type}/{key}", type, key).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/preference/revision").asText();
    }
    @Test void defaultsAreReadOnlyAndMenuFoldIsExplicitNull() throws Exception {
        var response = mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        var items = json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        assertThat(items.findValues("stored")).allMatch(n -> !n.booleanValue());
        assertThat(items.findValues("revision")).allMatch(n -> n.isNull());
        assertThat(items).anyMatch(n -> n.path("type").asText().equals("PAGE_SIZE") && n.path("value").isInt() && n.path("value").intValue()==50);
        assertThat(items).anyMatch(n -> n.path("type").asText().equals("MENU_FOLD") && n.has("value") && n.get("value").isNull());
        verify(mapper, never()).insert(any()); verify(mapper, never()).delete(any());
    }
    @Test void firstDefaultStillInsertsAndSameValueDoesNotUpdate() throws Exception {
        String revision = save("PAGE_SIZE", "*", "50", null);
        var before = rows.get("PAGE_SIZE/*");
        mvc.perform(put("/api/me/preferences/PAGE_SIZE/*").contentType("application/json").content("{\"value\":50,\"expectedRevision\":\""+revision+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
                .andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));
        assertThat(rows.get("PAGE_SIZE/*")).isEqualTo(before); verify(mapper, never()).update(any());
    }
    @Test void resetOnlyOneKeyAndReturns204ThenDefaults() throws Exception {
        var rev = save("PAGE_SIZE", "*", "20", null); save("DENSE", "*", "\"좁게\"", null);
        var dense = rows.get("DENSE/*");
        mvc.perform(delete("/api/me/preferences/PAGE_SIZE/*").param("expectedRevision",rev)).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(rows).containsOnlyKeys("DENSE/*"); assertThat(rows.get("DENSE/*")).isEqualTo(dense);
        defaultsAreReadOnlyPageSize();
    }
    private void defaultsAreReadOnlyPageSize() throws Exception {
        var r=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(r.getResponse().getContentAsString()).at("/data/items")).anyMatch(n -> n.path("type").asText().equals("PAGE_SIZE") && !n.path("stored").asBoolean() && n.path("value").asInt()==50);
    }
    @Test void businessCorruptedRowIsIsolatedAndHasRevisionWithoutDeletion() throws Exception {
        rows.put("DENSE/*",new PreferenceDtos.StoredPreference("admin","DENSE","*","{broken","admin",time,null,null));
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        assertThat(items).anyMatch(n->n.path("type").asText().equals("DENSE") && n.path("stored").asBoolean()
            && n.path("error").path("code").asText().equals("INVALID_PREFERENCE") && !n.path("revision").asText().isBlank());
        assertThat(items).anyMatch(n->n.path("type").asText().equals("PAGE_SIZE") && n.path("value").asInt()==50 && !n.has("error"));
        assertThat(rows.get("DENSE/*").value()).isEqualTo("{broken");
        assertThat(rows).hasSize(1); verify(mapper,never()).delete(any()); verify(mapper,never()).update(any());
    }
    @Test void staleResetAfterRepairPreservesLatest() throws Exception {
        var old=save("DENSE","*","\"넓게\"",null); var latest=save("DENSE","*","\"좁게\"",old);
        mvc.perform(delete("/api/me/preferences/DENSE/*").param("expectedRevision",old)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(latest).isNotEqualTo(old); assertThat(rows.get("DENSE/*").value()).isEqualTo("\"좁게\"");
    }
    @Test void concurrentFirstNullCannotOverwrite() throws Exception {
        save("PAGE_SIZE","*","50",null);
        mvc.perform(put("/api/me/preferences/PAGE_SIZE/*").contentType("application/json").content("{\"value\":20,\"expectedRevision\":null}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(rows.get("PAGE_SIZE/*").value()).isEqualTo("50");
    }
    @Test void missingDeleteTokenIs400() throws Exception {
        mvc.perform(delete("/api/me/preferences/DENSE/*")).andExpect(status().isBadRequest()); verify(mapper,never()).delete(any());
    }
    @Test void absentResetTargetIsStale() throws Exception {
        mvc.perform(delete("/api/me/preferences/DENSE/*").param("expectedRevision","observed")).andExpect(status().isConflict());
    }
    @Test void unknownGetQueryIsRejectedWithField() throws Exception {
        mvc.perform(get("/api/me/preferences").param("userId","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("userId"));
    }
    @Test void unknownDeleteQueryCannotMutate() throws Exception {
        mvc.perform(delete("/api/me/preferences/DENSE/*").param("expectedRevision","r").param("userId","other")).andExpect(status().isBadRequest()); verify(mapper,never()).delete(any());
    }
    @Test void publicSaveUsesServerActorAndRejectsInjection() throws Exception {
        save("DENSE","*","\"좁게\"",null); assertThat(rows.get("DENSE/*").userId()).isEqualTo("admin");
        mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json").content("{\"value\":\"좁게\",\"expectedRevision\":null,\"userId\":\"other\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        assertThat(rows).hasSize(1);
    }
    @Test void authPutPreferenceUsesOnlyAdminAndRejectsInjectedUserId() throws Exception {
        mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json")
            .content("{\"value\":\"좁게\",\"expectedRevision\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.preference.stored").value(true));
        verify(mapper).insert(argThat(w->w.id().userId().equals("admin") && w.id().type().equals("DENSE")));
        mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json")
            .content("{\"value\":\"넓게\",\"expectedRevision\":null,\"userId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));
        assertThat(rows.get("DENSE/*").value()).isEqualTo("\"좁게\"");
        verify(mapper,never()).update(any());
    }
    @Test void authDeletePreferenceRestoresStoredFalseAndMenuFoldNullThroughGetPreferences() throws Exception {
        String revision=save("MENU_FOLD","*","true",null);
        mvc.perform(delete("/api/me/preferences/MENU_FOLD/*").param("expectedRevision",revision).param("userId","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));
        verify(mapper,never()).delete(any());
        mvc.perform(delete("/api/me/preferences/MENU_FOLD/*").param("expectedRevision",revision))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(mapper).delete(new PreferenceDtos.PreferenceId("admin","MENU_FOLD","*"));
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).at("/data/items"))
            .anyMatch(n->n.path("type").asText().equals("MENU_FOLD") && !n.path("stored").asBoolean() && n.get("value").isNull());
        assertThat(rows).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"\"50\"","50.0","0","null","true"}) void rejectsNonIntegerPageSizes(String value) throws Exception {
        mvc.perform(put("/api/me/preferences/PAGE_SIZE/*").contentType("application/json").content("{\"value\":"+value+",\"expectedRevision\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("value")); verify(mapper,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"20","50","100"}) void allowedIntegerPageSizes(String value) throws Exception { save("PAGE_SIZE","*",value,null); }
    @Test void menuFoldRejectsStringBoolean() throws Exception { invalid("MENU_FOLD","*","\"true\""); }
    @Test void serializedLengthLimit() throws Exception { invalid("FAV","*",json.writeValueAsString(List.of("x".repeat(1999)))); }
    @Test void columnsCannotHideRequired() throws Exception { invalid("COLS","UI-27","{\"codes\":{\"order\":[\"code\",\"name\",\"addVal1\",\"addVal2\",\"sortOrder\",\"useYn\"],\"hidden\":[\"code\"]}}"); }
    @ParameterizedTest @ValueSource(strings={"UI-02","UI-33"})
    void peopleColumnDefaultsCanBeSavedAndResetWithoutHidingName(String screen) throws Exception {
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        String view=screen.equals("UI-02")?"developers":"candidates";
        var preference=java.util.stream.StreamSupport.stream(items.spliterator(),false)
            .filter(n->n.path("type").asText().equals("COLS") && n.path("key").asText().equals(screen)).findFirst().orElseThrow();
        var value=preference.path("value").deepCopy();
        assertThat(value.path(view).path("order").get(0).asText()).isEqualTo("name");
        String revision=save("COLS",screen,value.toString(),null);
        ((com.fasterxml.jackson.databind.node.ArrayNode)value.path(view).path("hidden")).add("name");
        mvc.perform(put("/api/me/preferences/COLS/{key}",screen).contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision",revision))))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/preferences/COLS/{key}",screen).param("expectedRevision",revision)).andExpect(status().isNoContent());
    }
    @Test void columnsCannotDuplicateOrder() throws Exception { invalid("COLS","UI-27","{\"mappings\":{\"order\":[\"code\",\"code\",\"sourceText\"],\"hidden\":[]}}"); }
    @Test void columnsCannotContainUnknown() throws Exception { invalid("COLS","UI-27","{\"mappings\":{\"order\":[\"sourceText\",\"groupId\",\"code\"],\"hidden\":[\"actions\"]}}"); }
    @Test void noPeriodForNoneScreen() throws Exception { invalid("PERIOD","UI-27","{\"type\":\"YEAR\",\"value\":\"2026\"}"); }
    @Test void missingExpectedRevisionIsNotFirstInsert() throws Exception {
        mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json").content("{\"value\":\"넓게\"}")).andExpect(status().isBadRequest());
    }
    @Test void contractResourceRemainsAvailable() throws Exception { assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("operationId: getPreferences","operationId: putPreference","operationId: deletePreference"); }
    @Test void favoriteOrderIsPreservedAndEleventhDoesNotChangeTen() throws Exception {
        var ids=java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->String.format("UI-%02d",i)).toList();
        var revision=save("FAV","*",json.writeValueAsString(ids),null);
        var before=rows.get("FAV/*");var eleven=new ArrayList<>(ids);eleven.add("UI-11");
        mvc.perform(put("/api/me/preferences/FAV/*").contentType("application/json").content(json.writeValueAsString(Map.of("value",eleven,"expectedRevision",revision))))
            .andExpect(status().isBadRequest());
        assertThat(rows.get("FAV/*")).isEqualTo(before);
        var reverse=new ArrayList<>(ids);Collections.reverse(reverse);save("FAV","*",json.writeValueAsString(reverse),revision);
        assertThat(json.readTree(rows.get("FAV/*").value())).isEqualTo(json.valueToTree(reverse));
    }
    @Test void duplicateFavoriteIsRejected() throws Exception { invalid("FAV","*","[\"UI-01\",\"UI-01\"]"); }
    @Test void unregisteredFavoriteIsRejected() throws Exception { invalid("FAV","*","[\"UI-99\"]"); }
    @Test void recentCannotExceedFive() throws Exception { invalid("RECENT","*","[\"UI-01\",\"UI-02\",\"UI-03\",\"UI-04\",\"UI-05\",\"UI-06\"]"); }
    @Test void registeredPcOnlyFavoriteIsNotDeletedByReads() throws Exception {
        save("FAV","*","[\"UI-27\",\"UI-16\"]",null);
        mvc.perform(get("/api/me/preferences")).andExpect(status().isOk());
        assertThat(rows.get("FAV/*").value()).isEqualTo("[\"UI-27\",\"UI-16\"]");verify(mapper,never()).delete(any());
    }
    @Test void firstInsertPkConflictIsTranslatedWithoutRetry() throws Exception {
        doThrow(new org.springframework.dao.DuplicateKeyException("private SQL")).when(mapper).insert(any());
        mvc.perform(put("/api/me/preferences/DENSE/*").contentType("application/json").content("{\"value\":\"좁게\",\"expectedRevision\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(mapper,times(1)).insert(any());verify(invalidation,never()).afterCommit(any());
    }
    @Test void missingAdminIsServerErrorNotAuthentication() throws Exception {
        when(user.userId()).thenThrow(new IllegalStateException("missing admin"));
        mvc.perform(get("/api/me/preferences")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
    @Test void periodTypeCannotDifferFromRegisteredType() throws Exception { invalid("PERIOD","UI-16","{\"type\":\"YEAR\",\"value\":\"2026\"}"); }
    @Test void periodMustBeActualMonth() throws Exception { invalid("PERIOD","UI-16","{\"type\":\"YEAR_MONTH\",\"value\":\"2026-13\"}"); }
    @Test void cumulativeYearAndMonthMustMatch() throws Exception { invalid("PERIOD","UI-07","{\"type\":\"YEAR_CUM\",\"year\":\"2026\",\"month\":\"2025-02\"}"); }
    @Test void periodsBeforeStartAreRejected() throws Exception { invalid("PERIOD","UI-16","{\"type\":\"YEAR_MONTH\",\"value\":\"2025-12\"}"); }
    @Test void reversedRangeCannotWrite() throws Exception {
        when(mapper.cashBaseMonth()).thenReturn("999901");invalid("PERIOD","UI-16","{\"type\":\"YEAR_MONTH\",\"value\":\"2026-02\"}");
    }
    @Test void temporaryAdminCanRestoreEveryProfitabilityReferenceColumnWithoutAuthentication() throws Exception {
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        var preference=java.util.stream.StreamSupport.stream(items.spliterator(),false)
            .filter(n->n.path("type").asText().equals("COLS") && n.path("key").asText().equals("UI-03")).findFirst().orElseThrow();
        var value=preference.path("value").deepCopy();
        var view=value.path("individualProfitability");
        assertThat(view.path("order").size()).isEqualTo(22);
        assertThat(view.path("hidden")).extracting(n->n.asText())
            .containsExactly("selfInsurance","withholdingTax","incomeTax","netPay");
        ((com.fasterxml.jackson.databind.node.ArrayNode)view.path("hidden")).removeAll();
        String revision=save("COLS","UI-03",value.toString(),null);
        assertThat(json.readTree(rows.get("COLS/UI-03").value()).path("individualProfitability").path("hidden")).isEmpty();
        var saved=rows.get("COLS/UI-03");
        save("DENSE","*","\"좁게\"",null);
        var other=rows.get("DENSE/*");
        for(String required:List.of("name","revenue","totalCost","profit")) {
            var invalidValue=value.deepCopy();
            ((com.fasterxml.jackson.databind.node.ArrayNode)invalidValue.path("individualProfitability").path("hidden")).add(required);
            mvc.perform(put("/api/me/preferences/COLS/UI-03").contentType("application/json")
                .content(json.writeValueAsString(Map.of("value",invalidValue,"expectedRevision",revision))))
                .andExpect(status().isBadRequest());
            assertThat(rows.get("COLS/UI-03")).isEqualTo(saved);
        }
        mvc.perform(put("/api/me/preferences/COLS/UI-03").contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision","stale"))))
            .andExpect(status().isConflict());
        var read=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(read.getResponse().getContentAsString()).at("/data/items"))
            .anyMatch(n->n.path("key").asText().equals("UI-03") && n.path("type").asText().equals("COLS")
                && n.path("value").equals(value));
        assertThat(rows.get("DENSE/*")).isEqualTo(other);
    }
    private com.fasterxml.jackson.databind.JsonNode preference(String type,String key) throws Exception {
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        return java.util.stream.StreamSupport.stream(json.readTree(response.getResponse().getContentAsString()).at("/data/items").spliterator(),false)
            .filter(n->n.path("type").asText().equals(type)&&n.path("key").asText().equals(key)).findFirst().orElseThrow();
    }
    @Test void profitabilityPermutationRoundTripsWithoutChangingOtherScreensOrGlobalPreferences() throws Exception {
        var other=preference("COLS","UI-27").path("value");
        save("COLS","UI-27",other.toString(),null);
        save("PAGE_SIZE","*","20",null); save("DENSE","*","\"좁게\"",null); save("AMT_UNIT","*","\"백만원\"",null);
        var before=new HashMap<>(rows);
        var value=preference("COLS","UI-03").path("value").deepCopy();
        var view=(com.fasterxml.jackson.databind.node.ObjectNode)value.path("individualProfitability");
        var order=new ArrayList<String>(); view.path("order").forEach(n->order.add(n.asText()));
        Collections.rotate(order,3); view.set("order",json.valueToTree(order));
        view.set("hidden",json.valueToTree(List.of("companyName","selfInsurance")));
        assertThat(value.toString().codePointCount(0,value.toString().length())).isLessThanOrEqualTo(2000);
        var revision=save("COLS","UI-03",value.toString(),null);
        var read=preference("COLS","UI-03");
        assertThat(read.path("value")).isEqualTo(value);
        assertThat(read.path("revision").asText()).isEqualTo(revision);
        assertThat(read.path("stored").asBoolean()).isTrue();
        assertThat(read.path("value").path("individualProfitability").path("order")).hasSize(22);
        assertThat(save("COLS","UI-03",value.toString(),revision)).isEqualTo(revision);
        before.forEach((key,row)->assertThat(rows.get(key)).isEqualTo(row));
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","unknown","missing","hiddenUnknown","hiddenDuplicate","wrongView","empty","tooLong"})
    void invalidProfitabilityColumnsCannotChangeStoredValueOrRevision(String kind) throws Exception {
        var value=preference("COLS","UI-03").path("value").deepCopy();
        var revision=save("COLS","UI-03",value.toString(),null);
        var before=new HashMap<>(rows);
        var order=(com.fasterxml.jackson.databind.node.ArrayNode)value.path("individualProfitability").path("order");
        var hidden=(com.fasterxml.jackson.databind.node.ArrayNode)value.path("individualProfitability").path("hidden");
        switch(kind) {
            case "duplicate" -> order.set(1,json.getNodeFactory().textNode("name"));
            case "unknown" -> order.set(1,json.getNodeFactory().textNode("notRegistered"));
            case "missing" -> order.remove(1);
            case "hiddenUnknown" -> hidden.add("actions");
            case "hiddenDuplicate" -> hidden.add("selfInsurance");
            case "wrongView" -> {var root=(com.fasterxml.jackson.databind.node.ObjectNode)value;var view=root.remove("individualProfitability");root.set("developers",view);}
            case "empty" -> ((com.fasterxml.jackson.databind.node.ObjectNode)value).removeAll();
            case "tooLong" -> order.add("x".repeat(2000));
            default -> throw new IllegalArgumentException(kind);
        }
        mvc.perform(put("/api/me/preferences/COLS/UI-03").contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision",revision))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(rows).isEqualTo(before);
        assertThat(preference("COLS","UI-03").path("revision").asText()).isEqualTo(revision);
        verify(mapper,never()).update(any());
    }
    @Test void damagedProfitabilityColumnsAreIsolatedWithoutAutomaticRepair() throws Exception {
        save("COLS","UI-27",preference("COLS","UI-27").path("value").toString(),null);
        rows.put("COLS/UI-03",new PreferenceDtos.StoredPreference("admin","COLS","UI-03","{broken","admin",time,null,null));
        var before=new HashMap<>(rows);
        var read=preference("COLS","UI-03");
        assertThat(read.path("error").path("code").asText()).isEqualTo("INVALID_PREFERENCE");
        assertThat(read.path("revision").asText()).isNotBlank();
        assertThat(read.path("value").path("individualProfitability").path("order")).hasSize(22);
        assertThat(preference("COLS","UI-27").has("error")).isFalse();
        assertThat(rows).isEqualTo(before);
        verify(mapper,never()).update(any());verify(mapper,never()).delete(any());
    }
    @Test void profitabilityYearIsScopedToCurrentYearWhileOtherYearScreensKeepNextYear() throws Exception {
        int year=java.time.Year.now(java.time.ZoneId.of("Asia/Seoul")).getValue();
        when(mapper.cashBaseMonth()).thenReturn(String.format("%04d01",year-1));
        var current=json.writeValueAsString(Map.of("type","YEAR","value",String.valueOf(year)));
        var revision=save("PERIOD","UI-03",current,null);
        var before=rows.get("PERIOD/UI-03");
        var next=Map.of("type","YEAR","value",String.valueOf(year+1));
        mvc.perform(put("/api/me/preferences/PERIOD/UI-03").contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",next,"expectedRevision",revision))))
            .andExpect(status().isBadRequest());
        assertThat(rows.get("PERIOD/UI-03")).isEqualTo(before);
        save("PERIOD","UI-01",json.writeValueAsString(next),null);
        assertThat(preference("PERIOD","UI-03").path("value")).isEqualTo(json.readTree(current));
    }
    @Test void renegotiationColumnsRoundTripAndResetWithoutChangingOtherPreferences() throws Exception {
        save("DENSE","*","\"좁게\"",null);
        save("COLS","UI-27",preference("COLS","UI-27").path("value").toString(),null);
        var before=new HashMap<>(rows);
        var defaults=preference("COLS","UI-32");
        assertThat(defaults.path("stored").asBoolean()).isFalse();
        assertThat(defaults.path("revision").isNull()).isTrue();
        var value=defaults.path("value").deepCopy();
        var view=(com.fasterxml.jackson.databind.node.ObjectNode)value.path("rateRenegotiations");
        var order=new ArrayList<String>();view.path("order").forEach(n->order.add(n.asText()));
        assertThat(order).containsExactly("name","employeeType","projectName","renewalDate","contractMm","unitPrice",
            "monthlyLaborCost","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain");
        Collections.reverse(order);view.set("order",json.valueToTree(order));
        view.set("hidden",json.valueToTree(List.of("employeeType","annualGain")));
        String revision=save("COLS","UI-32",value.toString(),null);
        assertThat(preference("COLS","UI-32").path("value")).isEqualTo(value);
        assertThat(save("COLS","UI-32",value.toString(),revision)).isEqualTo(revision);
        verify(mapper,never()).update(any());
        mvc.perform(delete("/api/me/preferences/COLS/UI-32").param("expectedRevision",revision)).andExpect(status().isNoContent());
        assertThat(preference("COLS","UI-32").path("value")).isEqualTo(defaults.path("value"));
        assertThat(rows).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","unknown","missing","hiddenUnknown","hiddenDuplicate","name","projectName","wrongView","empty"})
    void invalidRenegotiationColumnsPreserveStoredSettings(String rule) throws Exception {
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)preference("COLS","UI-32").path("value").deepCopy();
        String revision=save("COLS","UI-32",value.toString(),null);
        var before=new HashMap<>(rows);
        var order=(com.fasterxml.jackson.databind.node.ArrayNode)value.path("rateRenegotiations").path("order");
        var hidden=(com.fasterxml.jackson.databind.node.ArrayNode)value.path("rateRenegotiations").path("hidden");
        switch(rule) {
            case "duplicate" -> order.set(1,order.get(0));
            case "unknown" -> order.set(1,json.valueToTree("notRegistered"));
            case "missing" -> order.remove(1);
            case "hiddenUnknown" -> hidden.add("actions");
            case "hiddenDuplicate" -> {hidden.add("annualGain");hidden.add("annualGain");}
            case "name","projectName" -> hidden.add(rule);
            case "wrongView" -> value.set("projectCost",value.remove("rateRenegotiations"));
            case "empty" -> value.removeAll();
            default -> throw new IllegalArgumentException(rule);
        }
        mvc.perform(put("/api/me/preferences/COLS/UI-32").contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision",revision))))
            .andExpect(status().isBadRequest());
        assertThat(rows).isEqualTo(before);
        verify(mapper,never()).update(any());
    }
    @Test void settlementKeepsMonthPreferenceAndRenegotiationsHaveNoPeriod() throws Exception {
        assertThat(preference("PERIOD","UI-19").path("value").path("type").asText()).isEqualTo("YEAR_MONTH");
        invalid("COLS","UI-19","{}");
        invalid("PERIOD","UI-32","{\"type\":\"YEAR_MONTH\",\"value\":\"2026-01\"}");
    }
    private void invalid(String type,String key,String value) throws Exception {
        mvc.perform(put("/api/me/preferences/{type}/{key}",type,key).contentType("application/json").content("{\"value\":"+value+",\"expectedRevision\":null}"))
                .andExpect(status().isBadRequest()); verify(mapper,never()).insert(any()); verify(mapper,never()).update(any());
    }
    @ParameterizedTest @ValueSource(strings={"projectCost","statusBoard","issues"})
    void projectAnalysisColumnsRoundTripEntireOrderAndKeepSiblingAndOtherPreferences(String viewId) throws Exception {
        String key=viewId.equals("projectCost")?"UI-04":"UI-31";
        var value=preference("COLS",key).path("value").deepCopy();
        var view=(com.fasterxml.jackson.databind.node.ObjectNode)value.path(viewId);
        var order=new ArrayList<String>();view.path("order").forEach(n->order.add(n.asText()));
        if(viewId.equals("projectCost"))assertThat(order).containsExactlyElementsOf(com.sicost.projectcostsops.ProjectCostExportInput.COLUMNS);
        Collections.reverse(order);view.set("order",json.valueToTree(order));
        view.set("hidden",json.valueToTree(List.of(viewId.equals("projectCost")?"alerts":viewId.equals("statusBoard")?"pm":"action")));
        save("DENSE","*","\"좁게\"",null);
        var before=new HashMap<>(rows);
        String revision=save("COLS",key,value.toString(),null);
        assertThat(preference("COLS",key).path("value")).isEqualTo(value);
        assertThat(preference("COLS",key).path("revision").asText()).isEqualTo(revision);
        save("COLS",key,value.toString(),revision);
        verify(mapper,never()).update(any());
        before.forEach((id,row)->assertThat(rows.get(id)).isEqualTo(row));
        assertThat(rows.get("COLS/"+key).userId()).isEqualTo("admin");
        verify(invalidation).afterCommit("preferences:admin:COLS:"+key);
    }
    @ParameterizedTest @ValueSource(strings={"projectCost","statusBoard","issues"})
    void projectAnalysisStaleRevisionCannotOverwriteSettings(String viewId) throws Exception {
        String key=viewId.equals("projectCost")?"UI-04":"UI-31";
        var value=preference("COLS",key).path("value");
        save("COLS",key,value.toString(),null);
        var before=new HashMap<>(rows);
        mvc.perform(put("/api/me/preferences/COLS/{key}",key).contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision","stale"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(rows).isEqualTo(before);verify(mapper,never()).update(any());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
        "projectCost,duplicate","projectCost,missing","projectCost,unknown","projectCost,required","projectCost,hiddenDuplicate","projectCost,wrongView","projectCost,empty","projectCost,tooLong",
        "statusBoard,duplicate","statusBoard,missing","statusBoard,unknown","statusBoard,required","statusBoard,hiddenDuplicate","statusBoard,wrongView","statusBoard,empty","statusBoard,tooLong",
        "issues,duplicate","issues,missing","issues,unknown","issues,required","issues,hiddenDuplicate","issues,wrongView","issues,empty","issues,tooLong"
    })
    void invalidProjectAnalysisColumnsPreserveStoredValue(String viewId,String rule) throws Exception {
        String key=viewId.equals("projectCost")?"UI-04":"UI-31";
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)preference("COLS",key).path("value").deepCopy();
        String revision=save("COLS",key,value.toString(),null);
        var before=new HashMap<>(rows);
        var order=(com.fasterxml.jackson.databind.node.ArrayNode)value.path(viewId).path("order");
        var hidden=(com.fasterxml.jackson.databind.node.ArrayNode)value.path(viewId).path("hidden");
        switch(rule) {
            case "duplicate" -> order.set(1,order.get(0));
            case "missing" -> order.remove(1);
            case "unknown" -> order.set(1,json.valueToTree("unknownColumn"));
            case "required" -> hidden.add(viewId.equals("issues")?"title":"projectName");
            case "hiddenDuplicate" -> {hidden.add("dueDate");hidden.add("dueDate");}
            case "wrongView" -> value.set("clients",value.remove(viewId));
            case "empty" -> value.removeAll();
            case "tooLong" -> order.add("x".repeat(2000));
            default -> throw new IllegalArgumentException(rule);
        }
        mvc.perform(put("/api/me/preferences/COLS/{key}",key).contentType("application/json")
            .content(json.writeValueAsString(Map.of("value",value,"expectedRevision",revision))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(rows).isEqualTo(before);verify(mapper,never()).update(any());
    }
    @Test void statusBoardHasNoPeriodAndProjectCostRetainsMonthPeriod() throws Exception {
        var response=mvc.perform(get("/api/me/preferences")).andExpect(status().isOk()).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        assertThat(items).noneMatch(n->n.path("type").asText().equals("PERIOD")&&n.path("key").asText().equals("UI-31"));
        assertThat(preference("PERIOD","UI-04").path("value").path("type").asText()).isEqualTo("YEAR_MONTH");
        invalid("PERIOD","UI-31","{\"type\":\"YEAR\",\"value\":\"2026\"}");
    }
}
