package com.sicost.migration;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;

@Component
public class HistoricalProfitLossImportDefinition {
    public static final List<String> HEADERS=List.of("연월","매출","매출원가","판관비 합계","영업이익","배분원가","대기원가","직접경비","매출총이익","판관비 입력","간접인건비","연구개발비","전월조정","영업외수익","영업외비용","법인세","당기순이익");
    public static final List<String> FIELDS=List.of("salesAmt","salesCostAmt","sgaTotAmt","oprtPrftAmt","allocCostAmt","idleCostAmt","drctExpsAmt","grossPrftAmt","sgaInputAmt","indrctLaborAmt","rndAmt","prevAdjAmt","noprIncmAmt","noprExpsAmt","corpTaxAmt","netPrftAmt");
    public static final List<String> COLUMNS=List.of("SALES_AMT","SALES_COST_AMT","SGA_TOT_AMT","OPRT_PRFT_AMT","ALLOC_COST_AMT","IDLE_COST_AMT","DRCT_EXPS_AMT","GROSS_PRFT_AMT","SGA_INPUT_AMT","INDRCT_LABOR_AMT","RND_AMT","PREV_ADJ_AMT","NOPR_INCM_AMT","NOPR_EXPS_AMT","CORP_TAX_AMT","NET_PRFT_AMT");
    private static final BigDecimal MAX=new BigDecimal("999999999999999");
    private final ExcelImportService imports;
    public HistoricalProfitLossImportDefinition(ExcelImportService imports) { this.imports=imports; }
    public String filename(MultipartFile file) {
        try { return imports.filename(file); } catch(ApiException e) { throw translated(e); }
    }
    public Parsed parse(MultipartFile file) {
        try { return imports.parse(file,new ImportDefinition(HEADERS,Set.copyOf(HEADERS.subList(0,5)),Set.of(),Set.of(),Map.of(),Set.copyOf(HEADERS.subList(1,17)),Set.of("연월"))); }
        catch(ApiException e) { throw translated(e); }
    }
    private ApiException translated(ApiException e) { return new ApiException(e.status(),e.status()==413?"PAYLOAD_TOO_LARGE":e.status()==415?"UNSUPPORTED_MEDIA_TYPE":"VALIDATION_ERROR",e.getMessage(),"file",null); }
    public static YearMonth base(String value) {
        if(value==null||!value.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])")) throw new IllegalStateException("운영 시작월을 확인해 주세요.");
        return YearMonth.parse(value.substring(0,4)+"-"+value.substring(4));
    }
    public static List<String> months(String base) {
        YearMonth end=base(base);List<String> result=new ArrayList<>();
        for(YearMonth m=YearMonth.of(end.getYear()-1,1);m.isBefore(end);m=m.plusMonths(1)) result.add(m.toString());
        return result;
    }
    public Checked check(Parsed parsed,String base,List<Stored> old) {
        List<String> allowed=months(base);List<Issue> issues=new ArrayList<>(parsed.fileIssues());
        Map<Integer,List<Issue>> own=new LinkedHashMap<>();Map<String,List<SourceRow>> seen=new HashMap<>();List<Row> rows=new ArrayList<>();
        for(SourceRow source:parsed.rows()) {
            List<Issue> problems=new ArrayList<>();own.put(source.number(),problems);
            for(String h:HEADERS) if(source.cells().get(h).error()!=null) add(problems,source,h,"ERR",source.cells().get(h).error());
            String month=source.cells().get("연월").text();
            if(!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")||!allowed.contains(month)) add(problems,source,"연월","ERR","허용 범위 "+allowed.get(0)+"~"+allowed.get(allowed.size()-1)+"의 연월만 입력해 주세요.");
            seen.computeIfAbsent(month,k->new ArrayList<>()).add(source);
            Map<String,BigDecimal> amounts=new LinkedHashMap<>();
            for(int i=0;i<FIELDS.size();i++) amounts.put(FIELDS.get(i),amount(source,HEADERS.get(i+1),i<4,problems));
            if(problems.stream().noneMatch(i->"ERR".equals(i.severity()))) {
                formula(source,amounts,problems,"매출원가","salesCostAmt",List.of("allocCostAmt","idleCostAmt","drctExpsAmt"),List.of());
                formula(source,amounts,problems,"매출총이익","grossPrftAmt",List.of("salesAmt"),List.of("salesCostAmt"));
                formula(source,amounts,problems,"판관비 합계","sgaTotAmt",List.of("sgaInputAmt","indrctLaborAmt","rndAmt"),List.of());
                formula(source,amounts,problems,"영업이익","oprtPrftAmt",List.of("grossPrftAmt","prevAdjAmt"),List.of("sgaTotAmt"));
                formula(source,amounts,problems,"당기순이익","netPrftAmt",List.of("oprtPrftAmt","noprIncmAmt"),List.of("noprExpsAmt","corpTaxAmt"));
            }
            rows.add(new Row(source.number(),month,amounts));
        }
        for(var group:seen.values()) if(group.size()>1) for(var r:group) add(own.get(r.number()),r,"연월","ERR","연월이 중복되었습니다.");
        int errors=0,warnings=0;List<Change> changes=new ArrayList<>();
        for(Row row:rows) {
            var problems=own.get(row.rowNumber());boolean error=problems.stream().anyMatch(i->"ERR".equals(i.severity()));
            if(error)errors++;else {
                if(!problems.isEmpty())warnings++;
                Stored previous=old.stream().filter(r->r.month().equals(row.month().replace("-",""))).findFirst().orElse(null);
                boolean changed=previous==null||!"Y".equals(previous.fixed())||!"Y".equals(previous.migrated())||!same(previous.amounts(),row.amounts());
                changes.add(new Change(row.month(),row.rowNumber(),changed,previous==null?null:wire(previous.amounts()),wire(row.amounts())));
            }
            issues.addAll(problems);
        }
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparing(Issue::columnName));List<Issue> ordered=new ArrayList<>();
        for(Issue i:issues)ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        List<String> notices=ordered.stream().filter(i->"WARN".equals(i.severity())).map(Issue::reason).distinct().toList();
        return new Checked(rows,new Summary(rows.size(),rows.size()-errors-warnings,warnings,errors,parsed.fileIssues().size()),ordered,notices,changes);
    }
    private BigDecimal amount(SourceRow row,String h,boolean required,List<Issue> issues) {
        String text=row.cells().get(h).text().strip();
        if(!required&&(text.isEmpty()||text.equals("–")))return null;
        try {
            if(!text.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"))throw new IllegalArgumentException();
            BigDecimal value=new BigDecimal(text.replace(",",""));if(value.abs().compareTo(MAX)>0)throw new IllegalArgumentException();return value.setScale(0);
        }catch(RuntimeException e){add(issues,row,h,"ERR","필수 여부와 부호 있는 원 단위 정수 ±999999999999999 범위를 확인해 주세요.");return null;}
    }
    private void formula(SourceRow row,Map<String,BigDecimal> a,List<Issue> issues,String h,String target,List<String> plus,List<String> minus) {
        if(a.get(target)==null||plus.stream().anyMatch(k->a.get(k)==null)||minus.stream().anyMatch(k->a.get(k)==null))return;
        BigDecimal expected=BigDecimal.ZERO;for(String k:plus)expected=expected.add(a.get(k));for(String k:minus)expected=expected.subtract(a.get(k));
        if(expected.compareTo(a.get(target))!=0)add(issues,row,h,"WARN",h+" 계산식과 입력값이 다릅니다. 입력 원값을 그대로 반영합니다.");
    }
    private void add(List<Issue> issues,SourceRow row,String h,String severity,String reason) {
        String value=row.cells().get(h).text();if(value.length()>200)value=value.substring(0,200);
        issues.add(new Issue(0,severity,row.number(),h,value,reason));
    }
    public static boolean same(Map<String,BigDecimal> a,Map<String,BigDecimal> b) {
        return FIELDS.stream().allMatch(k->a.get(k)==null?b.get(k)==null:b.get(k)!=null&&a.get(k).compareTo(b.get(k))==0);
    }
    public static Map<String,String> wire(Map<String,BigDecimal> a) {
        Map<String,String> result=new LinkedHashMap<>();FIELDS.forEach(k->result.put(k,a.get(k)==null?null:a.get(k).toPlainString()));
        result.put("grossProfitRate",rate(a.get("grossPrftAmt"),a.get("salesAmt")));
        result.put("operatingProfitRate",rate(a.get("oprtPrftAmt"),a.get("salesAmt")));
        result.put("netProfitRate",rate(a.get("netPrftAmt"),a.get("salesAmt")));
        result.put("idleCostShare",rate(a.get("idleCostAmt"),a.get("salesCostAmt")));return result;
    }
    private static String rate(BigDecimal a,BigDecimal denominator) {
        return com.sicost.businessplansops.BusinessPlanActuals.ratio(a==null?null:a.toPlainString(),denominator==null?null:denominator.toPlainString(),false);
    }
}
