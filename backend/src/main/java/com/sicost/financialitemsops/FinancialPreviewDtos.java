package com.sicost.financialitemsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.common.financial.FinancialCalculation.Allocation;
import java.util.List;
import java.util.Map;

public final class FinancialPreviewDtos {
    private FinancialPreviewDtos() {}
    public record StoredMonth(String month,String closeStatus,int itemCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthStatus(String month,String closeStatus,Integer itemCount,boolean selectable,boolean editable) {}
    public record YearStatus(String year,String today,String cashBaseMonth,List<MonthStatus> months) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Draft(String sourceMonth,String sourceItemSn,String finClass,String sgaItemCode,String itemName,String amount,
        String vatAmount,String ioDate,String repeat,String remark,String autoFill,List<Map<String,String>> errors) {}
    public record Preview(String month,String revision,List<Draft> addedDrafts,int copiedCount,String message,Allocation allocation,Map<String,Object> contributions) {}
}
