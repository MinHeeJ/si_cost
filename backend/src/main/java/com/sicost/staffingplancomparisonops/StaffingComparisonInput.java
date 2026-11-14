package com.sicost.staffingplancomparisonops;

import com.sicost.common.api.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.staffingplancomparisonops.StaffingComparisonDtos.*;

public final class StaffingComparisonInput {
    private StaffingComparisonInput() {}
    public static Query parse(MultiValueMap<String,String> values) {
        values.forEach((key, entries) -> {
            if (!Set.of("year", "projectId", "difference", "amountUnit").contains(key))
                throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);
            if (entries.size()!=1 || entries.get(0)==null || entries.get(0).isBlank()) throw invalid(key);
        });
        String year=values.getFirst("year");
        if (year!=null && !year.matches("[1-9][0-9]{0,3}")) throw invalid("year");
        String project=values.getFirst("projectId");
        if (project!=null && (project.length()>20 || !project.equals(project.strip()) || project.chars().anyMatch(Character::isISOControl))) throw invalid("projectId");
        String difference=values.getFirst("difference");
        if (difference!=null && !Set.of("ALL","SHORT","EXCESS","MATCH").contains(difference)) throw invalid("difference");
        String unit=values.getFirst("amountUnit");
        if (unit!=null && !Set.of("WON","THOUSAND","MILLION").contains(unit)) throw invalid("amountUnit");
        return new Query(year==null?null:Integer.valueOf(year),project,difference==null?"ALL":difference,unit==null?"THOUSAND":unit);
    }
    public static ApiException invalid(String field) {
        return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 범위를 확인해 주세요.",field,null);
    }
}
