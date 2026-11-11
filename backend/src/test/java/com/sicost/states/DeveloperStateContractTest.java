package com.sicost.states;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import static com.sicost.states.DeveloperStateDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeveloperStateController.class)
@ContextConfiguration(classes=DeveloperStateController.class)
@Import({DeveloperStateService.class,DeveloperStateRepository.class,DeveloperAggregateRevision.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DeveloperStateContractTest {
    static final String ID="DEV-000901",URL="/api/developers/"+ID+"/states";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DeveloperAggregateRevision revisions;
    @MockBean DeveloperStateMapper mapper;
    @MockBean DeveloperRevisionMapper snapshots;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager manager;
    Developer developer;
    List<Stored> rows;
    int greatest;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        developer=new Developer(ID,LocalDate.parse("2026-01-01"),null,"IDLE","Y");rows=new ArrayList<>();greatest=0;
        when(mapper.developer(ID)).thenAnswer(i->developer);
        when(mapper.states(ID)).thenAnswer(i->List.copyOf(rows));
        when(mapper.closedMonths()).thenReturn(List.of());when(mapper.cashBaseMonth(anyString())).thenReturn("202601");
        when(mapper.actorName("admin")).thenReturn("임시 관리자");
        when(mapper.assignments(ID)).thenReturn(List.of());
        when(mapper.greatestSerial(ID)).thenAnswer(i->greatest);
        when(snapshots.developer(eq(ID),anyBoolean())).thenAnswer(i->json.writeValueAsString(developer));
        when(snapshots.states(eq(ID),anyBoolean())).thenAnswer(i->rows.stream().map(Object::toString).toList());
        when(snapshots.prices(eq(ID),anyBoolean())).thenReturn(List.of());when(snapshots.skills(eq(ID),anyBoolean())).thenReturn(List.of());
        when(mapper.insert(any())).thenAnswer(i->{Mutation m=i.getArgument(0);rows.add(m.row());greatest=m.row().sn();return 1;});
        when(mapper.update(any())).thenAnswer(i->{Mutation m=i.getArgument(0);rows.removeIf(r->r.sn()==m.row().sn());rows.add(m.row());return 1;});
        when(mapper.delete(eq(ID),anyInt())).thenAnswer(i->{rows.removeIf(r->r.sn()==(int)i.getArgument(1));return 1;});
        when(mapper.updateDeveloper(any())).thenAnswer(i->{DeveloperMutation m=i.getArgument(0);developer=new Developer(ID,developer.hireDate(),m.retireDate(),m.status(),m.useYn());return 1;});
    }
    Stored row(int sn,String state,String start,String end,Integer link,String original) {
        return new Stored(sn,state,LocalDate.parse(start),end==null?null:LocalDate.parse(end),null,link,original==null?null:LocalDate.parse(original),"admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null);
    }
    void leave(String end){rows.add(row(1,"LEAVE","2026-06-01",end,null,null));greatest=1;developer=new Developer(ID,developer.hireDate(),null,"LEAVE","Y");}
    String revision(){return revisions.revision(ID,false);}
    ObjectNode command(String state,String start){return json.createObjectNode().put("state",state).put("startDate",start).put("expectedRevision",revision());}
    ObjectNode end(String date){ObjectNode b=json.createObjectNode().put("expectedRevision",revision());if(date==null)b.putNull("endDate");else b.put("endDate",date);return b;}
    ObjectNode cancel(){return json.createObjectNode().put("reason","취소 사유").put("expectedRevision",revision());}
    ResultActions create(ObjectNode b)throws Exception{return mvc.perform(post(URL).contentType("application/json").content(b.toString()));}
    ResultActions patch(ObjectNode b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(URL+"/1/end").contentType("application/json").content(b.toString()));}
    ResultActions cancel(int sn,ObjectNode b)throws Exception{return mvc.perform(post(URL+"/"+sn+"/cancel").contentType("application/json").content(b.toString()));}
    void noWrites(){verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).updateDeveloper(any());verify(mapper,never()).delete(anyString(),anyInt());verifyNoInteractions(history);}
    @Test void getPublicSortedHistoryAndDisplayOnlyHireDate()throws Exception {
        leave(null);rows.add(row(2,"LEAVE","2026-08-01","2026-08-10",null,null));
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.data.hireDate").value("2026-01-01"))
            .andExpect(jsonPath("$.data.items[0].sn").value(2)).andExpect(jsonPath("$.data.items[1].endDate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[1].actorName").value("임시 관리자")).andExpect(jsonPath("$.data.items[1].allowedActions[0]").value("EDIT_END"))
            .andExpect(jsonPath("$.data.revision").isString());verify(mapper,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"actor","page","size","state","sort"}) void getRejectsEveryQuery(String key)throws Exception {
        mvc.perform(get(URL).param(key,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void missingDeveloperAndMissingChildAre404()throws Exception {
        mvc.perform(get("/api/developers/DEV-000999/states")).andExpect(status().isNotFound());
        patch(end(null)).andExpect(status().isNotFound());noWrites();
    }
    @Test void leaveWithActOverlapIsCreatedWithCountAndPeriodWarning()throws Exception {
        when(mapper.assignments(ID)).thenReturn(List.of(new Assignment("P-1","프로젝트",LocalDate.parse("2026-06-01"),LocalDate.parse("2026-07-01"))));
        create(command("LEAVE","2026-06-15").putNull("endDate").put("reason"," 사유 "))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sn").value(1)).andExpect(jsonPath("$.data.warnings[0].message").value(org.hamcrest.Matchers.containsString("1건")));
        assertThat(rows.get(0).reason()).isEqualTo("사유");assertThat(rows.get(0).regUserId()).isEqualTo("admin");
        verify(history).insert(argThat(e->e.tableName().equals("TB_HR_DEV_STAT")&&e.targetKey().equals(ID+":1")&&e.type().equals("NEW")&&e.userId().equals("admin")));
        verify(manager).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","modDttm","closeLeaveSn","originalLeaveEndDate"}) void allCommandsRejectSystemInjection(String key)throws Exception {
        create(command("LEAVE","2026-06-15").put(key,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        patch(end(null).put(key,"other")).andExpect(status().isBadRequest());cancel(1,cancel().put(key,"other")).andExpect(status().isBadRequest());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"WORK","RETIRE"}) void onlyLeaveAcceptsEvenNullEndDate(String state)throws Exception {create(command(state,"2026-06-15").putNull("endDate")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-02-30","0000-01-01","10000-01-01","2026-2-01"}) void rejectsInvalidCalendarDate(String date)throws Exception {
        create(command("LEAVE",date)).andExpect(status().isBadRequest());patch(end(date)).andExpect(status().isBadRequest());noWrites();
    }
    @Test void hireBoundaryAndReversedPeriodAre400()throws Exception {
        create(command("LEAVE","2025-12-31")).andExpect(status().isBadRequest());
        create(command("LEAVE","2026-06-15").put("endDate","2026-06-14")).andExpect(status().isBadRequest());noWrites();
    }
    @Test void patchRequiresExplicitEndDate()throws Exception {patch(json.createObjectNode().put("expectedRevision",revision())).andExpect(status().isBadRequest());noWrites();}
    @Test void reasonLengthBoundaryAndBlankNormalization()throws Exception {
        create(command("LEAVE","2026-06-15").put("reason","가".repeat(301))).andExpect(status().isBadRequest());
        create(command("LEAVE","2026-06-15").put("reason","  ")).andExpect(status().isCreated());assertThat(rows.get(0).reason()).isNull();
    }
    @Test void serialLimitAndCancelledSerialAreNeverReused()throws Exception {
        greatest=99999;create(command("LEAVE","2026-06-15")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));noWrites();
    }
    @Test void workRequiresCurrentLeave()throws Exception {create(command("WORK","2026-06-15")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("휴직 중인 사람만 재직으로 등록할 수 있습니다."));noWrites();}
    @Test void workStartMustExceedLeaveStart()throws Exception {leave(null);create(command("WORK","2026-06-01")).andExpect(status().isConflict());noWrites();}
    @Test void workCannotExceedOriginalEndPlusOne()throws Exception {leave("2026-06-20");create(command("WORK","2026-06-22")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("휴직 종료일 다음 날보다 늦습니다. 종료일 수정을 쓰세요."));noWrites();}
    @Test void workAtEndPlusOneClosesAndKeepsPerEventOriginalEndAndDefaultReason()throws Exception {
        leave("2026-06-20");create(command("WORK","2026-06-21")).andExpect(status().isCreated());
        Stored work=rows.stream().filter(r->r.state().equals("WORK")).findFirst().orElseThrow();
        assertThat(work.closeLeaveSn()).isEqualTo(1);assertThat(work.originalLeaveEndDate()).isEqualTo(LocalDate.parse("2026-06-20"));assertThat(work.reason()).isEqualTo("휴직 복귀");
        create(command("WORK","2026-06-19")).andExpect(status().isConflict());
    }
    @Test void workCancelRestoresNullAndSerialAdvancesAfterDeletion()throws Exception {
        leave(null);create(command("WORK","2026-06-15")).andExpect(status().isCreated());
        assertThat(rows.stream().filter(r->r.sn()==1).findFirst().orElseThrow().endDate()).isEqualTo(LocalDate.parse("2026-06-14"));
        cancel(2,cancel()).andExpect(status().isOk());assertThat(rows).hasSize(1);assertThat(rows.get(0).endDate()).isNull();
        create(command("WORK","2026-06-16")).andExpect(status().isCreated()).andExpect(jsonPath("$.data.sn").value(3));
        verify(history).insert(argThat(e->"재직 취소".equals(e.item())&&e.type().equals("UPD")));
    }
    @Test void endNullAndDateAreAllowedAndSameValueWritesNothing()throws Exception {
        leave("2026-06-20");patch(end("2026-06-20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
        patch(end(null)).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(rows.get(0).endDate()).isNull();verify(history).insert(argThat(e->e.item().equals("END_DT")&&e.before().equals("2026-06-20")&&e.after()==null));
    }
    @Test void endedLeaveCannotBeEdited()throws Exception {leave("2026-06-14");patch(end(null)).andExpect(status().isConflict());noWrites();}
    @Test void workClosedLeaveCannotBeEdited()throws Exception {leave("2026-06-30");rows.add(row(2,"WORK","2026-07-01",null,1,null));patch(end(null)).andExpect(status().isConflict());noWrites();}
    @Test void overlappingLeaveBlocksCreateAndEdit()throws Exception {
        leave("2026-06-20");create(command("LEAVE","2026-06-20")).andExpect(status().isConflict());
        rows.add(row(2,"LEAVE","2026-07-01","2026-07-10",null,null));patch(end("2026-07-01")).andExpect(status().isConflict());noWrites();
    }
    @Test void onlyChangedEndIntervalChecksClosedMonth()throws Exception {
        leave("2026-07-31");when(mapper.closedMonths()).thenReturn(List.of("202606"));
        patch(end("2026-08-01")).andExpect(status().isOk());
    }
    @Test void changedIntervalClosedBlocksEverything()throws Exception {leave("2026-06-30");when(mapper.closedMonths()).thenReturn(List.of("202607"));patch(end("2026-07-01")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void cashBaseBoundaryBlocksOlderStateButAllowsBoundaryMonth()throws Exception {
        when(mapper.cashBaseMonth(anyString())).thenReturn("202606");create(command("LEAVE","2026-05-31")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();
        create(command("LEAVE","2026-06-01").put("endDate","2026-06-30")).andExpect(status().isCreated());
    }
    @Test void retireDuplicateIs409()throws Exception {rows.add(row(1,"RETIRE","2026-07-01",null,null,null));greatest=1;create(command("RETIRE","2026-08-01")).andExpect(status().isConflict());noWrites();}
    @Test void retireBlocksActAfterButAllowsSameDayEnd()throws Exception {
        when(mapper.assignments(ID)).thenReturn(List.of(new Assignment("P-1","프로젝트",LocalDate.parse("2026-06-01"),LocalDate.parse("2026-06-16"))));
        create(command("RETIRE","2026-06-15")).andExpect(status().isConflict()).andExpect(jsonPath("$.details.referenceCount").value(1));noWrites();
        when(mapper.assignments(ID)).thenReturn(List.of(new Assignment("P-1","프로젝트",LocalDate.parse("2026-06-01"),LocalDate.parse("2026-06-15"))));
        create(command("RETIRE","2026-06-15")).andExpect(status().isCreated());assertThat(developer.retireDate()).isEqualTo(LocalDate.parse("2026-06-15"));assertThat(developer.useYn()).isEqualTo("Y");
    }
    @Test void retireClosesLeaveAndCopiesBaseDateAndStopsOnlyFollowingDay()throws Exception {
        leave(null);create(command("RETIRE","2026-06-14")).andExpect(status().isCreated());
        assertThat(developer.retireDate()).isEqualTo(LocalDate.parse("2026-06-14"));assertThat(developer.useYn()).isEqualTo("N");assertThat(developer.status()).isEqualTo("RETIRE");
        assertThat(rows.stream().filter(r->r.sn()==1).findFirst().orElseThrow().endDate()).isEqualTo(LocalDate.parse("2026-06-13"));
        create(command("LEAVE","2026-06-16")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RETIRED_DEVELOPER"));
        cancel(2,cancel()).andExpect(status().isOk());assertThat(developer.retireDate()).isNull();assertThat(developer.useYn()).isEqualTo("Y");assertThat(rows.get(0).endDate()).isNull();
    }
    @Test void retirementRejectsFollowingLeaveAndWork()throws Exception {
        rows.add(row(1,"LEAVE","2026-08-01","2026-08-10",null,null));greatest=1;
        create(command("RETIRE","2026-07-31")).andExpect(status().isConflict());noWrites();
    }
    @Test void leaveCannotEndAtOrAfterRetirement()throws Exception {
        leave("2026-06-20");rows.add(row(2,"RETIRE","2026-07-01",null,null,null));greatest=2;developer=new Developer(ID,developer.hireDate(),LocalDate.parse("2026-07-01"),"LEAVE","Y");
        patch(end("2026-07-01")).andExpect(status().isConflict());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={""," ","LONG"}) void retireCancelRequiresBoundedReason(String reason)throws Exception {
        rows.add(row(1,"RETIRE","2026-06-14",null,null,null));greatest=1;
        cancel(1,cancel().put("reason",reason.equals("LONG")?"가".repeat(301):reason)).andExpect(status().isBadRequest());noWrites();
    }
    @Test void cancelLeaveIsForbiddenWithoutDeletion()throws Exception {leave(null);cancel(1,cancel()).andExpect(status().isConflict());noWrites();}
    @Test void workCancelClosedMonthIsBlocked()throws Exception {
        leave("2026-06-14");rows.add(row(2,"WORK","2026-06-15",null,1,null));when(mapper.closedMonths()).thenReturn(List.of("202606"));
        cancel(2,cancel()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();
    }
    @Test void retireCancelClosedMessageContainsMonth()throws Exception {
        rows.add(row(1,"RETIRE","2026-05-31",null,null,null));when(mapper.closedMonths()).thenReturn(List.of("202605"));
        cancel(1,cancel()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("퇴사한 달(2026-05)이 마감되어 취소할 수 없습니다. 다시 쓰려면 새로 등록하세요."));noWrites();
    }
    @Test void cancelRestoringOverlapRollsBackBeforeAnyMutation()throws Exception {
        leave("2026-06-14");rows.add(row(2,"WORK","2026-06-15",null,1,null));rows.add(row(3,"LEAVE","2026-07-01",null,null,null));
        cancel(2,cancel()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_HISTORY"));noWrites();verify(manager).rollback(any());
    }
    @Test void workCancelWithRemainingRetireClipsRestoredEnd()throws Exception {
        leave("2026-06-14");rows.add(row(2,"WORK","2026-06-15",null,1,null));rows.add(row(3,"RETIRE","2026-07-01",null,null,null));
        developer=new Developer(ID,developer.hireDate(),LocalDate.parse("2026-07-01"),"LEAVE","Y");
        cancel(2,cancel()).andExpect(status().isOk());assertThat(rows.stream().filter(r->r.sn()==1).findFirst().orElseThrow().endDate()).isEqualTo(LocalDate.parse("2026-06-30"));
    }
    @Test void staleWinsBeforeNoOpAndTouchesNoRows()throws Exception {
        leave(null);patch(end(null).put("expectedRevision","stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void aggregateRevisionChangesForEveryChildEvenWithUnchangedParentTimes() {
        String initial=revision();when(snapshots.prices(eq(ID),anyBoolean())).thenReturn(List.of("changed price"));assertThat(revision()).isNotEqualTo(initial);
        initial=revision();when(snapshots.skills(eq(ID),anyBoolean())).thenReturn(List.of("changed skill"));assertThat(revision()).isNotEqualTo(initial);
        initial=revision();leave(null);assertThat(revision()).isNotEqualTo(initial);
    }
    @ParameterizedTest @ValueSource(strings={"NULL","2026-08-31"}) void cancellationRestoresReclosedLinkAndOriginalForEitherOrder(String original)throws Exception {
        String value=original.equals("NULL")?null:original;
        leave("2026-06-30");rows.add(row(2,"WORK","2026-06-15",null,1,value));rows.add(row(3,"RETIRE","2026-07-01",null,1,"2026-06-14"));
        developer=new Developer(ID,developer.hireDate(),LocalDate.parse("2026-07-01"),"LEAVE","Y");
        cancel(2,cancel()).andExpect(status().isOk());
        Stored retirement=rows.stream().filter(r->r.sn()==3).findFirst().orElseThrow();
        assertThat(retirement.originalLeaveEndDate()).isEqualTo(value==null?null:LocalDate.parse(value));
        cancel(3,cancel()).andExpect(status().isOk());
        assertThat(rows).hasSize(1);assertThat(rows.get(0).endDate()).isEqualTo(value==null?null:LocalDate.parse(value));
        rows.clear();leave("2026-06-30");rows.add(row(2,"WORK","2026-06-15",null,1,value));rows.add(row(3,"RETIRE","2026-07-01",null,1,"2026-06-14"));
        developer=new Developer(ID,developer.hireDate(),LocalDate.parse("2026-07-01"),"LEAVE","Y");
        cancel(3,cancel()).andExpect(status().isOk());cancel(2,cancel()).andExpect(status().isOk());
        assertThat(rows).hasSize(1);assertThat(rows.get(0).endDate()).isEqualTo(value==null?null:LocalDate.parse(value));
    }
    @Test void repeatedReadsDoNotResumeManualStopOrWriteHistory()throws Exception {
        developer=new Developer(ID,developer.hireDate(),null,"IDLE","N");mvc.perform(get(URL)).andExpect(status().isOk());mvc.perform(get(URL)).andExpect(status().isOk());noWrites();
    }
    @Test void readRecalculationUsesSeparateWriteTransactionAndActualChangeOnly()throws Exception {
        leave(null);developer=new Developer(ID,developer.hireDate(),null,"IDLE","N");
        mvc.perform(get(URL)).andExpect(status().isOk());mvc.perform(get(URL)).andExpect(status().isOk());
        verify(mapper,times(1)).updateDeveloper(any());assertThat(developer.useYn()).isEqualTo("N");verify(history,times(1)).insert(any());
    }
    @Test void errorsReturnSafeRequestIdNotClientHeaderInjection()throws Exception {
        mvc.perform(get(URL).param("actor","other").header("X-Request-Id","bad\r\nheader"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isString()).andExpect(header().string("X-Request-Id",org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\r"))));noWrites();
    }
}
