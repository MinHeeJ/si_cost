package com.sicost.developersops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.DeveloperAggregateRevision;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Repository
public class DeveloperActionsRepository {
    private final DeveloperActionsMapper mapper;
    private final DeveloperAggregateRevision revisions;
    public DeveloperActionsRepository(DeveloperActionsMapper mapper,DeveloperAggregateRevision revisions) { this.mapper=mapper;this.revisions=revisions; }
    public Base required(String id,boolean lock) {
        if(lock)revisions.lockSettlementSources(id,null);
        Base row=mapper.developer(id,lock);
        if(row==null) throw new ApiException(404,"NOT_FOUND","개발자를 찾을 수 없습니다.");
        return row;
    }
    public Snapshot snapshot(Base row,boolean lock) {
        var prices=mapper.prices(row.id(),lock);
        var skills=mapper.skills(row.id(),lock);
        var states=mapper.states(row.id(),lock);
        return new Snapshot(row,normalized(prices),normalized(skills),normalized(states));
    }
    private List<Map<String,Object>> normalized(List<Map<String,Object>> rows) {
        return rows.stream().map(row -> {
            Map<String,Object> result=new TreeMap<>();
            row.forEach((key,value)->result.put(key,value instanceof java.sql.Timestamp timestamp?timestamp.toLocalDateTime():
                value instanceof java.sql.Date date?date.toLocalDate():value));
            for(String key:List.of("modUserId","modDttm")) result.putIfAbsent(key,null);
            return result;
        }).toList();
    }
    public String revision(Base row,boolean lock) { return revisions.revision(row.id(),lock); }
}
