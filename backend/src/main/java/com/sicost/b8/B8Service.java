package com.sicost.b8;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.B7Dtos.Upload;
import com.sicost.b7.B7Dtos.UploadWrite;
import com.sicost.b7.B7Dtos.Summary;
import com.sicost.b7.B7Dtos.IssuesPage;
import static com.sicost.b8.B8Dtos.*;

@Service
public class B8Service {
    private final B8Repository repository;
    private final ExcelImportService imports;
    private final B8ImportDefinition definition;
    private final CurrentUserProvider users;
    private final TransactionTemplate transaction;
    private final Clock clock;
    public B8Service(B8Repository repository,ExcelImportService imports,B8ImportDefinition definition,
                     CurrentUserProvider users,PlatformTransactionManager manager,Clock clock) {
        this.repository=repository;this.imports=imports;this.definition=definition;this.users=users;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file) {
        Parsed parsed=imports.parse(file,B8ImportDefinition.FORMAT);
        return transaction.execute(status->{
            String actor=users.userId();Checked checked=definition.validate(parsed);
            long id=repository.create(new UploadWrite(0,parsed.filename(),parsed.size(),checked.summary(),actor,now()),checked);
            return validation(id,checked);
        });
    }
    public Applied apply(MultipartFile file,long uploadId) {
        String filename=imports.filename(file);
        Object result;
        try{result=transaction.execute(status->applyLocked(file,uploadId,filename,false));}
        catch(DuplicateKeyException race){result=transaction.execute(status->applyLocked(file,uploadId,filename,true));}
        if(result instanceof Validation rejected)throw new ApiException(422,"UPLOAD_VALIDATION_FAILED","현재 기준으로 다시 검사한 결과 오류가 있습니다.",null,
            Map.of("uploadId",rejected.uploadId(),"summary",rejected.summary(),"canApply",false,"issues",rejected.issues()));
        return (Applied)result;
    }
    private Object applyLocked(MultipartFile file,long id,String filename,boolean afterConflict) {
        String actor=users.userId();Upload upload=repository.lock(id);
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 결과를 찾을 수 없습니다.");
        if(!"B8".equals(upload.formType())||!"UI-02".equals(upload.screenId())||!actor.equals(upload.actor())
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!filename.equals(upload.filename())||upload.size()!=file.getSize())
            throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","동일한 검사 파일과 업로드 번호를 선택해 주세요.");
        Parsed parsed=imports.parse(file,B8ImportDefinition.FORMAT);
        if(upload.status().equals("APPLIED")) {
            int fileWarnings=(int)repository.issues(id).stream().filter(i->i.columnName().equals("[파일]")&&i.severity().equals("WARN")).count();
            return new Applied(new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),fileWarnings),repository.recover(id,parsed));
        }
        Checked checked=definition.validate(parsed);
        if(afterConflict&&checked.summary().errorRows()==0)throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","다른 저장과 충돌했습니다. 같은 파일을 다시 검사해 주세요.");
        LocalDateTime now=now();repository.replace(new UploadWrite(id,filename,file.getSize(),checked.summary(),actor,now),checked);
        if(checked.summary().errorRows()>0)return validation(id,checked);
        return new Applied(checked.summary(),repository.apply(id,checked,actor,now));
    }
    private Validation validation(long id,Checked checked) {
        var issues=checked.issues();return new Validation(id,checked.summary(),checked.summary().errorRows()==0&&checked.summary().totalRows()>0,
            new IssuesPage(issues.stream().limit(50).toList(),issues.size(),1,50),checked.expectedNumbers());
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
