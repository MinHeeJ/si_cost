package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.MasterHistoryDtos.*;

@Service
public class MasterHistoryService {
    private final MasterHistoryRepository repository;
    private final CurrentUserProvider users;
    public MasterHistoryService(MasterHistoryRepository repository, CurrentUserProvider users) {
        this.repository = repository; this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(Query query) {
        users.currentUser();
        if (!repository.exists(query)) throw new ApiException(404, "NOT_FOUND", "요청한 기준정보를 찾을 수 없습니다.");
        long total = repository.count(query);
        return new Page(repository.list(query).stream().map(this::publicEntry).toList(), total, query.page(), query.size());
    }
    private Entry publicEntry(Entry row) {
        String item = row.itemName() == null ? "" : row.itemName();
        String upper = item.toUpperCase(Locale.ROOT);
        boolean secret = upper.matches(".*(PWD|PASSWORD|HASH|MFA|CIPHER|TOKEN|SECRET|비밀번호|암호문).*");
        boolean account = upper.contains("ACCT") || upper.contains("ACCOUNT") || upper.contains("계좌");
        return new Entry(row.historyId(), row.targetTable(), row.targetKey(), row.changeType(), item,
            secret ? null : account ? masked(row.beforeValue()) : row.beforeValue(),
            secret ? null : account ? masked(row.afterValue()) : row.afterValue(),
            secret || account ? null : row.reason(), row.actorId(), row.actorName(), row.createdAt());
    }
    private String masked(String value) {
        if (value == null) return null;
        return value.matches("\\*{4}[0-9]{1,4}") ? value : "****";
    }
}
