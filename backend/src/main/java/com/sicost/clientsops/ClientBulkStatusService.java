package com.sicost.clientsops;

import com.sicost.common.api.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Service
public class ClientBulkStatusService {
    private final ClientStatusService status;
    public ClientBulkStatusService(ClientStatusService status) { this.status=status; }
    public BulkResult save(BulkRequest request,String requestId) {
        List<BulkItem> results=new ArrayList<>();
        for (Target target:request.items()) {
            try { results.add(status.change(target,request.targetUseYn())); }
            catch (RuntimeException failure) {
                ApiException error=failure instanceof ApiException known?known:
                    new ApiException(500,"INTERNAL_ERROR","거래처 상태를 처리하지 못했습니다. 다시 조회해 주세요.");
                String name=error.details() instanceof Map<?,?> details && details.get("clientName") instanceof String value?value:"";
                results.add(new BulkItem(target.id(),name,"rejected",new ApiError(false,Map.of(),error.code(),
                    error.getMessage(),error.field(),error.details(),requestId)));
            }
        }
        return new BulkResult(List.copyOf(results),count(results,"applied"),count(results,"noChange"),count(results,"rejected"));
    }
    private long count(List<BulkItem> results,String kind) { return results.stream().filter(item->item.result().equals(kind)).count(); }
}
