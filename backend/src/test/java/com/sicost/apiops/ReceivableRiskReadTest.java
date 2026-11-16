package com.sicost.apiops;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.apiops.BillingReviewDtos.*;

class ReceivableRiskReadTest {
    @Test void riskReadIncludesDueTodayAndFutureButLeavesExistingOverdueScreenUnchanged(){
        var repository=mock(BillingReviewRepository.class);
        var today=LocalDate.of(2026,11,17);
        var service=new ReceivableService(repository,mock(com.sicost.common.context.CurrentUserProvider.class),Clock.fixed(today.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul")));
        when(repository.standard("DUN_D1","202611")).thenReturn(new BigDecimal("7"));
        when(repository.standard("DUN_D2","202611")).thenReturn(new BigDecimal("30"));
        when(repository.standard("DUN_D3","202611")).thenReturn(new BigDecimal("60"));
        when(repository.overdue(today)).thenReturn(List.of(new OverdueBill("late","P","project",new BigDecimal("750000"),today.minusDays(1))));
        when(repository.notYetOverdue(today)).thenReturn(List.of(new OverdueBill("today","P","project",new BigDecimal("1000000"),today),new OverdueBill("future","P","project",new BigDecimal("500000"),today.plusDays(1)),new OverdueBill("paid","P","project",BigDecimal.ZERO,today)));
        assertThat(service.allReceivables().items()).extracting(Receivable::billId).containsExactly("late");
        var risks=service.allReceivableRisks();
        assertThat(risks.items()).extracting(Receivable::billId).containsExactly("late","today","future");
        assertThat(risks.items()).extracting(r->r.dunning().elapsedDays()).containsExactly(1,0,0);
        assertThat(risks.items().get(1).unpaidAmount()).isEqualTo("1000000");
        assertThat(risks.items().get(2).dunning().nextDate()).isEqualTo(today.plusDays(8));
        assertThat(service.allReceivables().items()).hasSize(1);
    }
}
