package com.sicost.b2;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b2.FinancialImportDtos.*;

@Component
public class FinancialImportDefinition {
    public static final List<String> HEADERS=List.of("구분","판관비 항목","세부 항목","금액","부가세","입출금일","매달 반복","비고");
    private final ExcelImportService imports;
    public FinancialImportDefinition(ExcelImportService imports) { this.imports=imports; }
    public String filename(MultipartFile file) { return imports.filename(file); }
    public Parsed parse(MultipartFile file) {
        try { return imports.parse(file,new ImportDefinition(HEADERS,Set.of("구분","세부 항목","금액"),Set.of(),Set.of("입출금일"),Map.of(),Set.of("금액","부가세"))); }
        catch(ApiException e) { throw new ApiException(e.status(),e.status()==413?"PAYLOAD_TOO_LARGE":e.status()==415?"UNSUPPORTED_MEDIA_TYPE":"INVALID_INPUT",e.getMessage(),"file",null); }
    }
    public Checked check(Parsed parsed,List<FinancialImportDtos.Code> codes,int replace,int preserve) {
        List<Issue> issues=new ArrayList<>(parsed.fileIssues());List<CheckedRow> rows=new ArrayList<>();int errors=0,warnings=0;
        for(SourceRow row:parsed.rows()) {
            List<Issue> own=new ArrayList<>();
            for(String field:HEADERS)if(row.cells().get(field).error()!=null)add(own,row,field,row.cells().get(field).error());
            String cls=code(row,"구분","FIN_CLS",codes,own,false);
            String sga=code(row,"판관비 항목","SGA_ITEM",codes,own,true);
            String name=text(row,"세부 항목",50,true,own),remark=text(row,"비고",500,false,own);
            BigDecimal amount=amount(row,"금액",true,own),vat=amount(row,"부가세",false,own);
            if(amount.add(vat).compareTo(new BigDecimal("999999999999999"))>0)add(own,row,"금액","금액과 부가세 합계가 허용 범위를 초과합니다.");
            LocalDate date=null;String dateText=row.cells().get("입출금일").text();
            if(!dateText.isEmpty())try {
                if(!dateText.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();
                date=LocalDate.parse(dateText);
            }catch(RuntimeException e){add(own,row,"입출금일","실제 달력일 YYYY-MM-DD를 입력해 주세요.");}
            String repeat=row.cells().get("매달 반복").text().toUpperCase(Locale.ROOT);if(repeat.isEmpty())repeat="N";
            if(!Set.of("Y","N").contains(repeat))add(own,row,"매달 반복","Y 또는 N을 입력해 주세요.");
            if(own.stream().anyMatch(i->"ERR".equals(i.severity())))errors++;
            else { if(!own.isEmpty())warnings++;rows.add(new CheckedRow(row.number(),new Values(cls,sga,name,amount,vat,date,repeat,remark))); }
            issues.addAll(own);
        }
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparingInt(i->HEADERS.indexOf(i.columnName())).thenComparing(Issue::severity));
        List<Issue> ordered=new ArrayList<>();for(Issue i:issues)ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        return new Checked(List.copyOf(rows),new Summary(parsed.rows().size(),parsed.rows().size()-errors-warnings,warnings,errors,replace,preserve),List.copyOf(ordered));
    }
    private String code(SourceRow row,String field,String group,List<FinancialImportDtos.Code> codes,List<Issue> issues,boolean optional) {
        String value=row.cells().get(field).text();if(optional&&value.isEmpty())return null;
        Set<String> matches=new HashSet<>();codes.stream().filter(c->group.equals(c.groupId())&&(value.equals(c.name())||value.equals(c.source()))).forEach(c->matches.add(c.code()));
        if(matches.size()!=1){add(issues,row,field,"사용 중인 코드 이름 또는 명시된 표기 대응을 하나로 확인해 주세요.");return null;}
        String code=matches.iterator().next();
        if(group.equals("FIN_CLS")&&!Set.of("SGA","NOPR_IN","NOPR_OUT").contains(code)){add(issues,row,field,"허용된 재무 구분이 아닙니다.");return null;}
        return code;
    }
    private String text(SourceRow row,String field,int max,boolean required,List<Issue> issues) {
        String v=row.cells().get(field).text();if((required&&v.isEmpty())||v.codePointCount(0,v.length())>max)add(issues,row,field,"필수 여부와 최대 "+max+"자를 확인해 주세요.");return v.isEmpty()?null:v;
    }
    private BigDecimal amount(SourceRow row,String field,boolean required,List<Issue> issues) {
        String v=row.cells().get(field).text();if(v.isEmpty()&&!required)return BigDecimal.ZERO;
        try { return ProjectInputPolicy.amount(v,field); }
        catch(ApiException e){add(issues,row,field,"원 단위 정수 0~999999999999999를 입력해 주세요. 빈 필수 금액은 허용하지 않습니다.");return BigDecimal.ZERO;}
    }
    private void add(List<Issue> issues,SourceRow row,String field,String reason) { issues.add(new Issue(0,"ERR",row.number(),field,excerpt(row.cells().get(field).text(),200),excerpt(reason,300))); }
    private String excerpt(String v,int max) { return v.codePointCount(0,v.length())<=max?v:v.substring(0,v.offsetByCodePoints(0,max-3))+"..."; }
}
