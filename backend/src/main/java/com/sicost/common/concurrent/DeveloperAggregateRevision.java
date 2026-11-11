package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeveloperAggregateRevision {
    public record Snapshot(String developer, List<String> prices, List<String> skills, List<String> states) {}
    private final DeveloperRevisionMapper mapper;
    private final RevisionService revisions;
    public DeveloperAggregateRevision(DeveloperRevisionMapper mapper, RevisionService revisions) {
        this.mapper=mapper; this.revisions=revisions;
    }
    public Snapshot snapshot(String id, boolean lock) {
        String parent=mapper.developer(id,lock);
        if(parent==null)throw new ApiException(404,"NOT_FOUND","개발자를 찾을 수 없습니다.");
        return new Snapshot(parent,mapper.prices(id,lock),mapper.skills(id,lock),mapper.states(id,lock));
    }
    public String revision(String id, boolean lock) { return revisions.revision(snapshot(id,lock)); }
    public void requireExpected(String expected, String actual) { revisions.requireExpected(expected,actual); }
}
