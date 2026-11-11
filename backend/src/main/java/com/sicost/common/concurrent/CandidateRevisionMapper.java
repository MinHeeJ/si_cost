package com.sicost.common.concurrent;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CandidateRevisionMapper {
    String lockCandidate(String id);
    List<String> lockSkills(String id);
    List<Integer> lockContacts(String id);
    String snapshot(String id);
}
