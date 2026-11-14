package com.sicost.salesopportunitiesops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

@Mapper
public interface SalesCommandMapper {
    Opportunity find(String id);
    List<DraftNeed> needs(String id);
    List<Tentative> tentatives(String id);
    String snapshot(String id);
    String latestPayrollMonth(String month);
    List<Standard> standards(String month);
    List<Code> codes();
    List<String> sourceLockKeys();
    String lockOpportunity(String id);
    int update(Mutation mutation);
    int transitionTentative(TentativeMutation mutation);
    int greatestHistorySn(String id);
    int approvalEvent(ApprovalEvent event);
    int notification(Notification notification);
    int insertOpportunityPlan(Plan plan);
    boolean activeLossReason(String code);
}
