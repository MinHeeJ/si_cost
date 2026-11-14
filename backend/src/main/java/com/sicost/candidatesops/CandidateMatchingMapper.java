package com.sicost.candidatesops;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.candidatesops.CandidateMatchingDtos.*;

@Mapper
public interface CandidateMatchingMapper {
    Candidate candidate(String id);
    List<Need> needs(String jobCode);
    List<CodeValue> allowances();
    BigDecimal standard(StandardQuery query);
    Counts counts(CountQuery query);
    List<JobOrder> jobs();
}
