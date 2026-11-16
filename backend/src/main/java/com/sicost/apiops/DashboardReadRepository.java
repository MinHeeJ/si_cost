package com.sicost.apiops;

import com.sicost.businessplansops.BusinessPlanPerformanceMapper;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DashboardReadRepository {
    private final DashboardReadMapper mapper;
    private final SharedQueriesMapper shared;
    private final BusinessPlanPerformanceMapper plans;
    private final CurrentUserProvider users;
    private final TransactionTemplate nested;
    public DashboardReadRepository(DashboardReadMapper mapper,SharedQueriesMapper shared,BusinessPlanPerformanceMapper plans,PlatformTransactionManager manager,CurrentUserProvider users) {
        this.mapper=mapper;this.shared=shared;this.plans=plans;this.users=users;
        nested=new TransactionTemplate(manager);nested.setReadOnly(true);nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
    public record Read<T>(T value,boolean failed) {}
    public <T> Read<T> section(Supplier<T> read) {
        try {return new Read<>(nested.execute(status->read.get()),false);}
        catch(RuntimeException ex){return new Read<>(null,true);}
    }
    public String base(){users.currentUser();return shared.cashBaseMonth();}
    public List<CloseRow> closes(String from,String to){return shared.monthCloses(from,to);}
    public List<Target> targets(int year){return plans.targets(new YearRange(year+"01",year+"12"));}
    public BigDecimal standard(String code,YearMonth month) {
        var p=shared.parameter(code,month.toString().replace("-",""));return p==null||p.value()==null?null:new BigDecimal(p.value());
    }
    public DashboardDtos.ReceivableTotals receivables(LocalDate day){return mapper.receivables(day);}
    public DashboardDtos.TaxDue taxes(LocalDate day){return mapper.taxes(day);}
}
