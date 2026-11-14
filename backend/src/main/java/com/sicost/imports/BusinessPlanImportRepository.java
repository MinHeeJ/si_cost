package com.sicost.imports;

import com.sicost.b7.B7Dtos.IssuesPage;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.imports.BusinessPlanImportDtos.*;

@Repository
public class BusinessPlanImportRepository {
    private final BusinessPlanImportMapper mapper;
    private final ChangeHistoryWriter history;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public BusinessPlanImportRepository(BusinessPlanImportMapper mapper, ChangeHistoryWriter history,
        RevisionService revisions, CurrentUserProvider users, Clock clock) {
        this.mapper = mapper; this.history = history; this.revisions = revisions; this.users = users; this.clock = clock;
    }
    public String actor() { return users.userId(); }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL); }
    public String revision(int year) { return revisions.revision(mapper.snapshot(year)); }
    public Upload required(long id, boolean lock) {
        Upload upload = lock ? mapper.lockUpload(id) : mapper.upload(id);
        if (upload == null) throw new ApiException(404, "NOT_FOUND", "업로드 검사 기록을 찾을 수 없습니다.");
        return upload;
    }
    public long create(UploadWrite write, Checked checked) {
        Draft draft = new Draft(write); one(mapper.insertUpload(draft));
        if (draft.getUploadId() == null) throw new IllegalStateException("업로드 번호가 없습니다.");
        saveIssues(draft.getUploadId(), checked); return draft.getUploadId();
    }
    public void replace(UploadWrite write, Checked checked) {
        one(mapper.replaceSummary(write)); mapper.deleteIssues(write.uploadId()); saveIssues(write.uploadId(), checked);
    }
    private void saveIssues(long id, Checked checked) {
        for (var issue : checked.issues()) one(mapper.insertIssue(new IssueWrite(id, issue, actor(), now())));
    }
    public Validation validation(long id, int year, Checked checked) {
        var issues = checked.issues();
        return new Validation(Long.toString(id), "CHECKED", checked.summary(), new IssuesPage(issues.stream().limit(50).toList(), issues.size(), 1, 50),
            revision(year), checked.canApply(), checked.warnings(), year, checked.changes(),
            checked.changes().stream().anyMatch(c -> c.closed() && c.changed()), checked.blankTargetMonths());
    }
    public Summary summary(Upload upload) {
        int fileWarnings = (int) mapper.issues(upload.uploadId()).stream().filter(i -> "[파일]".equals(i.columnName())).count();
        return new Summary(upload.totalRows(), upload.successRows(), upload.warningRows(), upload.errorRows(), fileWarnings);
    }
    public Result result(Upload upload) {
        var issues = mapper.issues(upload.uploadId());
        return new Result(Long.toString(upload.uploadId()), "B10", "UI-18", Integer.parseInt(upload.month().substring(0, 4)),
            apiMonth(upload.month()), upload.status(), summary(upload), time(upload.appliedAt()),
            mapper.changedMonths(upload.uploadId()), new IssuesPage(issues.stream().limit(50).toList(), issues.size(), 1, 50));
    }
    public Applied replay(Upload upload, Request request) {
        return new Applied(Long.toString(upload.uploadId()), "APPLIED", summary(upload), true, "이미 반영된 업로드입니다",
            time(upload.appliedAt()), mapper.changedMonths(upload.uploadId()), null, null, Collections.emptyList(), request.year(), request.mode());
    }
    public void applyTargets(Upload upload, Checked checked, String mode, List<Stored> old) {
        int year = now().getYear();
        for (Change change : checked.changes()) {
            if (!change.changed() || ("KEEP_CLOSED".equals(mode) && change.closed())) continue;
            if (YearMonth.parse(change.month()).getYear() != year) throw new ApiException(409, "PERIOD_READ_ONLY", "올해 목표만 변경할 수 있습니다.");
            Row row = checked.rows().stream().filter(r -> r.rowNumber() == change.rowNumber()).findFirst().orElseThrow();
            String month = change.month().replace("-", "");
            Stored previous = old.stream().filter(r -> month.equals(r.month())).findFirst().orElse(null);
            var write = new TargetWrite(month, row.sales(), row.profit(), upload.uploadId(), actor(), now());
            one(previous == null ? mapper.insertTarget(write) : mapper.updateTarget(write));
            String reason = "BusinessPlanImport:" + upload.uploadId() + ":" + row.rowNumber() + ":" + mode;
            audit(month, previous, change.before(), change.after(), reason);
            history.append(new ChangeHistoryWriter.Change("TB_FI_BIZ_PLAN", month + ":ALL", previous == null ? "NEW" : "UPD", "UPLD_ID",
                previous == null || previous.uploadId() == null ? null : previous.uploadId().toString(), Long.toString(upload.uploadId()), reason));
        }
    }
    private void audit(String month, Stored previous, Target before, Target after, String reason) {
        String type = previous == null ? "NEW" : "UPD";
        history.append(new ChangeHistoryWriter.Change("TB_FI_BIZ_PLAN", month + ":ALL", type, "SALES_TGT_AMT",
            before == null ? null : before.salesTargetAmt(), after.salesTargetAmt(), reason));
        history.append(new ChangeHistoryWriter.Change("TB_FI_BIZ_PLAN", month + ":ALL", type, "OPRT_PRFT_TGT_AMT",
            before == null ? null : before.operatingProfitTargetAmt(), after.operatingProfitTargetAmt(), reason));
    }
    public static String apiMonth(String month) { return month.substring(0, 4) + "-" + month.substring(4); }
    private String time(LocalDateTime time) { return time == null ? null : time.atZone(TimeConfiguration.SEOUL).toOffsetDateTime().toString(); }
    public static void one(int count) { if (count != 1) throw new IllegalStateException("목표와 필수 기록을 저장하지 못했습니다."); }
}
