package com.sicost.migration;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.uploadsops.UploadResultProvider;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;
import static com.sicost.migration.HistoricalProfitLossImportDefinition.months;

@Service
public class HistoricalProfitLossImportService implements UploadResultProvider {
    private final HistoricalProfitLossImportMapper mapper;
    private final HistoricalProfitLossImportRepository repository;
    private final HistoricalProfitLossImportDefinition definition;
    private final WriteCoordinator writes;
    private final MasterLockMapper master;
    private final TransactionTemplate transaction;
    public HistoricalProfitLossImportService(HistoricalProfitLossImportMapper mapper,HistoricalProfitLossImportRepository repository,
        HistoricalProfitLossImportDefinition definition,WriteCoordinator writes,MasterLockMapper master,PlatformTransactionManager manager) {
        this.mapper=mapper;this.repository=repository;this.definition=definition;this.writes=writes;this.master=master;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file) {
        String filename=definition.filename(file);
        return transaction.execute(status->{
            mapper.lockTimeout();master.lockClose();final Validation[] result={null};
            writes.create(keys(null),mapper::snapshot,()->months(mapper.cashBase()),()->{
                String base=mapper.cashBase();Checked checked=check(file,base);
                long id=repository.create(write(0,filename,file.getSize(),checked),checked);
                result[0]=repository.validation(id,checked,base);
            });return result[0];
        });
    }
    public Applied apply(MultipartFile file,Request request) {
        String filename=definition.filename(file);long id=id(request.uploadId());
        try {
            Object result=transaction.execute(status->{
                mapper.lockTimeout();master.lockClose();final Upload[] upload={null};final Checked[] checked={null};final String[] base={null};
                writes.write(keys(id),request.expectedRevision(),()->{
                    if(upload[0]==null) {
                        upload[0]=repository.required(id,true);context(upload[0],filename,file.getSize());
                        if("APPLIED".equals(upload[0].status()))throw new Replay(repository.replay(upload[0]));
                    }
                    return mapper.snapshot();
                },()->{
                    base[0]=mapper.cashBase();checked[0]=check(file,base[0]);
                    repository.replace(write(id,filename,file.getSize(),checked[0]),checked[0]);
                },()->checked[0].canApply()&&checked[0].changes().stream().anyMatch(Change::changed),
                    ()->repository.applyProfits(upload[0],checked[0],mapper.profits(),base[0]));
                if(!checked[0].canApply())return repository.validation(id,checked[0],base[0]);
                HistoricalProfitLossImportRepository.one(mapper.markApplied(write(id,filename,file.getSize(),checked[0])));
                Upload applied=repository.required(id,false);
                List<String> changed=checked[0].changes().stream().filter(Change::changed).map(Change::month).toList();
                List<String> unchanged=checked[0].changes().stream().filter(c->!c.changed()).map(Change::month).toList();
                Set<String> fileMonths=new HashSet<>();checked[0].rows().forEach(r->fileMonths.add(r.month()));
                List<String> kept=months(base[0]).stream().filter(m->!fileMonths.contains(m)).toList();
                return new Applied(Long.toString(id),"APPLIED",checked[0].summary(),false,"이관 손익 반영이 완료되었습니다.",
                    HistoricalProfitLossImportRepository.time(applied.appliedAt()),changed,unchanged,kept,checked[0].warnings());
            });
            if(result instanceof Validation validation)throw new ApiException(422,"REVALIDATION_FAILED","최신 검사 오류를 수정하고 다시 검사해 주세요.","file",Map.of("financial",Map.of("upload",validation)));
            return (Applied)result;
        }catch(Replay replay){return replay.result;}
    }
    private Checked check(MultipartFile file,String base) {return definition.check(definition.parse(file),base,mapper.profits());}
    private Set<String> keys(Long id) {
        Set<String> keys=new TreeSet<>(List.of("HISTORICAL_PROFIT_LOSS_RANGE","operating-standards:CASH_BASE_YM"));
        if(id!=null)keys.add("UPLOAD:"+id);return keys;
    }
    private void context(Upload upload,String filename,long size) {
        if(!"B11".equals(upload.formType())||!"UI-08".equals(upload.screenId())||upload.projectId()!=null||upload.step()!=null||upload.month()!=null
            ||!repository.actor().equals(upload.actor())||!filename.equals(upload.filename())||size!=upload.size()||!Set.of("CHECKED","APPLIED").contains(upload.status()))
            throw new ApiException(409,"UPLOAD_CONTEXT_MISMATCH","같은 처리자·양식·화면·파일명·크기의 검사 번호를 사용해 주세요.");
    }
    private UploadWrite write(long id,String filename,long size,Checked checked) {return new UploadWrite(id,filename,size,checked.summary(),repository.actor(),repository.now());}
    public static long id(String value) {if(value==null||!value.matches("[1-9][0-9]{0,11}"))throw ProjectInputPolicy.invalid("uploadId");return Long.parseLong(value);}
    @Override public boolean supports(long id) {Upload upload=mapper.upload(id);return upload!=null&&"B11".equals(upload.formType());}
    @Override public Object result(long id) {
        return transaction.execute(status->{Upload upload=repository.required(id,false);context(upload,upload.filename(),upload.size());return repository.result(upload);});
    }
    private static final class Replay extends RuntimeException {final Applied result;Replay(Applied result){this.result=result;}}
}
