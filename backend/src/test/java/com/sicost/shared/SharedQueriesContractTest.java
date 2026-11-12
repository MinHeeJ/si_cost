package com.sicost.shared;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SharedQueriesController.class)
@ContextConfiguration(classes=SharedQueriesController.class)
@Import({SharedQueriesService.class, MenuService.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class SharedQueriesContractTest {
    @Autowired MockMvc mvc;
    @MockBean SharedQueriesMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-10T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(mapper.cashBaseMonth()).thenReturn("202601");
        when(mapper.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
    }
    @Test void currentUserIsPublicAndHasNoSecrets() throws Exception {
        mvc.perform(get("/api/current-user")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.userId").value("admin"))
            .andExpect(jsonPath("$.data.roleIds[0]").value("ADMIN")).andExpect(jsonPath("$.data.name").value("임시 관리자"))
            .andExpect(jsonPath("$.data.developmentMode").value(true)).andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.email").doesNotExist());
    }
    @Test void validationCurrentUserRejectsForgedContext() throws Exception {
        mvc.perform(get("/api/current-user").param("userId","other")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(mapper,users);
    }
    @Test void validationMenusRejectsForgedContext() throws Exception {
        mvc.perform(get("/api/menus").param("userId","other")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(mapper,users);
    }
    @Test void validationPeriodContextRejectsForgedContext() throws Exception {
        mvc.perform(get("/api/period-context").param("userId","other")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("userId"));
        verifyNoInteractions(mapper,users);
    }
    @Test void missingContextIsServerErrorNotFabricatedUser() throws Exception {
        when(users.currentUser()).thenThrow(new IllegalStateException("missing"));
        mvc.perform(get("/api/current-user")).andExpect(status().isInternalServerError());
    }
    @Test void lookupKeepsOnlyPublicFieldsAndExactDecimal() throws Exception {
        when(mapper.codes("ALWNC",false)).thenReturn(List.of(new SharedQueryDtos.CodeLookup("INS_RATE","보험요율",null,null,null,"9.4","%")));
        mvc.perform(get("/api/codes/ALWNC")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].codeVal").value("9.4"))
            .andExpect(jsonPath("$.data.items[0].unitNm").value("%")).andExpect(jsonPath("$.data.items[0].addVal3").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].useYn").doesNotExist()).andExpect(jsonPath("$.data.items[0].sortOrder").doesNotExist());
    }
    @Test void inactiveLookupIsExplicitOnly() throws Exception {
        mvc.perform(get("/api/codes/JOB").param("includeInactive","true")).andExpect(status().isOk());
        verify(mapper).codes("JOB",true);
    }
    @Test void businessInactiveLabelsAreIncludedOnlyOnExplicitLookupWithoutWrites() throws Exception {
        when(mapper.codes("EDUCATION",false)).thenReturn(List.of());
        when(mapper.codes("EDUCATION",true)).thenReturn(List.of(new SharedQueryDtos.CodeLookup("STOPPED","중지 코드",null,null,null,null,null)));
        mvc.perform(get("/api/codes/EDUCATION")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/codes/EDUCATION").param("includeInactive","true")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].code").value("STOPPED"))
            .andExpect(jsonPath("$.data.items[0].name").value("중지 코드"));
        verify(mapper).codes("EDUCATION",false); verify(mapper).codes("EDUCATION",true); verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"wrong","1","TRUE",""})
    void invalidBooleanHasField(String value) throws Exception {
        mvc.perform(get("/api/codes/JOB").param("includeInactive",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("includeInactive"));
        verifyNoInteractions(mapper);
    }
    @Test void parameterUsesRequestedMonthAndExactValue() throws Exception {
        when(mapper.parameter("CASH_BASE_AMT","202601")).thenReturn(new SharedQueryDtos.Parameter("CASH_BASE_AMT","1150000000","원","2026-01"));
        mvc.perform(get("/api/params/CASH_BASE_AMT").param("month","2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.value").value("1150000000")).andExpect(jsonPath("$.data.effectiveMonth").value("2026-01"));
    }
    @Test void parameterDefaultsToSeoulCurrentMonth() throws Exception {
        when(mapper.parameter("KEY","202611")).thenReturn(new SharedQueryDtos.Parameter("KEY","1","원","2026-01"));
        mvc.perform(get("/api/params/KEY")).andExpect(status().isOk()); verify(mapper).parameter("KEY","202611");
    }
    @ParameterizedTest @ValueSource(strings={"MISSING","FUTURE_ONLY"})
    void missingParameterNeverBecomesZero(String key) throws Exception {
        mvc.perform(get("/api/params/"+key).param("month","2026-01")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STANDARD_NOT_FOUND"));
    }
    @Test void businessMissingParameterHasNoZeroFallbackOrWrites() throws Exception {
        mvc.perform(get("/api/params/MISSING").param("month","2026-01"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("STANDARD_NOT_FOUND")).andExpect(jsonPath("$.data").doesNotExist());
        verify(mapper).parameter("MISSING","202601"); verifyNoMoreInteractions(mapper);
    }
    @Test void invalidParameterMonth() throws Exception {
        mvc.perform(get("/api/params/KEY").param("month","2026-13")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"))
            .andExpect(jsonPath("$.field").value("month")); verifyNoInteractions(mapper);
    }
    @Test void closeUsesMigrationClosedWorkAndAbsentStatuses() throws Exception {
        when(mapper.monthCloses("202512","202603")).thenReturn(List.of(new SharedQueryDtos.CloseRow("202601","CLOSE"),new SharedQueryDtos.CloseRow("202602","WORK")));
        mvc.perform(get("/api/month-close").param("from","2025-12").param("to","2026-03")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].status").value("이관·결산완료"))
            .andExpect(jsonPath("$.data.items[1].status").value("마감")).andExpect(jsonPath("$.data.items[2].status").value("작업중"))
            .andExpect(jsonPath("$.data.items[3].status").value("작업중"));
        verify(mapper).cashBaseMonth(); verify(mapper).monthCloses("202512","202603"); verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"2026-13","2026-00","2026-2","0000-01"})
    void invalidFromMonth(String value) throws Exception {
        mvc.perform(get("/api/month-close").param("from",value).param("to","2026-11")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PERIOD")).andExpect(jsonPath("$.field").value("from")); verifyNoInteractions(mapper);
    }
    @Test void reversedRangeDoesNotQueryDatabase() throws Exception {
        mvc.perform(get("/api/month-close").param("from","2026-12").param("to","2026-01")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PERIOD")).andExpect(jsonPath("$.field").value("to")); verifyNoInteractions(mapper);
    }
    @Test void requiredRangeHasField() throws Exception {
        mvc.perform(get("/api/month-close").param("from","2026-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("to"));
    }
    @Test void contextUsesServerDateAndActualClose() throws Exception {
        when(mapper.monthCloses("202611","202611")).thenReturn(List.of(new SharedQueryDtos.CloseRow("202611","CLOSE")));
        mvc.perform(get("/api/period-context")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentDate").value("2026-11-10"))
            .andExpect(jsonPath("$.data.currentMonth").value("2026-11")).andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
            .andExpect(jsonPath("$.data.cashBaseMonth").value("2026-01")).andExpect(jsonPath("$.data.monthClose.status").value("마감"));
    }
    @Test void businessContextReportsFutureStartWithoutVirtualClose() throws Exception {
        when(mapper.cashBaseMonth()).thenReturn("202701");
        mvc.perform(get("/api/period-context")).andExpect(status().isOk()).andExpect(jsonPath("$.data.cashBaseMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.monthClose.status").value("이관·결산완료"));
        verify(mapper).cashBaseMonth(); verify(mapper).monthCloses("202611","202611"); verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"targetType=UNKNOWN","page=0","size=30"})
    void historyRejectsInvalidFilter(String query) throws Exception {
        String[] parts=query.split("=");
        mvc.perform(get("/api/common-code-history").param(parts[0],parts[1])).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(parts[0]));
        verifyNoInteractions(mapper);
    }
    @Test void businessHistoryPassesSameFiltersToListAndCountAndPreservesOldLongValues() throws Exception {
        var entry=new SharedQueryDtos.History(8L,OffsetDateTime.parse("2000-01-01T00:00:00+09:00"),"CODE_MAP","JOB","JOB:8","DEL","CODE_MAP","가".repeat(500),null,"SYSTEM");
        when(mapper.history(any())).thenReturn(List.of(entry)); when(mapper.historyCount(any())).thenReturn(51L);
        mvc.perform(get("/api/common-code-history").param("groupId","JOB").param("page","2").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].before").value("가".repeat(500)))
            .andExpect(jsonPath("$.data.items[0].occurredAt").value("2000-01-01T00:00:00+09:00"))
            .andExpect(jsonPath("$.data.total").value(51)).andExpect(jsonPath("$.data.page").value(2));
        var q=new SharedQueryDtos.HistoryQuery(null,"JOB",20,20L);
        verify(mapper).history(q); verify(mapper).historyCount(q); verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"GROUP","CODE","VALUE_CODE","OPER_STD","CODE_MAP"})
    void historySupportsEveryContractTarget(String target) throws Exception {
        when(mapper.history(any())).thenReturn(List.of());
        mvc.perform(get("/api/common-code-history").param("targetType",target))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50));
        var query=new SharedQueryDtos.HistoryQuery(target,null,50,0L);
        verify(mapper).history(query); verify(mapper).historyCount(query);
    }
    @Test void historyDatabaseFailureCannotLeakSqlOrConnectionDetails() throws Exception {
        when(mapper.history(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT private_value FROM internal_table; jdbc:postgresql://private-host/database"));
        mvc.perform(get("/api/common-code-history").header("X-Request-Id","history-failure-check"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").value("history-failure-check"))
            .andExpect(header().string("X-Request-Id","history-failure-check"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal_table"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:"))));
        verify(mapper,never()).historyCount(any());
    }
    @ParameterizedTest @ValueSource(strings={"/api/period-context","/api/month-close","/api/codes/JOB","/api/params/TGT_MRGN_RT"})
    void projectLookupsRemainPublicAndPreserveResponseAndRequestId(String path) throws Exception {
        when(mapper.codes("JOB",false)).thenReturn(List.of());
        when(mapper.parameter("TGT_MRGN_RT","202611"))
            .thenReturn(new SharedQueryDtos.Parameter("TGT_MRGN_RT","15","%","2026-01"));
        var request=get(path).header("X-Request-Id","project-lookup");
        if(path.equals("/api/month-close")) request.param("from","2026-11").param("to","2026-11");
        mvc.perform(request).andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id","project-lookup"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data").isMap());
        verifyNoInteractions(users);
    }
    @ParameterizedTest @ValueSource(strings={"/api/period-context","/api/month-close","/api/codes/JOB","/api/params/TGT_MRGN_RT"})
    void projectLookupsRejectUnknownOrRepeatedQueryValuesBeforeReadingSources(String path) throws Exception {
        String field=path.equals("/api/month-close")?"from":path.startsWith("/api/codes/")?"includeInactive":"month";
        mvc.perform(get(path).param(field,"2026-01","2026-02"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @Test void sharedOperationsUseThePackagedAuthoritativeContract() throws Exception {
        String contract=new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(contract).contains(
            "operationId: getCurrentUser", "operationId: getMenus", "operationId: getPeriodContext",
            "operationId: getMonthClose", "operationId: getCodes", "operationId: getParameter",
            "operationId: getCommonCodeHistory");
    }
}
