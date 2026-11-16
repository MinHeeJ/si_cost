package com.sicost.receivablesops;

import com.sicost.common.notifications.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DunningAutomaticEligibilityTest {
    final DunningDeliveryRepository repository=mock(DunningDeliveryRepository.class);
    final DunningDeliveryService service=mock(DunningDeliveryService.class);
    final NotificationPublisher publisher=mock(NotificationPublisher.class);
    final DunningDeliveryLocks locks=mock(DunningDeliveryLocks.class);
    final DunningDeliveryWorker worker=mock(DunningDeliveryWorker.class);
    final DunningDeliveryMail mail=mock(DunningDeliveryMail.class);
    final DunningDeliveryCommands commands=new DunningDeliveryCommands(repository,service,publisher,locks,worker,mail);
    @BeforeEach void setup() {
        when(repository.required("B")).thenReturn(new BillSource("B","P","사업",new BigDecimal("111"),LocalDate.of(2026,1,1),false));
        when(repository.nextSerial("B")).thenReturn(4);
        when(service.preview("B")).thenReturn(new DeliveryPreview("B","P","사업","111",LocalDate.of(2026,1,1),2,
            List.of(new DeliveryRecipient("pm","담당","pm@example.invalid",false,List.of("PM"))),List.of(),"BOTH",false,false,true,List.of(),"rev",OffsetDateTime.now()));
        when(publisher.publish(any(),any())).thenReturn(List.of(UUID.randomUUID()));
        when(mail.body(any())).thenReturn("본문");
        when(repository.rounds("B")).thenReturn(List.of(new DeliveryRound("DUN:A:B:2",4,2,false,null,List.of(),true)));
    }
    @Test void automaticOnlyReservesHighestCurrentStepWithManualFlagFalseAndNoPrematureDispatch() {
        commands.automatic("B");
        verify(publisher).publish(argThat(e->e.key().equals("DUN:A:B:2")&&e.dunning().serial()==4&&!e.dunning().resend()),any());
        verify(repository).reserve(argThat(r->!r.resend()&&r.serial()==4&&r.state().equals("FAILED")&&r.errorCode().equals("MAIL_NOT_CONFIGURED")));
        verifyNoInteractions(worker);
    }
    @Test void priorSameOrHigherManualOrAutomaticStepSuppressesNewEvent() {
        for(int highest:List.of(2,3)) {when(repository.highestStep("B")).thenReturn(highest);commands.automatic("B");}
        verifyNoInteractions(publisher,mail,worker);
    }
    @Test void exhaustedSerialIs409BeforeAnyPublication() {
        when(repository.nextSerial("B")).thenReturn(100000);
        assertThatThrownBy(()->commands.automatic("B")).isInstanceOf(com.sicost.common.api.ApiException.class);
        verifyNoInteractions(publisher,mail,worker);
    }
    @Test void cancelledAndResolvedBillsAreExcludedBeforeRecipientLookup() {
        when(repository.required("B")).thenReturn(new BillSource("B","P","사업",BigDecimal.ZERO,LocalDate.of(2026,1,1),false));commands.automatic("B");
        when(repository.required("B")).thenReturn(new BillSource("B","P","사업",BigDecimal.ONE,LocalDate.of(2026,1,1),true));commands.automatic("B");
        verifyNoInteractions(service,publisher,mail,worker);
    }
    @Test void lastValidSerial99999IsAllowed() {
        when(repository.nextSerial("B")).thenReturn(99999);commands.automatic("B");
        verify(publisher).publish(argThat(e->e.dunning().serial()==99999),any());
    }
    @Test void noRecipientFailsWithoutEvidenceButAlreadySentStageStillSuppresses() {
        var preview=service.preview("B");
        when(service.preview("B")).thenReturn(new DeliveryPreview("B","P","사업","111",preview.dueDate(),2,List.of(),List.of("미지정"),"BOTH",false,false,false,List.of("수신자가 지정되지 않았습니다."),"rev",preview.observedAt()));
        assertThatThrownBy(()->commands.automatic("B")).isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,e->assertThat(e.code()).isEqualTo("RECIPIENT_UNASSIGNED"));
        when(repository.highestStep("B")).thenReturn(2);commands.automatic("B");verifyNoInteractions(publisher,worker,mail);
    }
}
