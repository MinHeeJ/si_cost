package com.sicost.salespartsettlementsops;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementCommandMapper {
    record ParentWrite(String partId, String month, BigDecimal card, BigDecimal profit, String status,
        String confirmedBy, LocalDateTime confirmedAt, String reason, String actor, LocalDateTime at) {}
    record DetailWrite(String partId, String month, String developerId, String employeeType, String projects,
        BigDecimal mm, BigDecimal unitPrice, BigDecimal pay, BigDecimal expense, BigDecimal retirement,
        BigDecimal cost, BigDecimal profit, BigDecimal share, String remark, String actor, LocalDateTime at) {}
    record Identity(String partId, String month) {}
    List<String> sourceKeys(@Param("month") String month);
    int insertParent(ParentWrite value);
    int updateParent(ParentWrite value);
    int insertDetail(DetailWrite value);
    int deleteDetails(Identity value);
}
