package com.sicost.operating;

import com.sicost.common.api.ApiException;
import com.sicost.structure.ProfitStructureInput;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.Set;

public final class OperatingProfitInput {
    private OperatingProfitInput() {}
    public static OperatingProfitDtos.Query parse(MultiValueMap<String,String> values,boolean export) {
        Set<String> allowed=export?Set.of("year","month","amountUnit"):Set.of("year","month");
        values.forEach((key,items)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if(items.size()!=1)throw ProfitStructureInput.invalid(key);
        });
        var validated=new LinkedMultiValueMap<String,String>(values);
        validated.set("aggregation","YTD");
        var query=ProfitStructureInput.parse(validated,export);
        return new OperatingProfitDtos.Query(query.year(),query.month(),query.amountUnit());
    }
}
