package com.sicost.apiops;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.apiops.TentativeSelectionDtos.*;
@Mapper
public interface TentativeSelectionMapper {
    Need need(TentativeSelectionInput.Create input);
    List<Active> active(String developerId);
    String lockDeveloper(String developerId);
    String lockOpportunity(String opportunityId);
    boolean eligible(String developerId);
    int count(TentativeSelectionInput.Create input);
    String nextId();
    int insert(Write write);
    int release(Write write);
    int touch(Write write);
    List<String> assignmentIds(String developerId);
}
