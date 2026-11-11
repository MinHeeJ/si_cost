package com.sicost.contacts;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.contacts.CandidateContactDtos.*;

@Mapper
public interface CandidateContactMapper {
    Candidate candidate(String id);
    List<Contact> list(Selection selection);
    long count(String id);
    int greatestSerial(String id);
    int insert(Mutation mutation);
    int updateCandidate(Mutation mutation);
    Contact contact(@Param("id") String id,@Param("sn") int sn);
}
