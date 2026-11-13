package com.sicost.b2;

import com.sicost.b7.B7Dtos.Parsed;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.concurrent.WriteCoordinator;
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
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.b2.FinancialImportDtos.*;

@Service
public class FinancialImportService implements UploadResultProvider {
    private final FinancialImportMapper mapper;
    private final FinancialImportRepository repository;
    private final FinancialImportDefinition definition;
    private final WriteCoordinator writes;
    private final MasterLockMapper master;
    private final TransactionTemplate transaction;
    public FinancialImportService(FinancialImportMapper mapper,FinancialImportRepository repository,FinancialImportDefinition definition,
        WriteCoordinator writes,MasterLockMapper master,PlatformTransactionManager manager) {
        this.mapper=mapper;this.repository=repository;this.definition=definition;this.writes=writes;this.master=master;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Result validate(MultipartFile file,String selectedMonth) {
        String month=month(selectedMonth),filename=definition.filename(file),fingerprint=digest(file);Parsed parsed=definition.parse(file);
        return transaction.execute(status->{
            mapper.lockTimeout();master.lockClose();final long[] id={0};
            writes.create(keys(month,mapper.items(month),parsed,null),()->mapper.snapshot(month),()->writable(month),()->{
                Checked checked=check(parsed,month);protectCash(month,mapper.items(month),checked);
                id[0]=repository.create(write(0,filename,file.getSize(),month,checked),checked,fingerprint,repository.revision(month));
            });
            return repository.result(repository.required(id[0],false));
        });
    }
    public Result apply(MultipartFile file,Request request) {
        String month=month(request.month());long id=id(request.uploadId());String filename=definition.filename(file),fingerprint=digest(file);
        Upload initial=repository.required(id,false);context(initial,filename,file.getSize(),month,fingerprint);
        if("APPLIED".equals(initial.status()))return repository.result(initial);
        ProjectInputPolicy.expectedRevision(request.expectedRevision());Parsed discovered=definition.parse(file);
        Set<String> keys=keys(month,mapper.items(month),discovered,id);
        for(int attempt=0;attempt<2;attempt++) {
            try {
                Result result=transaction.execute(status->{
                    mapper.lockTimeout();master.lockClose();final Checked[] checked={null};final Upload[] upload={null};
                    writes.write(keys,request.expectedRevision(),()->{
                        upload[0]=repository.required(id,true);context(upload[0],filename,file.getSize(),month,fingerprint);
                        if("APPLIED".equals(upload[0].status()))throw new Replay(repository.result(upload[0]));
                        return mapper.snapshot(month);
                    },()->{
                        repository.expected(repository.context(id).get("검증revision"),repository.revision(month));
                        List<StoredItem> old=mapper.items(month);Parsed fresh=definition.parse(file);
                        Set<String> expanded=keys(month,old,fresh,id);if(!keys.containsAll(expanded))throw new Expanded(expanded);
                        checked[0]=check(fresh,month);writable(month);protectCash(month,old,checked[0]);
                        if(checked[0].summary().errorRows()>0)repository.replace(write(id,filename,file.getSize(),month,checked[0]),checked[0],repository.revision(month));
                    },()->checked[0].summary().errorRows()==0,()->{
                        repository.replace(write(id,filename,file.getSize(),month,checked[0]),checked[0],repository.revision(month));
                        repository.apply(upload[0],checked[0],mapper.items(month));
                    });
                    return repository.result(repository.required(id,false));
                });
                if("CHECKED".equals(result.status()))throw new ApiException(422,"REVALIDATION_FAILED","최신 기준의 검사 오류를 수정하고 다시 검사해 주세요.",null,Map.of("financial",Map.of("upload",result)));
                return result;
            } catch(Replay replay) { return replay.result; }
            catch(Expanded expanded) { keys.addAll(expanded.keys); }
        }
        throw new ApiException(409,"STALE_WRITE","잠금 범위가 변경되었습니다. 다시 검사해 주세요.");
    }
    private Checked check(Parsed parsed,String month) {
        List<StoredItem> old=mapper.items(month);int replace=(int)old.stream().filter(r->r.uploadId()!=null).count();
        return definition.check(parsed,mapper.codes(),replace,old.size()-replace);
    }
    private Set<String> keys(String month,List<StoredItem> old,Parsed parsed,Long id) {
        Set<String> keys=new TreeSet<>(List.of("FINANCIAL_MONTH:"+month,"operating-standards:CASH_BASE_YM","code-group:FIN_CLS","code-group:SGA_ITEM"));
        if(id!=null)keys.add("UPLOAD:"+id);
        old.stream().filter(r->r.uploadId()!=null).forEach(r->keys.add("CASH_MONTH:"+cashMonth(month,r.ioDate())));
        parsed.rows().forEach(r->{String v=r.cells().get("입출금일").text();try{keys.add("CASH_MONTH:"+(v.isBlank()?month:cashMonth(month,LocalDate.parse(v))));}catch(RuntimeException ignored){keys.add("CASH_MONTH:"+month);}});
        return keys;
    }
    private void protectCash(String month,List<StoredItem> old,Checked checked) {
        Set<String> cash=new TreeSet<>();old.stream().filter(r->r.uploadId()!=null).forEach(r->cash.add(cashMonth(month,r.ioDate())));
        checked.rows().forEach(r->cash.add(cashMonth(month,r.value().ioDate())));
        for(String value:cash)if(mapper.closed(value))throw conflict("MONTH_CLOSED",value,"기존 또는 새 입출금 월이 마감되었습니다.");
    }
    private String cashMonth(String month,LocalDate date) { return date==null?month:YearMonth.from(date).toString().replace("-",""); }
    private void writable(String month) {
        YearMonth target=YearMonth.parse(FinancialImportRepository.apiMonth(month)),today=YearMonth.from(repository.now());
        String base=mapper.cashBase();if(base==null)throw new IllegalStateException("운영 시작월이 없습니다.");
        if(target.getYear()!=today.getYear()||target.isAfter(today)||month.compareTo(base)<0)throw conflict("PERIOD_READ_ONLY",month,"올해 운영 기간의 미래가 아닌 월만 변경할 수 있습니다.");
        if(mapper.closed(month))throw conflict("MONTH_CLOSED",month,"선택월이 마감되었습니다.");
    }
    private ApiException conflict(String code,String month,String message) { return new ApiException(409,code,message,"month",Map.of("financial",Map.of("month",FinancialImportRepository.apiMonth(month),"business","B2"))); }
    private void context(Upload upload,String filename,long size,String month,String fingerprint) {
        if(!"B2".equals(upload.formType())||!"UI-10".equals(upload.screenId())||upload.projectId()!=null||upload.step()!=null
            ||!repository.actor().equals(upload.actor())||!filename.equals(upload.filename())||size!=upload.size()||!month.equals(upload.month())
            ||!Set.of("CHECKED","APPLIED").contains(upload.status())||!fingerprint.equals(repository.context(upload.uploadId()).get("파일지문")))
            throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","같은 처리자·파일·검사 번호·화면·선택월을 사용해 주세요.");
    }
    private UploadWrite write(long id,String filename,long size,String month,Checked checked) { return new UploadWrite(id,filename,size,month,checked.summary(),repository.actor(),repository.now()); }
    public String month(String value) {
        if(value==null||!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw ProjectInputPolicy.invalid("month");
        try { YearMonth.parse(value); }catch(RuntimeException e){throw ProjectInputPolicy.invalid("month");}return value.replace("-","");
    }
    public static long id(String value) { if(value==null||!value.matches("[1-9][0-9]{0,11}"))throw ProjectInputPolicy.invalid("uploadId");return Long.parseLong(value); }
    private String digest(MultipartFile file) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())); }
        catch(IOException e){throw ProjectInputPolicy.invalid("file");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Override public boolean supports(long id) { Upload upload=mapper.upload(id);return upload!=null&&"B2".equals(upload.formType()); }
    @Override public Object result(long id) {
        return transaction.execute(status->{Upload upload=repository.required(id,false);
            if(!"B2".equals(upload.formType())||!"UI-10".equals(upload.screenId())||!repository.actor().equals(upload.actor()))throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","업로드 문맥을 확인해 주세요.");
            return repository.result(upload);});
    }
    private static final class Replay extends RuntimeException { final Result result;Replay(Result result){this.result=result;} }
    private static final class Expanded extends RuntimeException { final Set<String> keys;Expanded(Set<String> keys){this.keys=keys;} }
}
