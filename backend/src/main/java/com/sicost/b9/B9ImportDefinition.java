package com.sicost.b9;

import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.SourceRow;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.projects.ProjectRegistrationService.Project;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.b9.B9Dtos.*;

@Component
public class B9ImportDefinition {
    public static final List<String> HEADERS = List.of("프로젝트ID","프로젝트명","고객사","계약유형","시작일","종료일","영업 파트","근무지","상태","견적 금액","계약금액","예산","팀비","프로젝트경비","특징");
    public static final ImportDefinition FORMAT = new ImportDefinition(HEADERS, Set.copyOf(HEADERS), Set.of("프로젝트ID","고객사"), Set.of("시작일","종료일"), Map.of());
    public static final List<String> GUIDE = List.of(
        "B9 프로젝트 신규 등록 전용입니다. 기존 프로젝트 수정은 화면에서 합니다. 첫 시트 첫 행의 15열 제목을 유지하세요. XLSX 10,485,760바이트 이하, 데이터 1~5,000행입니다.",
        "프로젝트ID(id)는 선택, 영문·숫자·하이픈 1~20자이며 대소문자를 구분합니다. 기존 모든 상태 ID와 파일 내 중복은 오류입니다. 빈칸은 공통 P-0001 형식으로 반영 때 발급합니다. 검사 시 예약하지 않습니다.",
        "프로젝트명(name)은 필수 100자 이하. 고객사(customerId)는 활성 CUST/BOTH의 유일한 상호 또는 정규화 10자리 사업자등록번호입니다. 이름이 여러 고객에 일치하면 번호를 입력하세요.",
        "계약유형(contractType)은 활성 CTRT_TYPE 코드명, 상태(initialStatus)는 PROJ_STAT의 진행/수주예정 표시명이며 빈칸은 진행입니다. 코드ID를 이름으로 입력하지 마세요. 이전 표기 대응은 경고합니다.",
        "시작일(startDate)/종료일(endDate)은 실제 YYYY-MM-DD, 시작≤종료이며 다른 연도에 걸친 계약도 허용합니다. 종료일이 지나면 END로 저장합니다. 선택연도는 읽기 전용 검사에만 사용합니다.",
        "영업 파트(partId)는 비내부 필수, 활성 파트명 또는 본사(직접 영업)/본사/HQ입니다. 중복명은 ID로 임의 선택하지 않습니다. 근무지(workplace)는 선택 200자, 특징(feature)은 선택 500자입니다.",
        "견적 금액(estimateAmount)은 선택·빈칸 NULL, 계약금액(contractAmount)은 비내부 필수·0 허용, 내부 빈칸 0입니다. 모든 금액은 원 정수 0~999999999999999이며 쉼표·공백·끝 원만 제거합니다.",
        "예산(budgetAmount/budgetMode)은 빈칸 AUTO·현재 TGT_MRGN_RT로 계산, 숫자는 MANUAL·0 보존입니다. 자동 기준이 없으면 수기 예산을 입력하세요. 팀비(teamExpenseAmount)/프로젝트경비(projectExpenseAmount)는 빈칸이면 현재 ALWNC TEAM_FEE/PEXP_FEE 기본값, 없으면 수기 필수입니다.",
        "내부 IN의 고객사/영업 파트/근무지/예산은 숨김값으로 폐기 경고하며 검사하지 않습니다. 견적·계약·팀비·경비·특징은 유지합니다. 식별자는 텍스트로 입력해 앞자리 0을 보존하세요.",
        "수식은 저장된 캐시만 읽고 실행하지 않습니다. 오류 0행·데이터 1행 이상일 때 같은 File/uploadId/year로 전량 반영합니다. 한 행 실패 시 전체 롤백하며 원본 파일은 서버에 보관하지 않습니다.");
    private final B9Mapper mapper;
    private final Clock clock;
    public B9ImportDefinition(B9Mapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }

    public Defaults requireYear(int year) {
        Defaults defaults = mapper.defaults(YearMonth.from(ProjectInputPolicy.today(clock)).format(DateTimeFormatter.ofPattern("uuuuMM")));
        if (defaults == null || defaults.cashBaseMonth() == null || !defaults.cashBaseMonth().matches("[0-9]{6}")) throw ProjectInputPolicy.invalid("year");
        String month = defaults.cashBaseMonth();
        ProjectInputPolicy.requireWritableYear(year, ProjectInputPolicy.month(month.substring(0,4)+"-"+month.substring(4),"year"), clock);
        return defaults;
    }
    public Checked validate(Parsed file, int year) {
        Defaults defaults = requireYear(year);
        var codes = mapper.codes(); var existing = new HashSet<>(mapper.existingIds());
        var customers = mapper.customers(); var parts = mapper.parts();
        Map<String,Integer> occurrences = new HashMap<>();
        file.rows().forEach(r -> { String id = r.cells().get("프로젝트ID").text(); if (!id.isBlank()) occurrences.merge(id,1,Integer::sum); });
        List<Problem> issues = new ArrayList<>();
        file.fileIssues().forEach(i -> issues.add(problem(i, List.of())));
        List<ProjectRow> projects = new ArrayList<>(); int success=0, warnings=0, errors=0;
        for (SourceRow row : file.rows()) {
            List<Problem> problems = new ArrayList<>();
            String id = text(row,"프로젝트ID",20,false,problems), name = text(row,"프로젝트명",100,true,problems);
            if (id != null) {
                if (!id.matches("[A-Za-z0-9-]{1,20}")) issue(row,"프로젝트ID","ERR","영문·숫자·하이픈 1~20자로 입력해 주세요.",problems);
                if (existing.contains(id)) issue(row,"프로젝트ID","ERR","이미 등록된 프로젝트ID입니다. 종료·실주 상태도 포함합니다.",problems);
                if (occurrences.getOrDefault(id,0)>1) issue(row,"프로젝트ID","ERR","파일 안에서 프로젝트ID가 중복됩니다. 관련 행을 모두 수정해 주세요.",problems);
            }
            String type = resolve(row,"계약유형","CTRT_TYPE",codes,problems);
            if (type != null && !Set.of("TK","MO","DP","IN").contains(type)) issue(row,"계약유형","ERR","등록 가능한 계약유형이 아닙니다.",problems);
            String state;
            if (row.cells().get("상태").text().isBlank() && row.cells().get("상태").error()==null) {
                state="ING";
                if (codes.stream().noneMatch(c->c.groupId().equals("PROJ_STAT")&&c.code().equals("ING")&&c.active())) issue(row,"상태","ERR","기본 진행 상태가 사용 중인지 확인해 주세요.",problems);
            } else state=resolve(row,"상태","PROJ_STAT",codes,problems);
            if (state!=null&&!Set.of("ING","PRE").contains(state)) issue(row,"상태","ERR","신규 상태는 진행 또는 수주예정만 가능합니다.",problems);
            LocalDate start=date(row,"시작일",problems), end=date(row,"종료일",problems);
            if(start!=null&&end!=null&&start.isAfter(end)) issue(row,"종료일","ERR","종료일은 시작일 이후여야 합니다.",problems);
            if(end!=null&&end.isBefore(ProjectInputPolicy.today(clock))) state="END";
            boolean internal="IN".equals(type);
            String customer=null, part=null, workplace=null;
            BigDecimal budget=null; String manual="N";
            if (internal) {
                for(String title:List.of("고객사","영업 파트","근무지","예산")) if(!row.cells().get(title).text().isBlank()||row.cells().get(title).error()!=null)
                    issue(row,title,"WARN","내부 프로젝트의 숨김값은 폐기합니다.",problems);
            } else {
                customer=customer(row,customers,problems); part=part(row,parts,problems);
                workplace=text(row,"근무지",200,false,problems);
                budget=amount(row,"예산",false,problems);
                if(!row.cells().get("예산").text().isBlank()) manual="Y";
            }
            BigDecimal estimate=amount(row,"견적 금액",false,problems), contract=amount(row,"계약금액",!internal,problems);
            if(internal&&contract==null&&row.cells().get("계약금액").text().isBlank()) contract=BigDecimal.ZERO;
            if(!internal&&manual.equals("N")) {
                BigDecimal rate=rate(defaults.marginRate());
                if(rate==null) issue(row,"예산","ERR","자동 예산 기준이 없습니다. 예산을 원 정수로 직접 입력해 주세요.",problems);
                else if(contract!=null) budget=ProjectInputPolicy.calculatedAmount(contract.multiply(BigDecimal.ONE.subtract(rate.movePointLeft(2))));
            }
            BigDecimal team=defaultAmount(row,"팀비",defaults.teamExpense(),problems), expense=defaultAmount(row,"프로젝트경비",defaults.projectExpense(),problems);
            String feature=text(row,"특징",500,false,problems);
            projects.add(new ProjectRow(row.number(),id,new Project(name,customer,type,state,start,end,part,workplace,estimate,contract,budget,manual,team,expense,feature)));
            if(problems.stream().anyMatch(i->i.severity().equals("ERR"))) errors++;
            else if(problems.stream().anyMatch(i->i.severity().equals("WARN"))) warnings++; else success++;
            issues.addAll(problems);
        }
        issues.sort(Comparator.comparingInt((Problem p)->p.severity().equals("ERR")?0:1).thenComparingInt(Problem::rowNumber)
            .thenComparingInt(p->HEADERS.indexOf(p.columnName())));
        List<Problem> numbered=new ArrayList<>();
        for(Problem p:issues) numbered.add(new Problem(numbered.size()+1,p.severity(),p.rowNumber(),p.columnName(),p.inputValue(),p.reason(),p.candidates()));
        return new Checked(projects,new Summary(file.rows().size(),success,warnings,errors,file.fileIssues().size(),0),numbered);
    }
    private String resolve(SourceRow row,String title,String group,List<Code> codes,List<Problem> issues) {
        String value=text(row,title,100,true,issues); if(value==null)return null;
        var named=codes.stream().filter(c->c.groupId().equals(group)&&c.active()&&c.name().equals(value)).map(Code::code).distinct().toList();
        if(named.size()==1)return named.get(0);
        if(named.isEmpty()) {
            var mapped=codes.stream().filter(c->c.groupId().equals(group)&&value.equals(c.source())).toList();
            var ids=mapped.stream().map(Code::code).distinct().toList();
            if(ids.size()==1&&mapped.stream().allMatch(Code::active)) {issue(row,title,"WARN","이전 표기의 코드명 대응을 적용했습니다.",issues);return ids.get(0);}
        }
        issue(row,title,"ERR","활성 코드명 또는 유일한 활성 대응값을 입력해 주세요. 코드ID는 사용할 수 없습니다.",issues);return null;
    }
    private String customer(SourceRow row,List<Candidate> customers,List<Problem> issues) {
        String value=text(row,"고객사",50,true,issues);if(value==null)return null;
        boolean business=value.matches("[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5}");
        String number=value.replace("-","");
        var found=customers.stream().filter(c->business?Objects.equals(c.businessNo(),number):c.name().strip().equals(value)).toList();
        if(found.size()==1)return found.get(0).id();
        String reason=found.size()>1?"활성 고객 "+found.size()+"건이 일치합니다. 후보 상세의 사업자번호를 입력해 주세요.":"활성 고객사 상호 또는 10자리 사업자번호를 입력해 주세요.";
        issue(row,"고객사","ERR",reason,found,issues);return null;
    }
    private String part(SourceRow row,List<Part> parts,List<Problem> issues) {
        String value=text(row,"영업 파트",30,true,issues);if(value==null)return null;
        boolean hq=Set.of("본사","본사(직접 영업)","HQ").contains(value);
        var found=parts.stream().filter(p->hq?p.headquarters():p.name().strip().equals(value)).toList();
        if(found.size()==1)return found.get(0).id();
        issue(row,"영업 파트","ERR",found.size()>1?"영업 파트명이 중복됩니다. 후보 ID/이름을 확인해 주세요.":"활성 영업 파트명 또는 본사(직접 영업)를 입력해 주세요.",
            found.stream().map(p->new Candidate(p.id(),p.name(),null)).toList(),issues);return null;
    }
    private BigDecimal defaultAmount(SourceRow row,String title,String fallback,List<Problem> issues) {
        if(!row.cells().get(title).text().isBlank()||row.cells().get(title).error()!=null)return amount(row,title,false,issues);
        try {if(fallback!=null)return ProjectInputPolicy.amount(fallback,title);}catch(ApiException ignored) { }
        issue(row,title,"ERR","유효한 기본값이 없습니다. 원 정수를 직접 입력해 주세요.",issues);return null;
    }
    private BigDecimal rate(String value) {
        if(value==null)return null;
        try {BigDecimal rate=new BigDecimal(value);return rate.signum()>=0&&rate.compareTo(new BigDecimal("100"))<=0?rate:null;}
        catch(NumberFormatException e){return null;}
    }
    private BigDecimal amount(SourceRow row,String title,boolean required,List<Problem> issues) {
        String value=text(row,title,100,required,issues);if(value==null)return null;
        try{return ProjectInputPolicy.amount(value,title);}catch(ApiException e){issue(row,title,"ERR","금액은 0~999999999999999원 정수로 입력해 주세요.",issues);return null;}
    }
    private LocalDate date(SourceRow row,String title,List<Problem> issues) {
        String value=text(row,title,10,true,issues);if(value==null)return null;
        try{return ProjectInputPolicy.date(value,title);}catch(ApiException e){issue(row,title,"ERR","실제 날짜를 YYYY-MM-DD로 입력해 주세요.",issues);return null;}
    }
    private String text(SourceRow row,String title,int max,boolean required,List<Problem> issues) {
        var cell=row.cells().get(title);String value=cell.text().replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$","");
        if(cell.error()!=null)issue(row,title,"ERR",cell.error(),issues);
        if(value.isBlank()){if(required)issue(row,title,"ERR","필수 항목을 입력해 주세요.",issues);return null;}
        if(value.codePointCount(0,value.length())>max)issue(row,title,"ERR",max+"자 이하로 입력해 주세요.",issues);
        return value;
    }
    private void issue(SourceRow row,String title,String severity,String reason,List<Problem> issues) {issue(row,title,severity,reason,List.of(),issues);}
    private void issue(SourceRow row,String title,String severity,String reason,List<Candidate> candidates,List<Problem> issues) {
        issues.add(new Problem(0,severity,row.number(),title,excerpt(row.cells().get(title).text(),200),excerpt(reason,300),candidates));
    }
    static Problem problem(Issue issue,List<Candidate> candidates) {return new Problem(issue.sequence(),issue.severity(),issue.rowNumber(),issue.columnName(),issue.inputValue(),issue.reason(),candidates);}
    static String excerpt(String text,int max){return text.codePointCount(0,text.length())<=max?text:text.substring(0,text.offsetByCodePoints(0,max-3))+"...";}
}
