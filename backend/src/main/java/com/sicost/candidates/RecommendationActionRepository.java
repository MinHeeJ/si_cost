package com.sicost.candidates;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.candidates.RecommendationActionDtos.*;

@Repository
public class RecommendationActionRepository {
    private final RecommendationActionMapper mapper;
    private final SettlementSourceLocks locks;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CommitInvalidation invalidation;
    public RecommendationActionRepository(RecommendationActionMapper mapper,SettlementSourceLocks locks,RevisionService revisions,ChangeHistoryWriter history,CommitInvalidation invalidation){this.mapper=mapper;this.locks=locks;this.revisions=revisions;this.history=history;this.invalidation=invalidation;}
    public Row required(Key key){Row row=mapper.row(key);if(row==null)throw new ApiException(404,"NOT_FOUND","추천 후보가 없습니다.");return row;}
    public Row lock(Key key,Collection<String> extraKeys){
        Row before=required(key);List<String> opportunities=mapper.activeOpportunityIds(before.developerId());
        Set<String> keys=new TreeSet<>(extraKeys);keys.addAll(List.of("RCMD:"+key.rcmdId(),"DEV:"+before.developerId(),"code-group:JOB","code-group:GRADE","code-group:INPUT_TYPE","code-group:RCMD_EXCL","operating-standards:CASH_BASE_YM","operating-standards:MM_OVER"));
        keys.add("RCMD_TARGET:"+(before.opportunityId()==null?"PROJECT:"+before.projectId():"OPP:"+before.opportunityId())+":"+before.jobCode());
        opportunities.forEach(id->keys.add("OPP:"+id));
        if(before.opportunityId()!=null)keys.add("OPP:"+before.opportunityId());
        if(before.projectId()!=null)keys.add("PROJECT:"+before.projectId());
        mapper.assignmentIds(before.developerId()).forEach(id->keys.add("ASGN:"+id));
        mapper.payrollMonths(before.developerId()).forEach(month->keys.add("PAY:"+month));
        keys.add("PAY:"+before.startMonth());locks.lock(keys);
        keys.stream().filter(k->k.startsWith("OPP:")).map(k->k.substring(4)).forEach(mapper::lockOpportunity);
        keys.stream().filter(k->k.startsWith("PROJECT:")).map(k->k.substring(8)).forEach(mapper::lockProject);
        keys.stream().filter(k->k.startsWith("OPP:")).map(k->k.substring(4)).forEach(mapper::lockNeeds);
        mapper.lockDeveloper(before.developerId());mapper.lockAssignments(before.developerId());
        mapper.lockClose();
        Row row=mapper.lockRow(key);if(row==null)throw new ApiException(404,"NOT_FOUND","추천 후보가 없습니다.");
        if(!Objects.equals(before.opportunityId(),row.opportunityId())||!Objects.equals(before.projectId(),row.projectId())||!before.developerId().equals(row.developerId())||(row.actionCode()==null&&!opportunities.equals(mapper.activeOpportunityIds(row.developerId()))))throw stale();return row;
    }
    public Map<String,String> values(Key key){Map<String,String> values=new TreeMap<>();for(AuditValue value:mapper.auditValues(key))values.putIfAbsent(value.item(),value.value());return values;}
    public Revisions current(Row row){
        String snapshot=mapper.snapshot(row.opportunityId());
        String target=revisions.revision(Arrays.asList(row.opportunityId(),row.projectId(),snapshot));
        String candidate=revisions.revision(Arrays.asList(row.developerId(),snapshot));
        return new Revisions(revisions.revision(Arrays.asList(row.rcmdId(),row.rank(),row.scoreDetail(),row.startMonth(),row.jobCode(),target,candidate)),target,candidate);
    }
    public void requireCurrent(Row row,Revisions requested){
        Revisions current=current(row);Map<String,String> original=values(row.key());
        check(requested.expectedRevision(),current.expectedRevision());
        if(requested.targetRevision()!=null)check(requested.targetRevision(),current.targetRevision());
        if(requested.candidateRevision()!=null)check(requested.candidateRevision(),current.candidateRevision());
        for(var field:Map.of("expectedRevision",current.expectedRevision(),"targetRevision",current.targetRevision(),"candidateRevision",current.candidateRevision()).entrySet()){
            if(original.containsKey(field.getKey()))check(original.get(field.getKey()),field.getValue());
        }
    }
    public void storeOriginal(Row row){
        Revisions current=current(row);Map<String,String> original=values(row.key());
        Map.of("expectedRevision",current.expectedRevision(),"targetRevision",current.targetRevision(),"candidateRevision",current.candidateRevision()).forEach((field,value)->{if(!original.containsKey(field))audit(row.key(),"NEW",field,value);});
    }
    public void requireDomain(Row row){
        LocalDateTime resumed=mapper.resumed(row);if(resumed!=null&&row.registeredAt().isBefore(resumed))throw stale();
        if(mapper.excluded(row))throw new ApiException(409,"INVALID_STATE","같은 대상·적용 직무에서 이미 제외한 인력입니다. 다시 추천해 주세요.");
    }
    public void check(String expected,String actual){revisions.requireExpected(expected,actual);}
    public void select(Row row,Choice choice,Map<String,String> payload,Map<String,String> connection){
        if(mapper.choose(choice)!=1)throw taken();
        audit(row.key(),"UPD","USER_ACT_CD",choice.action());audit(row.key(),"UPD","RCMD_EXCL_CD",choice.reason());
        audit(row.key(),"UPD","ACT_USER_ID",choice.actor());audit(row.key(),"UPD","ACT_DTTM",format(choice.now()));
        payload.forEach((field,value)->audit(row.key(),"UPD",field,value));connection.forEach((field,value)->audit(row.key(),"UPD",field,value));
        for(String key:List.of("assignments","assignment-candidates","sales-opportunities","workforce-forecasts","workforce-operations","workforce-recommendations","candidates"))invalidation.afterCommit(key);
    }
    public void audit(Key key,String type,String field,String value){history.append(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG",key.auditKey(),type,field,null,value,"추천 최종선택·원천 연결"));}
    public Result result(Row row,boolean changed,Map<String,String> connection,String message){
        return new Result(row.rcmdId(),row.rank(),changed,new Selection(row.actionCode(),row.exclusionReasonCode(),row.actedBy(),row.actedAt()==null?null:format(row.actedAt()),connection.get("assignmentId"),connection.get("tentativeAssignmentId"),connection.get("finalOpportunityId"),connection.get("finalNeedSn")),current(row).expectedRevision(),message,connection.get("releasedTentativeAssignmentId"));
    }
    public Result completed(Row row,Choice choice,Map<String,String> connection,String message){return result(new Row(row.rcmdId(),row.rank(),row.type(),row.opportunityId(),row.needSn(),row.projectId(),row.jobCode(),row.startMonth(),row.developerId(),row.scoreDetail(),choice.action(),choice.reason(),choice.actor(),choice.now(),row.registeredAt()),true,connection,message);}
    public boolean replay(Row row,String action,Map<String,String> payload){
        if(row.actionCode()==null)return false;
        Map<String,String> stored=values(row.key());
        if(!action.equals(row.actionCode())||!payload.entrySet().stream().allMatch(e->stored.containsKey(e.getKey())&&Objects.equals(stored.get(e.getKey()),e.getValue())))throw taken();return true;
    }
    public static ApiException stale(){return new ApiException(409,"STALE_WRITE","추천 원천이 변경되었습니다. 다시 조회하거나 다시 추천해 주세요.");}
    public static ApiException taken(){return new ApiException(409,"ACTION_ALREADY_TAKEN","이미 다른 최종선택이 저장되었습니다. 다시 조회해 주세요.");}
    private static String format(LocalDateTime date){return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
}
