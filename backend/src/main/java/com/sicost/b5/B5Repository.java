package com.sicost.b5;

import com.sicost.assignmentsops.*;
import com.sicost.assignmentsops.AssignmentActionsDtos.*;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.b5.B5Dtos.*;

@Repository
public class B5Repository {
    private final B5Mapper mapper;
    private final AssignmentActionsRepository assignments;
    private final AssignmentActionsService actions;
    private final AssignmentMonthlyService months;
    private final ChangeHistoryWriter audit;
    private final MasterIdService ids;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final CommitInvalidation invalidation;
    private final com.sicost.common.concurrent.RevisionService revisions;
    public B5Repository(B5Mapper mapper,AssignmentActionsRepository assignments,AssignmentActionsService actions,AssignmentMonthlyService months,
        ChangeHistoryWriter audit,MasterIdService ids,CurrentUserProvider users,Clock clock,CommitInvalidation invalidation,com.sicost.common.concurrent.RevisionService revisions) {
        this.mapper=mapper;this.assignments=assignments;this.actions=actions;this.months=months;this.audit=audit;this.ids=ids;this.users=users;this.clock=clock;this.invalidation=invalidation;this.revisions=revisions;
    }
    public long create(UploadWrite write,Checked checked,Map<String,String> context) {
        Draft draft=new Draft(write);one(mapper.insertUpload(draft));if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호를 발급하지 못했습니다.");
        long id=draft.getUploadId();context.forEach((k,v)->metadata(id,k,null,v,"B5 문맥"));saveIssues(id,write,checked);return id;
    }
    public void replace(UploadWrite write,Checked checked,String token) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),write,checked);
        metadata(write.uploadId(),"미리보기토큰",context(write.uploadId()).get("미리보기토큰"),token,"B5 문맥");
        String membership=revisions.revision(checked.substitutionPreviews().stream().map(p->List.of(p.rowNumber(),p.preview().candidates())).toList());
        metadata(write.uploadId(),"철수집합",context(write.uploadId()).get("철수집합"),membership,"B5 문맥");
    }
    private void saveIssues(long id,UploadWrite write,Checked checked) {for(var issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,write.actor(),write.now())));}
    public Map<String,String> context(long id) {return metadata(id,"B5 문맥");}
    private Map<String,String> metadata(long id,String reason) {Map<String,String> values=new HashMap<>();mapper.receipts(id,reason).forEach(r->values.put(r.item(),r.value()));return values;}
    private void metadata(long id,String key,String before,String after,String reason) {audit.append(new ChangeHistoryWriter.Change("TB_SY_UPLD",Long.toString(id),before==null?"NEW":"UPD",key,before,after,reason));}
    public void apply(Upload upload,Checked checked,int year) {
        for(Withdrawal withdrawal:checked.withdrawals()) {
            actions.change(withdrawal.assignmentId(),B5ImportDefinition.change(year,withdrawal));
            metadata(upload.uploadId(),"철수:"+withdrawal.assignmentId(),null,withdrawal.assignmentId(),"B5 결과");
        }
        String actor=users.userId();LocalDateTime time=LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        for(var row:checked.rows()) {
            Source s=row.source();String id=ids.nextAssignmentId();Source created=new Source(id,s.projectId(),s.developerId(),s.jobCode(),s.startDate(),s.endDate(),s.contractMm(),s.price(),s.inputType(),s.contractDeveloperId(),s.extensionPossibility(),0,"N","ACT");
            one(assignments.mapper().insert(new Write(created,actor,time)));
            one(assignments.mapper().event(new Event(id,1,"INPUT",s.startDate(),null,s.startDate()+"/"+s.endDate(),null,null,s.price(),s.contractMm(),"N",months.protectedMonths(created).isEmpty()?"N":"Y",actor,time)));
            audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",id,"NEW","ASGN_ID",null,id,"B5 업로드:"+upload.uploadId()+";행:"+row.rowNumber()));
            var project=assignments.project(s.projectId());String status=project.endDate().isBefore(com.sicost.common.validation.ProjectInputPolicy.today(clock))&&!"LOSE".equals(project.status())?"END":project.status();
            months.synchronize(created,status,project.contractType());
            metadata(upload.uploadId(),"행:"+row.rowNumber(),null,id,"B5 결과");
            for(String key:List.of("ASGN:"+id,"DEV:"+s.developerId(),"PROJECT:"+s.projectId()))invalidation.afterCommit(key);
        }
        one(mapper.markApplied(upload.uploadId(),actor,time));
    }
    public Result result(Upload upload) {
        var issues=mapper.issues(upload.uploadId());var summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),(int)issues.stream().filter(i->i.columnName().equals("[파일]")&&i.severity().equals("WARN")).count(),0);
        List<ResultRow> rows=new ArrayList<>();List<String> withdrawn=new ArrayList<>();boolean applied="APPLIED".equals(upload.status());
        if(applied)metadata(upload.uploadId(),"B5 결과").forEach((k,v)->{if(k.startsWith("행:"))rows.add(new ResultRow(Integer.parseInt(k.substring(2)),v));else if(k.startsWith("철수:"))withdrawn.add(v);});
        rows.sort(Comparator.comparingInt(ResultRow::rowNumber));Collections.sort(withdrawn);
        if(applied&&rows.size()!=upload.totalRows())throw new IllegalStateException("최초 반영 결과 이력을 확인하지 못했습니다.");
        return new Result(upload.uploadId(),"B5","UI-12",upload.status(),summary,List.copyOf(rows),applied,List.copyOf(withdrawn));
    }
    public static ApiException mismatch() {return new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","같은 처리자·양식·화면·파일·연도의 검사를 사용해 주세요.");}
    public static void one(int count) {if(count!=1)throw new IllegalStateException("투입 배정과 필수 기록을 저장하지 못했습니다.");}
}
