package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.forecast.*;
import com.sicost.workforceforecastsops.WorkforceForecastInput;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class WorkforceForecastService {
    private final SalesForecastRepository repository;
    private final SalesWorkforceProjectionService projections;
    private final CurrentUserProvider users;
    private final Clock clock;
    public WorkforceForecastService(SalesForecastRepository repository,SalesWorkforceProjectionService projections,CurrentUserProvider users,Clock clock){this.repository=repository;this.projections=projections;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public SalesForecastDtos.Workforce forecast(WorkforceForecastInput.Query query){
        users.currentUser();LocalDate day=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
        return projections.calculate(repository.load(YearMonth.from(day)),day,query.from(),query.months(),query.includeUnfilled());
    }
}
