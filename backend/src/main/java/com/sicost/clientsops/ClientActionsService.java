package com.sicost.clientsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Service
public class ClientActionsService {
    private final ClientActionsRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ClientActionsService(ClientActionsRepository repository, RevisionService revisions, CurrentUserProvider users, Clock clock) {
        this.repository=repository; this.revisions=revisions; this.users=users; this.clock=clock;
    }
    public LocalDate basisDate() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Options options(OptionQuery query) { users.currentUser(); return new Options(repository.options(query)); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Selection selection(MultiValueMap<String,String> query) {
        users.currentUser();
        var rows=repository.selection(ClientActionsInput.filters(query,basisDate()));
        var items=rows.stream().map(row -> new SelectionItem(row.id(),revisions.revision(row))).toList();
        return new Selection(items,items.size());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Assignments assignments(String id,int page,int size) {
        users.currentUser();
        ClientSnapshot row=repository.client(id,false);
        if (row==null) throw missing();
        var query=new AssignmentQuery(id,row.type(),basisDate(),page,size,(long)(page-1)*size);
        return new Assignments(repository.assignments(query),repository.assignmentCount(query),page,size,query.basisDate());
    }
    static ApiException missing() { return new ApiException(404,"NOT_FOUND","거래처를 찾을 수 없습니다."); }
}
