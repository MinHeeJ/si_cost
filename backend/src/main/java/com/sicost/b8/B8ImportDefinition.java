package com.sicost.b8;

import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.validation.PeopleInputPolicy;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.b7.B7Dtos.CellValue;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Summary;
import static com.sicost.b8.B8Dtos.*;

@Component
public class B8ImportDefinition {
    public static final List<String> HEADERS=List.of("관리번호","이름","직원구분","소속","등급","주 직무","보조 직무","직위","직접/간접","영업 파트","입사일","월 단가","월 급여·지급액","보유 스킬","거주지 시·도","거주지 시·군·구","거주지 동");
    public static final ImportDefinition FORMAT=new ImportDefinition(HEADERS,Set.of("이름","직원구분","소속","주 직무"),Set.of("관리번호","소속"),Set.of("입사일"),Map.of("월급여","월 급여·지급액"));
    public static final List<String> GUIDE=List.of(
        "B8 개발자 신규 등록 전용. 첫 시트 데이터 1~5,000행, XLSX 10MiB 이하입니다. 기존 개발자를 수정하거나 재개하지 않습니다.",
        "이름(name) 1~50자, 직원구분(employeeType), 소속(companyId), 주 직무(jobCode), 직접/간접(directClass), 영업 파트(partId)는 필수입니다. 필수값을 자동 보충하지 않습니다.",
        "직원구분 EMP_TYPE, 등급 GRADE, 주/보조 직무 JOB, 직위 JBPS, 직접/간접 DRCT_CLS의 활성 코드명을 입력하세요. 코드ID는 입력하지 않습니다. 이전 대응명은 경고합니다.",
        "직위(positionCode) 빈칸만 MEMBER입니다. 등급(gradeCode)·보조 직무(subJobCode)는 선택이며 주 직무와 같으면 보조는 비웁니다.",
        "소속은 자사 또는 사용 중 VNDR/BOTH 협력사의 사업자번호/유일한 업체명입니다. 같은 업체명이 여럿이면 사업자번호를 사용하세요. 외주(OS)는 협력사 소속이 조건부 필수입니다.",
        "영업 파트는 사용 중 파트명 또는 본사/본사(직접 영업)/HQ로 찾습니다. 파트 기본 선택은 없습니다.",
        "관리번호(managementNo)는 선택, 영문·숫자·하이픈 1~20자이며 대소문자를 구분합니다. 빈칸은 K-0001 형식으로 발급합니다. 검사 번호는 예상·미예약이며 반영 때 최종 발급됩니다.",
        "관리번호·소속·입사일·거주지 세 칸은 텍스트 형식으로 입력하여 앞자리 0을 보존하세요. 입사일(hireDate)은 YYYY-MM-DD 또는 YYYY.MM.DD 실제 날짜입니다.",
        "월 단가(monthlyRate)·월 급여·지급액(monthlyPay)은 독립 선택, 빈칸 NULL과 0을 구별합니다. 원 정수 0~999999999999999, 쉼표·공백·끝 원만 제거합니다.",
        "보유 스킬(skills)은 쉼표로 구분, 이름 1~50자·최대999개이며 첫 등장 순서를 보존합니다. 미등록 스킬명도 허용합니다.",
        "거주지 시·도/시·군·구/동은 선택입니다. 법정동을 먼저 찾고 유일한 행정동 대응은 WARN입니다. 찾지 못하면 마지막 확인 상위 경로만 저장하고 경고합니다. 도로명·번지에서 추정하지 않습니다.",
        "관리번호·이름/입사일 중복을 검사합니다. 기존 관리번호는 오류, 같은 이름/입사일은 기존 관리번호를 포함한 경고입니다. 검사 후 같은 파일과 uploadId로 전량 반영하세요.");
    private final B8Mapper mapper;
    public B8ImportDefinition(B8Mapper mapper) { this.mapper=mapper; }

    public Checked validate(Parsed file) {
        var codes=mapper.codes(); var existing=mapper.developers(); var companies=mapper.companies(); var parts=mapper.parts();
        var areas=mapper.areas(); var hdongs=mapper.hdongs();
        Set<String> reserved=new HashSet<>(); existing.forEach(d->reserved.add(d.managementNo()));
        Map<String,Integer> occurrences=new HashMap<>();
        file.rows().forEach(r->{String n=r.cells().get("관리번호").text();if(!n.isBlank()){occurrences.merge(n,1,Integer::sum);reserved.add(n);}});
        BigInteger preview=mapper.managementPreview();
        List<Issue> issues=new ArrayList<>(file.fileIssues()); List<Developer> developers=new ArrayList<>(); List<NumberRow> expected=new ArrayList<>();
        int success=0,warnings=0,errors=0;
        for(SourceRow row:file.rows()) {
            List<Issue> problems=new ArrayList<>();
            String number=text(row,"관리번호",20,false,problems),name=text(row,"이름",50,true,problems);
            if(number!=null) {
                if(!number.matches("[A-Za-z0-9-]{1,20}")) issue(row,"관리번호","ERR","관리번호는 영문·숫자·하이픈 1~20자입니다.",problems);
                if(occurrences.getOrDefault(number,0)>1)issue(row,"관리번호","ERR","파일 안에서 관리번호가 중복됩니다. 관련 행을 모두 수정해 주세요.",problems);
                String direct=number;
                if(existing.stream().anyMatch(d->d.managementNo().equals(direct)))issue(row,"관리번호","ERR","이미 등록된 관리번호입니다. 중지·퇴사자도 포함합니다.",problems);
            }
            String type=resolve(row,"직원구분","EMP_TYPE",true,codes,problems);
            if(type!=null&&!Set.of("FT","CT","FL","OS").contains(type))issue(row,"직원구분","ERR","등록 가능한 직원구분이 아닙니다.",problems);
            String company=company(row,type,companies,problems);
            String grade=resolve(row,"등급","GRADE",false,codes,problems),job=resolve(row,"주 직무","JOB",true,codes,problems);
            String sub=resolve(row,"보조 직무","JOB",false,codes,problems);
            if(Objects.equals(job,sub))sub=null;
            String position=resolve(row,"직위","JBPS",false,codes,problems);
            if(row.cells().get("직위").text().isBlank()) {
                position="MEMBER";
                if(codes.stream().noneMatch(c->c.groupId().equals("JBPS")&&c.code().equals("MEMBER")&&c.active()))issue(row,"직위","ERR","기본 직위 MEMBER가 사용 중인지 확인해 주세요.",problems);
            }
            String direct=resolve(row,"직접/간접","DRCT_CLS",true,codes,problems);
            if(direct!=null&&!Set.of("DRCT","INDRCT").contains(direct))issue(row,"직접/간접","ERR","직접/간접 값을 확인해 주세요.",problems);
            String part=part(row,parts,problems);
            LocalDate hire=null;String date=text(row,"입사일",10,false,problems);
            if(date!=null)try{hire=PeopleInputPolicy.date(date.replace('.','-'),"hireDate");}catch(ApiException e){issue(row,"입사일","ERR","실제 날짜를 YYYY-MM-DD 또는 YYYY.MM.DD로 입력해 주세요.",problems);}
            if(hire!=null)for(Existing d:existing)if(Objects.equals(name,d.name())&&hire.equals(d.hireDate()))issue(row,"이름","WARN","같은 이름과 입사일이 있습니다: "+d.managementNo(),problems);
            BigDecimal rate=amount(row,"월 단가",problems),pay=amount(row,"월 급여·지급액",problems);
            String skillText=text(row,"보유 스킬",51000,false,problems);List<String> skills=List.of();
            if(skillText!=null)try{skills=PeopleInputPolicy.skills(List.of(skillText),false);}catch(ApiException e){issue(row,"보유 스킬","ERR","스킬 이름은 1~50자, 최대999개로 입력해 주세요.",problems);}
            Address address=address(row,areas,hdongs,problems);
            developers.add(new Developer(row.number(),number,name,type,company,grade,job,sub,position,direct,part,hire,rate,pay,skills,address));
            if(number==null) {
                String candidate;
                do {candidate=management(preview);preview=preview.add(BigInteger.ONE);}while(reserved.contains(candidate));
                reserved.add(candidate);expected.add(new NumberRow(row.number(),candidate,null));
            } else expected.add(new NumberRow(row.number(),number,null));
            if(problems.stream().anyMatch(i->i.severity().equals("ERR")))errors++;
            else if(!problems.isEmpty())warnings++;else success++;
            issues.addAll(problems);
        }
        issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparingInt(i->file.columns().getOrDefault(i.columnName(),16384+HEADERS.indexOf(i.columnName())))
            .thenComparing(i->i.severity().equals("ERR")?0:1));
        List<Issue> ordered=new ArrayList<>();for(Issue i:issues)ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        return new Checked(developers,new Summary(file.rows().size(),success,warnings,errors,file.fileIssues().size()),ordered,expected);
    }
    static String management(BigInteger n) {
        if(n==null||n.signum()<1||n.toString().length()>18)throw new ApiException(409,"ID_EXHAUSTED","사용 가능한 관리번호를 모두 사용했습니다.");
        String value=n.toString();return "K-"+"0".repeat(Math.max(0,4-value.length()))+value;
    }
    private String company(SourceRow row,String type,List<Company> companies,List<Issue> issues) {
        String value=text(row,"소속",50,true,issues);if(value==null)return null;
        if(value.equals("자사")){if("OS".equals(type))issue(row,"소속","ERR","외주는 협력사 소속이 필수입니다.",issues);return null;}
        String digits=value.replace("-","");boolean business=value.matches("[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5}");
        var found=companies.stream().filter(c->business?c.businessNo().replace("-","").equals(digits):c.name().equals(value)).toList();
        if(found.size()==1)return found.get(0).id();
        issue(row,"소속","ERR",found.size()>1?"같은 업체명이 여럿입니다. 사업자번호를 사용해 주세요.":"사용 중인 협력사 이름 또는 사업자번호를 입력해 주세요.",issues);return null;
    }
    private String part(SourceRow row,List<Part> parts,List<Issue> issues) {
        String value=text(row,"영업 파트",30,true,issues);if(value==null)return null;
        boolean hq=Set.of("본사","본사(직접 영업)","HQ").contains(value);
        var found=parts.stream().filter(p->hq?p.headquarters():p.name().equals(value)).toList();
        if(found.size()==1)return found.get(0).id();
        issue(row,"영업 파트","ERR","사용 중인 유일한 영업 파트명을 입력해 주세요.",issues);return null;
    }
    private String resolve(SourceRow row,String title,String group,boolean required,List<Code> codes,List<Issue> issues) {
        String value=text(row,title,100,required,issues);if(value==null)return null;
        var named=codes.stream().filter(c->c.groupId().equals(group)&&c.active()&&c.name().equals(value)).map(Code::code).distinct().toList();
        if(named.size()==1)return named.get(0);
        if(named.isEmpty()) {
            var mapped=codes.stream().filter(c->c.groupId().equals(group)&&value.equals(c.source())).toList();
            var ids=mapped.stream().map(Code::code).distinct().toList();
            if(ids.size()==1&&mapped.stream().allMatch(Code::active)){issue(row,title,"WARN","이전 표기의 코드명 대응을 적용했습니다.",issues);return ids.get(0);}
        }
        issue(row,title,"ERR","활성 코드명 또는 유일한 활성 대응값을 입력해 주세요. 코드ID는 사용할 수 없습니다.",issues);return null;
    }
    private BigDecimal amount(SourceRow row,String title,List<Issue> issues) {
        String value=text(row,title,100,false,issues);try{return PeopleInputPolicy.amount(value,title);}catch(ApiException e){issue(row,title,"ERR","금액은 0~999999999999999원 정수로 입력해 주세요.",issues);return null;}
    }
    private Address address(SourceRow row,List<Area> areas,List<Hdong> hdongs,List<Issue> issues) {
        String sido=row.cells().get("거주지 시·도").text(),sgg=row.cells().get("거주지 시·군·구").text(),emd=row.cells().get("거주지 동").text();
        for(String field:List.of("거주지 시·도","거주지 시·군·구","거주지 동"))if(row.cells().get(field).error()!=null)issue(row,field,"ERR",row.cells().get(field).error(),issues);
        if(sido.isBlank()&&sgg.isBlank()&&emd.isBlank())return new Address(null,null);
        String normalized=sido(sido);
        var roots=areas.stream().filter(a->a.level().equals("SIDO")&&(a.sido().equals(normalized)||a.fullName().equals(sido)||sido(a.fullName()).equals(normalized))).toList();
        if(roots.size()!=1){issue(row,"거주지 시·도","WARN","거주지를 못 찾아 비웠습니다",issues);return new Address(null,null);}
        Area parent=roots.get(0);String root=parent.code();
        var districts=areas.stream().filter(a->a.level().equals("SGG")&&root.equals(a.parentCode())).toList();
        if(!districts.isEmpty()) {
            var found=districts.stream().filter(a->Objects.equals(a.sgg(),sgg)).toList();
            if(found.size()!=1){if(!sgg.isBlank()||!emd.isBlank())issue(row,"거주지 시·군·구","WARN","시·군·구를 못 찾아 비웠습니다",issues);return address(parent);}
            parent=found.get(0);
        } else if(!sgg.isBlank()) {issue(row,"거주지 시·군·구","WARN","시·군·구를 못 찾아 비웠습니다",issues);return address(parent);}
        if(emd.isBlank())return address(parent);
        String key=parent.code();var children=areas.stream().filter(a->a.level().equals("EMD")&&key.equals(a.parentCode())).toList();
        var exact=children.stream().filter(a->Objects.equals(a.emd(),emd)).toList();
        if(exact.size()==1)return address(exact.get(0));
        var mappedCodes=hdongs.stream().filter(h->dong(h.name()).equals(dong(emd))).map(Hdong::areaCode).collect(java.util.stream.Collectors.toSet());
        var mapped=children.stream().filter(a->mappedCodes.contains(a.code())).toList();
        if(exact.isEmpty()&&mapped.size()==1){issue(row,"거주지 동","WARN","행정동을 유일한 법정동으로 변환했습니다.",issues);return address(mapped.get(0));}
        issue(row,"거주지 동","WARN","동을 못 찾아 비웠습니다",issues);return address(parent);
    }
    private Address address(Area a) {
        String text=java.util.stream.Stream.of(a.sido(),a.sgg(),a.emd()).filter(Objects::nonNull).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.joining(" "));
        if(text.codePointCount(0,text.length())>50)throw new ApiException(400,"INVALID_AREA","주소 경로 길이를 확인해 주세요.");
        return new Address(a.code(),text);
    }
    private String sido(String s) {
        if(Set.of("광주광역시","광주시","광주","전라남도","전남","전남광주").contains(s))return "전남광주";
        if(s.equals("서울특별시")||s.equals("서울시"))return "서울";
        if(s.equals("경기도"))return "경기";
        return s.replaceFirst("(특별자치도|특별자치시|특별시|광역시|도)$","").replaceFirst("시$","");
    }
    private String dong(String s){return s.replaceAll("제(?=[0-9])","");}
    private String text(SourceRow row,String title,int max,boolean required,List<Issue> issues) {
        CellValue cell=row.cells().get(title);String value=cell.text();
        if(cell.error()!=null)issue(row,title,"ERR",cell.error(),issues);
        if(value.isBlank()){if(required)issue(row,title,"ERR","필수 항목을 입력해 주세요.",issues);return null;}
        if(value.codePointCount(0,value.length())>max)issue(row,title,"ERR",max+"자 이하로 입력해 주세요.",issues);return value;
    }
    private void issue(SourceRow row,String title,String severity,String reason,List<Issue> issues) {
        issues.add(new Issue(0,severity,row.number(),title,excerpt(row.cells().get(title).text(),200),excerpt(reason,300)));
    }
    static String excerpt(String value,int max){return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max-3))+"...";}
}
