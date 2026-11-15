package com.sicost.cashflowops;

import org.apache.ibatis.annotations.Mapper;

@Mapper
@org.springframework.stereotype.Component("cashFlowEvidenceMapper")
public interface CashFlowMapper {
    String receipts(String month);
}
