package com.sicost.clients;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.security.AccountCipher;
import com.sicost.common.validation.InputValidator;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.clients.ClientDtos.*;

@Service
public class ClientService {
    private final ClientRepository repository;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final AccountCipher cipher;
    private final MasterIdService ids;
    private final Clock clock;
    public ClientService(ClientRepository repository, RevisionService revisions, ChangeHistoryWriter history,
                         AccountCipher cipher, MasterIdService ids, Clock clock) {
        this.repository=repository;this.revisions=revisions;this.history=history;this.cipher=cipher;this.ids=ids;this.clock=clock;
    }
    private ClientMapper mapper(){return repository.mapper();}
    private LocalDate today(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
    private LocalDateTime now(){return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(Query query) {
        repository.actor(); LocalDate date=today();
        long total=mapper().count(query);
        List<Client> items=mapper().list(new Selection(query,date,(long)(query.page()-1)*query.size())).stream().map(row->view(row,date)).toList();
        return new Page(items,total,query.page(),query.size(),date);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Client get(String id){repository.actor();return view(required(id,false),today());}
    public Result create(Save input) {
        try {return repository.write(()->createInTransaction(input));}
        catch(DuplicateKeyException e) {
            Stored existing=mapper().byBusinessNo(input.businessNo());
            if(existing!=null)throw duplicate(existing);
            throw e;
        }
    }
    private Result createInTransaction(Save input) {
        String actor=repository.actor(); Fields f=input.fields();
        repository.lockBusinessNo(input.businessNo());
        Stored duplicate=mapper().byBusinessNo(input.businessNo());if(duplicate!=null)throw duplicate(duplicate);
        validateCodes(f,null);
        List<Warning> warnings=warnings(input,null);
        String encrypted=f.accountNumber()==null?null:cipher.encrypt(f.accountNumber());
        String id=ids.nextClientId();
        Stored row=new Stored(id,f.type(),f.name(),input.businessNo(),f.ceo(),f.industryCode(),f.contactName(),f.phone(),f.taxEmail(),f.bankCode(),encrypted,f.depositor(),"Y",actor,now(),null,null);
        if(mapper().insert(new Mutation(row))!=1)throw new IllegalStateException("거래처를 저장하지 못했습니다.");
        history.append(new ChangeHistoryWriter.Change("TB_BS_CLNT",id,"NEW","등록",null,id+" "+f.name(),null));
        repository.committed();
        return new Result(id,true,"등록했습니다.",warnings,view(required(id,false),today()));
    }
    public Result update(String id,Save input){return repository.write(()->updateInTransaction(id,input));}
    private Result updateInTransaction(String id,Save input) {
        String actor=repository.actor(); Stored old=required(id,true);
        revisions.requireExpected(input.expectedRevision(),revisions.revision(old));
        Fields f=input.fields();
        if("KEEP".equals(input.accountAction()) && (old.type().equals("CUST") || old.bankCode()==null || old.encryptedAccount()==null || old.depositor()==null))throw InputValidator.invalid("accountAction");
        validateCodes(f,old); references(old,f.type());
        List<Warning> warnings=warnings(input,id);
        boolean keep="KEEP".equals(input.accountAction());
        String bank=keep?old.bankCode():f.bankCode(),depositor=keep?old.depositor():f.depositor();
        String encrypted=keep?old.encryptedAccount():null;
        if(f.accountNumber()!=null) {
            boolean same=Objects.equals(bank,old.bankCode()) && Objects.equals(depositor,old.depositor())
                && Objects.equals(f.accountNumber(),cipher.decrypt(old.encryptedAccount()));
            encrypted=same?old.encryptedAccount():cipher.encrypt(f.accountNumber());
        }
        Stored candidate=new Stored(id,f.type(),f.name(),old.businessNo(),f.ceo(),f.industryCode(),f.contactName(),f.phone(),f.taxEmail(),bank,encrypted,depositor,old.useYn(),old.regUserId(),old.regDttm(),old.modUserId(),old.modDttm());
        if(candidate.equals(old)) return new Result(id,false,"바뀐 내용이 없습니다.",warnings,view(old,today()));
        Stored changed=new Stored(id,candidate.type(),candidate.name(),candidate.businessNo(),candidate.ceo(),candidate.industryCode(),candidate.contactName(),candidate.phone(),candidate.taxEmail(),candidate.bankCode(),candidate.encryptedAccount(),candidate.depositor(),candidate.useYn(),candidate.regUserId(),candidate.regDttm(),actor,now());
        if(mapper().update(new Mutation(changed))!=1)throw new IllegalStateException("거래처를 저장하지 못했습니다.");
        changes(old,changed);repository.committed();
        return new Result(id,true,"저장했습니다.",warnings,view(required(id,false),today()));
    }
    private Stored required(String id,boolean lock) {
        InputValidator.text(id,30,"id");Stored row=mapper().find(id,lock);
        if(row==null)throw new ApiException(404,"NOT_FOUND","거래처를 찾을 수 없습니다.");return row;
    }
    private void validateCodes(Fields f,Stored old) {
        validateCode("INDUTY",f.industryCode(),old==null?null:old.industryCode(),"industryCode");
        if(f.bankCode()!=null)validateCode("BANK",f.bankCode(),old==null?null:old.bankCode(),"bankCode");
    }
    private void validateCode(String group,String value,String original,String field) {
        if(value==null)return;
        Code code=mapper().code(group,value);
        if(code==null || ((!"Y".equals(code.useYn()) || !"N".equals(code.delYn())) && !Objects.equals(value,original)))throw InputValidator.invalid(field);
    }
    private void references(Stored old,String type) {
        List<Map<String,Object>> refs=new ArrayList<>();
        if(!old.type().equals("VNDR") && type.equals("VNDR")) {
            long n=mapper().projectReferences(old.id());if(n>0)refs.add(Map.of("type","PROJECT","count",n));
        }
        if(!old.type().equals("CUST") && type.equals("CUST")) {
            long n=mapper().developerReferences(old.id());if(n>0)refs.add(Map.of("type","DEVELOPER","count",n));
        }
        if(!refs.isEmpty())throw new ApiException(409,"REFERENCE_IN_USE","연결된 프로젝트 또는 소속 개발자가 있어 구분을 변경할 수 없습니다.","type",Map.of("references",refs));
    }
    private List<Warning> warnings(Save input,String id) {
        List<Warning> result=new ArrayList<>(input.warnings());
        for(Stored same:mapper().sameName(input.fields().name(),id))result.add(new Warning("name","동일한 상호가 있습니다: "+same.name()+" ("+same.businessNo()+")"));
        return List.copyOf(result);
    }
    private ApiException duplicate(Stored old){return new ApiException(409,"DUPLICATE_BUSINESS_NO","이미 등록된 사업자번호입니다.","businessNo",Map.of("clientName",old.name()));}
    private Client view(Stored row,LocalDate date) {
        Labels labels=mapper().labels(row.id());Counts counts=mapper().counts(row.id(),date);
        String mask=cipher.masked(row.encryptedAccount());
        return new Client(row.type(),row.name(),row.businessNo(),row.ceo(),row.industryCode(),row.contactName(),row.phone(),row.taxEmail(),row.bankCode(),row.depositor(),row.id(),labels.industry(),labels.bankName(),labels.industryActive(),labels.bankActive(),mask==null?null:Objects.toString(labels.bankName(),"")+" "+mask,row.useYn(),counts.projectCount(),counts.personCount(),date,revisions.revision(row),mapper().historyCount(row.id()));
    }
    private void changes(Stored before,Stored after) {
        Map<String,String> a=values(before),b=values(after);
        a.forEach((column,value)->{if(!Objects.equals(value,b.get(column)))history.append(new ChangeHistoryWriter.Change("TB_BS_CLNT",after.id(),"UPD",column,value,b.get(column),null));});
        if(!Objects.equals(before.encryptedAccount(),after.encryptedAccount()))history.append(new ChangeHistoryWriter.Change("TB_BS_CLNT",after.id(),"UPD","ACCOUNT",cipher.masked(before.encryptedAccount()),cipher.masked(after.encryptedAccount()),null));
    }
    private Map<String,String> values(Stored row) {
        Map<String,String> result=new LinkedHashMap<>();
        result.put("CLNT_TYPE_CD",row.type());result.put("CLNT_NM",row.name());result.put("CEO_NM",row.ceo());result.put("INDUTY_CD",row.industryCode());result.put("CHRG_NM",row.contactName());result.put("TELNO",row.phone());result.put("TAX_EMAIL_ADDR",row.taxEmail());result.put("BANK_CD",row.bankCode());result.put("DPSTR_NM",row.depositor());return result;
    }
}
