package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeveloperAggregateRevision {
    public record Snapshot(String developer, List<String> prices, List<String> skills, List<String> states) {}
    private final DeveloperRevisionMapper mapper;
    private final RevisionService revisions;
    private SettlementSourceLocks settlementLocks;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void settlementLocks(SettlementSourceLocks locks) { this.settlementLocks=locks; }
    public void lockSettlementSources(String id,String nextPart) {
        if(settlementLocks==null)return;
        var keys=new java.util.ArrayList<String>();
        if(id!=null)keys.add("DEV:"+id);
        if(nextPart!=null&&!nextPart.isBlank())keys.add("PART:"+nextPart);
        if(!keys.isEmpty())settlementLocks.lock(keys);
    }
    public DeveloperAggregateRevision(DeveloperRevisionMapper mapper, RevisionService revisions) {
        this.mapper=mapper; this.revisions=revisions;
    }
    public Snapshot snapshot(String id, boolean lock) {
        if(lock)lockSettlementSources(id,null);
        String parent=mapper.developer(id,lock);
        if(parent==null)throw new ApiException(404,"NOT_FOUND","개발자를 찾을 수 없습니다.");
        return new Snapshot(parent,mapper.prices(id,lock),mapper.skills(id,lock),mapper.states(id,lock));
    }
    public String revision(String id, boolean lock) { return revisions.revision(snapshot(id,lock)); }
    public void requireExpected(String expected, String actual) { revisions.requireExpected(expected,actual); }
}
