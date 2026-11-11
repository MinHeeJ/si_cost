package com.sicost.candidatesops;

import org.apache.ibatis.annotations.Mapper;
import static com.sicost.candidatesops.CandidateSummaryDtos.*;

@Mapper
public interface CandidateSummaryMapper {
    Summary summary(Basis basis);
}
