package com.sicost.salespartsettlements;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;

@Mapper
public interface SalesPartSettlementMapper {
    List<PartRow> parts();
    List<StandardRow> standards();
    List<UseHistory> useHistory();
    List<Parent> parents(String month);
    List<Detail> details(String month);
    List<FirstInput> firstInputs();
    List<RevisionRow> revisionSources(String month);
}
