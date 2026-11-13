package com.sicost.financialitems;

import com.sicost.common.api.ApiException;
import java.util.Set;
import org.springframework.stereotype.Service;
import static com.sicost.financialitems.FinancialDtos.*;

@Service
public class FinancialWriteService {
    private final FinancialCommands commands;
    public FinancialWriteService(FinancialCommands commands){this.commands=commands;}
    public Commit saveFinancialItems(Save input){
        Set<String> keys=Set.of();for(int attempt=0;attempt<2;attempt++)try{return commands.save(input,keys);}catch(FinancialCommands.ExpandedScope scope){keys=scope.keys;}
        throw new ApiException(409,"STALE_WRITE","관련 현금월이 변경되었습니다. 다시 조회해 주세요.");
    }
}
