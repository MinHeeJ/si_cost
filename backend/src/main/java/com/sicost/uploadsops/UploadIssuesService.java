package com.sicost.uploadsops;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.uploadsops.UploadIssuesDtos.*;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class UploadIssuesService {
    private final UploadIssuesRepository repository;
    private final ExcelExportService exports;
    private final CurrentUserProvider users;
    public UploadIssuesService(UploadIssuesRepository repository, ExcelExportService exports, CurrentUserProvider users) {
        this.repository = repository; this.exports = exports; this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(long id, String severity, int page, int size) {
        UploadIssuesInput.severity(severity); InputValidator.page(page); InputValidator.size(size);
        users.currentUser(); repository.requireUpload(id);
        Query query = new Query(id, severity, (long) (page - 1) * size, size);
        return new Page(repository.issues(query), repository.count(query), page, size);
    }
    public Download download(long id, String severity, HttpServletRequest request) {
        UploadIssuesInput.severity(severity);
        String form=repository.formType(id);
        boolean developers = "B8".equals(form) || "B1".equals(form);
        Specification specification = new Specification("B2".equals(form) ? "재무_업로드검사" : "B5".equals(form) ? "투입_업로드검사" : "B1".equals(form) ? "급여_업로드검사" : developers ? "개발자_업로드검사" : "거래처_업로드검사", Long.toString(id), List.of(
            new Column("severity", "유형", CellType.TEXT), new Column("rowNumber", "행", CellType.NUMBER),
            new Column("columnName", "열", CellType.TEXT), new Column("inputValue", "입력값", CellType.TEXT),
            new Column("reason", "이유", CellType.TEXT)), "severity=" + (severity == null ? "전체" : severity), "원", "B2".equals(form) ? "UI-10" : "B5".equals(form) ? "UI-12" : developers ? "UI-02" : "UI-15");
        return exports.generate(specification, sink -> {
            repository.requireUpload(id);
            long offset = 0;
            while (true) {
                List<Issue> chunk = repository.issues(new Query(id, severity, offset, 1000));
                for (Issue issue : chunk) sink.accept(List.of(issue.severity(), issue.rowNumber(), issue.columnName(), issue.inputValue(), issue.reason()));
                if (chunk.size() < 1000) break;
                offset += chunk.size();
            }
        }, request);
    }
}
