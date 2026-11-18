package com.sicost.receivablesops;

import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mock.env.MockEnvironment;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DunningDeliveryExecutionTest {
    final DunningDeliveryRepository repository=mock(DunningDeliveryRepository.class);
    final DunningDeliveryService service=mock(DunningDeliveryService.class);
    final DunningDeliveryLocks financial=mock(DunningDeliveryLocks.class);
    final DunningDeliveryMail mail=mock(DunningDeliveryMail.class);
    final DunningDeliverySessionLocks sessions=mock(DunningDeliverySessionLocks.class);
    final DunningDeliveryState state=new DunningDeliveryState(repository,service,financial);
    final LocalDateTime at=LocalDateTime.of(2026,7,15,9,0);
    final String event="DUN:M:B:"+UUID.randomUUID();
    DeliveryPreview preview;
    List<TaskSource> tasks;
    TaskSource task(String user,String address,String status) {
        return new TaskSource(UUID.randomUUID().toString(),event,"B",1,2,true,user,"당시 이름",address,"MAIL",status,null,"<fixed@sicost.invalid>",null,null,null,"청구 B · 2단계 미수 알림",
            "<p>프로젝트: 시험 프로젝트</p><p>납기: 2026-06-01</p><p>잔액: 110원</p>",at,"SYSTEM",at.minusMinutes(1));
    }
    @BeforeEach void setup() {
        tasks=new ArrayList<>();when(repository.tasks("B")).thenAnswer(c->List.copyOf(tasks));when(repository.now()).thenReturn(at);
        preview=new DeliveryPreview("B","P","시험 프로젝트","110",LocalDate.of(2026,6,1),2,List.of(new DeliveryRecipient("a","당시 이름","same@example.invalid",false,List.of("PM")),new DeliveryRecipient("z","당시 이름","SAME@example.invalid",false,List.of("FIN"))),List.of(),"BOTH",true,true,false,List.of("전달 처리 중입니다."),"revision",at.atOffset(ZoneOffset.ofHours(9)));
        when(service.preview("B")).thenAnswer(c->preview);
        doAnswer(c->{((Runnable)c.getArgument(1)).run();return null;}).when(sessions).withLock(anyString(),any());
    }
    void deliver(DunningDeliveryWorker worker) {
        com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.RECEIVABLE_DUNNING,
            ()->{worker.deliver("B",event);return null;});
    }
    @Test void pendingCommitsSendingInUserOrderBeforeReturningToNetworkWorker() {
        tasks.add(task("z","SAME@example.invalid","PENDING"));tasks.add(task("a","same@example.invalid","PENDING"));
        var selected=state.prepare("B",event,"same@example.invalid");assertThat(selected).extracting(TaskSource::userId).containsExactly("a","z");
        verify(repository,times(2)).transition(any(),argThat(t->t.state().equals("SENDING")&&t.messageId().equals("<fixed@sicost.invalid>")));
        verifyNoInteractions(mail);
    }
    @ParameterizedTest @ValueSource(strings={"amount","step","recipient","cancelled","due","project"})
    void changedFrozenEvidenceIsSkippedRatherThanRerendered(String changed) {
        tasks.add(task("a","same@example.invalid","PENDING"));
        preview=new DeliveryPreview("B","P",changed.equals("project")?"이름 변경":"시험 프로젝트",changed.equals("amount")?"109":"110",changed.equals("due")?LocalDate.of(2026,6,2):LocalDate.of(2026,6,1),changed.equals("step")?3:2,
            changed.equals("recipient")?List.of():preview.recipients(),List.of(),"BOTH",true,true,false,changed.equals("cancelled")?List.of("취소된 청구입니다."):List.of(),"revision",preview.observedAt());
        assertThat(state.prepare("B",event,"same@example.invalid")).isEmpty();verify(repository).transition(any(),argThat(t->t.state().equals("SKIPPED")));verifyNoInteractions(mail);
    }
    @Test void interruptedSendingIsUnknownAndNeverResent() {
        tasks.add(task("a","same@example.invalid","SENDING"));assertThat(state.prepare("B",event,"same@example.invalid")).isEmpty();verify(repository).transition(any(),argThat(t->t.state().equals("UNKNOWN")));verifyNoInteractions(mail);
    }
    @Test void unknownIsNotAutomaticallyRetried() {tasks.add(task("a","same@example.invalid","UNKNOWN"));assertThat(state.prepare("B",event,"same@example.invalid")).isEmpty();verify(repository,never()).transition(any(),any());}
    @Test void oneNormalizedAddressUsesMinimumUserRepresentativeAndOneSmtpCall() {
        var stateAdapter=mock(DunningDeliveryState.class);var worker=new DunningDeliveryWorker(repository,stateAdapter,mail,sessions,new SyncTaskExecutor());
        var a=task("a","same@example.invalid","PENDING");var z=task("z"," SAME@example.invalid ","PENDING");tasks.add(z);tasks.add(a);
        when(stateAdapter.prepare("B",event,"same@example.invalid")).thenReturn(List.of(a,z));when(stateAdapter.finalRecipients("B")).thenReturn(preview.recipients());var result=new DunningDeliveryMail.MailResult("SENT",null,null);when(mail.send(a)).thenReturn(result);
        deliver(worker);var order=inOrder(stateAdapter,mail);order.verify(stateAdapter).prepare("B",event,"same@example.invalid");order.verify(mail).send(a);order.verify(stateAdapter).complete("B",event,List.of(a,z),result);verify(mail,times(1)).send(any());
    }
    @ParameterizedTest @ValueSource(strings={"SENT","FAILED","UNKNOWN"})
    void completionPersistsExactTransportClassification(String status) {
        var task=task("a","same@example.invalid","SENDING");tasks.add(task);state.complete("B",event,List.of(task),new DunningDeliveryMail.MailResult(status,status.equals("SENT")?null:"MAIL_RESULT","메일 결과"));
        verify(repository).transition(eq(task),argThat(t->t.state().equals(status)&&(status.equals("SENT")?at.equals(t.sentAt()):t.sentAt()==null)));verify(repository).finalizeRound("B",event);
    }
    @Test void startupOnlyRecoversSendingAndDoesNotDispatchPendingOrSmtp() {
        var stateAdapter=mock(DunningDeliveryState.class);when(repository.interrupted()).thenReturn(List.of(new DeliveryWork("B",event)));
        var worker=new DunningDeliveryWorker(repository,stateAdapter,mail,sessions,new SyncTaskExecutor());worker.recoverInterrupted();verify(stateAdapter).recoverInterrupted("B",event);verifyNoInteractions(mail);verify(repository,never()).automaticPending();
    }
    @Test void lostDatabaseReceiptCannotBecomeSafeRetry() {
        var stateAdapter=mock(DunningDeliveryState.class);var task=task("a","same@example.invalid","PENDING");tasks.add(task);
        when(stateAdapter.prepare("B",event,"same@example.invalid")).thenReturn(List.of(task));when(stateAdapter.finalRecipients("B")).thenReturn(preview.recipients());var success=new DunningDeliveryMail.MailResult("SENT",null,null);when(mail.send(task)).thenReturn(success);
        doThrow(new IllegalStateException("receipt unavailable")).when(stateAdapter).complete("B",event,List.of(task),success);
        deliver(new DunningDeliveryWorker(repository,stateAdapter,mail,sessions,new SyncTaskExecutor()));
        verify(mail,times(1)).send(task);verify(stateAdapter).complete(eq("B"),eq(event),eq(List.of(task)),argThat(r->r.state().equals("UNKNOWN")));
    }
    @Test void missingOptionalMailSettingsNeverPreventStartupOrClaimSuccess() {
        var disabled=new DunningDeliveryMail(new MockEnvironment());assertThat(disabled.configured()).isFalse();assertThat(disabled.send(task("a","same@example.invalid","PENDING")).errorCode()).isEqualTo("MAIL_NOT_CONFIGURED");
    }
    @ParameterizedTest @ValueSource(strings={"javascript:alert(1)","https://name:password@example.invalid","https://example.invalid/?token=secret","bad-url"})
    void unsafePublicLinkDisablesOptionalMail(String url) {var settings=new MockEnvironment().withProperty("MAIL_ENABLED","true").withProperty("MAIL_HOST","collector").withProperty("MAIL_FROM","sender@example.invalid").withProperty("APP_PUBLIC_URL",url);assertThat(new DunningDeliveryMail(settings).configured()).isFalse();}
    @Test void userHtmlIsEscapedAndNoReferenceUrlIsSubstituted() {assertThat(DunningDeliveryMail.escape("<script>\"&'")).isEqualTo("&lt;script&gt;&quot;&amp;&#39;");}
    @Test void mapperWritesMatchImmutablePersistenceGrammarAndRealRecordBindings() throws Exception {
        var configuration=new Configuration();var resource=new ClassPathResource("mappers/receivablesops/DunningDeliveryMapper.xml");
        try(var input=resource.getInputStream()){new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();}
        var reservation=new MailReservation(UUID.randomUUID().toString(),event,"B",1,2,true,"a","당시 이름","same@example.invalid","PENDING",null,null,"미수 알림","제목","<p>"+"본문".repeat(1000)+"</p>","<fixed@sicost.invalid>",at);
        var parameters=Map.of("insertMail",(Object)reservation,"transition",new TaskTransition(reservation.taskId(),"PENDING","SENDING",reservation.messageId(),null,null,null,at),"insertHistory",new HistoryInsert("B",1,2,at.toLocalDate(),"당시 이름","BOTH","Y",at));
        parameters.forEach((method,value)->assertThatCode(()->PersistenceBoundary.requireAllowed(configuration.getMappedStatement(DunningDeliveryMapper.class.getName()+"."+method).getBoundSql(value).getSql())).doesNotThrowAnyException());
        var mapper=mock(DunningDeliveryMapper.class);var history=mock(ChangeHistoryWriter.class);when(mapper.insertMail(any())).thenReturn(1);
        new DunningDeliveryRepository(mapper,history,Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"),ZoneOffset.UTC)).reserve(reservation);
        verify(history).appendAsSystem(argThat(c->c.item().equals("MAIL_BODY")&&c.after().equals(reservation.taskId()+":MAIL_BODY")));
        doThrow(new IllegalStateException("required audit")).when(history).appendAsSystem(any());assertThatThrownBy(()->new DunningDeliveryRepository(mapper,history,Clock.systemUTC()).reserve(reservation)).isInstanceOf(IllegalStateException.class);
    }
}
