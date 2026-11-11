package com.sicost.b7;

import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.security.AccountCipher;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import static com.sicost.b7.B7Dtos.*;

@Repository
public class B7Repository {
    private final B7Mapper mapper;
    private final MasterIdService ids;
    private final AccountCipher cipher;
    private final ChangeHistoryWriter history;
    public B7Repository(B7Mapper mapper, MasterIdService ids, AccountCipher cipher, ChangeHistoryWriter history) {
        this.mapper=mapper;this.ids=ids;this.cipher=cipher;this.history=history;
    }
    public long nextUploadId(){return mapper.nextUploadId();}
    public Upload lock(long id){return mapper.lockUpload(id);}
    public List<Issue> issues(long id){return mapper.issues(id);}
    public void save(UploadWrite write,Checked checked,boolean replace) {
        requireOne(replace?mapper.replaceSummary(write):mapper.insertUpload(write));
        if(replace)mapper.deleteIssues(write.uploadId());
        for(Issue issue:checked.issues()) requireOne(mapper.insertIssue(new IssueWrite(write.uploadId(),issue,write.actor(),write.now())));
    }
    public void apply(long id,Checked checked,String actor,LocalDateTime now) {
        for(Client client:checked.clients()) {
            String encrypted=client.accountNumber()==null?null:cipher.encrypt(client.accountNumber());
            String clientId=ids.nextClientId();
            requireOne(mapper.insertClient(new ClientWrite(clientId,client,encrypted,actor,now)));
            history.append(new ChangeHistoryWriter.Change("TB_BS_CLNT",clientId,"NEW","등록",null,clientId+" "+client.name(),"B7 업로드:"+id));
        }
        requireOne(mapper.markApplied(id,actor,now));
    }
    private void requireOne(int count) {if(count!=1)throw new IllegalStateException("업로드 결과를 저장하지 못했습니다.");}
}
