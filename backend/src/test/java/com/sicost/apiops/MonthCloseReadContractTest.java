package com.sicost.apiops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.monthclosesops.*;
import com.sicost.monthclosesops.MonthCloseDtos.Check;
import com.sicost.profitability.MonthlyCostDtos.Sources;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonthCloseReadContractTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    MonthCloseMapper mapper;MonthCloseCalculation calculation;CloseCheckService checks;CurrentUserProvider users;MockMvc mvc;
    Map<String,List<Map<String,Object>>> data;
    @BeforeEach void setup() throws Exception {
        mapper=mock(MonthCloseMapper.class);calculation=mock(MonthCloseCalculation.class);checks=mock(CloseCheckService.class);users=mock(CurrentUserProvider.class);
        data=new HashMap<>();data.put("TB_SY_OPER_STD",List.of(row("std_cd","CASH_BASE_YM","aply_bgn_ym","202601","std_val","202601")));
        when(mapper.rows(any())).thenAnswer(c->{var query=(MonthCloseMapper.Read)c.getArgument(0);return data.getOrDefault(query.table(),List.of()).stream().map(r->{try{return json.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException(e);}}).toList();});
        when(calculation.sources(anyString(),anyMap())).thenReturn(mock(Sources.class));
        when(checks.calculate(anyString(),anyMap(),any())).thenAnswer(c->current(c.getArgument(0)));
        var repository=new MonthCloseRepository(mapper,json,new RevisionService(json));
        var names=mock(com.sicost.common.context.CurrentUserMapper.class);
        when(names.findName("original")).thenReturn("재무담당");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),false));
        var service=new MonthCloseReadService(repository,calculation,checks,users,Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"),ZoneOffset.UTC),names);
        mvc=MockMvcBuilders.standaloneSetup(new MonthCloseReadController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    static Map<String,Object> row(Object... fields){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)r.put(fields[i].toString(),fields[i+1]);return r;}
    static List<Check> current(String month){List<Check> rows=new ArrayList<>();for(int i=0;i<6;i++)rows.add(new Check(CloseCheckService.CODES.get(i),CloseCheckService.NAMES.get(i),"OK",0L,"현재 점검",null,i==0?null:i==1?"UI-12":i==4?"UI-19":i==3?"UI-10":"UI-13",month,i==4?"part":""));return rows;}
    ResultActions read(String month) throws Exception{return mvc.perform(get("/api/month-closes").param("month",month).param("page","1").param("size","20"));}
    void closed(String month) {
        data.computeIfAbsent("TB_CL_CLOSE",k->new ArrayList<>()).add(row("close_ym",month,"close_stat_cd","CLOSE","close_user_id","original","close_dttm","2026-11-01T10:00:00"));
        List<Map<String,Object>> saved=data.computeIfAbsent("TB_CL_CLOSE_CHK",k->new ArrayList<>());
        for(String code:CloseCheckService.CODES)saved.add(row("close_ym",month,"chk_item_cd",code,"chk_stat_cd",code.equals("BILL")?"WARN":"OK","chk_cnt",code.equals("BILL")?1:0,"chk_cn","당시 점검","chk_dttm","2026-11-01T09:00:00","link_scrn_id",code.equals("PAY")?null:"UI-13"));
    }
    @Test void publicEmptyMonthReturnsCurrentSixChecksDefaultsAndCompleteRevision() throws Exception {
        read("2026-11").andExpect(status().isOk()).andExpect(jsonPath("$.data.checks.length()").value(6))
            .andExpect(jsonPath("$.data.checks[*].code").value(org.hamcrest.Matchers.contains("PAY","MM_OVER","BILL","FI","SETL","BILL_MISS"))).andExpect(jsonPath("$.data.status").value("WORK"))
            .andExpect(jsonPath("$.data.nextCloseMonth").value("2026-01")).andExpect(jsonPath("$.data.latestCloseMonth").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.managementAllowed").value(true)).andExpect(jsonPath("$.data.canCheck").value(true)).andExpect(jsonPath("$.data.canClose").value(false))
            .andExpect(jsonPath("$.data.canReopen").value(false)).andExpect(jsonPath("$.data.revision").isNotEmpty()).andExpect(jsonPath("$.data.history.items").isEmpty())
            .andExpect(jsonPath("$.data.checks[0].description").value("0명 업로드 완료")).andExpect(jsonPath("$.data.checks[3].description").value("0건 입력"));
        verify(users).currentUser();verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).delete(any());verify(mapper,never()).release(any());
        verify(mapper,atLeastOnce()).rows(argThat(q->!q.lock()));
    }
    @Test void omittedMonthUsesSeoulCurrentMonthAndDefaultHistoryPage() throws Exception {
        mvc.perform(get("/api/month-closes")).andExpect(status().isOk()).andExpect(jsonPath("$.data.month").value("2026-11"))
            .andExpect(jsonPath("$.data.history.page").value(1)).andExpect(jsonPath("$.data.history.size").value(50));
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","closedBy","year","filter"})
    void unknownFieldsAreRejectedBeforeUserAndSourceReads(String field) throws Exception {
        mvc.perform(get("/api/month-closes").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"2026-13","2026-00","2026-1","0000-01","2026-01-01",""})
    void malformedMonthHasFieldError(String month) throws Exception {
        read(month).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","2147483648"})
    void invalidPageHasFieldError(String page) throws Exception {
        mvc.perform(get("/api/month-closes").param("page",page)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));verifyNoInteractions(mapper);
    }
    @Test void unsupportedSizeHasFieldError() throws Exception {
        mvc.perform(get("/api/month-closes").param("size","30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));verifyNoInteractions(mapper);
    }
    @Test void duplicateQueryIsNotSilentlySelected() throws Exception {
        mvc.perform(get("/api/month-closes").param("month","2026-01","2026-02")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"2025-12","2026-12"})
    void validMonthOutsideManagementScopeIsConflictNotAuthentication(String month) throws Exception {
        read(month).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_OUT_OF_SCOPE"));verifyNoInteractions(calculation,checks);verify(mapper,never()).insert(any());
    }
    @Test void workParentIsNotClosedAndReadDoesNotModifyIt() throws Exception {
        data.put("TB_CL_CLOSE",List.of(row("close_ym","202601","close_stat_cd","WORK","mod_dttm","2026-01-01T00:00:00")));
        var before=json.writeValueAsString(data);
        read("2026-01").andExpect(jsonPath("$.data.status").value("WORK")).andExpect(jsonPath("$.data.canClose").value(true)).andExpect(jsonPath("$.data.checkMode").value("CURRENT"));
        assertThat(json.writeValueAsString(data)).isEqualTo(before);verify(mapper,never()).update(any());
    }
    @Test void closedMonthKeepsHistoricalWarningsEvenAfterCurrentSourcesAreResolved() throws Exception {
        closed("202601");
        read("2026-01").andExpect(status().isOk()).andExpect(jsonPath("$.data.checkMode").value("CLOSE_SNAPSHOT"))
            .andExpect(jsonPath("$.data.checks[2].status").value("WARN")).andExpect(jsonPath("$.data.warningCount").value(1))
            .andExpect(jsonPath("$.data.closedBy").value("original")).andExpect(jsonPath("$.data.closedAt").value("2026-11-01 10:00:00"))
            .andExpect(jsonPath("$.data.closedByName").value("재무담당"))
            .andExpect(jsonPath("$.data.canCheck").value(false)).andExpect(jsonPath("$.data.canClose").value(false)).andExpect(jsonPath("$.data.canReopen").value(true));
        verifyNoInteractions(calculation,checks);verify(mapper,never()).update(any());
    }
    @Test void onlyLatestAcrossYearsCanReopenAndOldOperatingYearsRemainManagementAllowed() throws Exception {
        data.put("TB_SY_OPER_STD",List.of(row("std_cd","CASH_BASE_YM","aply_bgn_ym","202501","std_val","202512")));
        closed("202512");closed("202601");
        read("2025-12").andExpect(status().isOk()).andExpect(jsonPath("$.data.managementAllowed").value(true)).andExpect(jsonPath("$.data.canReopen").value(false)).andExpect(jsonPath("$.data.latestCloseMonth").value("2026-01"));
        read("2026-01").andExpect(jsonPath("$.data.canReopen").value(true));
    }
    @Test void financeCannotReopenEvenTheLatestMonth() throws Exception {
        closed("202601");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("finance","재무담당",List.of("FIN"),false));
        read("2026-01").andExpect(status().isOk()).andExpect(jsonPath("$.data.canReopen").value(false));
    }
    @Test void historySpansSelectedYearWithStableOrderingAndFullTotalOnSecondAndBeyondPages() throws Exception {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=1;i<=21;i++)rows.add(row("close_ym",i==21?"202602":"202601","hist_sn",i,"act_cd","CLOSE","reg_user_id","original","reg_dttm","2026-11-01T10:00:00","remain_chk_cn","당시 WARN"));
        rows.add(row("close_ym","202512","hist_sn",1,"act_cd","OPEN","reg_user_id","original","reg_dttm","2026-11-02T10:00:00"));data.put("TB_CL_CLOSE_HIST",rows);
        read("2026-11").andExpect(jsonPath("$.data.history.total").value(21)).andExpect(jsonPath("$.data.history.items.length()").value(20)).andExpect(jsonPath("$.data.history.hasMore").value(true))
            .andExpect(jsonPath("$.data.history.items[0].historySequence").value(1))
            .andExpect(jsonPath("$.data.history.items[0].actorId").value("original"))
            .andExpect(jsonPath("$.data.history.items[0].actorName").value("재무담당"));
        mvc.perform(get("/api/month-closes").param("month","2026-11").param("page","2").param("size","20"))
            .andExpect(jsonPath("$.data.history.total").value(21)).andExpect(jsonPath("$.data.history.items.length()").value(1)).andExpect(jsonPath("$.data.history.items[0].month").value("2026-02")).andExpect(jsonPath("$.data.history.hasMore").value(false));
        mvc.perform(get("/api/month-closes").param("month","2026-11").param("page","2147483647").param("size","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.history.total").value(21)).andExpect(jsonPath("$.data.history.items").isEmpty());
    }
    @Test void currentCalculationCannotBeMistakenForSavedOldResult() throws Exception {
        data.put("TB_CL_CLOSE_CHK",List.of(row("close_ym","202601","chk_item_cd","PAY","chk_stat_cd","WARN","chk_cnt",9,"chk_dttm","2026-01-01T12:00:00")));
        read("2026-01").andExpect(jsonPath("$.data.checkMode").value("CURRENT")).andExpect(jsonPath("$.data.checks[0].status").value("OK"))
            .andExpect(jsonPath("$.data.checks[0].count").value(0)).andExpect(jsonPath("$.data.checks[0].checkedAt").value("2026-01-01 12:00:00"));
    }
    @Test void completeRevisionChangesWhenOffPageSourceChanges() throws Exception {
        String before=json.readTree(read("2026-01").andReturn().getResponse().getContentAsString()).path("data").path("revision").asText();
        data.put("TB_FI_CORP_TAX",List.of(row("tax_yr","2026","est_tax_amt",1,"mod_dttm","2026-01-01T12:00:00")));
        String after=json.readTree(read("2026-01").andReturn().getResponse().getContentAsString()).path("data").path("revision").asText();assertThat(after).isNotEqualTo(before);
    }
    @Test void gapInClosedSequenceReturnsConflictWithoutSynthesizingParents() throws Exception {
        closed("202602");read("2026-01").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSE_SEQUENCE_GAP"));verify(mapper,never()).insert(any());
    }
    @Test void missingCloseSnapshotsAreNotReconstructedFromCurrentSources() throws Exception {
        closed("202601");data.put("TB_CL_CLOSE_CHK",List.of());read("2026-01").andExpect(status().isInternalServerError());verifyNoInteractions(calculation,checks);
    }
}
