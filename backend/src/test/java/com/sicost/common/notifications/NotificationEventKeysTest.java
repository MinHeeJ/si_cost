package com.sicost.common.notifications;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationEventKeysTest {
    @Test void automaticAndManualDunningHaveDifferentStableIdentities() {
        UUID operation=UUID.randomUUID();
        assertThat(NotificationEventKeys.automaticDunning("B-202607-001",1)).isEqualTo("DUN:A:B-202607-001:1");
        assertThat(NotificationEventKeys.manualDunning("B-202607-001",operation)).isEqualTo("DUN:M:B-202607-001:"+operation);
    }
    @Test void withdrawalIncludesDateAndNormalizedPositiveIntegerStandard() {
        LocalDate date=LocalDate.of(2026,7,31);
        assertThat(NotificationEventKeys.withdrawal("A-0001",date,new BigDecimal("7.0000"))).isEqualTo("WTHDR:A-0001:2026-07-31:7");
        assertThat(NotificationEventKeys.withdrawal("A-0001",date,new BigDecimal("8"))).isNotEqualTo(NotificationEventKeys.withdrawal("A-0001",date,new BigDecimal("7")));
    }
    @Test void nonPositiveOrFractionalWithdrawalStandardsDoNotCreateEvents() {
        for(String days:new String[]{"0","-1","1.5"})assertThatThrownBy(()->NotificationEventKeys.withdrawal("A-0001",LocalDate.of(2026,7,31),new BigDecimal(days))).isInstanceOf(ApiException.class);
    }
    @Test void sourceCallbacksHaveExactStableEventNames() {
        assertThat(NotificationEventKeys.cash(LocalDate.of(2026,7,15))).isEqualTo("CASH:2026-07-15");
        assertThat(NotificationEventKeys.upload("123")).isEqualTo("UPLD:123:APPLIED");
        assertThat(NotificationEventKeys.prediction("456")).isEqualTo("AI:456:DONE");
        assertThat(NotificationEventKeys.quoteRejected("O-0001",2)).isEqualTo("QUOTE:O-0001:2:REJ");
    }
    @Test void invalidIdsAndUnknownStepsAreNotNormalizedOrAccepted() {
        for(String id:new String[]{"", "https://x", "a:b", "a\r\nb", "x".repeat(21)})assertThatThrownBy(()->NotificationEventKeys.automaticDunning(id,1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->NotificationEventKeys.automaticDunning("B-1",0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->NotificationEventKeys.upload("01")).isInstanceOf(ApiException.class);
    }
}
