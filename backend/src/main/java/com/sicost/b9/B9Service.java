package com.sicost.b9;

import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.projects.ProjectRegistrationService;
import com.sicost.common.validation.ProjectInputPolicy;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b9.B9Dtos.*;

@Service
public class B9Service {
    private final B9Repository repository;
    private final ExcelImportService imports;
    private final B9ImportDefinition definition;
    private final CurrentUserProvider users;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final WriteCoordinator coordinator;
    public B9Service(B9Repository repository, ExcelImportService imports, B9ImportDefinition definition,
                     CurrentUserProvider users, PlatformTransactionManager manager, Clock clock, WriteCoordinator coordinator) {
        this.repository=repository;this.imports=imports;this.definition=definition;this.users=users;this.clock=clock;this.coordinator=coordinator;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file,int year) {
        Parsed parsed=parse(file);Context context=new Context(year,digest(file));
        return transaction.execute(status->{
            String actor=users.userId();Checked checked=definition.validate(parsed,year);
            long id=repository.create(new UploadWrite(0,parsed.filename(),parsed.size(),checked.summary(),actor,now()),checked,context);
            return validation(id,checked);
        });
    }
    public Result apply(MultipartFile file,long uploadId,int year) {
        Parsed parsed=parse(file);Context supplied=new Context(year,digest(file));
        Object result;
        try{result=transaction.execute(status->applyLocked(parsed,uploadId,supplied,false));}
        catch(DuplicateKeyException race){result=transaction.execute(status->applyLocked(parsed,uploadId,supplied,true));}
        if(result instanceof Validation rejected)throw new ApiException(422,"UPLOAD_VALIDATION_FAILED","현재 기준으로 다시 검사한 결과 오류가 있습니다.",null,rejected);
        return (Result)result;
    }
    private Object applyLocked(Parsed parsed,long id,Context supplied,boolean afterConflict) {
        var keys=new TreeSet<String>();keys.add(ProjectRegistrationService.CREATION_LOCK);keys.add("UPLOAD:"+id);
        parsed.rows().forEach(row->{String value=row.cells().get("프로젝트ID").text();if(value.matches("[A-Za-z0-9-]{1,20}"))keys.add("PROJECT:"+value);});
        repository.lockKeys(keys);
        Upload upload=repository.lock(id);String actor=users.userId();
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 결과를 찾을 수 없습니다.");
        if(!"B9".equals(upload.formType())||!"UI-01".equals(upload.screenId())||!actor.equals(upload.actor())
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!parsed.filename().equals(upload.filename())||parsed.size()!=upload.size()
            ||upload.projectId()!=null||upload.step()!=null||!supplied.equals(repository.context(id)))throw B9Repository.mismatch();
        if(upload.status().equals("APPLIED"))return repository.result(upload);
        Checked checked=definition.validate(parsed,supplied.year());
        if(afterConflict&&checked.summary().errorRows()==0)throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","다른 등록과 충돌했습니다. 파일을 다시 검사해 주세요.");
        LocalDateTime now=now();
        repository.replace(new UploadWrite(id,parsed.filename(),parsed.size(),checked.summary(),actor,now),checked);
        if(checked.summary().errorRows()>0)return validation(id,checked);
        coordinator.create(keys,()->repository.snapshot(id),()->definition.requireYear(supplied.year()),()->repository.apply(id,checked,actor,now));
        return repository.result(repository.lock(id));
    }
    private Parsed parse(MultipartFile file) {
        if(file==null||file.isEmpty())throw ProjectInputPolicy.invalid("file");
        try{return imports.parse(file,B9ImportDefinition.FORMAT);}
        catch(ApiException e){if(e.status()==400)throw new ApiException(400,"INVALID_INPUT",e.getMessage(),"file",null);throw e;}
    }
    private String digest(MultipartFile file) {
        byte[] bytes;
        try{bytes=file.getBytes();}catch(IOException e){throw ProjectInputPolicy.invalid("file");}
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("검증 문맥을 생성하지 못했습니다.");}
        // MultipartFile owns its bytes; do not erase the caller's in-memory File.
    }
    private Validation validation(long id,Checked checked) {
        return new Validation(id,checked.summary(),new IssuesPage(checked.issues().stream().limit(50).toList(),checked.issues().size(),1,50),
            checked.summary().errorRows()==0&&checked.summary().totalRows()>0,null,null,null);
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
