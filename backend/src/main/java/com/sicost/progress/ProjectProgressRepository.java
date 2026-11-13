package com.sicost.progress;

import com.sicost.common.api.ApiException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.progress.ProjectProgressDtos.*;

@Repository
public class ProjectProgressRepository {
    private final ProjectProgressMapper mapper;
    public ProjectProgressRepository(ProjectProgressMapper mapper) {this.mapper=mapper;}
    public ProjectRow required(String id,LocalDate today) {
        var row=mapper.project(id,today);if(row==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return row;
    }
    public List<StepRow> steps(String id){return mapper.steps(id);}
    public List<MemoRow> memos(String id){return mapper.memos(id);}
    public List<IssueRow> issues(String id){return mapper.issues(id);}
    public List<Actor> pm(String id){return mapper.pm(id);}
    public List<Template> templates(){return mapper.templates();}
    public List<HistoryRow> history(String id){return mapper.history(id);}
    public void insertStep(StepWrite write){one(mapper.insertStep(write));}
    public void updateDates(StepWrite write){one(mapper.updateDates(write));}
    public void complete(StepWrite write){one(mapper.complete(write));}
    public void insertMemo(MemoWrite write){one(mapper.insertMemo(write));}
    public void updateMemo(MemoWrite write){one(mapper.updateMemo(write));}
    private static void one(int count){if(count!=1)throw new IllegalStateException("진행 자료를 저장하지 못했습니다.");}
}
