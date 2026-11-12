package com.sicost.b6;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.ExcelImportService;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b6.B6Dtos.*;

@Component
public class B6ImportDefinition {
    public static final List<String> GUIDE=List.of("B6 턴키 배분표 · 월 금액 단위는 항상 원입니다.",
        "구분은 사람 또는 항목입니다. 사람은 실제 관리번호와 현재 이름을 입력하세요. 중지 인력도 기존 번호로 입력할 수 있습니다.",
        "항목은 항목명 1~50자를 입력합니다. 같은 항목명은 별도 행으로 입력할 수 있습니다. 사람은 중복할 수 없습니다.",
        "월 금액은 0~999999999999999 정수입니다. 빈 월은 0입니다. 합계는 검사 참고값으로 저장하지 않습니다.",
        "단계는 업로드 창에서 선택합니다. 같은 단계 전체를 교체하며 다른 단계는 보존합니다.",
        "계약기간의 모든 YYYY-MM 월 제목을 유지하세요. 프로젝트ID·단계·직무 열은 사용하지 않습니다.");
    private final ExcelImportService imports;
    public B6ImportDefinition(ExcelImportService imports) {this.imports=imports;}
    public String filename(MultipartFile file) {return imports.filename(file);}
    public static List<String> months(Project project) {
        List<String> months=new ArrayList<>();var end=YearMonth.from(project.endDate());
        for(var m=YearMonth.from(project.startDate());!m.isAfter(end);m=m.plusMonths(1))months.add(m.toString());
        return months;
    }
    public static List<String> headers(Project project) {
        var headers=new ArrayList<>(List.of("구분","관리번호","이름","항목명"));headers.addAll(months(project));headers.add("합계");return headers;
    }
    public Parsed parse(MultipartFile file,Project project) {
        if(file==null||file.isEmpty())throw invalidFile("검사할 파일을 선택해 주세요.");
        var headers=headers(project);
        try {
            var numeric=new HashSet<>(months(project));numeric.add("합계");
            var parsed=imports.parse(file,new ImportDefinition(headers,Set.copyOf(headers.subList(0,headers.size()-1)),Set.of("관리번호"),Set.of(),Map.of(),numeric));
            for(Issue issue:parsed.fileIssues())if(issue.inputValue().matches("[0-9]{4}.*")||issue.inputValue().matches(".*[0-9]{4}[-/.].*"))
                throw new B6HeaderException("계약 밖 또는 형식이 잘못된 월 제목입니다: "+issue.inputValue());
            return parsed;
        } catch(ApiException e) {
            if(e instanceof B6HeaderException)throw e;
            if(e.code().equals("INVALID_FILE_HEADER"))throw new B6HeaderException(e.getMessage());
            if(e.status()==400)throw invalidFile(e.getMessage());throw e;
        }
    }
    public Checked validate(Parsed parsed,Project project,String step,B6Sources sources) {
        List<Issue> issues=new ArrayList<>();parsed.fileIssues().forEach(i->add(issues,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        Map<String,Developer> byNumber=new HashMap<>();sources.developers().forEach(d->byNumber.put(d.managementNo(),d));
        Map<String,List<Integer>> duplicateRows=new LinkedHashMap<>();List<Cell> cells=new ArrayList<>();
        int rowSn=0;
        for(SourceRow row:parsed.rows()) {
            ++rowSn;for(var entry:row.cells().entrySet())if(entry.getValue().error()!=null)add(issues,"ERR",row.number(),entry.getKey(),entry.getValue().text(),entry.getValue().error());
            String type=text(row,"구분"),number=text(row,"관리번호"),name=text(row,"이름"),item=text(row,"항목명");
            String rowType=type.equals("사람")?"DEV":type.equals("항목")?"ITEM":null;String developerId=null,itemName=null;
            if(rowType==null)add(issues,"ERR",row.number(),"구분",type,"사람 또는 항목을 입력해 주세요.");
            if("DEV".equals(rowType)) {
                Developer developer=byNumber.get(number);
                if(number.isEmpty()||developer==null)add(issues,"ERR",row.number(),"관리번호",number,"실제 등록된 관리번호를 입력해 주세요.");
                else {
                    developerId=developer.id();duplicateRows.computeIfAbsent(developerId,k->new ArrayList<>()).add(row.number());
                    if(!name.equals(developer.name()))add(issues,"ERR",row.number(),"이름",name,"현재 인력 이름과 일치해야 합니다.");
                    if("N".equals(developer.useYn()))add(issues,"WARN",row.number(),"관리번호",number,"중지 인력입니다. 기존 관리번호로 반영할 수 있습니다.");
                }
                if(name.isEmpty())add(issues,"ERR",row.number(),"이름",name,"이름은 필수입니다.");
                if(!item.isEmpty())add(issues,"WARN",row.number(),"항목명",item,"사람 행의 항목명은 반영하지 않습니다.");
            } else if("ITEM".equals(rowType)) {
                itemName=item;
                if(item.isEmpty()||item.codePointCount(0,item.length())>50)add(issues,"ERR",row.number(),"항목명",item,"항목명은 1~50자로 입력해 주세요.");
                if(!number.isEmpty())add(issues,"WARN",row.number(),"관리번호",number,"항목 행의 관리번호는 반영하지 않습니다.");
                if(!name.isEmpty())add(issues,"WARN",row.number(),"이름",name,"항목 행의 이름은 반영하지 않습니다.");
            }
            BigDecimal sum=BigDecimal.ZERO;
            for(String month:months(project)) {
                var amount=amount(row,month,true,issues);sum=sum.add(amount);
                cells.add(new Cell(step,rowSn,month.replace("-",""),rowType,developerId,itemName,amount));
            }
            if(!text(row,"합계").isEmpty()) {
                int previous=issues.size();var supplied=amount(row,"합계",false,issues);
                if(previous==issues.size()&&supplied.compareTo(sum)!=0)add(issues,"WARN",row.number(),"합계",text(row,"합계"),"입력 합계와 월 금액 합계가 다릅니다. 월 합계 "+sum.toPlainString()+"원만 반영합니다.");
            }
        }
        duplicateRows.values().stream().filter(rows->rows.size()>1).forEach(rows->rows.forEach(row->add(issues,"ERR",row,"관리번호","","같은 개발자가 파일 안에서 중복되었습니다. 관련 행을 모두 확인해 주세요.")));
        BigDecimal uploadedTotal=cells.stream().map(Cell::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        boolean valid=issues.stream().noneMatch(i->i.severity().equals("ERR"));
        var comparison=valid?B6Comparison.compare(project,B6Comparison.substitute(sources.stored(),step,cells),sources.actuals(),sources.developers()):null;
        if(comparison!=null)comparison.warnings().forEach(w->add(issues,"WARN",0,"[표]","",w));
        Set<Integer> errors=new HashSet<>(),warnings=new HashSet<>();
        for(Issue issue:issues)if(!Set.of("[파일]","[표]").contains(issue.columnName())) {
            if(issue.severity().equals("ERR"))errors.add(issue.rowNumber());else warnings.add(issue.rowNumber());
        }
        warnings.removeAll(errors);
        var summary=new Summary(parsed.rows().size(),parsed.rows().size()-errors.size()-warnings.size(),warnings.size(),errors.size(),parsed.fileIssues().size(),comparison==null?0:comparison.warnings().size());
        return new Checked(List.copyOf(cells),summary,List.copyOf(issues),comparison,uploadedTotal.toPlainString());
    }
    public record B6Sources(List<Cell> stored,List<Actual> actuals,List<Developer> developers) {}
    private String text(SourceRow row,String key) {var value=row.cells().get(key);return value==null?"":value.text().strip();}
    private BigDecimal amount(SourceRow row,String key,boolean blankZero,List<Issue> issues) {
        String value=text(row,key);
        if(blankZero&&value.isEmpty())return BigDecimal.ZERO;
        try {return ProjectInputPolicy.amount(value,key);}
        catch(ApiException e) {add(issues,"ERR",row.number(),key,value,"원 단위 0~999999999999999 정수를 입력해 주세요.");return BigDecimal.ZERO;}
    }
    private void add(List<Issue> issues,String severity,int row,String column,String value,String reason) {
        issues.add(new Issue(issues.size()+1,severity,row,excerpt(column,50),excerpt(value,200),excerpt(reason,300)));
    }
    private String excerpt(String value,int max) {return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max-3))+"...";}
    private ApiException invalidFile(String message) {return new ApiException(400,"INVALID_INPUT",message,"file",null);}
    public static final class B6HeaderException extends ApiException {
        public B6HeaderException(String message) {super(400,"INVALID_INPUT",message,"file",null);}
    }
}
