package com.sicost.common.excel;

import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.MenuRow;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

/** Isolated workbook tests supply the menu rows that production reads from PostgreSQL. */
public final class ExportTestLookups {
    private ExportTestLookups() {}
    public static List<MenuRow> menus() {
        return Map.of("UI-02", "개발자 풀", "UI-33", "외부 인력 후보", "UI-01", "프로젝트",
            "UI-12", "투입 배정", "UI-11", "인원 계획 대비 투입", "UI-31", "프로젝트 현황판",
            "UI-28", "인력 운영 보드", "UI-29", "영업 파이프라인", "UI-05", "철수 리스트", "UI-17", "인력 수요 예측")
            .entrySet().stream().map(e -> new MenuRow(e.getKey(), null, e.getValue(), e.getKey(), 2, 1, "Y", null, false)).toList();
    }
    public static void configure(ExcelExportService excel) {
        SharedQueriesMapper queries = mock(SharedQueriesMapper.class);
        when(queries.menus()).thenReturn(menus());
        excel.setMenuQueries(queries);
    }
    public static ExportLabels labels() {
        var codes = mock(SharedQueriesMapper.class);
        when(codes.codes(anyString(), eq(true))).thenAnswer(call -> {
            String group = call.getArgument(0);
            Map<String,String> values = switch(group) {
                case "JOB" -> Map.of("BE","백엔드","FE","프런트엔드","DEV","개발","QA","품질관리","PM","프로젝트 관리","0001","시험 직무");
                case "GRADE" -> Map.of("SR","고급","MD","중급");
                case "EMP_TYPE" -> Map.of("FT","정규직","OUT","외주");
                case "EXT_PSBL" -> Map.of("L","낮음","M","보통","H","높음");
                case "TENT_STAT" -> Map.of("ACT","활성","CNVT","전환됨");
                default -> Map.of();
            };
            return values.entrySet().stream().map(e -> new com.sicost.shared.SharedQueryDtos.CodeLookup(e.getKey(),e.getValue(),null,null,null,null,null)).toList();
        });
        return new ExportLabels(codes);
    }
    public static void configure(ExportLabels labels) {
        var real = labels();
        when(labels.labels(anyString())).thenAnswer(call -> real.labels(call.getArgument(0)));
    }
}
