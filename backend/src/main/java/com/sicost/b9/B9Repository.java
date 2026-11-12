package com.sicost.b9;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectRegistrationService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.b9.B9Dtos.*;

@Repository
public class B9Repository {
    private final B9Mapper mapper;
    private final ChangeHistoryWriter history;
    private final ProjectRegistrationService registration;
    private final ObjectMapper json;
    private final RevisionService revisions;
    private final AggregateLockMapper locks;
    public B9Repository(B9Mapper mapper, ChangeHistoryWriter history, ProjectRegistrationService registration,
                        ObjectMapper json, RevisionService revisions, AggregateLockMapper locks) {
        this.mapper=mapper;this.history=history;this.registration=registration;this.json=json;this.revisions=revisions;this.locks=locks;
    }
    public void lockKeys(Collection<String> keys) { keys.stream().distinct().sorted().forEach(locks::lock); }
    public Upload lock(long id) { return mapper.lockUpload(id); }
    public long create(UploadWrite write, Checked checked, Context context) {
        var draft=new UploadDraft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호를 발급하지 못했습니다.");
        long id=draft.getUploadId();saveIssues(id,write,checked);
        history.append(new ChangeHistoryWriter.Change("TB_SY_UPLD",Long.toString(id),"NEW","검증 문맥",null,encode(context),"B9"));
        return id;
    }
    public void replace(UploadWrite write, Checked checked) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),write,checked);
    }
    private void saveIssues(long id,UploadWrite write,Checked checked) {
        for(Problem p:checked.issues())one(mapper.insertIssue(new IssueWrite(id,new Issue(p.sequence(),p.severity(),p.rowNumber(),p.columnName(),p.inputValue(),p.reason()),write.actor(),write.now())));
    }
    public Context context(long id) {
        String value=mapper.context(id);
        if(value==null)throw mismatch();
        try{return json.readValue(value,Context.class);}catch(JsonProcessingException e){throw mismatch();}
    }
    public record Snapshot(Upload upload, Context context) {}
    public Snapshot snapshot(long id) {return new Snapshot(lock(id),context(id));}
    public void apply(long id,Checked checked,String actor,LocalDateTime now) {
        var reserved=registration.reservedIds(checked.projects().stream().map(ProjectRow::explicitId).filter(Objects::nonNull).toList());
        for(ProjectRow row:checked.projects()) {
            String projectId=row.explicitId()==null?ProjectRegistrationService.allocate(reserved):row.explicitId();
            registration.register(projectId,row.project(),actor,now);
            history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",projectId,"NEW","등록",null,
                encode(Map.of("id",projectId,"name",row.project().name())),"B9 업로드:"+id+";행:"+row.rowNumber()));
        }
        one(mapper.markApplied(id,actor,now));
    }
    public Result result(Upload upload) {
        List<Issue> issues=mapper.issues(upload.uploadId());
        var summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),
            (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.columnName().equals("[파일]")).count(),
            (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.columnName().equals("[표]")).count());
        List<ResultRow> rows=new ArrayList<>();
        boolean applied=upload.status().equals("APPLIED");
        if(applied) {
            String prefix="B9 업로드:"+upload.uploadId()+";행:";
            for(Receipt receipt:mapper.results(prefix)) {
                try {
                    var value=json.readTree(receipt.value());int row=Integer.parseInt(receipt.reason().substring(prefix.length()));
                    String id=value.path("id").asText(),name=value.path("name").asText();
                    if(row<1||id.isEmpty()||name.isEmpty())throw new IllegalStateException("반영 결과 이력을 확인하지 못했습니다.");
                    rows.add(new ResultRow(row,id,name));
                } catch(JsonProcessingException|NumberFormatException e){throw new IllegalStateException("반영 결과 이력을 확인하지 못했습니다.");}
            }
            rows.sort(Comparator.comparingInt(ResultRow::rowNumber));
            if(rows.size()!=upload.totalRows())throw new IllegalStateException("반영 결과 이력을 확인하지 못했습니다.");
        }
        return new Result(upload.uploadId(),"B9",upload.status(),null,null,summary,List.copyOf(rows),
            applied?revisions.revision(new Snapshot(upload,context(upload.uploadId()))):null,applied);
    }
    public static ApiException mismatch(){return new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","동일한 검사 파일·업로드 번호·선택연도를 사용해 주세요.");}
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException("업로드 감사 이력을 생성하지 못했습니다.");}
    }
    private void one(int count){if(count!=1)throw new IllegalStateException("업로드 결과를 저장하지 못했습니다.");}
}
