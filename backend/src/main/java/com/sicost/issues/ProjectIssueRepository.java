package com.sicost.issues;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.ProjectProgressView;
import org.springframework.beans.factory.ObjectProvider;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.issues.ProjectIssueDtos.*;

@Repository
public class ProjectIssueRepository {
    private final ProjectIssueMapper mapper;
    private final ObjectProvider<ProjectProgressView> progress;
    public ProjectIssueRepository(ProjectIssueMapper mapper, ObjectProvider<ProjectProgressView> progress) {
        this.mapper=mapper; this.progress=progress;
    }
    public Object progress(String id, int year) {
        ProjectProgressView reader=progress.getIfAvailable();
        if (reader==null) throw new IllegalStateException("진행 조회 서비스를 연결해 주세요.");
        return reader.getProjectProgress(id,year);
    }
    public Project required(String id) {
        Project p=mapper.project(id);
        if (p==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        return p;
    }
    public List<Row> issues(String id) {return mapper.issues(id);}
    public boolean activeCode(String group,String code) {return mapper.activeCode(group,code);}
    public long insert(Mutation mutation) {
        var values=new HashMap<String,Object>();values.put("data",mutation);
        try {
            if (mapper.insert(values)!=1) throw new IllegalStateException("이슈를 저장하지 못했습니다.");
        } catch (RuntimeException e) {
            for (Throwable t=e;t!=null;t=t.getCause()) {
                if (t instanceof SQLException sql && ("22003".equals(sql.getSQLState()) || "2200H".equals(sql.getSQLState()))) throw exhausted();
            }
            throw e;
        }
        long id=((Number)values.get("issueId")).longValue();
        if (id<1 || id>9999999999L) throw exhausted();
        return id;
    }
    public void update(Mutation mutation) {
        if (mapper.update(mutation)!=1) throw new ApiException(404,"NOT_FOUND","이슈를 찾을 수 없습니다.");
    }
    private static ApiException exhausted() {return new ApiException(409,"ID_EXHAUSTED","이슈 식별자가 소진되었습니다.");}
}
