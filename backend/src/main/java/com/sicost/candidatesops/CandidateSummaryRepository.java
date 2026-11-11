package com.sicost.candidatesops;

import org.springframework.stereotype.Repository;
import static com.sicost.candidatesops.CandidateSummaryDtos.*;

@Repository
public class CandidateSummaryRepository {
    private final CandidateSummaryMapper mapper;
    public CandidateSummaryRepository(CandidateSummaryMapper mapper) { this.mapper = mapper; }
    public Summary summary(Basis basis) { return mapper.summary(basis); }
}
