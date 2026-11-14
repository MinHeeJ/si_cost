package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.profitability.MonthlyCostDtos.Sources;
import com.sicost.salespartsettlements.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementCommandTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    SettlementCommandMapper mapper;SalesPartSettlementService reads;SalesPartSettlementRepository repository;
    SettlementSourceLocks locks;RevisionService revisions;ChangeHistoryWriter history;CurrentUserProvider users;CommitInvalidation invalidation;
    SettlementCommandService service;MockMvc mvc;Result result;Sources sources;
    SalesPartSettlementRepository.Snapshot snapshot;
    @BeforeEach void setup() {
        mapper=mock(SettlementCommandMapper.class);reads=mock(SalesPartSettlementService.class);repository=mock(SalesPartSettlementRepository.class);
        locks=mock(SettlementSourceLocks.class);history=mock(ChangeHistoryWriter.class);users=mock(CurrentUserProvider.class);invalidation=mock(CommitInvalidation.class);
        revisions=new RevisionService(json);
        service=new SettlementCommandService(mapper,reads,repository,locks,revisions,history,users,Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"),ZoneOffset.UTC),new SettlementSnapshotCodec(json),invalidation);
        mvc=MockMvcBuilders.standaloneSetup(new SettlementCommandController(service,new SettlementTrendService(reads))).setControllerAdvice(new ApiExceptionHandler()).build();
        sources=mock(Sources.class);when(sources.asOfDate()).thenReturn(LocalDate.of(2026,7,14));when(sources.people()).thenReturn(List.of());
        snapshot=new SalesPartSettlementRepository.Snapshot(sources,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        result=result("WORK","5",true,true,false,List.of());
        when(reads.previewSettlement(anyString(),anyString())).thenAnswer(c->result);
        when(repository.load(anyString(),anyBoolean())).thenAnswer(c->snapshot);
        when(mapper.sourceKeys(anyString())).thenReturn(List.of("PART:part","DEV:developer","PAY:202607"));
        when(mapper.insertParent(any())).thenReturn(1);when(mapper.updateParent(any())).thenReturn(1);when(mapper.insertDetail(any())).thenReturn(1);
        when(users.userId()).thenReturn("server-actor");
    }
    static Result result(String status,String card,boolean save,boolean confirm,boolean cancel,List<Row> rows) {
        var totals=new Totals(rows.size(),0,"1.00","100","10","2","0","12","88","35",card,"30");
        return new Result("part","2026-07",status,new Standard("leader","파트장","40","2","2026-01",true),card,"revision",status.equals("WORK")?null:"original-actor",null,status.equals("WORK")?null:"2026-07-01T00:00:00+09:00",null,!save,List.of(),save,confirm,cancel,totals,totals,rows,new Page(1,50,rows.size()),List.of());
    }
    static Row row(String id,String project) {
        return new Row(id,id,id,"FT",List.of(project),null,"1.00","100","10","2","0","12","88","35","급여 예정",new Basis("ESTIMATED_BASE",true,false,null,null,null,null,"40",List.of(),List.of()),List.of());
    }
    String command(String field,String value) throws Exception {return json.writeValueAsString(Map.of("month","2026-07",field,value,"expectedRevision","revision"));}
    @Test void cardZeroWritesOnlyParentAndRawFieldAuditAfterLocks() throws Exception {
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount","0")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.partProfitAmount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.confirmedBy").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.confirmedAt").value(org.hamcrest.Matchers.nullValue()));
        var order=inOrder(locks,reads,mapper,history);
        order.verify(locks).lock(anyList());order.verify(reads).previewSettlement("part","2026-07");
        order.verify(history).preflightSettlement(anyList());order.verify(mapper).insertParent(argThat(p->p.card().signum()==0&&p.profit()==null&&p.confirmedAt()==null&&p.actor().equals("server-actor")));
        verify(mapper,never()).insertDetail(any());verify(mapper,never()).deleteDetails(any());
        verify(history).append(argThat(c->c.targetKey().equals("part:202607")&&c.item().equals("CORP_CARD_AMT")&&c.after().equals("0")&&c.reason().equals("카드저장")));
    }
    @Test void latestSameCardDoesNotTouchAuditOrTimestamps() throws Exception {
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount","5"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        verify(mapper,never()).insertParent(any());verifyNoInteractions(history,invalidation);
    }
    @Test void absentParentZeroIsNoOp() throws Exception {
        result=result("WORK","0",true,true,false,List.of());
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount",""))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        verifyNoInteractions(history);verify(mapper,never()).insertParent(any());
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","1,00","10원","1 000","1000000000000000"})
    void invalidCardDoesNotLockOrWrite(String value) throws Exception {
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount",value))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("cardAmount"));verifyNoInteractions(locks,history);
    }
    @ParameterizedTest @ValueSource(strings={"card","confirm","cancel"})
    void staleRevisionPrecedesStateOrNoOpChecks(String action) throws Exception {
        String body=command(action.equals("cancel")?"reason":"cardAmount",action.equals("cancel")?"취소":"5").replace("revision","stale");
        var request=action.equals("card")?put("/api/sales-part-settlements/part/"+action):post("/api/sales-part-settlements/part/"+action);
        mvc.perform(request.contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"CNFM","AUTO"})
    void confirmedCardCannotBeEdited(String state) throws Exception {
        result=result(state,"5",false,false,true,List.of());
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount","0"))).andExpect(status().isConflict());verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"card","confirm","cancel"})
    void closeRejectsEveryCommandWithoutAudit(String action) throws Exception {
        when(sources.protectedMonth("202607")).thenReturn(true);
        var request=action.equals("card")?put("/api/sales-part-settlements/part/card"):post("/api/sales-part-settlements/part/"+action);
        mvc.perform(request.contentType("application/json").content(command(action.equals("cancel")?"reason":"cardAmount",action.equals("cancel")?"사유":"8"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verifyNoInteractions(history);
    }
    @Test void confirmationInsertsHeaderBeforeAllDetailsIncludingOffPageAndUsesServerActor() throws Exception {
        var rows=new ArrayList<Row>();for(int i=0;i<61;i++)rows.add(row("dev"+i,"프로젝트, 이름"));
        result=result("WORK","5",true,true,false,rows);
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","9"))).andExpect(status().isOk());
        var order=inOrder(mapper);order.verify(mapper).sourceKeys("202607");order.verify(mapper).insertParent(argThat(p->p.status().equals("CNFM")&&p.card().intValueExact()==9&&p.profit().intValueExact()==26&&p.confirmedBy().equals("server-actor")&&p.confirmedAt().equals(p.at())));order.verify(mapper).deleteDetails(any());
        verify(mapper,times(61)).insertDetail(argThat(d->d.projects().equals("[\"프로젝트, 이름\"]")&&d.remark().equals("급여 예정")));
        verify(mapper,never()).updateParent(any());verify(history,atLeastOnce()).append(argThat(c->c.tableName().equals("TB_CO_PART_SETL_DTL")&&c.targetKey().startsWith("part:202607:dev")));
    }
    @Test void latestDuplicateConfirmationIsNoOp() throws Exception {
        result=result("CNFM","5",false,false,true,List.of());
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","999"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.confirmedBy").value("original-actor"));verifyNoInteractions(history);
    }
    @Test void invalidStandardOrUnknownAmountBlocksConfirmationBeforeCardWrite() throws Exception {
        result=result("WORK","5",true,false,false,List.of());
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","9"))).andExpect(status().isConflict());verifyNoInteractions(history);verify(mapper,never()).insertParent(any());
    }
    @Test void snapshot1001CodepointsBlocksAllWrites() throws Exception {
        result=result("WORK","5",true,true,false,List.of(row("dev","가".repeat(997))));
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","9"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SNAPSHOT_TEXT_TOO_LONG"));verify(mapper,never()).insertParent(any());
    }
    @Test void auditPreflightFailureBlocksParentAndDetails() throws Exception {
        doThrow(new com.sicost.common.api.ApiException(409,"STATE_CONFLICT","감사 폭 초과")).when(history).preflightSettlement(anyList());
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","9"))).andExpect(status().isConflict());verify(mapper,never()).insertParent(any());
    }
    @Test void workCancellationIsConflict() throws Exception {
        mvc.perform(post("/api/sales-part-settlements/part/cancel").contentType("application/json").content(command("reason","사유"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"","   "})
    void blankReasonIsFieldError(String reason) throws Exception {
        mvc.perform(post("/api/sales-part-settlements/part/cancel").contentType("application/json").content(command("reason",reason))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reason"));verifyNoInteractions(locks);
    }
    @Test void reason501IsRejectedWithoutTruncation() throws Exception {
        mvc.perform(post("/api/sales-part-settlements/part/cancel").contentType("application/json").content(command("reason","가".repeat(501)))).andExpect(status().isBadRequest());verifyNoInteractions(locks);
    }
    @Test void cancellationPreservesCardAndAuditsDetailBeforeBoundedDelete() throws Exception {
        result=result("AUTO","5",false,false,true,List.of());
        var parent=new Parent("part","202607",new BigDecimal("5"),new BigDecimal("30"),"AUTO","original-actor",null,LocalDateTime.of(2026,7,1,0,0),null,"actor",null,null,null);
        var d=new Detail("part","202607","dev",null,null,"FT","[\"원본\"]",BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ONE,null,"actor",null,null,null);
        snapshot=new SalesPartSettlementRepository.Snapshot(sources,List.of(),List.of(),List.of(),List.of(parent),List.of(d),List.of(),List.of());
        String reason="가".repeat(500);
        mvc.perform(post("/api/sales-part-settlements/part/cancel").contentType("application/json").content(command("reason"," "+reason+" "))).andExpect(status().isOk());
        verify(mapper).updateParent(argThat(p->p.status().equals("WORK")&&p.card().intValueExact()==5&&p.profit()==null&&p.confirmedBy()==null&&p.confirmedAt()==null&&p.reason().equals(reason)));
        verify(mapper).deleteDetails(new SettlementCommandMapper.Identity("part","202607"));
        verify(history).append(argThat(c->c.type().equals("DEL")&&c.item().equals("PROJ_NM_LIST")&&c.before().equals("[\"원본\"]")&&c.after()==null&&c.reason().equals(reason)));
    }
    @Test void missingRevisionAndInjectedActorAreRejected() throws Exception {
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content("{\"month\":\"2026-07\",\"cardAmount\":\"1\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));
        mvc.perform(post("/api/sales-part-settlements/part/confirm").contentType("application/json").content(command("cardAmount","1").replace("{","{\"actor\":\"injected\","))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actor"));verifyNoInteractions(locks);
    }
    @Test void cancellationReturnsWorkMetadataRatherThanRecalculatedProfit() throws Exception {
        Result confirmed=result("AUTO","5",false,false,true,List.of());
        Result work=result("WORK","5",true,true,false,List.of());
        when(reads.previewSettlement("part","2026-07")).thenReturn(confirmed,work);
        mvc.perform(post("/api/sales-part-settlements/part/cancel").contentType("application/json").content(command("reason","재검토")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("WORK"))
            .andExpect(jsonPath("$.data.cardAmount").value("5"))
            .andExpect(jsonPath("$.data.partProfitAmount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.confirmedBy").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.confirmedAt").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper).deleteDetails(new SettlementCommandMapper.Identity("part","202607"));
    }
    @ParameterizedTest @ValueSource(strings={"card","confirm","cancel"})
    void nonexistentPartReturns404WithoutWrites(String action) throws Exception {
        when(reads.previewSettlement(anyString(),anyString())).thenThrow(new com.sicost.common.api.ApiException(404,"NOT_FOUND","일반 영업 파트를 찾을 수 없습니다."));
        var request=action.equals("card")?put("/api/sales-part-settlements/missing/card"):post("/api/sales-part-settlements/missing/"+action);
        mvc.perform(request.contentType("application/json").content(command(action.equals("cancel")?"reason":"cardAmount",action.equals("cancel")?"사유":"9")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.requestId").isString());
        verifyNoInteractions(history,invalidation);verify(mapper,never()).insertParent(any());verify(mapper,never()).updateParent(any());verify(mapper,never()).deleteDetails(any());
    }
    @Test void auditStorageFailureIsSanitizedAs500() throws Exception {
        doThrow(new IllegalStateException("SQL secret")).when(history).append(any());
        mvc.perform(put("/api/sales-part-settlements/part/card").contentType("application/json").content(command("cardAmount","9"))).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
    }
    @Test void closeConfirmationUsesSameAmountsStoredCardAndCallerActor() {
        service.confirmForClose("part","2026-07","close-actor");
        verify(mapper).insertParent(argThat(p->p.status().equals("AUTO")&&p.card().intValueExact()==5&&p.profit().intValueExact()==30&&p.confirmedBy().equals("close-actor")));
        verify(history,atLeastOnce()).appendSettlement(argThat(c->c.reason().equals("마감자동확정")),eq("close-actor"));
        verifyNoInteractions(users);
    }
    @Test void closeConfirmationNeverRebuildsExistingSnapshot() {
        result=result("CNFM","5",false,false,true,List.of());
        assertThat(service.confirmForClose("part","2026-07","close-actor").changed()).isFalse();
        verify(mapper,never()).insertParent(any());verify(mapper,never()).updateParent(any());verifyNoInteractions(history);
    }
    @Test void closeConfirmationPropagatesUnknownSourceFailureWithoutPartialSave() {
        result=result("WORK","5",true,false,false,List.of());
        assertThatThrownBy(()->service.confirmForClose("part","2026-07","close-actor")).isInstanceOf(com.sicost.common.api.ApiException.class);
        verify(mapper,never()).insertParent(any());verifyNoInteractions(history);
    }
}
