package com.sicost.forecast;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.forecast.SalesForecastSources.*;

@Mapper
public interface SalesForecastMapper {
    List<Project> projects();
    List<Opportunity> opportunities();
    List<Need> needs();
    List<Plan> plans();
    List<Person> people();
    List<Leave> leaves();
    List<Candidate> candidates();
    List<Standard> standards(@Param("month") String month);
    List<Job> jobs();
    List<String> assignmentIds();
}
