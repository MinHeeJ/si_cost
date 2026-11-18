package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import static com.sicost.common.excel.ExcelExportService.*;

final class DashboardWorkbook {
    private DashboardWorkbook() {}
    private static final Set<String> DECIMALS = Set.of("value","previousValue","difference","changeRate","targetValue","achievementRate","profitRate",
        "targetGapPercentagePoints","overdueAmount","amount","personCount","marginRate","budgetConsumptionRate","salesAmount","salesCostAmount",
        "salesTargetAmount","requiredPersons","availablePersons","differencePersons","projectCostAmount","contributionMarginRate","winProbabilityRate",
        "expectedAmount","weightedExpectedAmount","closingBalance","minimumCashAmount");
    private static final Set<String> MONEY = Set.of("previousValue","difference","targetValue","overdueAmount","amount","salesAmount","salesCostAmount",
        "salesTargetAmount","projectCostAmount","expectedAmount","weightedExpectedAmount","closingBalance","minimumCashAmount");
    private static final List<String> REGIONS = List.of("metrics","actions","closeProgress","projectAlerts","salesCostTrend","supplyForecast","salesParts","pipeline","cashForecast");
    private static final List<String> TITLES = List.of("요약","확인할 일","마감 진행","프로젝트 경보","매출·원가 추이","인력 수급 예측","영업 파트별","영업 파이프라인","자금 3개월");

    static WorkbookData create(JsonNode data, String unit) {
        List<Sheet> sheets = new ArrayList<>();
        var criteria = new LinkedHashMap<String,JsonNode>();
        data.fields().forEachRemaining(e -> { if (!REGIONS.contains(e.getKey())) flatten(e.getKey(),e.getValue(),criteria); });
        sheets.add(new Sheet("조회 기준",List.of(new Column("field","항목",CellType.TEXT),new Column("text","텍스트값",CellType.TEXT),new Column("number","숫자값",CellType.NUMBER)),sink ->
            criteria.forEach((key,value) -> sink.accept(Arrays.asList(key,value.isNumber()?null:text(value),value.isNumber()?value.decimalValue():null)))));
        long count = 0;
        for (int i=0;i<REGIONS.size();i++) {
            if(!data.has(REGIONS.get(i)))continue;
            JsonNode region = data.required(REGIONS.get(i));
            List<JsonNode> rows = new ArrayList<>();
            if (region.isArray()) region.forEach(rows::add); else rows.add(region);
            count += rows.size();
            sheets.add(table(TITLES.get(i),rows,unit));
        }
        return new WorkbookData(sheets,List.of(new Information("단위",unit),new Information("원금액 단위","원"),
            new Information("선택월 Q",data.path("selectedMonth").asText()),new Information("오늘 D",data.path("today").asText()),
            new Information("현재월 M",data.path("currentMonth").asText()),new Information("조회 기준시각",data.path("observedAt").asText()),
            new Information("미확인 표시","빈 숫자셀은 미확인(NULL)이며 실제 0과 다릅니다. 근거의 상태와 사유를 확인하세요.")),
            count,data.path("selectedMonth").asText().replace("-",""));
    }
    private static Sheet table(String title,List<JsonNode> rows,String unit) {
        List<Map<String,JsonNode>> flattened = new ArrayList<>();
        Set<String> paths = new LinkedHashSet<>();
        for (JsonNode row:rows) {
            var values = new LinkedHashMap<String,JsonNode>(); flatten("",row,values); flattened.add(values); paths.addAll(values.keySet());
        }
        // Even an empty successful section retains its header and does not manufacture data rows.
        if (paths.isEmpty()) paths.add("status");
        List<Column> columns = new ArrayList<>();
        for (String path:paths) {
            boolean numeric = flattened.stream().anyMatch(r -> numeric(path,r.get(path)));
            boolean money = monetary(path,rows);
            columns.add(new Column(path,path+(money?" (원)":""),numeric||money?CellType.NUMBER:CellType.TEXT));
            if (money) columns.add(new Column(path+"Display",path+" ("+unit+")",CellType.NUMBER));
        }
        return new Sheet(title,columns,sink -> {
            for (Map<String,JsonNode> row:flattened) {
                List<Object> cells = new ArrayList<>();
                for (String path:paths) {
                    JsonNode value = row.get(path);
                    boolean number = columns.stream().filter(c -> c.id().equals(path)).findFirst().orElseThrow().type()==CellType.NUMBER;
                    BigDecimal decimal = value==null||value.isNull()?null:number?new BigDecimal(value.asText()):null;
                    cells.add(number?decimal:text(value));
                    if (monetary(path,rows)) cells.add(decimal==null?null:decimal.movePointLeft(unit.equals("원")?0:unit.equals("천원")?3:6));
                }
                sink.accept(cells);
            }
        });
    }
    private static boolean numeric(String path,JsonNode value) {
        return value!=null&&!value.isNull()&&(value.isNumber()||DECIMALS.contains(leaf(path))&&value.isTextual());
    }
    private static boolean monetary(String path,List<JsonNode> rows) {
        String leaf = leaf(path);
        if (leaf.equals("value")||leaf.equals("previousValue")||leaf.equals("difference")||leaf.equals("targetValue"))
            return false;
        return MONEY.contains(leaf);
    }
    private static String leaf(String path) { return path.substring(path.lastIndexOf('.')+1); }
    private static String text(JsonNode value) { return value==null||value.isNull()?null:value.isValueNode()?value.asText():value.toString(); }
    private static void flatten(String prefix,JsonNode value,Map<String,JsonNode> target) {
        if (value.isObject()) value.fields().forEachRemaining(e -> flatten(prefix.isEmpty()?e.getKey():prefix+"."+e.getKey(),e.getValue(),target));
        else if (value.isArray()) {
            for (int i=0;i<value.size();i++) flatten(prefix+"."+i,value.get(i),target);
            if (value.isEmpty()) target.put(prefix,value);
        } else target.put(prefix,value);
    }
}
