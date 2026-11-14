package com.sicost.salesopportunities;

import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.salesopportunitiesops.SalesCommandRepository;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;

@Repository
public class SalesOpportunityRepository {
    private final SalesOpportunityMapper mapper;
    private final SalesCommandRepository commands;
    private final ChangeHistoryWriter audit;
    public SalesOpportunityRepository(SalesOpportunityMapper mapper,SalesCommandRepository commands,ChangeHistoryWriter audit){this.mapper=mapper;this.commands=commands;this.audit=audit;}
    public SalesOpportunityMapper mapper(){return mapper;}
    public Stored required(String id){Stored s=mapper.find(id);if(s==null)throw new ApiException(404,"NOT_FOUND","영업 기회를 찾을 수 없습니다.");return s;}
    public List<String> keys(String id){var keys=new TreeSet<>(commands.keys(id));keys.addAll(mapper.opportunityKeys());keys.add("OPP:CREATE");return List.copyOf(keys);}
    public String allocate(){String s=mapper.nextId();if(s==null||!s.matches("[1-9][0-9]{0,17}"))throw new ApiException(409,"ID_EXHAUSTED","영업 기회 번호를 모두 사용했습니다.");return "O-"+"0".repeat(Math.max(0,4-s.length()))+s;}
    public void one(int count){if(count!=1)throw new IllegalStateException("영업 기회를 저장하지 못했습니다.");}
    public void auditDelta(List<Field> before,List<Field> after){
        Map<String,Field> old=index(before),next=index(after);var all=new TreeSet<String>(old.keySet());all.addAll(next.keySet());
        for(String key:all){Field a=old.get(key),b=next.get(key),f=b==null?a:b;
            if(a!=null&&b!=null&&Objects.equals(a.value(),b.value()))continue;
            audit.appendSales(new ChangeHistoryWriter.Change(f.tableName(),f.targetKey(),a==null?"NEW":b==null?"DEL":"UPD",f.item(),a==null?null:a.value(),b==null?null:b.value(),"영업 기회 저장"));
        }
    }
    private Map<String,Field> index(List<Field> fields){var m=new TreeMap<String,Field>();for(Field f:fields)m.put(f.tableName()+":"+f.targetKey()+":"+f.item(),f);return m;}
}
