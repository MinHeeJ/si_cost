package com.sicost.common.notifications;

import java.time.LocalDate;
import java.util.List;

/** Delivery feature implements this same coordinator for scheduled and manual today runs. */
public interface ReceivableDunningCoordinator {
    List<String> businessKeys(int stage, LocalDate today);
    void process(int stage, String businessKey, LocalDate today);
    void recoverAbandonedSending();
}
