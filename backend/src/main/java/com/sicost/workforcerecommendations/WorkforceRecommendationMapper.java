package com.sicost.workforcerecommendations;

import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@Mapper
public interface WorkforceRecommendationMapper {
    Target target(Condition condition);
    boolean needExists(Condition condition);
    List<Person> people();
    List<Code> codes();
    List<Standard> standards(String month);
    List<Skill> skills();
    List<Leave> leaves();
    List<Area> areas();
    List<Assignment> assignments();
    List<Tentative> tentatives();
    List<Excluded> exclusions(Condition condition);
    BigDecimal price(@Param("developerId") String developerId,@Param("month") String month);
    String snapshot();
    String nextId();
    int insert(Row row);
    List<Row> rows(String rcmdId);
    Person person(String developerId);
    List<AuditValue> metadata(String key);
    NextCard nextCard(Row row);
}
