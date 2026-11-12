package com.sicost.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import java.math.BigDecimal;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.budget.ProjectBudgetDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectBudgetController.class)
@ContextConfiguration(classes=ProjectBudgetController.class)
@Import({ProjectBudgetService.class,ProjectBudgetRepository.class,WriteCoordinator.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectBudgetContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectBudgetMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Stored row;
    String childState;
    @BeforeEach void setup() {
        row=new Stored("BUDGET-1","TK","ING",LocalDate.of(2027,12,31),new BigDecimal("101"),new BigDecimal("80"),"N");
        childState="initial";
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(mapper.find(anyString())).thenAnswer(call->row);
        when(mapper.snapshot(anyString())).thenAnswer(call->snapshot());
        when(mapper.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202501"));
        when(mapper.operatingValue("TGT_MRGN_RT","202607")).thenReturn(new BigDecimal("20"));
        when(mapper.update(any())).thenAnswer(call->{Mutation m=call.getArgument(0);row=new Stored(row.id(),row.contractType(),row.status(),row.endDate(),row.contractAmount(),m.amount(),"Y");return 1;});
        when(mapper.insertHistory(any())).thenReturn(1);
    }
    String snapshot() {return row==null?null:row.toString()+childState;}
    ObjectNode body(String amount) {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot())).put("budgetAmount",amount);}
    void save(ObjectNode body,int status,String code) throws Exception {
        var response=mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body.toString())).andExpect(status().is(status));
        if (code!=null) response.andExpect(jsonPath("$.code").value(code));
    }
    void noWrites() {verify(mapper,never()).update(any());verify(mapper,never()).insertHistory(any());verifyNoInteractions(history,invalidation);}
    @Test void publicGetReturnsPersistedBudgetAndSeoulCurrentPreviewWithoutWrites() throws Exception {
        mvc.perform(get("/api/projects/BUDGET-1/budget")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.data.budgetAmount").value("80")).andExpect(jsonPath("$.data.budgetManual").value("N"))
            .andExpect(jsonPath("$.data.autoPreviewAmount").value("80")).andExpect(jsonPath("$.data.targetMarginRate").value("20"))
            .andExpect(jsonPath("$.data.basisMonth").value("2026-07")).andExpect(jsonPath("$.data.revision").value(revisions.revision(snapshot())));
        verify(users).currentUser();noWrites();
    }
    @Test void zeroManualSaveNeedsNoPmRoleAndReturnsFinalRevision() throws Exception {
        save(body("0"),200,null);
        assertThat(row.budgetAmount()).isEqualByComparingTo("0");assertThat(row.budgetManual()).isEqualTo("Y");
        verify(mapper).update(argThat(m->m.actor().equals("admin") && m.now().equals(LocalDateTime.of(2026,7,1,0,0))));
        mvc.perform(get("/api/projects/BUDGET-1/budget")).andExpect(jsonPath("$.data.budgetAmount").value("0"))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision(snapshot())));
    }
    @Test void equalAutoAmountStillChangesFlagAndCreatesExactlyOneBudgetHistoryAndAudit() throws Exception {
        String revision=body("80").get("expectedRevision").asText();
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body("80").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.value.budgetManual").value("Y"));
        assertThat(revisions.revision(snapshot())).isNotEqualTo(revision);
        verify(mapper).insertHistory(argThat(h->h.historySn()==1 && h.before().contains("budgetManual=N") && h.after().contains("budgetManual=Y")));
        verify(history).insert(argThat(h->h.tableName().equals("TB_PJ_PROJ") && h.userId().equals("admin") && h.before().contains("=N") && h.after().contains("=Y")));
        clearInvocations(mapper,history,invalidation);
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body("80").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1e3","+1","1%","","9999999999999999","NaN"})
    void invalidMoneyIsFieldErrorAndNeverWrites(String amount) throws Exception {
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body(amount).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("budgetAmount"));noWrites();
    }
    @Test void unicodeWhitespaceCommasAndTerminalWonRemainExact() throws Exception {
        save(body(" 999,999,999,999,999\u00a0원 "),200,null);assertThat(row.budgetAmount()).isEqualByComparingTo("999999999999999");
    }
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision","budgetAmount"})
    void missingRequiredFieldsAreRejected(String field) throws Exception {
        ObjectNode body=body("0");body.remove(field);
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.code").value("INVALID_INPUT"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"budgetMode","budgetManual","modUserId","id"})
    void unknownJsonFieldsAreNotAccepted(String field) throws Exception {
        ObjectNode body=body("0").put(field,"injected");
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.code").value("INVALID_INPUT"));noWrites();
    }
    @Test void unknownQueryRejectedOnBothOperations() throws Exception {
        mvc.perform(get("/api/projects/BUDGET-1/budget").param("year","2026"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year")).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mvc.perform(put("/api/projects/BUDGET-1/budget").param("q","x").contentType("application/json").content(body("0").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("q"));noWrites();
    }
    @Test void malformedJsonUsesInvalidInputAndRequestIdWithoutLeakingParser() throws Exception {
        mvc.perform(put("/api/projects/BUDGET-1/budget").header("X-Request-Id","budget-json").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("body")).andExpect(jsonPath("$.requestId").value("budget-json"));noWrites();
    }
    @Test void numericAmountIsNotCoercedToString() throws Exception {
        save(body("0").put("budgetAmount",0),400,"INVALID_INPUT");noWrites();
    }
    @Test void stringYearIsNotCoercedToInteger() throws Exception {
        save(body("0").put("year","2026"),400,"INVALID_INPUT");noWrites();
    }
    @Test void longIdRejectedBeforeSourceRead() throws Exception {
        mvc.perform(get("/api/projects/"+"x".repeat(21)+"/budget")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));noWrites();
    }
    @Test void missingProjectReturns404OnGetAndPut() throws Exception {
        ObjectNode body=body("0");row=null;
        mvc.perform(get("/api/projects/unknown/budget")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        save(body,404,"NOT_FOUND");noWrites();
    }
    @Test void internalProjectRetainsNullAndAutomaticFlag() throws Exception {
        row=new Stored(row.id(),"IN","ING",row.endDate(),BigDecimal.ZERO,null,"N");
        save(body("0"),400,"INVALID_INPUT");assertThat(row.budgetAmount()).isNull();assertThat(row.budgetManual()).isEqualTo("N");noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"LOSE","END"})
    void lockedLifecycleDoesNotMutate(String state) throws Exception {
        row=new Stored(row.id(),row.contractType(),state,row.endDate(),row.contractAmount(),row.budgetAmount(),"N");
        save(body("0"),409,"INVALID_PROJECT_STATUS");noWrites();
    }
    @Test void elapsedEndDateIsReadOnlyWithoutReadSideUpdate() throws Exception {
        row=new Stored(row.id(),row.contractType(),"ING",LocalDate.of(2026,6,30),row.contractAmount(),row.budgetAmount(),"N");
        mvc.perform(get("/api/projects/BUDGET-1/budget")).andExpect(status().isOk());
        save(body("0"),409,"INVALID_PROJECT_STATUS");noWrites();
    }
    @Test void previousYearIsConflict() throws Exception { save(body("0").put("year",2025),409,"READ_ONLY_YEAR");noWrites(); }
    @ParameterizedTest @ValueSource(ints={2024,2028})
    void outsideAllowedYearsIsInvalid(int year) throws Exception { save(body("0").put("year",year),400,"INVALID_INPUT");noWrites(); }
    @Test void nextYearIsAllowed() throws Exception { save(body("0").put("year",2027),200,null); }
    @Test void staleRevisionRollsBackBeforeAnyMutation() throws Exception { save(body("0").put("expectedRevision","stale"),409,"STALE_WRITE");noWrites();verify(manager).rollback(any()); }
    @Test void childChangeInvalidatesBudgetRevision() throws Exception {
        ObjectNode body=body("0");childState="new-plan";save(body,409,"STALE_WRITE");noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"-0.1","100.1","absent"})
    void unavailableRateNeverFabricatesPreviewOrBlocksManual(String value) throws Exception {
        when(mapper.operatingValue("TGT_MRGN_RT","202607")).thenReturn(value.equals("absent")?null:new BigDecimal(value));
        mvc.perform(get("/api/projects/BUDGET-1/budget")).andExpect(status().isOk()).andExpect(jsonPath("$.data.autoPreviewAmount").isEmpty())
            .andExpect(jsonPath("$.data.budgetAmount").value("80"));
        save(body("0"),200,null);
    }
    @Test void sequenceExhaustionPrecedesBusinessMutation() throws Exception {
        when(mapper.greatestHistorySn(anyString())).thenReturn(99999);save(body("0"),409,"SERIAL_LIMIT");noWrites();
    }
    @Test void auditFailureRollsBackAndDoesNotInvalidateOrLeak() throws Exception {
        doThrow(new IllegalStateException("SELECT private sql stack")).when(history).insert(any());
        mvc.perform(put("/api/projects/BUDGET-1/budget").contentType("application/json").content(body("0").toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private sql"))));
        verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
    @Test void projectLockIsAcquiredBeforeSnapshotAndDomainRead() throws Exception {
        save(body("0"),200,null);
        var order=inOrder(locks,mapper);
        order.verify(locks).lock("PROJECT:BUDGET-1");order.verify(locks).lock("operating-standards:CASH_BASE_YM");
        order.verify(locks).lock("operating-standards:TGT_MRGN_RT");order.verify(mapper).snapshot("BUDGET-1");order.verify(mapper).find("BUDGET-1");
    }
}
