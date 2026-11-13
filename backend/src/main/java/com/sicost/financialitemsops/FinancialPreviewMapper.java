package com.sicost.financialitemsops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.financialitemsops.FinancialPreviewDtos.*;

@Mapper
public interface FinancialPreviewMapper {
    List<StoredMonth> yearStatus(@Param("year") String year);
    String snapshot(@Param("month") String month);
    String previousItems(@Param("month") String month);
    List<com.sicost.common.financial.FinancialCalculation.TaxMonth> taxMonths(@Param("month") String month);
}
