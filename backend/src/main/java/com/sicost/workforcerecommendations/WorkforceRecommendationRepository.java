package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@Repository
public class WorkforceRecommendationRepository {
    private final WorkforceRecommendationMapper mapper;
    private final ObjectMapper json;
    private final ChangeHistoryWriter audit;
    private final RevisionService revisions;
    private final SettlementSourceLocks locks;
    private final CommitInvalidation invalidation;
    public WorkforceRecommendationRepository(WorkforceRecommendationMapper mapper,ObjectMapper json,ChangeHistoryWriter audit,RevisionService revisions,SettlementSourceLocks locks,CommitInvalidation invalidation){this.mapper=mapper;this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);this.audit=audit;this.revisions=revisions;this.locks=locks;this.invalidation=invalidation;}
    public WorkforceRecommendationMapper mapper(){return mapper;}
    public String revision(Object snapshot){return revisions.revision(snapshot);}
    public void lock(Condition c){locks.lock(new TreeSet<>(List.of("RCMD_TARGET:"+("PROJECT".equals(c.targetType())?"PROJECT:":"OPP:")+c.targetId()+":"+c.jobCode(),("PROJECT".equals(c.targetType())?"PROJECT:":"OPP:")+c.targetId())));}
    public String encode(Object detail,String reason){try{String encoded=json.writeValueAsString(detail);if(encoded.codePointCount(0,encoded.length())>500)throw new ApiException(409,"SCORE_DETAIL_TOO_LONG","추천 점수 근거의 저장 폭 500자를 초과했습니다.");if(reason==null||reason.codePointCount(0,reason.length())>300)throw new ApiException(409,"REASON_TOO_LONG","추천 이유의 저장 폭 300자를 초과했습니다.");return encoded;}catch(JsonProcessingException e){throw new IllegalStateException("추천 점수 사본을 생성하지 못했습니다.");}}
    public JsonNode decode(String value){try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("저장된 점수 사본을 읽지 못했습니다.");}}
    public AiScoreDetail ai(String value){try{return json.readValue(value,AiScoreDetail.class);}catch(JsonProcessingException e){throw new IllegalStateException("저장된 점수 사본을 읽지 못했습니다.");}}
    public void insert(Row row,String targetRevision,String candidateRevision,String expectedRevision){
        if(mapper.insert(row)!=1)throw new IllegalStateException("추천 순위를 저장하지 못했습니다.");
        for(var entry:Map.of("targetRevision",targetRevision,"candidateRevision",candidateRevision,"expectedRevision",expectedRevision).entrySet())audit.appendRecommendation(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG",row.rcmdId()+":"+row.rank(),"NEW",entry.getKey(),null,entry.getValue(),"추천 생성 당시 변경 식별값"));
    }
    public Map<String,String> metadata(Row row){Map<String,String> m=new LinkedHashMap<>();for(AuditValue v:mapper.metadata(row.rcmdId()+":"+row.rank()))m.putIfAbsent(v.item(),v.value());return m;}
    public Selection selection(Row row,Map<String,String> metadata){return new Selection(row.actionCode(),row.exclusionReasonCode(),row.actedBy(),time(row.actedAt()),metadata.get("assignmentId"),metadata.get("tentativeAssignmentId"),metadata.get("finalOpportunityId"),metadata.get("finalNeedSn"));}
    public void invalidate(){invalidation.afterCommit("workforce-recommendations");}
    public JsonNode nextPosition(Row row){
        if(!"NEXT".equals(row.recommendationType()))return null;
        JsonNode detail=decode(row.scoreDetail()),e=detail.path("e");NextCard names=mapper.nextCard(row);
        if(names==null)throw new IllegalStateException("추천 기회 표시 정보를 찾을 수 없습니다.");
        var card=json.createObjectNode();
        card.put("opportunityName",names==null?row.opportunityId():names.opportunityName());
        card.put("salesStepCode",names==null?"UNKNOWN":names.salesStepCode());
        card.put("probability",e.path("pr").decimalValue().toPlainString());
        card.put("customerId",names==null?null:names.customerId());card.put("customerName",names==null?null:names.customerName());
        card.put("originalStartMonth",e.path("st").asText());card.put("actualStartMonth",com.sicost.common.assignments.AssignmentMonthlyService.api(row.startMonth()));
        card.put("gradeCode",row.requestedGradeCode());card.put("remainingCount",e.path("left").asInt());
        card.put("gapMonths",e.path("gap").asInt());card.put("waitDays",e.path("wait").asInt());
        card.put("alreadyTentativelyAssigned",names!=null&&names.alreadyTentativelyAssigned());
        var chips=card.putArray("reasonChips");chips.add(detail.path("s").path(0).asInt()==40?"주직무 일치":"관련 직무");
        chips.add(e.path("gap").asInt()==0?"공백 없음":"공백 "+e.path("gap").asInt()+"개월");
        if(!e.path("g").isNull())chips.add(e.path("g").asInt()==0?"등급 일치":"등급 "+e.path("g").asInt()+"단계 차이");
        if(e.path("cu").asInt()==1)chips.add("원고객 일치");
        chips.add("수주 확률 "+e.path("pr").decimalValue().toPlainString()+"%");return card;
    }
    public static String time(java.time.LocalDateTime value){return value==null?null:value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
}
