package com.sicost.apiops;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.StaffingReadDtos.*;
@Mapper
public interface StaffingReadMapper {
    List<CandidateSource> candidates(String month);
    List<Person> people();
    List<TentativeSource> tentatives();
}
