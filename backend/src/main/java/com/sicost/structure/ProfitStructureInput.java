package com.sicost.structure;

import com.sicost.common.api.ApiException;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.structure.ProfitStructureDtos.*;

public final class ProfitStructureInput {
    private ProfitStructureInput() {}
    public static Query parse(MultiValueMap<String,String> values,boolean export) {
        Set<String> allowed=export?Set.of("year","month","aggregation","amountUnit"):Set.of("year","month","aggregation");
        values.forEach((key,items)->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);if(items.size()!=1)throw invalid(key);});
        String year=values.getFirst("year"),month=values.getFirst("month"),aggregation=values.getFirst("aggregation");
        if(year==null||!year.matches("[1-9][0-9]{0,3}"))throw invalid("year");
        if(month==null||!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")||YearMonth.parse(month).getYear()!=Integer.parseInt(year))throw invalid("month");
        if(aggregation==null||!Set.of("MONTH","QUARTER","YTD").contains(aggregation))throw invalid("aggregation");
        String unit=values.getFirst("amountUnit");if(unit==null)unit=export?"THOUSAND_WON":"WON";
        if(!Set.of("WON","THOUSAND_WON","MILLION_WON").contains(unit))throw invalid("amountUnit");
        return new Query(Integer.parseInt(year),month,aggregation,unit);
    }
    public static ApiException invalid(String field) {return new ApiException(400,"VALIDATION_ERROR","조회 기간·단위의 형식과 허용 범위를 확인해 주세요.",field,null);}
}
