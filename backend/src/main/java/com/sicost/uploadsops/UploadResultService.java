package com.sicost.uploadsops;

import com.sicost.b9.B9Dtos.Result;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.b9.B9Repository;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadResultService {
    private final UploadResultRepository repository;
    private final B9Repository projects;
    private final CurrentUserProvider users;
    private final RevisionService revisions;
    public UploadResultService(UploadResultRepository repository, B9Repository projects,
                               CurrentUserProvider users, RevisionService revisions) {
        this.repository=repository;this.projects=projects;this.users=users;this.revisions=revisions;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result get(long id) {
        if(id<1)throw ProjectInputPolicy.invalid("id");
        users.currentUser();
        var upload=repository.find(id);
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 결과를 찾을 수 없습니다.");
        if(!"CHECKED".equals(upload.status())&&!"APPLIED".equals(upload.status()))
            throw new IllegalStateException("업로드 상태를 확인하지 못했습니다.");
        if("B9".equals(upload.formType()))return projects.result(upload);
        var warnings=repository.warnings(id);
        var summary=new Summary(upload.totalRows(),upload.successRows(),upload.warningRows(),upload.errorRows(),
            warnings.fileWarningCount(),warnings.tableWarningCount());
        boolean applied="APPLIED".equals(upload.status());
        String revision=applied?revisions.revision(new B9Repository.Snapshot(upload,repository.context(upload))):null;
        return new Result(id,upload.formType(),upload.status(),upload.projectId(),upload.step(),summary,
            Collections.emptyList(),revision,applied);
    }
}
