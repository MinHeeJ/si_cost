package com.sicost.developersops;

import com.sicost.common.api.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Service
public class DeveloperBulkStatusService {
    private final DeveloperUseStatusService statuses;
    public DeveloperBulkStatusService(DeveloperUseStatusService statuses) { this.statuses=statuses; }
    public BulkResult save(BulkRequest request) {
        List<Id> applied=new ArrayList<>(),noChange=new ArrayList<>(); List<Rejected> rejected=new ArrayList<>();
        for(SelectionItem item:request.items()) {
            try { (statuses.change(item,request.targetUseYn())?applied:noChange).add(new Id(item.id())); }
            catch(RuntimeException failure) {
                ApiException error=failure instanceof ApiException known?known:new ApiException(500,"INTERNAL_ERROR","사용 상태를 처리하지 못했습니다. 다시 조회해 주세요.");
                rejected.add(new Rejected(item.id(),error.code(),error.getMessage(),error.details()==null?Map.of():error.details()));
            }
        }
        return new BulkResult(List.copyOf(applied),List.copyOf(noChange),List.copyOf(rejected),applied.size(),noChange.size(),rejected.size());
    }
}
