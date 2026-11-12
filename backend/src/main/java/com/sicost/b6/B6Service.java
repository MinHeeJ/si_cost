package com.sicost.b6;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssuesPage;
import com.sicost.b9.B9Dtos.Context;
import com.sicost.b9.B9Dtos.Result;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b6.B6Dtos.*;

@Service
public class B6Service {
    private final B6Repository repository;
    private final B6Mapper mapper;
    private final B6ImportDefinition definition;
    private final CurrentUserProvider users;
    private final WriteCoordinator coordinator;
    private final RevisionService revisions;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public B6Service(B6Repository repository,B6Mapper mapper,B6ImportDefinition definition,CurrentUserProvider users,
                     WriteCoordinator coordinator,RevisionService revisions,Clock clock,PlatformTransactionManager manager) {
        this.repository=repository;this.mapper=mapper;this.definition=definition;this.users=users;this.coordinator=coordinator;this.revisions=revisions;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Project project(String id) {users.currentUser();return repository.required(ProjectInputPolicy.text(id,20,"projectId"));}
    public Validation validate(MultipartFile file,Request request) {
        Context context=context(file,request.year());
        return transaction.execute(status->{
            repository.lockKeys(List.of("PROJECT:"+request.projectId()));
            Project project=repository.required(request.projectId());requireWritable(request,project);
            var parsed=definition.parse(file,project);var checked=check(parsed,project,request.step());
            long id=repository.create(write(0,parsed.filename(),parsed.size(),request,checked),checked,context);
            return validation(id,checked,revisions.revision(repository.snapshot(request.projectId())));
        });
    }
    public Result apply(MultipartFile file,Request request) {
        Context supplied=context(file,request.year());Object result;
        try {result=transaction.execute(status->applyLocked(file,request,supplied,false));}
        catch(DuplicateKeyException race) {result=transaction.execute(status->applyLocked(file,request,supplied,true));}
        if(result instanceof Validation rejected)throw new ApiException(422,"UPLOAD_VALIDATION_FAILED","최신 기준으로 재검사한 결과 오류가 있습니다.",null,rejected);
        return (Result)result;
    }
    private Object applyLocked(MultipartFile file,Request request,Context supplied,boolean afterConflict) {
        var keys=List.of("PROJECT:"+request.projectId(),"UPLOAD:"+request.uploadId());repository.lockKeys(keys);
        var upload=repository.lock(request.uploadId());String actor=users.userId();
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 결과를 찾을 수 없습니다.");
        String filename=definition.filename(file);
        if(!"B6".equals(upload.formType())||!"UI-01".equals(upload.screenId())||!actor.equals(upload.actor())
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!filename.equals(upload.filename())||file.getSize()!=upload.size()
            ||!request.projectId().equals(upload.projectId())||!request.step().equals(upload.step())||!supplied.equals(repository.context(upload.uploadId())))throw B6Repository.mismatch();
        if(upload.status().equals("APPLIED"))return repository.result(upload);
        Project project=repository.required(request.projectId());requireWritable(request,project);
        Checked checked;
        try {checked=check(definition.parse(file,project),project,request.step());}
        catch(B6ImportDefinition.B6HeaderException e) {
            var issue=new Issue(1,"ERR",0,"[파일]","","현재 계약기간과 파일 제목을 다시 확인해 주세요.");
            checked=new Checked(Collections.emptyList(),new Summary(upload.totalRows(),0,0,upload.totalRows(),0,0),List.of(issue),null,null);
        }
        if(afterConflict&&checked.summary().errorRows()==0)throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","동시 반영과 충돌했습니다. 파일을 다시 검사해 주세요.");
        String revision=revisions.revision(repository.snapshot(request.projectId()));
        if(checked.summary().errorRows()>0) {
            repository.replace(write(upload.uploadId(),filename,file.getSize(),request,checked),checked);
            return validation(upload.uploadId(),checked,revision);
        }
        Checked accepted=checked;
        coordinator.write(keys,request.expectedRevision(),()->repository.snapshot(request.projectId()),()->{
            requireWritable(request,repository.required(request.projectId()));mapper.lockClose();
            var before=mapper.cells(request.projectId());
            B6Comparison.protect(before,B6Comparison.substitute(before,request.step(),accepted.cells()),new HashSet<>(mapper.protectedMonths(request.projectId())),repository.cashBase(clock));
        },()->true,()->{
            repository.replace(write(upload.uploadId(),filename,file.getSize(),request,accepted),accepted);
            repository.apply(request,upload.uploadId(),accepted,actor,now());
        });
        return repository.result(repository.lock(upload.uploadId()));
    }
    private Checked check(com.sicost.b7.B7Dtos.Parsed parsed,Project project,String step) {
        return definition.validate(parsed,project,step,new B6ImportDefinition.B6Sources(mapper.cells(project.id()),mapper.actuals(project.id()),mapper.developers()));
    }
    private void requireWritable(Request request,Project project) {
        ProjectInputPolicy.requireWritableYear(request.year(),repository.cashBase(clock),clock);
        if(project.status().equals("LOSE"))throw new ApiException(409,"INVALID_PROJECT_STATUS","실주 프로젝트는 조회만 할 수 있습니다.");
    }
    private Validation validation(long id,Checked checked,String revision) {
        return new Validation(id,checked.summary(),new IssuesPage(checked.issues().stream().limit(50).toList(),checked.issues().size(),1,50),
            checked.summary().errorRows()==0&&checked.summary().totalRows()>0,revision,checked.comparison(),checked.uploadedTableTotal());
    }
    private UploadWrite write(long id,String filename,long size,Request request,Checked checked) {
        return new UploadWrite(id,filename,size,request.projectId(),request.step(),checked.summary(),users.userId(),now());
    }
    private Context context(MultipartFile file,int year) {
        if(file==null||file.isEmpty())throw ProjectInputPolicy.invalid("file");
        try {
            definition.filename(file);
            return new Context(year,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())));
        } catch(IOException e) {throw ProjectInputPolicy.invalid("file");}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException("검증 문맥을 생성하지 못했습니다.");}
        catch(ApiException e) {if(e.status()==400)throw ProjectInputPolicy.invalid("file");throw e;}
    }
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
