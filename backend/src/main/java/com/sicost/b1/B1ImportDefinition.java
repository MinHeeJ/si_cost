package com.sicost.b1;

import com.sicost.b7.B7Dtos.CellValue;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.ExcelImportService;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b1.B1Dtos.*;

@Component
public class B1ImportDefinition {
    public static final List<String> HEADERS=List.of("관리번호","이름","급여","4대보험 회사","4대보험 본인","근로소득세","외주 공급가");
    private final ExcelImportService imports;
    public B1ImportDefinition(ExcelImportService imports) { this.imports=imports; }
    public String filename(MultipartFile file) {
        try { return imports.filename(file); } catch(ApiException e) { throw translated(e); }
    }
    public Parsed parse(MultipartFile file) {
        try { return imports.parse(file,new ImportDefinition(HEADERS,Set.of("관리번호"),Set.of("관리번호"),Set.of(),Map.of(),Set.copyOf(HEADERS.subList(2,7)))); }
        catch(ApiException e) { throw translated(e); }
    }
    private ApiException translated(ApiException e) {
        return new ApiException(e.status(),e.status()==413?"PAYLOAD_TOO_LARGE":e.status()==415?"UNSUPPORTED_MEDIA_TYPE":"INVALID_INPUT",e.getMessage(),e.field(),e.details());
    }
    public Checked check(Parsed parsed,List<Developer> developers,List<Rate> rateRows) {
        Map<String,Developer> byNumber=new HashMap<>();developers.forEach(d->byNumber.put(d.managementNo(),d));
        Map<String,BigDecimal> rates=new HashMap<>();rateRows.forEach(r->rates.put(r.code(),r.value()));
        Map<String,Long> occurrences=new HashMap<>();
        for(SourceRow row:parsed.rows()) {
            Developer d=byNumber.get(row.cells().get("관리번호").text());
            if(d!=null)occurrences.merge(d.id(),1L,Long::sum);
        }
        List<Issue> issues=new ArrayList<>(parsed.fileIssues());List<CheckedRow> accepted=new ArrayList<>();int errors=0,warnings=0;
        for(SourceRow row:parsed.rows()) {
            List<Issue> own=new ArrayList<>();String number=row.cells().get("관리번호").text();Developer d=byNumber.get(number);
            for(String field:HEADERS.subList(0,2)) {
                CellValue cell=row.cells().get(field);
                if(cell.error()!=null)add(own,row,field,"ERR",cell.error());
            }
            if(number.isBlank()||number.codePointCount(0,number.length())>20||d==null)add(own,row,"관리번호","ERR","등록된 관리번호를 입력해 주세요.");
            if(d!=null) {
                if(occurrences.get(d.id())>1)add(own,row,"관리번호","ERR","파일 내 개발자가 중복되었습니다.");
                String name=row.cells().get("이름").text();
                if(!name.isEmpty()&&!name.equals(d.name()))add(own,row,"이름","ERR","현재 개발자 이름과 일치하지 않습니다.");
                if(d.employeeType()==null||!Set.of("FT","CT","FL","OS").contains(d.employeeType()))add(own,row,"관리번호","ERR","직원구분은 FT/CT/FL/OS여야 합니다.");
                else try { accepted.add(new CheckedRow(row.number(),d,calculate(row,d,rates,own))); }
                catch(ApiException e) { add(own,row,HEADERS.contains(e.field())?e.field():"급여","ERR",e.getMessage()); }
            }
            if(own.stream().anyMatch(i->i.severity().equals("ERR")))errors++;
            else if(!own.isEmpty())warnings++;
            issues.addAll(own);
        }
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparingInt(i->Math.max(-1,HEADERS.indexOf(i.columnName()))).thenComparing(Issue::severity));
        List<Issue> ordered=new ArrayList<>();for(Issue i:issues)ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        return new Checked(List.copyOf(accepted),new Summary(parsed.rows().size(),parsed.rows().size()-errors-warnings,warnings,errors,parsed.fileIssues().size(),0),List.copyOf(ordered));
    }
    private Values calculate(SourceRow row,Developer d,Map<String,BigDecimal> rates,List<Issue> issues) {
        boolean os=d.employeeType().equals("OS"),fl=d.employeeType().equals("FL");
        Set<String> discarded=os?Set.copyOf(HEADERS.subList(2,6)):fl?Set.of("4대보험 회사","4대보험 본인","외주 공급가"):Set.of("외주 공급가");
        for(String field:discarded) {
            CellValue c=row.cells().get(field);
            if(!c.text().isEmpty()||c.error()!=null)add(issues,row,field,"WARN","해당 직원구분에 적용하지 않는 값은 반영하지 않습니다.");
        }
        BigDecimal zero=BigDecimal.ZERO;
        if(os)return new Values(zero,zero,zero,zero,zero,zero,zero,null,amount(row,"외주 공급가"),d.employeeType(),d.directClass());
        BigDecimal pay=amount(row,"급여"),income=amount(row,"근로소득세");
        BigDecimal allowance=zero,company=zero,self=zero,retire=zero,withholding;
        if(!fl) {
            if("TEAM".equals(d.position()))allowance=rate(rates,"ALLOW_TEAM");
            else if("GROUP".equals(d.position()))allowance=rate(rates,"ALLOW_GROUP");
            self=amount(row,"4대보험 본인");
            CellValue c=row.cells().get("4대보험 회사");
            company=c.text().isEmpty()&&c.error()==null?down(pay.multiply(rate(rates,"INS_RATE")).movePointLeft(2)):amount(row,"4대보험 회사");
            if(d.employeeType().equals("FT"))retire=down(pay.add(allowance).multiply(rate(rates,"SEV_RATE")).movePointLeft(2));
        }
        withholding=d.employeeType().equals("FT")?zero:down(pay.add(allowance).multiply(new BigDecimal("0.033")));
        BigDecimal real=pay.add(allowance).subtract(self).subtract(withholding).subtract(income);
        for(BigDecimal value:List.of(pay,allowance,company,self,retire,income,withholding,real))
            if(value.abs().compareTo(new BigDecimal("999999999999999"))>0)throw invalid("급여","계산 결과가 NUMERIC(15) 범위를 초과합니다.");
        return new Values(pay,allowance,company,self,retire,income,withholding,real,zero,d.employeeType(),d.directClass());
    }
    private BigDecimal amount(SourceRow row,String field) {
        CellValue cell=row.cells().get(field);
        if(cell.error()!=null)throw invalid(field,cell.error());
        if(cell.text().isEmpty())return BigDecimal.ZERO;
        try { return ProjectInputPolicy.amount(cell.text(),field); }
        catch(ApiException e) { throw invalid(field,"원 단위 정수와 허용 범위를 확인해 주세요."); }
    }
    private BigDecimal rate(Map<String,BigDecimal> rates,String key) {
        BigDecimal value=rates.get(key);
        if(value==null||value.signum()<0)throw invalid("급여","현재 활성 ALWNC "+key+" 기준값을 확인해 주세요.");
        return downIfAmount(key,value);
    }
    private BigDecimal downIfAmount(String key,BigDecimal value) {
        if(key.startsWith("ALLOW_")&&value.stripTrailingZeros().scale()>0)throw invalid("급여","수당은 원 단위 정수여야 합니다.");
        return value;
    }
    private BigDecimal down(BigDecimal value) { return value.setScale(0,RoundingMode.DOWN); }
    private ApiException invalid(String field,String message) { return new ApiException(400,"INVALID_INPUT",message,field,null); }
    private void add(List<Issue> issues,SourceRow row,String field,String severity,String reason) {
        String value=row.cells().get(field).text();
        issues.add(new Issue(0,severity,row.number(),field,excerpt(value,200),excerpt(reason,300)));
    }
    private String excerpt(String value,int max) { return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max)); }
}
