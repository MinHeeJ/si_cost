package com.sicost.candidates;

import java.util.*;
import org.apache.ibatis.annotations.*;
import static com.sicost.candidates.CandidateInput.*;

@Mapper
public interface CandidateMapper {
    List<Map<String,Object>> list(Selection selection);
    long count(Selection selection);
    Map<String,Object> find(String id);
    List<String> skills(String id);
    Map<String,Object> labels(String id);
    com.sicost.common.validation.PeopleInputPolicy.CodeSelection code(@Param("group") String group,@Param("code") String code);
    Map<String,Object> company(String id);
    int insert(Map<String,Object> row);
    int update(Map<String,Object> row);
    int deleteSkills(String id);
    int insertSkill(Map<String,Object> row);
    int greatestSerial(String id);
    int insertContact(Map<String,Object> row);
}
