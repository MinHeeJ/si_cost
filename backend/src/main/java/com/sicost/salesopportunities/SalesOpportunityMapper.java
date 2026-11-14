package com.sicost.salesopportunities;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;

@Mapper
public interface SalesOpportunityMapper {
    Stored find(String id);
    List<Stored> all();
    List<StoredTent> tentatives(String id);
    List<ApprovalHistory> approvals(String id);
    List<String> opportunityKeys();
    List<Field> fields(String id);
    String nextId();
    boolean customer(String id);
    boolean part(String id);
    boolean code(CodeKey key);
    int greatestNeedSn(String id);
    int insert(Write write);
    int update(Write write);
    int insertNeed(NeedWrite write);
    int updateNeed(NeedWrite write);
    int deleteNeed(NeedKey key);
    int deleteTentatives(NeedKey key);
    int updateTentative(TentWrite write);
}
