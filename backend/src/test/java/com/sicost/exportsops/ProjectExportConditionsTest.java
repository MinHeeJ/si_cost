package com.sicost.exportsops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectExportConditionsTest {
    private static final Map<String, String> TYPES = Map.of("TK", "턴키", "IN", "내부");
    private static final Map<String, String> STATUSES = Map.of("ING", "진행", "DONE", "종료");

    @Test void defaultListShowsYearAndScreenSortWithoutInternalKeys() {
        var query = new LinkedHashMap<String, String>();
        query.put("year", "2026"); query.put("sort", "startDate"); query.put("direction", "DESC");
        String text = ProjectExportService.conditions(query, TYPES, STATUSES, null);
        assertThat(text).isEqualTo("연도=2026; 정렬=시작일 내림차순");
        assertThat(text).doesNotContain("{", "year", "startDate", "DESC");
    }

    @Test void filtersUseCodeNamesAndPartName() {
        var query = new LinkedHashMap<String, String>();
        query.put("year", "2026"); query.put("contractType", "TK"); query.put("status", "ING");
        query.put("partId", "P-01"); query.put("q", "유통"); query.put("sort", "contractAmount"); query.put("direction", "ASC");
        assertThat(ProjectExportService.conditions(query, TYPES, STATUSES, "영업1파트"))
            .isEqualTo("연도=2026; 계약유형=턴키; 상태=진행; 영업 파트=영업1파트; 검색어=유통; 정렬=계약금액 오름차순");
    }

    @Test void unknownCodeOrMissingPartNeverLeaksTheRawValue() {
        var query = new LinkedHashMap<String, String>();
        query.put("status", "ZZZ"); query.put("partId", "P-99");
        String text = ProjectExportService.conditions(query, TYPES, STATUSES, null);
        assertThat(text).isEqualTo("상태=–; 영업 파트=–; 정렬=시작일 내림차순");
        assertThat(text).doesNotContain("ZZZ", "P-99");
    }
}
