package com.sicost.b5;

import com.sicost.assignmentsops.AssignmentActionsRepository;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.uploadsops.UploadResultProvider;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b5.B5Dtos.*;

@Service
public class B5Service implements UploadResultProvider {
    private final B5Mapper mapper;
    private final B5Repository repository;
    private final B5ImportDefinition definition;
    private final AssignmentActionsRepository assignments;
    private final AssignmentMonthlyService months;
    private final CurrentUserProvider users;
    private final RevisionService revisions;
    private final AggregateLockMapper locks;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public B5Service(B5Mapper mapper,B5Repository repository,B5ImportDefinition definition,AssignmentActionsRepository assignments,
        AssignmentMonthlyService months,CurrentUserProvider users,RevisionService revisions,AggregateLockMapper locks,Clock clock,PlatformTransactionManager manager) {
        this.mapper=mapper;this.repository=repository;this.definition=definition;this.assignments=assignments;this.months=months;this.users=users;this.revisions=revisions;this.locks=locks;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file,Request request) {
        String filename=definition.filename(file),digest=digest(file);
        return transaction.execute(status->{
            Upload upload=null;
            if(request.uploadId()!=null){locks.lock("UPLOAD:"+request.uploadId());upload=required(request.uploadId());context(upload,request,filename,file.getSize(),digest);if("APPLIED".equals(upload.status()))return replay(repository.result(upload));}
            writable(request.year());Parsed parsed=definition.parse(file);lockSources(parsed,request);Checked checked=definition.check(parsed,request);String token=token(request,checked);
            long id;
            if(upload==null)id=repository.create(write(0,filename,file.getSize(),checked),checked,Map.of("파일지문",digest,"연도",Integer.toString(request.year()),"미리보기토큰",token,"철수집합",membership(checked)));
            else {id=upload.uploadId();repository.replace(write(id,filename,file.getSize(),checked),checked,token);}
            return validation(id,checked,token);
        });
    }
    public Result apply(MultipartFile file,Request request) {
        if(request.uploadId()==null)throw ProjectInputPolicy.invalid("uploadId");ProjectInputPolicy.text(request.previewToken(),200,"previewToken");
        String filename=definition.filename(file),digest=digest(file);
        Object result=transaction.execute(status->{
            locks.lock("UPLOAD:"+request.uploadId());Upload upload=required(request.uploadId());context(upload,request,filename,file.getSize(),digest);
            if("APPLIED".equals(upload.status()))return repository.result(upload);
            writable(request.year());Parsed parsed=definition.parse(file);lockSources(parsed,request);Checked checked=definition.check(parsed,request);String token=token(request,checked);
            var context=repository.context(upload.uploadId());
            if(!request.withdrawalSelections().isEmpty()&&!Objects.equals(context.get("철수집합"),membership(checked)))throw stale();
            if(checked.summary().errorRows()>0){repository.replace(write(upload.uploadId(),filename,file.getSize(),checked),checked,token);return validation(upload.uploadId(),checked,token);}
            revisions.requireExpected(request.previewToken(),context.get("미리보기토큰"));revisions.requireExpected(request.previewToken(),token);
            repository.replace(write(upload.uploadId(),filename,file.getSize(),checked),checked,token);repository.apply(upload,checked,request.year());return repository.result(required(upload.uploadId()));
        });
        if(result instanceof Validation rejected)throw new ApiException(422,"REVALIDATION_FAILED","최신 기준으로 재검사한 결과 오류가 있습니다.",null,rejected);
        return (Result)result;
    }
    private void writable(int year) {ProjectInputPolicy.requireWritableYear(year,months.cashBase(),clock);}
    private void lockSources(Parsed parsed,Request request) {
        Set<String> keys=new TreeSet<>(List.of("operating-standards:CASH_BASE_YM","operating-standards:MM_OVER","code-group:JOB","code-group:INPUT_TYPE","code-group:EXT_PSBL","code-group:ASGN_CHG_RSN"));
        Set<String> numbers=new HashSet<>();parsed.rows().forEach(r->{keys.add("PROJECT:"+r.cells().get("프로젝트ID").text());numbers.add(r.cells().get("관리번호").text());numbers.add(r.cells().get("계약상 인력 관리번호").text());});
        mapper.developers().stream().filter(d->numbers.contains(d.managementNo())).forEach(d->keys.add("DEV:"+d.id()));
        request.withdrawalSelections().forEach(s->s.withdrawals().forEach(w->{keys.add("ASGN:"+w.assignmentId());var a=assignments.required(w.assignmentId());keys.add("DEV:"+a.developerId());keys.add("PROJECT:"+a.projectId());}));
        keys.forEach(locks::lock);months.mapper().lockClose();
    }
    private String token(Request request,Checked checked) {return revisions.revision(Arrays.asList(request.year(),canonical(request.withdrawalSelections()),checked.substitutionPreviews(),mapper.snapshot()));}
    private List<Selection> canonical(List<Selection> selections) {return selections.stream().map(s->new Selection(s.rowNumber(),s.withdrawals().stream().sorted(Comparator.comparing(com.sicost.assignmentsops.AssignmentActionsDtos.Withdrawal::assignmentId)).toList())).sorted(Comparator.comparingInt(Selection::rowNumber)).toList();}
    private String membership(Checked checked) {return revisions.revision(checked.substitutionPreviews().stream().map(p->List.of(p.rowNumber(),p.preview().candidates())).toList());}
    private ApiException stale() {return new ApiException(409,"STALE_WRITE","기존 철수 대상이 변경되었습니다. 같은 파일로 재검사하고 다시 확인해 주세요.");}
    private void context(Upload upload,Request request,String filename,long size,String digest) {
        var saved=repository.context(upload.uploadId());
        if(!"B5".equals(upload.formType())||!"UI-12".equals(upload.screenId())||!users.userId().equals(upload.actor())||!filename.equals(upload.filename())||size!=upload.size()||!Set.of("CHECKED","APPLIED").contains(upload.status())||!digest.equals(saved.get("파일지문"))||!Integer.toString(request.year()).equals(saved.get("연도")))throw B5Repository.mismatch();
    }
    private Upload required(long id) {Upload upload=mapper.lockUpload(id);if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 기록을 찾을 수 없습니다.");return upload;}
    private UploadWrite write(long id,String filename,long size,Checked checked) {return new UploadWrite(id,filename,size,checked.summary(),users.userId(),LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))));}
    private Validation validation(long id,Checked checked,String token) {return new Validation(id,"B5","UI-12","CHECKED",checked.summary(),List.of(),false,List.of(),checked.issues().size(),token,checked.substitutionPreviews());}
    private Validation replay(Result result) {return new Validation(result.uploadId(),result.formType(),result.screenId(),result.status(),result.summary(),result.results(),result.changed(),result.withdrawnIds(),mapper.issues(result.uploadId()).size(),repository.context(result.uploadId()).get("미리보기토큰"),List.of());}
    private String digest(MultipartFile file) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));}
        catch(IOException e){throw ProjectInputPolicy.invalid("file");}catch(NoSuchAlgorithmException e){throw new IllegalStateException("파일 문맥을 생성하지 못했습니다.");}
    }
    @Override public boolean supports(long id) {Upload upload=mapper.upload(id);return upload!=null&&"B5".equals(upload.formType());}
    @Override public Object result(long id) {return transaction.execute(status->{Upload upload=required(id);if(!"B5".equals(upload.formType())||!"UI-12".equals(upload.screenId())||!users.userId().equals(upload.actor()))throw B5Repository.mismatch();return repository.result(upload);});}
}
