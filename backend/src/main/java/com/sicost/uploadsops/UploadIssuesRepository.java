package com.sicost.uploadsops;

import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.uploadsops.UploadIssuesDtos.*;

@Repository
public class UploadIssuesRepository {
    private final UploadIssuesMapper mapper;
    public UploadIssuesRepository(UploadIssuesMapper mapper) { this.mapper = mapper; }
    public void requireUpload(long id) {
        if (!mapper.exists(id)) throw new ApiException(404, "NOT_FOUND", "업로드 검사 기록을 찾을 수 없습니다.");
    }
    public long count(Query query) { return mapper.count(query); }
    public String formType(long id) { requireUpload(id); return mapper.formType(id); }
    public List<Issue> issues(Query query) { return mapper.issues(query); }
}
