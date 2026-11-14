package com.sicost.salespartsettlements;

import com.sicost.profitability.MonthlyCostDtos.Sources;
import com.sicost.profitability.MonthlyCostRepository;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;

@Repository
public class SalesPartSettlementRepository {
    public record Snapshot(Sources sources, List<PartRow> parts, List<StandardRow> standards,
        List<UseHistory> history, List<Parent> parents, List<Detail> details,
        List<FirstInput> firstInputs, List<RevisionRow> revisionSources) {}
    private final MonthlyCostRepository monthly;
    private final SalesPartSettlementMapper mapper;
    public SalesPartSettlementRepository(MonthlyCostRepository monthly, SalesPartSettlementMapper mapper) {
        this.monthly=monthly; this.mapper=mapper;
    }
    public Snapshot load(String month, boolean withSources) {
        Sources sources=monthly.loadRange(month);
        String selected=sources.period().toMonth();
        return new Snapshot(sources,mapper.parts(),mapper.standards(),mapper.useHistory(),mapper.parents(selected),
            mapper.details(selected),withSources?mapper.firstInputs():List.of(),withSources?mapper.revisionSources(selected):List.of());
    }
}
