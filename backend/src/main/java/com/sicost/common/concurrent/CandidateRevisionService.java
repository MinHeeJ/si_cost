package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateRevisionService {
    private final CandidateRevisionMapper mapper;
    private final RevisionService revisions;
    public CandidateRevisionService(CandidateRevisionMapper mapper, RevisionService revisions) {
        this.mapper=mapper;this.revisions=revisions;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String revision(String id, boolean lock) {
        if(lock) {
            if(mapper.lockCandidate(id)==null)throw missing();
            mapper.lockSkills(id);
            mapper.lockContacts(id);
        }
        String snapshot=mapper.snapshot(id);
        if(snapshot==null)throw missing();
        return revisions.revision(snapshot);
    }
    private ApiException missing(){return new ApiException(404,"NOT_FOUND","후보를 찾을 수 없습니다.");}
}
