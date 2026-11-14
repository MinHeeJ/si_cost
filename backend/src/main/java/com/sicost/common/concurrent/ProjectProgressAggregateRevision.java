package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import org.springframework.stereotype.Service;

@Service
public class ProjectProgressAggregateRevision {
    private final ProjectProgressRevisionMapper mapper;
    private final RevisionService revisions;
    public ProjectProgressAggregateRevision(ProjectProgressRevisionMapper mapper, RevisionService revisions) {
        this.mapper = mapper; this.revisions = revisions;
    }
    public String snapshot(String id) {
        String stored = mapper.snapshot(id);
        if (stored == null) throw new ApiException(404, "NOT_FOUND", "프로젝트를 찾을 수 없습니다.");
        return stored;
    }
    public String revision(String id) { return revisions.revision(snapshot(id)); }
    public void requireExpected(String expected, String actual) { revisions.requireExpected(expected, actual); }
}
