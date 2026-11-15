package com.sicost.monthclosesops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.profitability.MonthlyCostDtos.Sources;
import com.sicost.salespartsettlementsops.SettlementClosingService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.monthclosesops.MonthCloseDtos.*;
import static com.sicost.monthclosesops.MonthCloseCalculation.row;
import static com.sicost.monthclosesops.MonthCloseRepository.s;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MonthCloseContractTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    MonthCloseRepository repository;MonthCloseCalculation calculation;CloseCheckService checks;MonthCloseBoundary boundary;
    SettlementClosingService settlements;ChangeHistoryWriter history;AccessLogWriter access;CurrentUserProvider users;CommitInvalidation invalidation;
    MonthCloseService service;MockMvc mvc;Map<String,List<Map<String,Object>>> data;Sources sources;
    @BeforeEach void setup() {
        repository=mock(MonthCloseRepository.class);calculation=mock(MonthCloseCalculation.class);checks=mock(CloseCheckService.class);boundary=mock(MonthCloseBoundary.class);
        settlements=mock(SettlementClosingService.class);history=mock(ChangeHistoryWriter.class);access=mock(AccessLogWriter.class);users=mock(CurrentUserProvider.class);invalidation=mock(CommitInvalidation.class);
        service=new MonthCloseService(repository,calculation,checks,boundary,settlements,history,access,users,Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"),ZoneOffset.UTC),invalidation);
        mvc=MockMvcBuilders.standaloneSetup(new MonthCloseController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        data=new TreeMap<>();for(String table:List.of("TB_CL_CLOSE","TB_CL_CLOSE_CHK","TB_CL_CLOSE_HIST","TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL","TB_SY_CHG_HIST","TB_CO_PREV_ADJ"))data.put(table,new ArrayList<>());
        data.put("TB_SY_OPER_STD",List.of(row("std_cd","CASH_BASE_YM","std_val","202601","aply_bgn_ym","202601")));
        when(repository.load(anyBoolean())).thenReturn(data);when(repository.revision(anyString(),any())).thenReturn("revision");when(users.userId()).thenReturn("server-actor");
        sources=mock(Sources.class);when(calculation.sources(anyString(),any())).thenReturn(sources);
        when(checks.calculate(anyString(),any(),any())).thenReturn(six(false));
        when(calculation.plan(anyString(),any())).thenReturn(new MonthCloseCalculation.Plan(sources,List.of(),List.of(),List.of(),false,new CashImpact("0","0","0","0","0","0","0","0")));
        doAnswer(c->{String table=c.getArgument(0);Map<String,Object> value=new LinkedHashMap<>(c.getArgument(1));MonthCloseRepository.WriteContext ctx=c.getArgument(2);boolean existing=c.getArgument(3);
            value.put("reg_user_id",ctx.actor());value.put("reg_dttm",ctx.at().toString());
            if(existing)data.get(table).removeIf(old->MonthCloseRepository.key(table,old).equals(MonthCloseRepository.key(table,value)));
            data.get(table).add(value);return null;
        }).when(repository).save(anyString(),anyMap(),any(),anyBoolean());
    }
    static List<Check> six(boolean warn){List<Check> list=new ArrayList<>();for(int i=0;i<6;i++)list.add(new Check(CloseCheckService.CODES.get(i),CloseCheckService.NAMES.get(i),warn&&i==4?"WARN":"OK",0L,"대상 0건",null,i==0?null:"UI-10","2026-01",""));return list;}
    String body(String action)throws Exception {Map<String,Object> b=new LinkedHashMap<>();b.put("expectedRevision","revision");if(action.equals("close")||action.equals("reopen")){b.put("operationId",UUID.randomUUID().toString());b.put(action.equals("close")?"acceptWarnings":"acceptAdjustments",true);if(action.equals("reopen"))b.put("reason"," 정정 ");}return json.writeValueAsString(b);}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void missingRevisionIsAFieldErrorBeforeLocks(String action)throws Exception {mvc.perform(post("/api/month-closes/2026-01/"+action).contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));verifyNoInteractions(boundary,repository,history,access);}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void actorInjectionIsRejectedWithoutAuthenticationOrWrites(String action)throws Exception {mvc.perform(post("/api/month-closes/2026-01/"+action).contentType("application/json").content("{\"expectedRevision\":\"revision\",\"actor\":\"injected\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(boundary,repository,history,access);}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void invalidCalendarMonthIs400(String action)throws Exception {mvc.perform(post("/api/month-closes/2026-13/"+action).contentType("application/json").content(body(action))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(repository);}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void futureMonthIs409Not403(String action)throws Exception {mvc.perform(post("/api/month-closes/2026-08/"+action).contentType("application/json").content(body(action))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_OUT_OF_SCOPE"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void unknownDuplicateQueryNeverReachesService(String action)throws Exception {mvc.perform(post("/api/month-closes/2026-01/"+action+"?actor=a&actor=b").contentType("application/json").content(body(action))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(repository);}
    @Test void checksCreateOnlyWorkAndSixRowsAndUseServerTime()throws Exception {
        mvc.perform(post("/api/month-closes/2026-01/checks").contentType("application/json").content(body("checks"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.checks.length()").value(6)).andExpect(jsonPath("$.data.checkedAt").value("2026-07-14 09:00:00"));
        var order=inOrder(boundary,repository);order.verify(boundary).exclusive();order.verify(repository).load(true);
        verify(repository,times(1)).save(eq("TB_CL_CLOSE"),anyMap(),argThat(c->c.actor().equals("server-actor")),eq(false));
        verify(repository,times(6)).save(eq("TB_CL_CLOSE_CHK"),anyMap(),any(),eq(false));verifyNoInteractions(history,access,settlements);
        assertThat(data.get("TB_CL_CLOSE_HIST")).isEmpty();assertThat(data.get("TB_CO_MON_PL")).isEmpty();
    }
    @Test void staleCheckRejectsBeforeFirstWrite()throws Exception {
        doThrow(new ApiException(409,"STALE_WRITE","다시 조회")).when(repository).requireRevision(anyString(),anyString(),any());
        mvc.perform(post("/api/month-closes/2026-01/checks").contentType("application/json").content(body("checks"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());
    }
    @Test void closedCheckCannotOverwriteHistoricalWarnings()throws Exception {closed("202601");mvc.perform(post("/api/month-closes/2026-01/checks").contentType("application/json").content(body("checks"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @Test void checkColumnLimitsArePreflightedWithoutTruncation() {
        var valid=six(false);List<Check> bad=new ArrayList<>(valid);bad.set(0,new Check("PAY","급여","OK",9999999L,"가".repeat(300),null,null,"2026-01",""));CloseCheckService.validate(bad);
        bad.set(0,new Check("PAY","급여","OK",10000000L,"내용",null,null,"2026-01",""));assertThatThrownBy(()->CloseCheckService.validate(bad)).isInstanceOf(ApiException.class);
        bad.set(0,new Check("PAY","급여","OK",0L,"가".repeat(301),null,null,"2026-01",""));assertThatThrownBy(()->CloseCheckService.validate(bad)).isInstanceOf(ApiException.class);
    }
    @Test void purePreviewHasSixChecksAndNeverWritesDefaults()throws Exception {mvc.perform(post("/api/month-closes/2026-01/preview").contentType("application/json").content(body("preview"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.canClose").value(true)).andExpect(jsonPath("$.data.checks.length()").value(6));verifyNoInteractions(boundary,history,access,invalidation);verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @Test void blockerIsNotASeventhCheckAndIsVisibleInPreview()throws Exception {when(calculation.plan(anyString(),any())).thenReturn(new MonthCloseCalculation.Plan(sources,List.of(),List.of(),List.of(new Issue("AMOUNT_UNCONFIRMED","필수 단가 미확인","2026-01","TB_CO_PART_SETL","part","UPRC_AMT")),false,new CashImpact(null,null,null,null,null,null,null,null)));
        mvc.perform(post("/api/month-closes/2026-01/preview").contentType("application/json").content(body("preview"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.canClose").value(false)).andExpect(jsonPath("$.data.checks.length()").value(6)).andExpect(jsonPath("$.data.blockingIssues[0].sourceId").value("part"));
    }
    @Test void warningMustBeExplicitlyAccepted()throws Exception {when(checks.calculate(anyString(),any(),any())).thenReturn(six(true));mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close").replace("true","false"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WARNINGS_NOT_ACCEPTED"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @Test void closeCommitsHistoryChecksAndRequiredAccessUsingOneActorAndTime()throws Exception {
        mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSE")).andExpect(jsonPath("$.data.history.historySequence").value(1)).andExpect(jsonPath("$.data.history.reason").value(org.hamcrest.Matchers.nullValue()));
        verify(access).appendMonthClose(argThat(e->e.action().equals("CLOSE")&&e.historySequence()==1),eq("server-actor"),eq(LocalDateTime.of(2026,7,14,9,0)),any());verify(history,atLeastOnce()).appendMonthClose(argThat(c->c.item().equals("operationId")),eq("server-actor"),any());
    }
    @Test void gapDoesNotCreateMissingCloseRows()throws Exception {closed("202602");mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSE_SEQUENCE_GAP"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @Test void historyExhaustionRejectsBeforeParentCreation()throws Exception {data.get("TB_CL_CLOSE_HIST").add(row("close_ym","202601","hist_sn",99999));mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());}
    @Test void snapshotPrecisionNeverClamps(){MonthCloseCalculation.range(new java.math.BigDecimal("999999999999999"),15,0);assertThatThrownBy(()->MonthCloseCalculation.range(new java.math.BigDecimal("1000000000000000"),15,0)).isInstanceOf(ArithmeticException.class);assertThatThrownBy(()->MonthCloseCalculation.range(new java.math.BigDecimal("10000.00"),6,2)).isInstanceOf(ArithmeticException.class);}
    @Test void reopenPreviewIncludesEveryLaterAdjustmentAndNoOtherTarget()throws Exception {reopenReady();data.get("TB_CO_PREV_ADJ").add(row("adj_id",1,"trgt_ym","202601","aply_ym","202602","adj_amt",7,"adj_rsn","정정"));data.get("TB_CO_PREV_ADJ").add(row("adj_id",2,"trgt_ym","202601","aply_ym","202605","adj_amt",11,"adj_rsn","늦은 정정"));data.get("TB_CO_PREV_ADJ").add(row("adj_id",3,"trgt_ym","202602","aply_ym","202605","adj_amt",13,"adj_rsn","다른 월"));
        mvc.perform(post("/api/month-closes/2026-01/reopen-preview").contentType("application/json").content(body("reopen-preview"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.adjustmentCount").value(2)).andExpect(jsonPath("$.data.adjustmentAmount").value("18")).andExpect(jsonPath("$.data.settlementsPreserved").value(true));verifyNoInteractions(boundary,history,access);}
    @Test void nonLatestAcrossYearsIsNotReopenable()throws Exception {reopenReady();closed("202607");mvc.perform(post("/api/month-closes/2026-01/reopen-preview").contentType("application/json").content(body("reopen-preview"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.canReopen").value(false)).andExpect(jsonPath("$.data.blockingIssues[0].code").value("NOT_LATEST_CLOSE"));}
    @Test void reopenKeepsSettlementsAndClearsOnlyParentMetadata()throws Exception {reopenReady();mvc.perform(post("/api/month-closes/2026-01/reopen").contentType("application/json").content(body("reopen"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WORK")).andExpect(jsonPath("$.data.history.reason").value("정정"));verifyNoInteractions(settlements);verify(repository).save(eq("TB_CL_CLOSE"),argThat(r->r.get("close_dttm")==null&&r.get("close_user_id")==null),any(),eq(true));verify(access).appendMonthClose(argThat(e->e.action().equals("UNCLOSE")),anyString(),any(),any());}
    @Test void unknownOwnershipBlocksExecutionRatherThanBlanketUnlock()throws Exception {reopenReady();data.get("TB_CO_MON_PL").add(row("pl_ym","202601","fix_yn","Y","migr_yn","N"));mvc.perform(post("/api/month-closes/2026-01/reopen").contentType("application/json").content(body("reopen"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SNAPSHOT_OWNERSHIP_UNKNOWN"));verify(repository,never()).release(anyString(),any(),any(),any());}
    @Test void accessFailureIs500AndNoSuccessInvalidation()throws Exception {doThrow(new IllegalStateException("private SQL")).when(access).appendMonthClose(any(),anyString(),any(),any());mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close"))).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verifyNoInteractions(invalidation);}
    @ParameterizedTest @ValueSource(strings={"checks","preview","close","reopen-preview","reopen"})
    void staleRevisionStopsEveryNewCommandWithoutWriting(String action)throws Exception {
        if(action.startsWith("reopen"))reopenReady();
        doThrow(new ApiException(409,"STALE_WRITE","원천이 변경되었습니다.")).when(repository).requireRevision(anyString(),anyString(),any());
        mvc.perform(post("/api/month-closes/2026-01/"+action).contentType("application/json").content(body(action)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());verifyNoInteractions(history,access,settlements,invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"reopen-preview","reopen"})
    void missingClosedMonthIs404AndNeverWrites(String action)throws Exception {
        mvc.perform(post("/api/month-closes/2026-01/"+action).contentType("application/json").content(body(action)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());verifyNoInteractions(history,access);
    }
    @Test void latestIdenticalCommandReplaysBeforeWorkAndRevisionChecks()throws Exception {
        recordReceipts();String request=body("close");
        var first=mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(false)).andReturn();
        clearInvocations(repository,history,access,settlements,invalidation,calculation);
        doThrow(new ApiException(409,"STALE_WRITE","낡은 revision")).when(repository).requireRevision(anyString(),anyString(),any());
        var second=mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(request))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true)).andReturn();
        var expected=json.readTree(first.getResponse().getContentAsString()).path("data").deepCopy();
        var actual=json.readTree(second.getResponse().getContentAsString()).path("data").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)expected).remove("replayed");((com.fasterxml.jackson.databind.node.ObjectNode)actual).remove("replayed");
        assertThat(actual).isEqualTo(expected);
        verify(repository,never()).requireRevision(anyString(),anyString(),any());verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());
        verifyNoInteractions(history,access,settlements,invalidation,calculation);
    }
    @Test void sameUuidWithDifferentInputOrAfterReopenConflictsWithoutSideEffects()throws Exception {
        recordReceipts();String request=body("close");
        mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(request)).andExpect(status().isOk());
        clearInvocations(repository,history,access,settlements,invalidation);
        mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(request.replace("true","false")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPERATION_CONFLICT"));
        data.get("TB_CL_CLOSE_HIST").add(row("close_ym","202601","hist_sn",2,"act_cd","OPEN"));
        mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(request))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPERATION_CONFLICT"));
        verify(repository,never()).save(anyString(),anyMap(),any(),anyBoolean());verifyNoInteractions(history,access,settlements,invalidation);
    }
    @Test void requiredAccessFailureNeverLeaksSqlOrReturnsSuccess()throws Exception {
        doThrow(new IllegalStateException("SELECT private_account FROM internal_table")).when(access).appendMonthClose(any(),anyString(),any(),any());
        mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(body("close")))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.requestId").isString())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_account"))));
        verifyNoInteractions(invalidation);
    }
    void recordReceipts() {
        doAnswer(call->{ChangeHistoryWriter.Change change=call.getArgument(0);
            data.get("TB_SY_CHG_HIST").add(row("trgt_tbl_nm",change.tableName(),"trgt_key",change.targetKey(),"chg_item_nm",change.item(),"bfr_val",change.before(),"aft_val",change.after(),"chg_rsn",change.reason()));return null;
        }).when(history).appendMonthClose(any(),anyString(),any());
    }
    @ParameterizedTest @ValueSource(strings={""," ","not-a-uuid"})void uuidFormatIsStrict(String id)throws Exception {mvc.perform(post("/api/month-closes/2026-01/close").contentType("application/json").content(json.writeValueAsString(Map.of("operationId",id,"expectedRevision","revision","acceptWarnings",true)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("operationId"));verifyNoInteractions(boundary);}
    void closed(String db){data.get("TB_CL_CLOSE").add(row("close_ym",db,"close_stat_cd","CLOSE","close_user_id","admin","close_dttm","2026-01-31T10:00:00"));}
    void reopenReady(){closed("202601");data.get("TB_CL_CLOSE_HIST").add(row("close_ym","202601","hist_sn",1,"act_cd","CLOSE"));for(String table:List.of("TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL"))for(String field:List.of("rowCount","preservedIndependentFixedCount"))data.get("TB_SY_CHG_HIST").add(row("trgt_tbl_nm","TB_CL_CLOSE_HIST","trgt_key","202601:1","chg_item_nm",table+"."+field,"aft_val","0"));}
}
