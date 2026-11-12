package com.sicost.b6;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssueWrite;
import com.sicost.b9.B9Dtos.Context;
import com.sicost.b9.B9Dtos.Result;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.b9.B9Dtos.Upload;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.b6.B6Dtos.*;

@Repository
public class B6Repository {
    private final B6Mapper mapper;
    private final ProjectBudgetRepository projects;
    private final ChangeHistoryWriter history;
    private final ObjectMapper json;
    private final RevisionService revisions;
    private final AggregateLockMapper locks;
    public B6Repository(B6Mapper mapper,ProjectBudgetRepository projects,ChangeHistoryWriter history,
                        ObjectMapper json,RevisionService revisions,AggregateLockMapper locks) {
        this.mapper=mapper;this.projects=projects;this.history=history;this.json=json;this.revisions=revisions;this.locks=locks;
    }
    public Project required(String id) {
        var project=mapper.project(id);
        if(project==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        if(!project.contractType().equals("TK"))throw new ApiException(400,"INVALID_INPUT","턴키 프로젝트만 배분표를 사용할 수 있습니다.","projectId",null);
        return project;
    }
    public String snapshot(String id) {return projects.snapshot(id);}
    public YearMonth cashBase(Clock clock) {
        var value=projects.operatingValue("CASH_BASE_YM",YearMonth.now(clock).toString().replace("-",""));
        if(value==null)throw new ApiException(400,"INVALID_INPUT","시작 연월 기준을 확인해 주세요.","year",null);
        String month=value.toBigIntegerExact().toString();
        if(!month.matches("[0-9]{4}(0[1-9]|1[0-2])"))throw new ApiException(400,"INVALID_INPUT","시작 연월 기준을 확인해 주세요.","year",null);
        return YearMonth.of(Integer.parseInt(month.substring(0,4)),Integer.parseInt(month.substring(4)));
    }
    public void lockKeys(Collection<String> keys) {keys.stream().distinct().sorted().forEach(locks::lock);}
    public Upload lock(long id) {return mapper.lockUpload(id);}
    public long create(UploadWrite write,Checked checked,Context context) {
        var draft=new B6UploadDraft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호를 발급하지 못했습니다.");
        long id=draft.getUploadId();saveIssues(id,write,checked);
        history.append(new ChangeHistoryWriter.Change("TB_SY_UPLD",Long.toString(id),"NEW","검증 문맥",null,encode(context),"B6"));
        return id;
    }
    public void replace(UploadWrite write,Checked checked) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),write,checked);
    }
    private void saveIssues(long id,UploadWrite write,Checked checked) {
        for(Issue issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,write.actor(),write.now())));
    }
    public Context context(long id) {
        String value=mapper.context(id);if(value==null)throw mismatch();
        try{return json.readValue(value,Context.class);}catch(JsonProcessingException e){throw mismatch();}
    }
    public void apply(Request request,long id,Checked checked,String actor,LocalDateTime now) {
        var before=mapper.cells(request.projectId()).stream().filter(c->c.step().equals(request.step())).toList();
        mapper.deleteStage(request.projectId(),request.step());
        for(Cell cell:checked.cells())one(mapper.insertCell(new CellWrite(request.projectId(),id,cell,actor,now)));
        Map<String,Cell> old=new LinkedHashMap<>(),current=new LinkedHashMap<>();
        before.forEach(c->old.put(c.rowSn()+":"+c.month(),c));checked.cells().forEach(c->current.put(c.rowSn()+":"+c.month(),c));
        var keys=new TreeSet<>(old.keySet());keys.addAll(current.keySet());
        for(String key:keys) {
            Cell a=old.get(key),b=current.get(key);
            if(!Objects.equals(a,b))history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",request.projectId(),"UPD","배분표 "+request.step()+" "+key,
                a==null?null:encode(a),b==null?null:encode(b),"B6 업로드:"+id));
        }
        history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",request.projectId(),"UPD","배분표 업로드 "+request.step(),null,Long.toString(id),"B6 업로드:"+id));
        one(mapper.markApplied(id,actor,now));
    }
    public Result result(Upload upload) {
        var issues=mapper.issues(upload.uploadId());
        var summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),
            (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.columnName().equals("[파일]")).count(),
            (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.columnName().equals("[표]")).count());
        boolean applied=upload.status().equals("APPLIED");
        return new Result(upload.uploadId(),"B6",upload.status(),upload.projectId(),upload.step(),summary,List.of(),
            applied?revisions.revision(new B6ResultSnapshot(upload,context(upload.uploadId()))):null,applied);
    }
    public record B6ResultSnapshot(Upload upload,Context context) {}
    public static ApiException mismatch() {return new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","같은 파일·검사 번호·프로젝트·단계·선택연도를 사용해 주세요.");}
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("배분표 감사 이력을 생성하지 못했습니다.");}
    }
    private void one(int count) {if(count!=1)throw new IllegalStateException("배분표와 검사 결과를 저장하지 못했습니다.");}
}
