package com.sicost.receivablesops;

import com.sicost.apiops.BillingReviewRepository;
import com.sicost.apiops.BillingReviewDtos.ProjectPart;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.notifications.*;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DunningDeliveryController.class)
@ContextConfiguration(classes=DunningDeliveryController.class)
@Import({DunningDeliveryService.class,DunningDeliveryRepository.class,DunningDeliveryCommands.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DunningDeliveryContractTest {
    @Autowired MockMvc mvc;
    @Autowired DunningDeliveryService service;
    @MockBean DunningDeliveryMapper mapper;
    @MockBean BillingReviewRepository billing;
    @MockBean NotificationPublicationMapper recipients;
    @MockBean ChangeHistoryWriter audits;
    @MockBean NotificationPublisher publisher;
    @MockBean DunningDeliveryLocks locks;
    @MockBean DunningDeliveryWorker worker;
    @MockBean DunningDeliveryMail mail;
    @MockBean Clock clock;
    final String id="BILL-TEST";
    final LocalDate today=LocalDate.of(2026,7,15);
    List<TaskSource> tasks;
    List<RoundSource> rounds;
    List<DeliveryHistory> histories;
    final String operation="7047f01a-fab0-48cb-9208-99481bc962ff";
    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        bill(new BigDecimal("110"),40,false);
        when(mapper.snapshot(id)).thenReturn("stored-source");
        when(mapper.lockBill(id)).thenReturn(id);
        when(billing.standard("DUN_D1","202607")).thenReturn(new BigDecimal("7"));
        when(billing.standard("DUN_D2","202607")).thenReturn(new BigDecimal("30"));
        when(billing.standard("DUN_D3","202607")).thenReturn(new BigDecimal("60"));
        when(billing.part("P","202607")).thenReturn(new ProjectPart("PART",false,"leader"));
        when(recipients.eligibleRoles()).thenReturn(List.of(new NotificationRecipients.Role("pm","담당","pm@example.invalid","PM"),new NotificationRecipients.Role("leader","파트장",null,"SALES_LDR"),new NotificationRecipients.Role("admin","임시 관리자","admin@example.invalid","ADMIN")));
        when(recipients.recipientScopes()).thenReturn(List.of(new NotificationRecipients.Scope("pm","PROJ",null,"P")));
        tasks=new ArrayList<>();rounds=new ArrayList<>();histories=new ArrayList<>();
        when(mapper.tasks(id)).thenAnswer(c->List.copyOf(tasks));when(mapper.rounds(id)).thenAnswer(c->List.copyOf(rounds));when(mapper.histories(id)).thenAnswer(c->List.copyOf(histories));
        when(mapper.nextSerial(id)).thenReturn(1);when(mail.body(any())).thenReturn("<p>retained evidence</p>");
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void teardown() {TransactionSynchronizationManager.clearSynchronization();}
    void bill(BigDecimal amount,int days,boolean cancelled) {when(mapper.bill(id)).thenReturn(new BillSource(id,"P","프로젝트",amount,today.minusDays(days),cancelled));}
    TaskSource task(int serial,String state,String channel) {
        return new TaskSource(UUID.randomUUID().toString(),"DUN:M:"+id+":"+operation,id,serial,2,true,"pm","당시 이름",channel.equals("MAIL")?"old@example.invalid":null,channel,state,null,channel.equals("MAIL")?"<fixed@example.invalid>":null,state.equals("SENT")?today.atStartOfDay():null,null,null,null,null,today.atStartOfDay(),null,null);
    }
    @Test void previewUsesActualBalanceRecipientsMissingEmailAndDistinctDeliveryRevision() throws Exception {
        mvc.perform(get("/api/receivables/{id}/delivery-preview",id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.unpaidAmount").value("110"))
            .andExpect(jsonPath("$.data.currentStep").value(2)).andExpect(jsonPath("$.data.sendMethodCode").value("BOTH"))
            .andExpect(jsonPath("$.data.recipients.length()").value(2)).andExpect(jsonPath("$.data.recipients[0].emailAddress").isEmpty())
            .andExpect(jsonPath("$.data.mailConfigured").value(false)).andExpect(jsonPath("$.data.canRemind").value(true)).andExpect(jsonPath("$.data.revision").isNotEmpty());
        verifyNoInteractions(publisher,audits,worker);verify(mapper,never()).nextSerial(any());
    }
    @Test void temporaryAdminIsNotAddedAsRecipientAndNeedsNoEndpointRole() throws Exception {
        mvc.perform(get("/api/receivables/{id}/delivery-preview",id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.recipients[*].userId",org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("admin"))));
    }
    @Test void zeroStageIsNotEligible() {bill(BigDecimal.TEN,1,false);assertThat(service.preview(id).canRemind()).isFalse();assertThat(service.preview(id).unavailableReasons()).contains("알림 단계에 도달하지 않았습니다.");}
    @Test void clearedBillIsNotEligible() {bill(BigDecimal.ZERO,40,false);assertThat(service.preview(id).canRemind()).isFalse();}
    @Test void cancelledBillIsNotEligible() {bill(BigDecimal.TEN,40,true);assertThat(service.preview(id).canRemind()).isFalse();}
    @Test void absentRecipientsAreNotReplacedByAdmin() {when(recipients.eligibleRoles()).thenReturn(List.of());assertThat(service.preview(id).canRemind()).isFalse();}
    @Test void inProgressIsVisibleAndPreventsCommand() throws Exception {tasks.add(task(1,"SENDING","MAIL"));assertThat(service.preview(id).inProgress()).isTrue();post("other").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELIVERY_IN_PROGRESS"));verifyNoInteractions(publisher,audits);}
    @Test void changedSourceRejectsStaleConfirmationWithoutWrites() throws Exception {post("old").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verifyNoInteractions(publisher,audits);}
    org.springframework.test.web.servlet.ResultActions post(String revision) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/receivables/{id}/reminders",id).contentType("application/json").content("{\"expectedRevision\":\""+revision+"\",\"operationId\":\""+operation+"\"}"));
    }
    @Test void acceptsFirstManualRoundAndKeepsFinancialDataUntouched() throws Exception {
        when(publisher.publish(any(),anyList())).thenReturn(List.of(UUID.randomUUID(),UUID.randomUUID()));
        when(mapper.insertMail(any())).thenAnswer(c->{var r=(MailReservation)c.getArgument(0);tasks.add(new TaskSource(r.taskId(),r.eventKey(),id,r.serial(),r.step(),true,r.userId(),r.userName(),r.emailAddress(),"MAIL",r.state(),null,r.messageId(),null,r.errorCode(),r.errorMessage(),r.subject(),r.body(),r.at(),null,null));
            if(rounds.isEmpty())rounds.add(new RoundSource(r.eventKey(),r.serial(),r.step(),true));return 1;});
        post(service.preview(id).revision()).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.eventKey").value("DUN:M:"+id+":"+operation))
            .andExpect(jsonPath("$.data.reservedSn").value(1)).andExpect(jsonPath("$.data.tasks.length()").value(2));
        verify(mapper,times(2)).insertMail(any());verify(worker,never()).submit(any(),any());
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());verify(worker).submit(id,"DUN:M:"+id+":"+operation);
        assertThat(mapper.bill(id).unpaidAmount()).isEqualByComparingTo("110");
    }
    @Test void sameCompletedUuidWinsBeforeStaleComparison() throws Exception {
        tasks.add(task(1,"FAILED","MAIL"));rounds.add(new RoundSource(tasks.get(0).eventKey(),1,2,true));
        post("stale").andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(true)).andExpect(jsonPath("$.data.history").isEmpty());verifyNoInteractions(publisher,audits);
    }
    @Test void samePendingUuidReturns202WithoutCreatingAgain() throws Exception {
        tasks.add(task(1,"PENDING","MAIL"));rounds.add(new RoundSource(tasks.get(0).eventKey(),1,2,true));
        post("stale").andExpect(status().isAccepted());verifyNoInteractions(publisher,audits);
    }
    @Test void exhaustedSerialIsAtomicConflict() throws Exception {when(mapper.nextSerial(id)).thenReturn(100000);post(service.preview(id).revision()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUNNING_SEQUENCE_EXHAUSTED"));verifyNoInteractions(publisher,audits);}
    @Test void invalidStateIs409() throws Exception {bill(BigDecimal.ZERO,40,false);post(service.preview(id).revision()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_BILL_STATE"));}
    @Test void mixedLegacyFailedAndPendingRoundsKeepEverySnapshotWithoutInventingHistory() throws Exception {
        histories.add(new DeliveryHistory(1,1,today.minusDays(5),"기존 수신자","SYS",false));rounds.add(new RoundSource("",1,1,false));
        tasks.add(task(2,"FAILED","MAIL"));rounds.add(new RoundSource(tasks.get(0).eventKey(),2,2,true));
        tasks.add(task(3,"PENDING","MAIL"));rounds.add(new RoundSource("DUN:A:"+id+":2",3,2,false));
        mvc.perform(get("/api/receivables/{id}/deliveries",id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.rounds[0].eventKey").value("")).andExpect(jsonPath("$.data.rounds[0].tasks").isEmpty())
            .andExpect(jsonPath("$.data.rounds[1].history").isEmpty()).andExpect(jsonPath("$.data.rounds[1].tasks[0].userName").value("당시 이름"))
            .andExpect(jsonPath("$.data.rounds[1].tasks[0].emailAddress").value("old@example.invalid")).andExpect(jsonPath("$.data.rounds[1].tasks[0].notificationId").isEmpty())
            .andExpect(jsonPath("$.data.rounds[2].terminal").value(false));verifyNoInteractions(publisher,audits,worker);
    }
    @Test void nonexistentBillIs404ForAllOperations() throws Exception {when(mapper.bill(id)).thenReturn(null);mvc.perform(get("/api/receivables/{id}/deliveries",id)).andExpect(status().isNotFound());mvc.perform(get("/api/receivables/{id}/delivery-preview",id)).andExpect(status().isNotFound());}
    @ParameterizedTest @ValueSource(strings={"page=0","size=30","status=FAILED","sort=state,desc","page=2147483648"})
    void rejectsInvalidCollectionConditions(String condition) throws Exception {String[] pair=condition.split("=",2);mvc.perform(get("/api/receivables/{id}/deliveries",id).param(pair[0],pair[1])).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void previewRejectsExtraQueryAndInvalidId() throws Exception {mvc.perform(get("/api/receivables/{id}/delivery-preview",id).param("month","2026-07")).andExpect(status().isBadRequest());mvc.perform(get("/api/receivables/{id}/delivery-preview","bad id")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @ParameterizedTest @ValueSource(strings={"{}","{\"operationId\":\"not-uuid\",\"expectedRevision\":\"r\"}","{\"operationId\":\"7047f01a-fab0-48cb-9208-99481bc962ff\"}","{\"expectedRevision\":\"r\",\"operationId\":\"7047f01a-fab0-48cb-9208-99481bc962ff\",\"unpaidAmount\":\"1\"}"})
    void rejectsIncompleteAndInjectedBodies(String body) throws Exception {mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/receivables/{id}/reminders",id).contentType("application/json").content(body)).andExpect(status().isBadRequest());verifyNoInteractions(publisher,audits,mapper);}
}
