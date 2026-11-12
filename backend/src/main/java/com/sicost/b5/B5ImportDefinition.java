package com.sicost.b5;

import com.sicost.assignmentsops.*;
import com.sicost.assignmentsops.AssignmentActionsDtos.*;
import com.sicost.b7.B7Dtos.*;
import com.sicost.b7.ExcelImportService;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.excel.ImportDefinition;
import com.sicost.common.validation.*;
import com.sicost.projectsops.ProjectAssignmentMonths;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b5.B5Dtos.*;
import com.sicost.b5.B5Dtos.Checked;
import com.sicost.b5.B5Dtos.Selection;

@Component
public class B5ImportDefinition {
    public static final List<String> HEADERS=List.of("프로젝트ID","관리번호","직무","투입일","철수일","월 MM","청구 단가","투입 구분","계약상 인력 관리번호","연장 가능성");
    public static final Set<String> REQUIRED=Set.of("프로젝트ID","관리번호","직무","투입일","철수일","투입 구분");
    private final ExcelImportService imports;
    private final B5Mapper mapper;
    private final AssignmentActionsService actions;
    private final AssignmentActionsRepository assignments;
    private final AssignmentMonthlyService months;
    public B5ImportDefinition(ExcelImportService imports,B5Mapper mapper,AssignmentActionsService actions,AssignmentActionsRepository assignments,AssignmentMonthlyService months) {
        this.imports=imports;this.mapper=mapper;this.actions=actions;this.assignments=assignments;this.months=months;
    }
    public String filename(MultipartFile file) {try{return imports.filename(file);}catch(ApiException e){throw translated(e);}}
    public Parsed parse(MultipartFile file) {
        try{return imports.parse(file,new ImportDefinition(HEADERS,REQUIRED,Set.of("프로젝트ID","관리번호","계약상 인력 관리번호"),Set.of("투입일","철수일"),Map.of(),Set.of("월 MM","청구 단가")));}
        catch(ApiException e){throw translated(e);}
    }
    private ApiException translated(ApiException e) {return new ApiException(e.status(),e.status()==413?"PAYLOAD_TOO_LARGE":e.status()==415?"UNSUPPORTED_MEDIA_TYPE":"INVALID_INPUT",e.getMessage(),e.field(),e.details());}
    public Checked check(Parsed file,Request request) {
        List<Issue> issues=new ArrayList<>(file.fileIssues());List<B5Dtos.Row> rows=new ArrayList<>();List<Substitution> previews=new ArrayList<>();
        Map<String,String> developers=new HashMap<>();mapper.developers().forEach(d->developers.put(d.managementNo(),d.id()));List<Code> codes=mapper.codes();
        for(SourceRow row:file.rows()) {
            try {
                String projectId=text(row,"프로젝트ID",20,true),number=text(row,"관리번호",20,true),job=code(row,"직무","JOB",codes,issues);
                String developerId=developers.get(number);if(developerId==null)throw invalid("관리번호");
                String type=code(row,"투입 구분","INPUT_TYPE",codes,issues);if(!Set.of("NEW","ADD","SUB","FREE").contains(type))throw invalid("투입 구분");
                LocalDate start=date(row,"투입일"),end=date(row,"철수일");BigDecimal mm=AssignmentInputPolicy.contractMm(text(row,"월 MM",4,false),true);
                String contract=null;
                if("SUB".equals(type)){String n=text(row,"계약상 인력 관리번호",20,true);contract=developers.get(n);if(contract==null)throw invalid("계약상 인력 관리번호");}
                else discard(row,"계약상 인력 관리번호",issues);
                var project=assignments.project(projectId);String price=null;
                if(Set.of("MO","DP").contains(project.contractType())&&!"FREE".equals(type))price=text(row,"청구 단가",100,false);
                else discard(row,"청구 단가",issues);
                String possibility=possibility(row,codes,issues);
                Create create=new Create(request.year(),projectId,developerId,job,start,end,mm,price,type,contract);
                Preview preview=actions.preview(new PreviewRequest(create,null,null));
                BigDecimal resolved=price==null?null:AssignmentInputPolicy.paidUnitPrice(price);
                if("FREE".equals(type))resolved=BigDecimal.ZERO;
                else if(resolved==null&&Set.of("MO","DP").contains(project.contractType())) {
                    if("SUB".equals(type)) {
                        String contractId=contract;
                        var candidate=assignments.mapper().candidates(new CandidateQuery(projectId,developerId)).stream().filter(c->c.developerId().equals(contractId)).findFirst().orElseThrow(()->invalid("계약상 인력 관리번호"));
                        Source prior=assignments.required(candidate.lastAssignmentId());resolved=ProjectAssignmentMonths.value(months.mapper().histories(prior.id()),YearMonth.from(prior.endDate()),false,prior.price());
                    } else resolved=assignments.mapper().price(new PriceQuery(developerId,AssignmentMonthlyService.db(YearMonth.from(start).toString())));
                    if(resolved==null||resolved.signum()<=0)throw invalid("청구 단가");
                }
                Source source=new Source("",projectId,developerId,job,start,end,mm,resolved,type,contract,possibility,0,"N","ACT");
                rows.add(new B5Dtos.Row(row.number(),create,source,preview.months()));
                for(Warning warning:preview.warnings())if(!"MM_OVER".equals(warning.code()))add(issues,row.number(),"[행]","WARN",number,warning.message());
                if("SUB".equals(type))previews.add(new Substitution(row.number(),actions.substitutionPreview(create)));
            } catch(ApiException e) {add(issues,row.number(),field(e.field()),"ERR","",e.getMessage());}
        }
        Map<List<Object>,List<B5Dtos.Row>> duplicates=new HashMap<>();
        for(var row:rows){Source s=row.source();duplicates.computeIfAbsent(List.of(s.projectId(),s.developerId(),s.jobCode(),s.startDate(),s.endDate(),s.inputType()),k->new ArrayList<>()).add(row);}
        duplicates.values().stream().filter(v->v.size()>1).forEach(v->v.forEach(r->add(issues,r.rowNumber(),"[행]","ERR","","파일 안의 동일 6키 배정이 중복되었습니다.")));
        List<Withdrawal> withdrawals=withdrawals(request,rows,previews,issues);
        totals(rows,withdrawals,issues);
        Set<Integer> errors=new HashSet<>(),warnings=new HashSet<>();Set<Integer> dataRows=new HashSet<>();file.rows().forEach(r->dataRows.add(r.number()));
        for(Issue i:issues)if(dataRows.contains(i.rowNumber())&&!"[파일]".equals(i.columnName())){if(i.severity().equals("ERR"))errors.add(i.rowNumber());else warnings.add(i.rowNumber());}warnings.removeAll(errors);
        List<Issue> ordered=new ArrayList<>();issues.sort(Comparator.comparingInt(Issue::rowNumber).thenComparing(Issue::columnName).thenComparing(Issue::severity));
        for(Issue i:issues)ordered.add(new Issue(ordered.size()+1,i.severity(),i.rowNumber(),i.columnName(),i.inputValue(),i.reason()));
        return new Checked(List.copyOf(rows),new Summary(file.rows().size(),file.rows().size()-errors.size()-warnings.size(),warnings.size(),errors.size(),file.fileIssues().size(),0),List.copyOf(ordered),List.copyOf(previews),List.copyOf(withdrawals));
    }
    private List<Withdrawal> withdrawals(Request request,List<B5Dtos.Row> rows,List<Substitution> previews,List<Issue> issues) {
        Map<String,List<Map.Entry<Integer,Withdrawal>>> targets=new TreeMap<>();Set<Integer> seen=new HashSet<>();
        for(Selection selection:request.withdrawalSelections()) {
            if(!seen.add(selection.rowNumber()))throw invalid("withdrawalSelections");
            var row=rows.stream().filter(r->r.rowNumber()==selection.rowNumber()).findFirst().orElse(null);
            var preview=previews.stream().filter(p->p.rowNumber()==selection.rowNumber()).findFirst().orElse(null);
            Set<String> ids=new HashSet<>();
            for(Withdrawal w:selection.withdrawals()) {
                if(!ids.add(w.assignmentId()))throw invalid("withdrawalSelections");
                if(row==null||preview==null||preview.preview().candidates().stream().noneMatch(i->i.id().equals(w.assignmentId()))||!preview.preview().withdrawalDate().equals(w.withdrawalDate())) {
                    add(issues,selection.rowNumber(),"[철수]","ERR",w.assignmentId(),"최신 대체 철수 후보와 예정일을 다시 확인해 주세요.");continue;
                }
                try {actions.preview(new PreviewRequest(null,w.assignmentId(),change(request.year(),w)));}
                catch(ApiException e){add(issues,selection.rowNumber(),"[철수]","ERR",w.assignmentId(),e.getMessage());}
                targets.computeIfAbsent(w.assignmentId(),k->new ArrayList<>()).add(Map.entry(selection.rowNumber(),w));
            }
        }
        List<Withdrawal> result=new ArrayList<>();
        targets.forEach((id,entries)->{
            if(entries.stream().map(e->e.getValue().withdrawalDate()).distinct().count()>1)entries.forEach(e->add(issues,e.getKey(),"[철수]","ERR",id,"같은 배정을 서로 다른 날짜에 철수할 수 없습니다."));
            else result.add(entries.get(0).getValue());
        });return result;
    }
    public static Change change(int year,Withdrawal w) {return new Change(year,w.expectedRevision(),"EARLY",null,null,null,w.withdrawalDate(),null,null,null,null,w.reasonCode(),w.memo());}
    private void totals(List<B5Dtos.Row> rows,List<Withdrawal> withdrawals,List<Issue> issues) {
        Map<String,BigDecimal> delta=new HashMap<>();
        for(var row:rows)for(var month:row.months())delta.merge(row.source().developerId()+":"+month.month(),new BigDecimal(month.monthlyMm()),BigDecimal::add);
        for(var w:withdrawals) {
            Source before=assignments.required(w.assignmentId());var project=assignments.project(before.projectId());
            Source after=before.changed(w.withdrawalDate(),before.contractMm(),before.price(),before.inputType(),before.extensionPossibility(),before.extensionCount(),"Y",before.status());
            var preview=months.preview(after,months.mapper().histories(before.id()),project.status(),project.contractType());
            Map<String,BigDecimal> desired=new HashMap<>();preview.forEach(m->desired.put(m.month(),new BigDecimal(m.monthlyMm())));
            for(var old:months.mapper().months(before.id())) {String key=AssignmentMonthlyService.api(old.month());delta.merge(before.developerId()+":"+key,desired.getOrDefault(key,BigDecimal.ZERO).subtract(old.mm()),BigDecimal::add);}
        }
        for(var row:rows)for(var month:row.months()) {
            String db=AssignmentMonthlyService.db(month.month());BigDecimal limit=assignments.mapper().mmLimit(db);
            BigDecimal total=assignments.mapper().totalMm(new TotalQuery(row.source().developerId(),db,null)).add(delta.get(row.source().developerId()+":"+month.month()));
            if(limit!=null&&total.compareTo(limit)>0)add(issues,row.rowNumber(),"월 MM","WARN",row.source().contractMm().toPlainString(),month.month()+" 전프로젝트 예상 월MM "+total.toPlainString()+" > "+limit.toPlainString());
        }
    }
    private String possibility(SourceRow row,List<Code> codes,List<Issue> issues) {
        String value=text(row,"연장 가능성",100,false);if(value==null)return null;
        String c=switch(value){case "높음"->"H";case "보통"->"M";case "낮음"->"L";default->code(row,"연장 가능성","EXT_PSBL",codes,issues);};
        if(!assignments.mapper().active(new CodeQuery("EXT_PSBL",c)))throw invalid("연장 가능성");return c;
    }
    private String code(SourceRow row,String field,String group,List<Code> codes,List<Issue> issues) {
        String value=text(row,field,100,true);Set<String> direct=new HashSet<>();codes.stream().filter(c->c.active()&&c.groupId().equals(group)&&c.name().equals(value)).forEach(c->direct.add(c.code()));
        if(direct.size()==1)return ProjectInputPolicy.text(direct.iterator().next(),10,field);
        if(direct.isEmpty()) {
            List<Code> mapped=codes.stream().filter(c->c.groupId().equals(group)&&value.equals(c.source())).toList();Set<String> ids=new HashSet<>();mapped.forEach(c->ids.add(c.code()));
            if(ids.size()==1&&mapped.stream().allMatch(Code::active)){add(issues,row.number(),field,"WARN",value,"활성 코드명 대응을 적용했습니다.");return ProjectInputPolicy.text(ids.iterator().next(),10,field);}
        }
        throw invalid(field);
    }
    private String text(SourceRow row,String field,int max,boolean required) {
        CellValue c=row.cells().get(field);if(c.error()!=null)throw new ApiException(400,"INVALID_INPUT",c.error(),field,null);
        return required?ProjectInputPolicy.text(c.text(),max,field):ProjectInputPolicy.optionalText(c.text(),max,field);
    }
    private LocalDate date(SourceRow row,String field) {String value=text(row,field,10,true);if(value.matches("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}"))value=value.replace('.','-');return ProjectInputPolicy.date(value,field);}
    private void discard(SourceRow row,String field,List<Issue> issues) {CellValue c=row.cells().get(field);if(!c.text().isBlank()||c.error()!=null)add(issues,row.number(),field,"WARN",c.text(),"이 배정에 적용하지 않는 입력은 폐기합니다.");}
    private String field(String field) {return switch(field==null?"":field){case "projectId"->"프로젝트ID";case "developerId"->"관리번호";case "jobCode"->"직무";case "startDate"->"투입일";case "endDate"->"철수일";case "contractMm"->"월 MM";case "billingUnitPrice"->"청구 단가";case "contractDeveloperId"->"계약상 인력 관리번호";default->field!=null&&HEADERS.contains(field)?field:"[행]";};}
    private ApiException invalid(String field) {return ProjectInputPolicy.invalid(field);}
    private void add(List<Issue> issues,int row,String column,String severity,String value,String reason) {issues.add(new Issue(0,severity,row,column,excerpt(value,200),excerpt(reason,300)));}
    private String excerpt(String value,int max) {return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max));}
}
