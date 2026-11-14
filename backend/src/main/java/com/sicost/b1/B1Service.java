package com.sicost.b1;

import com.sicost.apiops.PayrollMapper;
import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.uploadsops.UploadResultProvider;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.b1.B1Dtos.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class B1Service implements UploadResultProvider {
    public record Dependencies(B1Mapper mapper,B1Repository repository,B1ImportDefinition definition) {}
    private final B1Mapper mapper;
    private final B1Repository repository;
    private final B1ImportDefinition definition;
    private final CurrentUserProvider users;
    private final RevisionService revisions;
    private final PayrollMapper payroll;
    private final AggregateLockMapper locks;
    private com.sicost.common.concurrent.SettlementSourceLocks settlementLocks;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void settlementLocks(com.sicost.common.concurrent.SettlementSourceLocks value) { this.settlementLocks=value; }
    private final Clock clock;
    private final TransactionTemplate transaction;
    public B1Service(B1Mapper mapper,B1Repository repository,B1ImportDefinition definition,CurrentUserProvider users,
        RevisionService revisions,PayrollMapper payroll,AggregateLockMapper locks,Clock clock,PlatformTransactionManager manager) {
        this.mapper=mapper;this.repository=repository;this.definition=definition;this.users=users;this.revisions=revisions;this.payroll=payroll;this.locks=locks;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Object validate(MultipartFile file,Request request) {
        String month=month(request.month()),filename=definition.filename(file),digest=digest(file);
        return transaction.execute(status->{
            Upload upload=null;
            if(request.uploadId()!=null) {
                locks.lock("UPLOAD:"+request.uploadId());upload=required(request.uploadId());context(upload,filename,file.getSize(),month,digest);
                if(upload.status().equals("APPLIED"))return validationReplay(repository.result(upload));
            }
            Parsed parsed=definition.parse(file);lockSources(parsed,month);writable(month);
            Checked checked=definition.check(parsed,mapper.developers(),mapper.rates());String revision=revision(month);
            var write=write(request.uploadId()==null?0:request.uploadId(),filename,file.getSize(),month,checked);
            long id;
            if(upload==null)id=repository.create(write,checked,digest,revision);
            else {id=upload.uploadId();repository.replace(write,checked,revision);}
            return validation(id,month,checked,revision);
        });
    }
    public Result apply(MultipartFile file,Request request) {
        if(request.uploadId()==null)throw ProjectInputPolicy.invalid("uploadId");
        if(!request.confirmFullReplacement())throw ProjectInputPolicy.invalid("confirmFullReplacement");
        ProjectInputPolicy.expectedRevision(request.expectedRevision());String month=month(request.month());
        String filename=definition.filename(file),digest=digest(file);
        Object result=transaction.execute(status->{
            locks.lock("UPLOAD:"+request.uploadId());Upload upload=required(request.uploadId());context(upload,filename,file.getSize(),month,digest);
            if(upload.status().equals("APPLIED"))return repository.result(upload);
            Parsed parsed=definition.parse(file);lockSources(parsed,month);writable(month);
            String revision=revision(month);revisions.requireExpected(request.expectedRevision(),revision);
            revisions.requireExpected(repository.context(upload.uploadId()).get("검증revision"),revision);
            Checked checked=definition.check(parsed,mapper.developers(),mapper.rates());
            repository.replace(write(upload.uploadId(),filename,file.getSize(),month,checked),checked,revision);
            if(checked.summary().errorRows()>0)return validation(upload.uploadId(),month,checked,revision);
            boolean changed=repository.apply(upload,checked,users.userId(),now());
            repository.receipt(upload.uploadId(),checked,revision(month),changed);
            B1Repository.one(mapper.markApplied(upload.uploadId(),users.userId(),now()));
            return repository.result(required(upload.uploadId()));
        });
        if(result instanceof Validation rejected)throw new ApiException(422,"REVALIDATION_FAILED","최신 기준으로 다시 검사한 결과 오류가 있습니다.",null,rejected);
        return (Result)result;
    }
    private void lockSources(Parsed parsed,String month) {
        Set<String> numbers=new HashSet<>();parsed.rows().forEach(r->numbers.add(r.cells().get("관리번호").text()));
        Set<String> keys=new TreeSet<>();keys.add("PAY:"+month);keys.add("operating-standards:CASH_BASE_YM");keys.add("code-group:ALWNC");
        mapper.developers().stream().filter(d->numbers.contains(d.managementNo())).forEach(d->keys.add("DEV:"+d.id()));
        mapper.members(month).forEach(d->keys.add("DEV:"+d.developerId()));if(settlementLocks!=null)settlementLocks.lock(keys);else keys.forEach(locks::lock);mapper.lockClose();
        List<String> ids=keys.stream().filter(k->k.startsWith("DEV:")).map(k->k.substring(4)).toList();
        if(!ids.isEmpty())mapper.lockDevelopers(ids);
        // Re-read all business sources only after the reference and month locks.
    }
    private void writable(String month) {
        YearMonth target=YearMonth.parse(B1Repository.apiMonth(month)),current=YearMonth.from(LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)));
        if(target.getYear()!=current.getYear())throw conflict("READ_ONLY_YEAR",month,"올해 급여만 검사·반영할 수 있습니다.");
        String base=mapper.cashBase();
        if(base==null)throw new IllegalStateException("운영 시작월을 확인하지 못했습니다.");
        if(month.compareTo(base)<0||target.isAfter(current)||mapper.closed(month))throw conflict("CLOSED_MONTH",month,"마감·이관·미래월 급여는 변경할 수 없습니다.");
    }
    private ApiException conflict(String code,String month,String reason) { return new ApiException(409,code,reason,"month",Map.of("month",B1Repository.apiMonth(month),"reason",reason)); }
    private void context(Upload upload,String filename,long size,String month,String digest) {
        if(!"B1".equals(upload.formType())||!"UI-02".equals(upload.screenId())||!users.userId().equals(upload.actor())||!filename.equals(upload.filename())||size!=upload.size()||!month.equals(upload.month())
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!digest.equals(repository.context(upload.uploadId()).get("파일지문")))throw B1Repository.mismatch();
    }
    private Upload required(long id) { Upload value=mapper.lockUpload(id);if(value==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 기록을 찾을 수 없습니다.");return value; }
    private String revision(String month) { return revisions.revision(payroll.snapshot(month)); }
    private UploadWrite write(long id,String filename,long size,String month,Checked checked) { return new UploadWrite(id,filename,size,month,checked.summary(),users.userId(),now()); }
    private Validation validation(long id,String month,Checked checked,String revision) { return new Validation(id,"B1","UI-02","CHECKED",checked.summary(),Collections.emptyList(),false,B1Repository.apiMonth(month),revision,checked.issues().size(),repository.preview(month,checked)); }
    private Validation validationReplay(Result result) { return new Validation(result.uploadId(),result.formType(),result.screenId(),result.status(),result.summary(),result.results(),result.changed(),result.month(),result.revision(),mapper.issues(result.uploadId()).size(),new Replacement(0,0,0,Collections.emptyList())); }
    private String month(String value) { String trimmed=ProjectInputPolicy.text(value,7,"month");if(!trimmed.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw ProjectInputPolicy.invalid("month");return DatabaseMonthCodec.toDatabase(trimmed); }
    private String digest(MultipartFile file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch(IOException e) { throw ProjectInputPolicy.invalid("file"); }catch(NoSuchAlgorithmException e) { throw new IllegalStateException("파일 문맥을 생성하지 못했습니다."); }
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL); }
    @Override public boolean supports(long id) { Upload value=mapper.upload(id);return value!=null&&"B1".equals(value.formType()); }
    @Override public Object result(long id) {
        return transaction.execute(status->{Upload upload=required(id);if(!users.userId().equals(upload.actor())||!"UI-02".equals(upload.screenId()))throw B1Repository.mismatch();return repository.result(upload);});
    }
}
