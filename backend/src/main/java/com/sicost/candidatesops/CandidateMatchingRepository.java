package com.sicost.candidatesops;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.candidatesops.CandidateMatchingDtos.*;

@Repository
public class CandidateMatchingRepository {
    private final CandidateMatchingMapper mapper;
    public CandidateMatchingRepository(CandidateMatchingMapper mapper) { this.mapper=mapper; }
    public Candidate required(String id) {
        Candidate candidate=mapper.candidate(id);
        if (candidate==null) throw new ApiException(404,"NOT_FOUND","후보를 찾을 수 없습니다.");
        return candidate;
    }
    public List<Need> needs(String jobCode) { return mapper.needs(jobCode); }
    public List<CodeValue> allowances() { return mapper.allowances(); }
    public BigDecimal standard(String code,String month) { return mapper.standard(new StandardQuery(code,month)); }
    public Counts counts(String jobCode,java.time.LocalDate until) { return mapper.counts(new CountQuery(jobCode,until)); }
    public List<JobOrder> jobs() { return mapper.jobs(); }
}
