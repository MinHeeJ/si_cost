package com.sicost.payrollops;

import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.payrollops.PayrollUploadDtos.*;

@Service
public class PayrollUploadService {
    private final PayrollUploadRepository repository;
    private final CurrentUserProvider users;
    public PayrollUploadService(PayrollUploadRepository repository,CurrentUserProvider users) {
        this.repository=repository;this.users=users;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page getPayrollUploads(Query query) {
        users.currentUser();
        var items=repository.list(query).stream().map(row->new PayrollUploadDtos.Record(row.uploadId(),
            DatabaseMonthCodec.toApi(row.month()),row.fileName(),row.actor(),format(row.registeredAt()),row.status(),
            new Summary(row.totalRows(),row.successRows(),row.warningRows(),row.errorRows(),row.fileWarningCount(),row.tableWarningCount()),
            format(row.appliedAt()))).toList();
        return new Page(items,repository.count(query),query.page(),query.size());
    }
    private String format(LocalDateTime value) {
        return value==null?null:value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
