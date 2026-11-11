package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.PeopleHistoryDtos.*;

@Service
public class PeopleHistoryService {
    private final PeopleHistoryRepository repository;
    private final CurrentUserProvider users;
    public PeopleHistoryService(PeopleHistoryRepository repository, CurrentUserProvider users) {
        this.repository = repository; this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(Query query) {
        users.currentUser();
        if (!repository.exists(query)) throw new ApiException(404, "NOT_FOUND", "요청한 인력을 찾을 수 없습니다.");
        long total = repository.count(query);
        return new Page(repository.list(query).stream().map(this::publicEntry).toList(), total, query.page(), query.size());
    }
    private Entry publicEntry(Entry row) {
        String item = row.itemName() == null ? "" : row.itemName();
        String upper = item.toUpperCase(Locale.ROOT);
        boolean contact = upper.matches(".*(TELNO|PHONE|EMAIL|E-MAIL|연락처|전화|이메일).*");
        return new Entry(row.historyId(), row.targetTable(), row.targetKey(), row.changeType(), item,
            contact ? masked(row.beforeValue()) : row.beforeValue(),
            contact ? masked(row.afterValue()) : row.afterValue(),
            contact ? null : row.reason(), row.actorId(), row.actorName(), row.createdAt());
    }
    private String masked(String value) { return value == null ? null : "[비공개]"; }
}
