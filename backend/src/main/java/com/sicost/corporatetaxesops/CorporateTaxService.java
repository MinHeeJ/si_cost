package com.sicost.corporatetaxesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@Service
public class CorporateTaxService {
    private final CorporateTaxRepository repository;
    private final CorporateTaxAllocationService allocation;
    private final CorporateTaxCommands commands;
    private final CurrentUserProvider users;
    public CorporateTaxService(CorporateTaxRepository repository,CorporateTaxAllocationService allocation,CorporateTaxCommands commands,CurrentUserProvider users){this.repository=repository;this.allocation=allocation;this.commands=commands;this.users=users;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Allocation getCorporateTaxAllocation(String year){CorporateTaxInput.year(year);users.currentUser();return allocation.calculate(year,repository.annual(year),repository.cashBase(),repository.months(year));}
    public DefaultsResult createCorporateTaxDefaults(YearMonth month){users.currentUser();return commands.initialize(month);}
    public ConfirmationResult saveCorporateTaxPaymentConfirmation(String year,String type,Confirmation input){
        CorporateTaxInput.year(year);CorporateTaxInput.payType(type);users.currentUser();
        Set<String> keys=Set.of("CASH_MONTH:"+repository.payment(year,type).dueDate().toString().substring(0,7).replace("-",""));
        for(int attempt=0;attempt<2;attempt++){
            try{return commands.confirm(year,type,input,keys);}catch(CorporateTaxCommands.ExpandedScope expanded){keys=expanded.keys();}
        }
        throw new ApiException(409,"STALE_WRITE","납부 의존월이 변경되었습니다. 다시 조회한 뒤 확인해 주세요.");
    }
}
