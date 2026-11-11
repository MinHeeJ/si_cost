package com.sicost.developersops;

import com.sicost.common.context.CurrentUserProvider;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Service
public class DeveloperActionsQueryService {
    private final DeveloperActionsMapper mapper;
    private final DeveloperActionsRepository repository;
    private final CurrentUserProvider users;
    public DeveloperActionsQueryService(DeveloperActionsMapper mapper,DeveloperActionsRepository repository,CurrentUserProvider users) {
        this.mapper=mapper;this.repository=repository;this.users=users;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Selection selection(Filters filters) {
        users.userId();
        validateFilters(filters);
        return new Selection(mapper.selection(filters).stream().map(row->new SelectionItem(row.id(),repository.revision(row,false))).toList());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AssignmentsPage assignments(AssignmentQuery query) {
        users.userId();repository.required(query.id(),false);
        return new AssignmentsPage(mapper.assignments(query),mapper.assignmentCount(query.id()),query.page(),query.size());
    }
    public void validateFilters(Filters filters) {
        if(filters.jobCode()!=null && mapper.activeJob(filters.jobCode())==0) throw com.sicost.common.validation.InputValidator.invalid("jobCode");
    }
}
