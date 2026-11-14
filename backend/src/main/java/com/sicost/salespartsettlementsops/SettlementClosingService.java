package com.sicost.salespartsettlementsops;

import com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import com.sicost.salespartsettlements.SalesPartSettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Shared entry point for the subsequent month-close feature; no API or job invokes it here. */
@Service
public class SettlementClosingService {
    private final SalesPartSettlementService reads;
    private final SettlementCommandService commands;
    public SettlementClosingService(SalesPartSettlementService reads,SettlementCommandService commands) {
        this.reads=reads;this.commands=commands;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TabsResult listEligibleParts(String month) { return reads.listEligibleParts(month); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result previewSettlement(String partId,String month) { return reads.previewSettlement(partId,month); }
    @Transactional(propagation=Propagation.MANDATORY)
    public SettlementCommandService.Mutation confirmForClose(String partId,String month,String actor) {
        return commands.confirmForClose(partId,month,actor);
    }
}
