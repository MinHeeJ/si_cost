package com.sicost.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
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
import static com.sicost.history.AssignmentHistoryDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssignmentHistoryController.class)
@ContextConfiguration(classes=AssignmentHistoryController.class)
@Import({AssignmentHistoryService.class,WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AssignmentHistoryContractTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired RevisionService revisions;
    @MockBean AssignmentHistoryRepository repository;
    @MockBean CurrentUserProvider users; @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks; @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager; @MockBean Clock clock;
    AssignmentHistoryMapper mapper; AssignmentMonthlyService months; AssignmentMonthlyMapper monthlyMapper;
    Entry original; String snapshot; CorrectionWrite appended;
    Entry entry(int sn,String type,String corrected) {return new Entry(sn,type,LocalDate.of(2026,8,1),"100","200","ETC",null,"200","0.50","N","N",corrected,null,"admin","2026-07-01T09:00:00.123456");}
    @BeforeEach void setup() {
        mapper=mock(AssignmentHistoryMapper.class);months=mock(AssignmentMonthlyService.class);monthlyMapper=mock(AssignmentMonthlyMapper.class);
        when(repository.mapper()).thenReturn(mapper);when(repository.months()).thenReturn(months);when(months.mapper()).thenReturn(monthlyMapper);
        when(months.cashBase()).thenReturn(YearMonth.of(2025,1));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var source=new AssignmentMonthlyService.Source("A-1","P-1","D-1","DEV",LocalDate.of(2026,8,1),LocalDate.of(2026,8,31),BigDecimal.ONE,new BigDecimal("200"),"NEW",null,null,0,"N","CNCL");
        when(repository.required("A-1")).thenReturn(source);snapshot="before";original=entry(2,"UPRC","N");
        when(repository.snapshot(anyString())).thenAnswer(c->snapshot);when(repository.serial(anyString())).thenReturn(3);
        when(repository.entry(eq("A-1"),eq(2))).thenAnswer(c->original);
        when(repository.entry(eq("A-1"),eq(3))).thenAnswer(c->new Entry(3,"CRCT",appended.date(),appended.before(),appended.content(),appended.reason(),null,null,null,"N","N","N",2,"admin",appended.now().toString()));
        when(mapper.mark(anyString(),anyInt())).thenAnswer(c->{original=entry(2,original.type(),"Y");return 1;});
        when(mapper.append(any())).thenAnswer(c->{appended=c.getArgument(0);snapshot="after";return 1;});
        when(mapper.list(any())).thenAnswer(c->List.of(original));when(mapper.count(any())).thenReturn(1L);
    }
    ObjectNode body() {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot)).put("correctContent"," 바른 감사 내용 ").put("reasonCode"," ETC ");}
    void send(ObjectNode body,int status) throws Exception {mvc.perform(post("/api/assignments/A-1/history/2/corrections").contentType("application/json").content(body.toString())).andExpect(status().is(status));}
    void noWrites() {verify(mapper,never()).mark(anyString(),anyInt());verify(mapper,never()).append(any());verifyNoInteractions(history);}
    @Test void publicReadPreservesTypedValuesAndDoesNotWrite() throws Exception {
        mvc.perform(get("/api/assignments/A-1/history").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].newUnitPrice").value("200")).andExpect(jsonPath("$.data.items[0].newContractMm").value("0.50")).andExpect(jsonPath("$.data.total").value(1));noWrites();verify(users).currentUser();
    }
    @Test void filterAndPagingHaveSameQuery() throws Exception {
        mvc.perform(get("/api/assignments/A-1/history").param("year","2026").param("type","CRCT").param("page","2").param("size","20")).andExpect(status().isOk());
        var q=new Query("A-1","CRCT",2,20,20);verify(mapper).list(q);verify(mapper).count(q);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"actor","REG_USER_ID","unknown"}) void rejectsUnknownQuery(String field) throws Exception {
        mvc.perform(get("/api/assignments/A-1/history").param("year","2026").param(field,"x")).andExpect(status().isBadRequest());noWrites();
    }
    @Test void invalidType() throws Exception {mvc.perform(get("/api/assignments/A-1/history").param("year","2026").param("type","OTHER")).andExpect(status().isBadRequest());noWrites();}
    @Test void pageZero() throws Exception {mvc.perform(get("/api/assignments/A-1/history").param("year","2026").param("page","0")).andExpect(status().isBadRequest());noWrites();}
    @Test void emptySource() throws Exception {when(mapper.list(any())).thenReturn(List.of());when(mapper.count(any())).thenReturn(0L);mvc.perform(get("/api/assignments/A-1/history").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));noWrites();}
    @Test void missingAssignment() throws Exception {when(repository.required(anyString())).thenThrow(new ApiException(404,"NOT_FOUND","없음"));mvc.perform(get("/api/assignments/A-1/history").param("year","2026")).andExpect(status().isNotFound());noWrites();}
    @Test void correctionBeforeFutureStartOnCancelledAssignmentIsAuditOnly() throws Exception {
        send(body(),200);assertThat(appended.date()).isEqualTo("2026-07-01");assertThat(appended.target()).isEqualTo(2);assertThat(appended.content()).isEqualTo("바른 감사 내용");assertThat(appended.actor()).isEqualTo("admin");
        assertThat(original.newUnitPrice()).isEqualTo("200");verify(months,never()).synchronize(any(),anyString(),anyString());verify(history).insert(any());verify(manager).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"NEW_UPRC","REG_USER_ID","actor","newContractMm"}) void rejectsInjectedServerField(String field) throws Exception {send(body().put(field,"forged"),400);noWrites();}
    @Test void blankContent() throws Exception {send(body().put("correctContent","  "),400);noWrites();}
    @Test void content201() throws Exception {send(body().put("correctContent","가".repeat(201)),400);noWrites();}
    @Test void missingReason() throws Exception {var b=body();b.remove("reasonCode");send(b,400);noWrites();}
    @Test void inactiveReason() throws Exception {doThrow(com.sicost.common.validation.ProjectInputPolicy.invalid("reasonCode")).when(repository).reason(anyString());send(body(),400);noWrites();}
    @ParameterizedTest @ValueSource(strings={"INPUT","CRCT"}) void invalidTarget(String type) throws Exception {original=entry(2,type,"N");send(body(),400);noWrites();}
    @Test void correctedTarget() throws Exception {original=entry(2,"UPRC","Y");send(body(),400);noWrites();}
    @Test void stale() throws Exception {send(body().put("expectedRevision","stale"),409);noWrites();}
    @Test void readOnlyYear() throws Exception {send(body().put("year",2025),409);noWrites();}
    @Test void nextYearAllowed() throws Exception {send(body().put("year",2027),200);}
    @Test void missingTarget() throws Exception {when(repository.entry("A-1",2)).thenThrow(new ApiException(404,"NOT_FOUND","없음"));send(body(),404);noWrites();}
    @Test void serialOverflow() throws Exception {when(repository.serial(anyString())).thenThrow(new ApiException(409,"STALE_WRITE","소진"));send(body(),409);noWrites();}
    @Test void auditFailureRollsBackWithoutLeak() throws Exception {
        doThrow(new IllegalStateException("private SQL")).when(history).insert(any());mvc.perform(post("/api/assignments/A-1/history/2/corrections").contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
    @Test void summaryIsBoundedWithoutChangingSource() {original=new Entry(2,"UPRC",LocalDate.now(),"가".repeat(200),"나".repeat(200),null,null,"200",null,"N","N","N",null,"admin","time");String summary=AssignmentHistoryService.summary(original);assertThat(summary.codePointCount(0,summary.length())).isEqualTo(200);assertThat(original.before()).hasSize(200);}
}
