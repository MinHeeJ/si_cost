package com.sicost.developers;

import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.annotations.*;
import static com.sicost.developers.DeveloperInput.*;

@Mapper
public interface DeveloperMapper {
    List<com.sicost.profitability.MonthlyCostDtos.Person> historicalPeople();
    List<com.sicost.profitability.MonthlyCostDtos.Price> historicalPrices(com.sicost.profitability.MonthlyCostDtos.Range range);
    List<com.sicost.profitability.MonthlyCostDtos.WorkState> historicalStates(com.sicost.profitability.MonthlyCostDtos.Range range);
    List<Map<String,Object>> list(Selection selection);
    long count(@Param("query") Query query);
    Map<String,Object> find(@Param("id") String id,@Param("lock") boolean lock);
    List<Map<String,Object>> prices(@Param("id") String id,@Param("lock") boolean lock);
    List<Map<String,Object>> skills(@Param("id") String id,@Param("lock") boolean lock);
    List<Map<String,Object>> states(@Param("id") String id,@Param("lock") boolean lock);
    List<String> ids();
    List<Map<String,Object>> assignments(@Param("id") String id,@Param("today") LocalDate today,@Param("month") String month);
    Map<String,Object> labels(String id);
    Map<String,Object> code(@Param("group") String group,@Param("code") String code);
    Map<String,Object> company(String id);
    Map<String,Object> part(String id);
    String mmOver(String month);
    boolean managementExists(String managementNo);
    List<String> samePerson(@Param("name") String name,@Param("hireDate") LocalDate hireDate);
    int insert(Map<String,Object> row);
    int update(Map<String,Object> row);
    int updateStatus(Map<String,Object> row);
    int insertPrice(Map<String,Object> row);
    int updatePrice(Map<String,Object> row);
    int closePrice(Map<String,Object> row);
    int deleteSkills(String id);
    int insertSkill(Map<String,Object> row);
    void lockClose();
    boolean closed(String month);
}
