package com.sicost.expenses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.expenses.ProjectExpenseDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectExpenseController.class)
@ContextConfiguration(classes=ProjectExpenseController.class)
@Import({ProjectExpenseService.class,ProjectExpenseRepository.class,ProjectBudgetRepository.class,WriteCoordinator.class,
    RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectExpenseContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectExpenseMapper mapper;
    @MockBean ProjectBudgetMapper projects;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ProjectState project;
    List<Stored> rows;
    List<ChangeHistoryEntry> audits;
    String sibling;
    @BeforeEach void setup() {
        project=new ProjectState("TK","ING");rows=new ArrayList<>();audits=new ArrayList<>();sibling="body+parts+plans+stages+pm";
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(projects.snapshot(anyString())).thenAnswer(call->snapshot());
        when(mapper.project(anyString())).thenAnswer(call->project);
        when(shared.cashBaseMonth()).thenReturn("202501");
        when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.activeItem("TRIP")).thenReturn(true);
        when(mapper.list(anyString())).thenAnswer(call->List.copyOf(rows));
        when(mapper.find(any())).thenAnswer(call->{ExpenseKey k=call.getArgument(0);return rows.stream().filter(r->r.month().equals(k.month())&&r.sn()==k.sn()).findFirst().orElse(null);});
        when(mapper.greatestSerial(anyString(),anyString())).thenAnswer(call->{String month=call.getArgument(1);return Math.max(rows.stream().filter(r->r.month().equals(month)).mapToInt(Stored::sn).max().orElse(0),audits.stream().map(ChangeHistoryEntry::targetKey).filter(k->k.startsWith("EXPENSE-1:"+month+":" )).mapToInt(k->Integer.parseInt(k.substring(k.lastIndexOf(':')+1))).max().orElse(0));});
        when(mapper.insert(any())).thenAnswer(call->{ExpenseWrite w=call.getArgument(0);rows.add(0,new Stored(w.key().month(),w.key().sn(),w.itemCode(),"출장비",w.amount(),w.memo(),w.now()));return 1;});
        when(mapper.delete(any())).thenAnswer(call->{ExpenseKey k=call.getArgument(0);return rows.removeIf(r->r.month().equals(k.month())&&r.sn()==k.sn())?1:0;});
        doAnswer(call->{audits.add(call.getArgument(0));return 1;}).when(history).insert(any());
    }
    String snapshot() { return project==null?null:project+"/"+sibling+"/"+rows; }
    ObjectNode body() {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot())).put("expenseMonth","2026-07").put("itemCode","TRIP").put("amount","1").put("memo","출장 메모");}
    ObjectNode deletion() {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot()));}
    ResultActions create(ObjectNode b) throws Exception {return mvc.perform(post("/api/projects/EXPENSE-1/expenses").contentType("application/json").content(b.toString()));}
    ResultActions remove(String month,String sn,ObjectNode b) throws Exception {return mvc.perform(delete("/api/projects/EXPENSE-1/expenses/"+month+"/"+sn).contentType("application/json").content(b.toString()));}
    void invalid(ResultActions result,String field) throws Exception {result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).delete(any());verifyNoInteractions(history,invalidation);}
    @Test void publicEmptyGetReturnsRealEmptyListAndDoesNotWrite() throws Exception {
        mvc.perform(get("/api/projects/EXPENSE-1/expenses")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.totalAmount").value("0")).andExpect(header().exists("X-Request-Id"));
        verify(users).currentUser();noWrites();
    }
    @Test void publicOneWonCreateWithoutPmOrAssignmentsReturnsFinalValueAndExactAudit() throws Exception {
        create(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.items[0].amount").value("1")).andExpect(jsonPath("$.data.value.items[0].expenseMonth").value("2026-07"));
        mvc.perform(get("/api/projects/EXPENSE-1/expenses")).andExpect(jsonPath("$.data.totalAmount").value("1"))
            .andExpect(jsonPath("$.data.items[0].memo").value("출장 메모")).andExpect(jsonPath("$.data.items[0].itemName").value("출장비"))
            .andExpect(jsonPath("$.data.items[0].registeredAt").value("2026-07-01T00:00"))
            .andExpect(jsonPath("$.data.items[0].revision").value(revisions.revision(snapshot())));
        assertThat(audits).hasSize(4).allSatisfy(a->{assertThat(a.tableName()).isEqualTo("TB_PJ_DRCT_EXPS");assertThat(a.targetKey()).isEqualTo("EXPENSE-1:202607:1");assertThat(a.type()).isEqualTo("NEW");assertThat(a.before()).isNull();assertThat(a.userId()).isEqualTo("admin");});
        assertThat(audits).extracting(ChangeHistoryEntry::after).containsExactly("2026-07","TRIP","1","출장 메모");
        verify(invalidation).afterCommit("PROJECT:EXPENSE-1");
    }
    @Test void endedContractStillAcceptsUnclosedOneOffExpense() throws Exception {project=new ProjectState("MO","END");create(body()).andExpect(status().isOk());}
    @Test void publicConfirmedDeleteKeepsOtherMonthAndDoesNotReuseAuditedSerial() throws Exception {
        create(body()).andExpect(status().isOk());create(body().put("expenseMonth","2026-08")).andExpect(status().isOk());
        remove("2026-07","1",deletion()).andExpect(status().isOk()).andExpect(jsonPath("$.data.value.items.length()").value(1));
        assertThat(rows).extracting(Stored::month).containsExactly("202608");
        assertThat(audits.stream().filter(a->a.type().equals("DEL")).toList()).extracting(ChangeHistoryEntry::before).containsExactly("2026-07","TRIP","1","출장 메모");
        create(body()).andExpect(status().isOk());assertThat(rows.get(0).sn()).isEqualTo(2);
    }
    @Test void missingDeleteRevisionNeverDeletesOriginal() throws Exception {
        rows.add(new Stored("202607",1,"TRIP","출장비",BigDecimal.ONE,null,LocalDateTime.of(2026,7,1,0,0)));
        var b=deletion();b.remove("expectedRevision");invalid(remove("2026-07","1",b),"expectedRevision");assertThat(rows).hasSize(1);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.1","1e3","+1","1%","","1000000000000000"})
    void invalidAmountIsDistinctFieldError(String value) throws Exception {invalid(create(body().put("amount",value)),"amount");noWrites();}
    @Test void fifteenDigitUnicodeFormattedAmountIsExact() throws Exception {create(body().put("amount","999,999,999,999,999\u00a0원")).andExpect(status().isOk()).andExpect(jsonPath("$.data.value.totalAmount").value("999999999999999"));}
    @Test void memoOver500IsRejected() throws Exception {invalid(create(body().put("memo","가".repeat(501))),"memo");noWrites();}
    @Test void memo500IsNotCombinedWithOtherAuditFieldsOrTruncated() throws Exception {create(body().put("memo","가".repeat(500))).andExpect(status().isOk());assertThat(audits.get(3).after()).hasSize(500);}
    @Test void blankMemoNormalizesToNull() throws Exception {create(body().put("memo"," \u00a0 ")).andExpect(status().isOk());assertThat(rows.get(0).memo()).isNull();}
    @Test void inactiveItemCannotBeCreated() throws Exception {invalid(create(body().put("itemCode","STOP")),"itemCode");noWrites();}
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision","expenseMonth","itemCode","amount"})
    void requiredCreateFieldsAreRejected(String field) throws Exception {var b=body();b.remove(field);invalid(create(b),field);noWrites();}
    @Test void numericAmountRejectedRatherThanCoerced() throws Exception {invalid(create(body().put("amount",1)),"amount");noWrites();}
    @Test void stringYearRejectedRatherThanCoerced() throws Exception {invalid(create(body().put("year","2026")),"year");noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","0000-01","202607","2026-7"})
    void invalidMonthRejected(String month) throws Exception {invalid(create(body().put("expenseMonth",month)),"expenseMonth");invalid(remove(month,"1",deletion()),"expenseMonth");noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","100000","abc","1.5","-1"})
    void invalidSerialRejected(String serial) throws Exception {invalid(remove("2026-07",serial,deletion()),"expenseSn");noWrites();}
    @Test void unknownQueryIsRejectedOnEveryOperation() throws Exception {
        invalid(mvc.perform(get("/api/projects/EXPENSE-1/expenses").param("month","2026-07")),"month");
        invalid(mvc.perform(post("/api/projects/EXPENSE-1/expenses").param("year","2026").contentType("application/json").content(body().toString())),"year");
        invalid(mvc.perform(delete("/api/projects/EXPENSE-1/expenses/2026-07/1").param("force","true").contentType("application/json").content(deletion().toString())),"force");noWrites();
    }
    @Test void injectedJsonFieldsRejectedOnBothWrites() throws Exception {invalid(create(body().put("regUserId","someone")),"regUserId");invalid(remove("2026-07","1",deletion().put("force",true)),"force");noWrites();}
    @Test void malformedJsonPreservesSafeEnvelopeAndRequestId() throws Exception {
        mvc.perform(post("/api/projects/EXPENSE-1/expenses").header("X-Request-Id","expense-json").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("body"))
            .andExpect(jsonPath("$.requestId").value("expense-json"));noWrites();
    }
    @Test void idLengthValidatedBeforeDatabase() throws Exception {invalid(mvc.perform(get("/api/projects/"+"x".repeat(21)+"/expenses")),"id");verifyNoInteractions(projects);noWrites();}
    @Test void missingProjectReturns404OnAllOperations() throws Exception {
        var b=body();var d=deletion();project=null;
        mvc.perform(get("/api/projects/missing/expenses")).andExpect(status().isNotFound());
        create(b).andExpect(status().isNotFound());remove("2026-07","1",d).andExpect(status().isNotFound());noWrites();
    }
    @Test void missingExpenseReturns404() throws Exception {remove("2026-07","1",deletion()).andExpect(status().isNotFound());noWrites();}
    @Test void closedMonthCreateLeavesNoRowsOrAudits() throws Exception {
        when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));
        create(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH")).andExpect(jsonPath("$.details.blockingMonths[0]").value("2026-07"));noWrites();
    }
    @Test void closeAfterReadBlocksDeleteEvenWithUnchangedProjectRevision() throws Exception {
        rows.add(new Stored("202607",1,"TRIP","출장비",BigDecimal.ONE,"memo",LocalDateTime.of(2026,7,1,0,0)));var b=deletion();
        when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));
        remove("2026-07","1",b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));assertThat(rows).hasSize(1);noWrites();
    }
    @Test void migratedMonthBlocksBothWrites() throws Exception {when(shared.cashBaseMonth()).thenReturn("202607");create(body().put("expenseMonth","2026-06")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));remove("2026-06","1",deletion()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"IN","LOSE"})
    void internalAndLostBlockBothWrites(String state) throws Exception {project=state.equals("IN")?new ProjectState("IN","ING"):new ProjectState("TK","LOSE");create(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));remove("2026-07","1",deletion()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();}
    @Test void pastYearBlocksBothWrites() throws Exception {create(body().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));remove("2026-07","1",deletion().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @ParameterizedTest @ValueSource(ints={2024,2028})
    void outsideAllowedYearIs400(int year) throws Exception {invalid(create(body().put("year",year)),"year");invalid(remove("2026-07","1",deletion().put("year",year)),"year");noWrites();}
    @Test void staleSiblingChangeBlocksBeforeWrites() throws Exception {var b=body();var d=deletion();sibling="changed-pm";create(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));remove("2026-07","1",d).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void exhaustedSerialDoesNotInsert() throws Exception {when(mapper.greatestSerial(anyString(),anyString())).thenReturn(99999);create(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));noWrites();}
    @Test void locksPrecedeSnapshotAndLatestCloseGuard() throws Exception {create(body()).andExpect(status().isOk());var order=inOrder(locks,projects,mapper,shared);order.verify(locks).lock("PROJECT:EXPENSE-1");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(projects).snapshot("EXPENSE-1");order.verify(mapper).project("EXPENSE-1");order.verify(mapper).lockClose();order.verify(shared).cashBaseMonth();}
    @Test void auditFailureRollsBackAndNeverInvalidatesOrLeaksSql() throws Exception {doThrow(new IllegalStateException("SQL private stack")).when(history).insert(any());create(body()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL private"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
}
