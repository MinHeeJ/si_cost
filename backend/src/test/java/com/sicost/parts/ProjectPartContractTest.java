package com.sicost.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.parts.ProjectPartDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectPartController.class)
@ContextConfiguration(classes=ProjectPartController.class)
@Import({ProjectPartService.class,ProjectPartRepository.class,ProjectBudgetRepository.class,WriteCoordinator.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectPartContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired ProjectPartService service;
    @MockBean ProjectPartMapper mapper;
    @MockBean ProjectBudgetMapper aggregate;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper calendarLocks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ProjectSource project;
    List<PartSource> rows;
    List<PartHistory> events;
    String otherChildren;
    @BeforeEach void setup() {
        project=new ProjectSource("PARTS-1","TK","ING",LocalDate.of(2026,1,1),LocalDate.of(2027,12,31));
        rows=new ArrayList<>(List.of(row("202601","A"),row("202603","B"),row("202608","C"),row("202609","D")));
        events=new ArrayList<>();otherChildren="body+plans+expenses+distribution+pm";
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(mapper.find(anyString())).thenAnswer(call->project);
        when(mapper.parts(anyString())).thenAnswer(call->List.copyOf(rows));
        when(mapper.protectedMonths()).thenReturn(List.of("202603"));when(mapper.lastClose()).thenReturn("202603");
        when(aggregate.snapshot(anyString())).thenAnswer(call->snapshot());
        when(aggregate.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202601"));
        when(mapper.reference(anyString())).thenAnswer(call->new PartReference(call.getArgument(0),"파트 "+call.getArgument(0),"Y"));
        when(mapper.deleteReservation(anyString(),anyString())).thenAnswer(call->rows.removeIf(r->r.month().equals(call.getArgument(1)))?1:0);
        when(mapper.insert(any())).thenAnswer(call->{PartInsert p=call.getArgument(0);rows.add(new PartSource(p.month(),p.partId(),"파트 "+p.partId(),p.reason(),p.actor()));rows.sort(Comparator.comparing(PartSource::month));return 1;});
        when(mapper.insertHistory(any())).thenAnswer(call->{events.add(call.getArgument(0));return 1;});
    }
    PartSource row(String month,String part) {return new PartSource(month,part,"파트 "+part,null,"admin");}
    String snapshot() {return project==null?null:project.toString()+rows+otherChildren;}
    ObjectNode body(String part,String month) {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot())).put("partId",part).put("effectiveMonth",month).put("reason","  원문 사유  ");}
    org.springframework.test.web.servlet.ResultActions save(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/projects/PARTS-1/parts").contentType("application/json").content(body.toString()));
    }
    void noWrites() {verify(mapper,never()).deleteReservation(anyString(),anyString());verify(mapper,never()).insert(any());verify(mapper,never()).insertHistory(any());verifyNoInteractions(audit,invalidation);}
    @Test void publicGetReturnsTimelineReplacementBaseAndBoundsWithoutWrites() throws Exception {
        String before=snapshot();
        mvc.perform(get("/api/projects/PARTS-1/parts").header("X-Request-Id","parts-read"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","parts-read"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items.length()").value(4)).andExpect(jsonPath("$.data.items[2].scheduled").value(true))
            .andExpect(jsonPath("$.data.retainedBasePart.partId").value("B"))
            .andExpect(jsonPath("$.data.replacementTargets.length()").value(2))
            .andExpect(jsonPath("$.data.minimumMonth").value("2026-04")).andExpect(jsonPath("$.data.maximumMonth").value("2027-12"))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision(before)));
        assertThat(snapshot()).isEqualTo(before);verify(users).currentUser();noWrites();
    }
    @Test void replacementPreservesFirstAndClosedRowsAndReturnsFinalValueAndRevision() throws Exception {
        save(body("E","2026-07")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.items.length()").value(3)).andExpect(jsonPath("$.data.value.items[2].partId").value("E"));
        assertThat(rows).extracting(PartSource::partId).containsExactly("A","B","E");
        verify(mapper).insert(argThat(p->p.actor().equals("admin") && p.reason().equals("  원문 사유  ") && p.now().equals(LocalDateTime.of(2026,7,1,0,0))));
        assertThat(events).singleElement().satisfies(h->{assertThat(h.before()).isEqualTo("파트 B");assertThat(h.after()).isEqualTo("파트 E (2026-07부터)");});
        mvc.perform(get("/api/projects/PARTS-1/parts")).andExpect(jsonPath("$.data.revision").value(revisions.revision(snapshot())));
    }
    @Test void cancellationUsesLastProtectedBaseDeletesAllReservationsAndKeepsPastAudit() throws Exception {
        save(body("B","2026-07")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(rows).extracting(PartSource::partId).containsExactly("A","B");verify(mapper,never()).insert(any());
        assertThat(events).singleElement().satisfies(h->{assertThat(h.before()).contains("2026-08","파트 C","2026-09","파트 D");assertThat(h.after()).isEqualTo("변경 취소");});
        verify(audit).insert(argThat(h->h.reason().equals("  원문 사유  ") && h.after().equals("변경 취소") && h.userId().equals("admin")));
        verifyNoMoreInteractions(audit);
    }
    @Test void noReservationsSameBaseIsNoOpWithoutTimestampHistoryOrInvalidation() throws Exception {
        rows.removeIf(r->r.month().compareTo("202603")>0);String before=snapshot();
        save(body("B","2026-07")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision(before)));assertThat(snapshot()).isEqualTo(before);noWrites();
    }
    @Test void noCloseOmitsCloseBoundAndCashBaseWinsAndMigratedRowsRetained() throws Exception {
        when(mapper.lastClose()).thenReturn(null);when(aggregate.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202604"));
        when(mapper.protectedMonths()).thenReturn(List.of("202608"));
        mvc.perform(get("/api/projects/PARTS-1/parts")).andExpect(status().isOk()).andExpect(jsonPath("$.data.minimumMonth").value("2026-04"))
            .andExpect(jsonPath("$.data.retainedBasePart.partId").value("C")).andExpect(jsonPath("$.data.replacementTargets[0].partId").value("D"));noWrites();
    }
    @Test void contractStartWinsMinimum() throws Exception {
        project=new ProjectSource(project.id(),"TK","ING",LocalDate.of(2026,6,1),project.endDate());
        mvc.perform(get("/api/projects/PARTS-1/parts")).andExpect(jsonPath("$.data.minimumMonth").value("2026-06"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"2026-01","2026-02","2026-03"})
    void firstSameMonthAndBeforeMinimumAreRejectedAtomically(String month) throws Exception {
        String before=snapshot();save(body("E",month)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));assertThat(snapshot()).isEqualTo(before);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"2026-07","2026-08"})
    void retainedLastMonthOrEarlierIsRejectedEvenWhenAfterClose(String month) throws Exception {
        when(mapper.protectedMonths()).thenReturn(List.of("202603","202608"));
        save(body("E",month)).andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("effectiveMonth"));noWrites();
    }
    @Test void sameFirstMonthRejectedEvenWithoutClose() throws Exception {
        when(mapper.lastClose()).thenReturn(null);when(mapper.protectedMonths()).thenReturn(List.of());
        save(body("E","2026-01")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));noWrites();
    }
    @Test void afterContractEndRejected() throws Exception {save(body("E","2028-01")).andExpect(status().isConflict());noWrites();}
    @Test void migratedTargetMonthRejected() throws Exception {when(mapper.protectedMonths()).thenReturn(List.of("202607"));save(body("E","2026-07")).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","0000-01","202607","2026-1","x"})
    void invalidMonthIsFieldError(String month) throws Exception {save(body("E",month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("effectiveMonth"));noWrites();}
    @Test void reasonOver500RejectedBeforeMutation() throws Exception {save(body("E","2026-07").put("reason","가".repeat(501))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reason"));noWrites();}
    @Test void full500ReasonStoredWithoutTruncationOrTrimming() throws Exception {
        String reason=" "+"가".repeat(498)+" ";save(body("E","2026-07").put("reason",reason)).andExpect(status().isOk());
        verify(audit).insert(argThat(h->reason.equals(h.reason())));assertThat(rows.get(2).reason()).isEqualTo(reason);
    }
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision","partId","effectiveMonth"})
    void missingRequiredFieldIsRejected(String field) throws Exception {var b=body("E","2026-07");b.remove(field);save(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor","revision","id","scheduled"})
    void unknownBodyFieldRejected(String field) throws Exception {save(body("E","2026-07").put(field,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void unknownQueryRejectedOnBothOperations() throws Exception {
        mvc.perform(get("/api/projects/PARTS-1/parts").param("month","2026-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        mvc.perform(put("/api/projects/PARTS-1/parts").param("year","2026").contentType("application/json").content(body("E","2026-07").toString())).andExpect(status().isBadRequest());noWrites();
    }
    @Test void invalidTypesAndMalformedJsonRetainEnvelopeAndRequestId() throws Exception {
        save(body("E","2026-07").put("year","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));
        save(body("E","2026-07").put("partId",1)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("partId"));
        mvc.perform(put("/api/projects/PARTS-1/parts").header("X-Request-Id","parts-invalid").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.requestId").value("parts-invalid"));noWrites();
    }
    @Test void emptyPartsReturnEmptyListNotVirtualRows() throws Exception {rows.clear();mvc.perform(get("/api/projects/PARTS-1/parts")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.replacementTargets").isEmpty());noWrites();}
    @Test void unknownProject404OnBothOperations() throws Exception {var b=body("E","2026-07");project=null;mvc.perform(get("/api/projects/unknown/parts")).andExpect(status().isNotFound());save(b).andExpect(status().isNotFound());noWrites();}
    @Test void longIdRejectedBeforeSources() throws Exception {mvc.perform(get("/api/projects/"+"x".repeat(21)+"/parts")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));verifyNoInteractions(mapper);}
    @Test void inactivePartRejected() throws Exception {when(mapper.reference("E")).thenReturn(new PartReference("E","중지","N"));save(body("E","2026-07")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("partId"));noWrites();}
    @Test void nonexistentPartRejected() throws Exception {when(mapper.reference("E")).thenReturn(null);save(body("E","2026-07")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"LOSE","END"})
    void lifecycleLockedIs409NotCaller403(String state) throws Exception {project=new ProjectSource(project.id(),"TK",state,project.startDate(),project.endDate());save(body("E","2026-07")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();}
    @Test void elapsedEndDateRejectedWithoutReadSideUpdate() throws Exception {project=new ProjectSource(project.id(),"TK","ING",project.startDate(),LocalDate.of(2026,6,30));mvc.perform(get("/api/projects/PARTS-1/parts")).andExpect(status().isOk());save(body("E","2026-07")).andExpect(status().isConflict());noWrites();}
    @Test void internalProjectCannotChangeParts() throws Exception {project=new ProjectSource(project.id(),"IN","ING",project.startDate(),project.endDate());save(body("E","2026-07")).andExpect(status().isBadRequest());noWrites();}
    @Test void pastYearIsReadOnly() throws Exception {when(aggregate.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202501"));save(body("E","2026-07").put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @ParameterizedTest @ValueSource(ints={2025,2028})
    void yearOutsideCashBaseThroughNextYearIsInvalid(int year) throws Exception {save(body("E","2026-07").put("year",year)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));noWrites();}
    @Test void nextYearAllowed() throws Exception {save(body("E","2026-07").put("year",2027)).andExpect(status().isOk());}
    @Test void staleChildRevisionRejectedBeforeAnyMutation() throws Exception {var b=body("E","2026-07");otherChildren="concurrent-plan";save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();verify(manager).rollback(any());}
    @Test void sequenceCapCheckedBeforeDeletingReservations() throws Exception {when(aggregate.greatestHistorySn(anyString())).thenReturn(99999);save(body("E","2026-07")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));noWrites();}
    @Test void auditFailureRollsBackAndDoesNotInvalidateOrLeak() throws Exception {doThrow(new IllegalStateException("private SQL stack")).when(audit).insert(any());save(body("E","2026-07")).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void locksPrecedeLatestSnapshotAndDomainChecks() throws Exception {save(body("E","2026-07")).andExpect(status().isOk());var order=inOrder(locks,aggregate,calendarLocks,mapper);order.verify(locks).lock("PROJECT:PARTS-1");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(aggregate).snapshot("PARTS-1");order.verify(calendarLocks).lockClose();order.verify(mapper).find("PARTS-1");}
    @Test void monthServiceUsesNextStartMinusOneAndOpenFinalWithoutConfusingCurrentAndPast() {
        assertThat(service.timeline("PARTS-1")).extracting(PartPeriod::endMonth).containsExactly("2026-02","2026-07","2026-08",null);
        assertThat(service.partAt("PARTS-1","2026-02")).get().extracting(PartPeriod::partId).isEqualTo("A");
        assertThat(service.partAt("PARTS-1","2026-07")).get().extracting(PartPeriod::partId).isEqualTo("B");
        assertThat(service.partAt("PARTS-1","2030-01")).get().extracting(PartPeriod::partId).isEqualTo("D");noWrites();
    }
    @Test void cancellationSummaryFits500AndKeepsCountAndRange() throws Exception {
        rows.subList(2,rows.size()).clear();YearMonth start=YearMonth.of(2026,4);
        for (int i=0;i<20;i++) rows.add(new PartSource(start.plusMonths(i).toString().replace("-",""),"C","가".repeat(30),null,"admin"));
        save(body("B","2026-07")).andExpect(status().isOk());assertThat(events.get(0).before()).hasSizeLessThanOrEqualTo(500).contains("20건","2026-04","2027-11");
    }
}
