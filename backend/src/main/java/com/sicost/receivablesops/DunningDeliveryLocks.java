package com.sicost.receivablesops;

import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DunningDeliveryLocks {
    private final MonthCloseBoundary boundary;
    private final SettlementSourceLocks sources;
    private final DunningDeliveryRepository repository;
    private final CurrentUserProvider users;
    public DunningDeliveryLocks(MonthCloseBoundary boundary,SettlementSourceLocks sources,DunningDeliveryRepository repository,CurrentUserProvider users) {
        this.boundary=boundary;this.sources=sources;this.repository=repository;this.users=users;
    }
    public void acquire(String billId) {
        users.currentUser();boundary.shared();
        var bill=repository.required(billId);
        sources.lock(List.of("BILL:"+billId,"DUN:"+billId,"PROJECT:"+bill.projectId()));
        repository.lock(billId);
    }
}
