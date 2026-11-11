package com.sicost.b7;

import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.b7.B7Dtos.*;
import static com.sicost.b7.ExcelImportService.*;

@Component
public class B7ImportDefinition {
    private final B7Mapper mapper;
    public B7ImportDefinition(B7Mapper mapper) { this.mapper=mapper; }
    public Checked validate(Parsed file) {
        List<Code> codes=mapper.codes(); List<Existing> existing=mapper.clients();
        Set<String> numbers=new HashSet<>(), names=new HashSet<>();
        existing.forEach(c->{numbers.add(c.businessNo().replace("-","")); names.add(c.name());});
        Map<String,Integer> occurrences=new HashMap<>();
        file.rows().forEach(r->{String n=r.cells().get("사업자등록번호").text().replace("-","");occurrences.merge(n,1,Integer::sum);});
        List<Issue> issues=new ArrayList<>(file.fileIssues()); List<Client> clients=new ArrayList<>();
        int success=0,warnings=0,errors=0;
        for(SourceRow row:file.rows()) {
            List<Issue> problems=new ArrayList<>();
            String type=resolve(row,"구분","CLNT_TYPE",true,codes,problems);
            String name=text(row,"상호",50,true,problems);
            String business=text(row,"사업자등록번호",12,true,problems);
            String digits=business==null?"":business.replace("-","");
            if(business!=null && !business.matches("(?:[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})")) issue(row,"사업자등록번호","ERR","사업자등록번호는 숫자 10자리 또는 3-2-5 형식이어야 합니다.",problems);
            if(occurrences.getOrDefault(digits,0)>1) issue(row,"사업자등록번호","ERR","파일 안에서 사업자등록번호가 중복됩니다.",problems);
            if(numbers.contains(digits)) issue(row,"사업자등록번호","ERR","이미 등록된 사업자등록번호입니다. 중지 거래처도 포함합니다.",problems);
            if(name!=null && names.contains(name)) issue(row,"상호","WARN","같은 상호가 이미 등록되어 있습니다.",problems);
            String ceo=text(row,"대표자",20,false,problems), contact=text(row,"담당자",20,false,problems), phone=text(row,"연락처",20,false,problems);
            String industry=resolve(row,"업종","INDUTY",false,codes,problems);
            String email=null,bank=null,account=null,depositor=null;
            if("CUST".equals(type)||"BOTH".equals(type)) {
                email=text(row,"세금계산서 이메일",100,true,problems);
                if(email!=null&&!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) issue(row,"세금계산서 이메일","ERR","이메일 형식을 확인해 주세요.",problems);
            } else if("VNDR".equals(type)) discard(row,List.of("세금계산서 이메일"),problems);
            if("VNDR".equals(type)||"BOTH".equals(type)) {
                bank=resolve(row,"은행","BANK",true,codes,problems);
                account=text(row,"계좌번호",20,true,problems); depositor=text(row,"예금주",30,true,problems);
                if(account!=null&&!account.matches("(?=.*[0-9])[0-9-]{8,20}")) issue(row,"계좌번호","ERR","계좌번호는 숫자·하이픈으로 8~20자 입력해 주세요.",problems);
            } else if("CUST".equals(type)) discard(row,List.of("은행","계좌번호","예금주"),problems);
            if(type!=null&&!Set.of("CUST","VNDR","BOTH").contains(type)) issue(row,"구분","ERR","등록 가능한 거래처 구분이 아닙니다.",problems);
            if(problems.stream().anyMatch(i->i.severity().equals("ERR"))) errors++;
            else if(!problems.isEmpty()) warnings++; else success++;
            issues.addAll(problems);
            String formatted=digits.matches("[0-9]{10}")?digits.substring(0,3)+"-"+digits.substring(3,5)+"-"+digits.substring(5):business;
            clients.add(new Client(type,name,formatted,ceo,industry,contact,phone,email,bank,account,depositor));
        }
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparingInt(i->file.columns().getOrDefault(i.columnName(), syntheticIndex(i.columnName())))
            .thenComparing(i->i.severity().equals("ERR")?0:1));
        List<Issue> ordered=new ArrayList<>();
        for(Issue i:issues) ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        return new Checked(clients,new Summary(file.rows().size(),success,warnings,errors,file.fileIssues().size()),ordered);
    }
    private int syntheticIndex(String title) {int i=HEADERS.indexOf(title);return i<0?-1:16384+i;}
    private String resolve(SourceRow row,String title,String group,boolean required,List<Code> codes,List<Issue> issues) {
        String value=text(row,title,100,required,issues); if(value==null)return null;
        List<Code> direct=codes.stream().filter(c->c.groupId().equals(group)&&c.active()&&c.name().equals(value)).toList();
        Set<String> ids=new HashSet<>();direct.forEach(c->ids.add(c.code()));
        if(ids.size()==1)return ids.iterator().next();
        if(ids.isEmpty()) {
            List<Code> mapped=codes.stream().filter(c->c.groupId().equals(group)&&value.equals(c.source())).toList();
            mapped.forEach(c->ids.add(c.code()));
            if(ids.size()==1&&!mapped.isEmpty()&&mapped.stream().allMatch(Code::active)) {
                issue(row,title,"WARN","코드명 대응을 적용했습니다.",issues);return ids.iterator().next();
            }
        }
        issue(row,title,"ERR","활성 코드명 또는 유일한 활성 대응값을 입력해 주세요.",issues);return null;
    }
    private String text(SourceRow row,String title,int max,boolean required,List<Issue> issues) {
        CellValue cell=row.cells().get(title); String value=cell.text();
        if(cell.error()!=null)issue(row,title,"ERR",cell.error(),issues);
        if(value.isBlank()) {if(required)issue(row,title,"ERR","필수 항목을 입력해 주세요.",issues);return null;}
        if(value.codePointCount(0,value.length())>max)issue(row,title,"ERR",max+"자 이하로 입력해 주세요.",issues);
        return value;
    }
    private void discard(SourceRow row,List<String> fields,List<Issue> issues) {
        fields.forEach(field->{CellValue cell=row.cells().get(field);if(!cell.text().isBlank()||cell.error()!=null)issue(row,field,"WARN",field+" 항목은 이 구분에 적용되지 않아 반영하지 않습니다.",issues);});
    }
    private void issue(SourceRow row,String title,String severity,String reason,List<Issue> issues) {
        String value=Set.of("은행","계좌번호","예금주").contains(title)?"[비공개]":excerpt(row.cells().get(title).text(),200);
        issues.add(new Issue(0,severity,row.number(),title,value,excerpt(reason,300)));
    }
}
