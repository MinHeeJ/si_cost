package com.sicost.migration;

import com.sicost.b7.B7Dtos.IssuesPage;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;
import static com.sicost.migration.HistoricalProfitLossImportDefinition.*;

@Repository
public class HistoricalProfitLossImportRepository {
    private final HistoricalProfitLossImportMapper mapper;
    private final ChangeHistoryWriter history;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public HistoricalProfitLossImportRepository(HistoricalProfitLossImportMapper mapper,ChangeHistoryWriter history,
        RevisionService revisions,CurrentUserProvider users,Clock clock) {
        this.mapper=mapper;this.history=history;this.revisions=revisions;this.users=users;this.clock=clock;
    }
    public String actor() {return users.userId();}
    public LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    public String revision() {return revisions.revision(mapper.snapshot());}
    public Upload required(long id,boolean lock) {
        Upload upload=lock?mapper.lockUpload(id):mapper.upload(id);
        if(upload==null)throw new ApiException(404,"NOT_FOUND","업로드 검사 기록을 찾을 수 없습니다.");return upload;
    }
    public long create(UploadWrite write,Checked checked) {
        Draft draft=new Draft(write);one(mapper.insertUpload(draft));
        if(draft.getUploadId()==null)throw new IllegalStateException("업로드 번호가 없습니다.");
        saveIssues(draft.getUploadId(),checked);return draft.getUploadId();
    }
    public void replace(UploadWrite write,Checked checked) {
        one(mapper.replaceSummary(write));mapper.deleteIssues(write.uploadId());saveIssues(write.uploadId(),checked);
    }
    private void saveIssues(long id,Checked checked) {for(var issue:checked.issues())one(mapper.insertIssue(new IssueWrite(id,issue,actor(),now())));}
    public Validation validation(long id,Checked checked,String base) {
        var allowed=months(base);var issues=checked.issues();
        return new Validation(Long.toString(id),"CHECKED",checked.summary(),new IssuesPage(issues.stream().limit(50).toList(),issues.size(),1,50),
            revision(),checked.canApply(),checked.warnings(),allowed.get(0),allowed.get(allowed.size()-1),checked.changes());
    }
    public Summary summary(Upload u) {
        int warnings=(int)mapper.issues(u.uploadId()).stream().filter(i->"[파일]".equals(i.columnName())).count();
        return new Summary(u.totalRows(),u.successRows(),u.warningRows(),u.errorRows(),warnings);
    }
    public Result result(Upload u) {
        var issues=mapper.issues(u.uploadId());
        return new Result(Long.toString(u.uploadId()),"B11","UI-08",null,null,u.status(),summary(u),time(u.appliedAt()),
            mapper.changedMonths(u.uploadId()),new IssuesPage(issues.stream().limit(50).toList(),issues.size(),1,50));
    }
    public Applied replay(Upload u) {return new Applied(Long.toString(u.uploadId()),"APPLIED",summary(u),true,"이미 반영된 업로드입니다",
        time(u.appliedAt()),mapper.changedMonths(u.uploadId()),null,null,Collections.emptyList());}
    public void applyProfits(Upload upload,Checked checked,List<Stored> old,String base) {
        for(Change change:checked.changes()) {
            if(!change.changed())continue;
            if(!months(base).contains(change.month()))throw new ApiException(409,"STALE_WRITE","이관 범위가 변경되었습니다. 다시 검사해 주세요.");
            Row row=checked.rows().stream().filter(r->r.rowNumber()==change.rowNumber()).findFirst().orElseThrow();
            String month=change.month().replace("-","");Stored previous=old.stream().filter(r->r.month().equals(month)).findFirst().orElse(null);
            var write=new ProfitLossWrite(month,base,row.amounts(),actor(),now());one(previous==null?mapper.insertProfit(write):mapper.updateProfit(write));
            String reason="HistoricalProfitLossImport:"+upload.uploadId()+":"+row.rowNumber()+":ALL";
            String type=previous==null?"NEW":"UPD";
            for(int i=0;i<FIELDS.size();i++) {
                String key=FIELDS.get(i),before=change.before()==null?null:change.before().get(key),after=change.after().get(key);
                if(!Objects.equals(before,after))history.append(new ChangeHistoryWriter.Change("TB_CO_MON_PL",month,type,COLUMNS.get(i),before,after,reason));
            }
            history.append(new ChangeHistoryWriter.Change("TB_CO_MON_PL",month,type,"MIGR_YN",previous==null?null:previous.migrated(),"Y",reason));
            history.append(new ChangeHistoryWriter.Change("TB_CO_MON_PL",month,type,"FIX_YN",previous==null?null:previous.fixed(),"Y",reason));
        }
    }
    public static String time(LocalDateTime time) {return time==null?null:time.atZone(TimeConfiguration.SEOUL).toOffsetDateTime().toString();}
    public static void one(int count) {if(count!=1)throw new IllegalStateException("이관 손익과 필수 기록을 저장하지 못했습니다.");}
}
