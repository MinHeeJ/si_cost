package com.sicost.candidatesops;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.candidatesops.CandidateSummaryDtos.*;

@Service
public class CandidateSummaryService {
    private final CandidateSummaryRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    public CandidateSummaryService(CandidateSummaryRepository repository, CurrentUserProvider users, Clock clock) {
        this.repository = repository; this.users = users; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public Summary summary() {
        users.currentUser();
        LocalDate today = PeopleInputPolicy.today(clock);
        return repository.summary(new Basis(today.withDayOfMonth(1).plusMonths(1), today.minusDays(30)));
    }
}
