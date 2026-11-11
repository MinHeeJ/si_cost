package com.sicost.states;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.states.DeveloperStateDtos.*;

@Mapper
public interface DeveloperStateMapper {
    Developer developer(String id);
    List<String> developerIds();
    List<Stored> states(String id);
    List<Assignment> assignments(String id);
    String actorName(String actor);
    String cashBaseMonth(String month);
    List<String> closedMonths();
    int greatestSerial(String id);
    int insert(Mutation mutation);
    int update(Mutation mutation);
    int delete(@Param("id") String id,@Param("sn") int sn);
    int updateDeveloper(DeveloperMutation mutation);
}
