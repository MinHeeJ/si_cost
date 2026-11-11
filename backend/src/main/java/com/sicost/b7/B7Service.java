package com.sicost.b7;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b7.B7Dtos.*;

@Service
public class B7Service {
    private final B7Repository repository;
    private final ExcelImportService imports;
    private final B7ImportDefinition definition;
    private final CurrentUserProvider users;
    private final TransactionTemplate transaction;
    private final Clock clock;
    public B7Service(B7Repository repository,ExcelImportService imports,B7ImportDefinition definition,
                     CurrentUserProvider users,PlatformTransactionManager manager,Clock clock) {
        this.repository=repository;this.imports=imports;this.definition=definition;this.users=users;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file) {
        Parsed parsed=imports.parse(file);
        return transaction.execute(status->{
            String actor=users.userId();Checked checked=definition.validate(parsed);long id=repository.nextUploadId();
            repository.save(new UploadWrite(id,parsed.filename(),parsed.size(),checked.summary(),actor,LocalDateTime.now(clock)),checked,false);
            return validation(id,checked);
        });
    }
    public Applied apply(MultipartFile file,long uploadId) {
        String filename=imports.filename(file);
        Object result;
        try { result=transaction.execute(status->applyLocked(file,uploadId,filename,false)); }
        catch(DuplicateKeyException race) {
            result=transaction.execute(status->applyLocked(file,uploadId,filename,true));
        }
        if(result instanceof Validation rejected) throw new ApiException(422,"UPLOAD_VALIDATION_FAILED","현재 기준으로 다시 검사한 결과 오류가 있습니다.",null,
            Map.of("uploadId",rejected.uploadId(),"summary",rejected.summary(),"canApply",false,"issues",rejected.issues()));
        return (Applied)result;
    }
    private Object applyLocked(MultipartFile file,long id,String filename,boolean afterConflict) {
        String actor=users.userId();Upload upload=repository.lock(id);
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 결과를 찾을 수 없습니다.");
        if(!upload.formType().equals("B7")||!upload.screenId().equals("UI-15")||!upload.actor().equals(actor)
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!upload.filename().equals(filename)||upload.size()!=file.getSize())
            throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","동일한 검사 파일과 업로드 번호를 선택해 주세요.");
        if(upload.status().equals("APPLIED"))return applied(upload,repository.issues(id));
        Parsed parsed=imports.parse(file);Checked checked=definition.validate(parsed);
        if(afterConflict && checked.summary().errorRows()==0)throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","다른 저장과 충돌했습니다. 파일을 다시 검사해 주세요.");
        LocalDateTime now=LocalDateTime.now(clock);
        repository.save(new UploadWrite(id,filename,file.getSize(),checked.summary(),actor,now),checked,true);
        if(checked.summary().errorRows()>0)return validation(id,checked);
        repository.apply(id,checked,actor,now);
        return new Applied(id,"APPLIED",checked.summary(),checked.summary().totalRows(),format(now));
    }
    private Applied applied(Upload upload,List<Issue> issues) {
        int fileWarnings=fileWarningCount(issues);
        return new Applied(upload.uploadId(),"APPLIED",new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),fileWarnings),upload.totalRows(),format(upload.appliedAt()));
    }
    private int fileWarningCount(List<Issue> issues) {
        return (int)issues.stream().filter(i->i.severity().equals("WARN")&&i.reason().equals("알 수 없는 열은 반영하지 않습니다.")).count();
    }
    private Validation validation(long id,Checked checked) {
        List<Issue> all=checked.issues();
        return new Validation(id,"CHECKED",checked.summary(),checked.summary().errorRows()==0&&checked.summary().totalRows()>0,
            new IssuesPage(all.stream().limit(50).toList(),all.size(),1,50));
    }
    private String format(LocalDateTime date){return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}
}
