package com.sicost.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.notifications.PredictionNotificationAdapter;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

@Service
public class AnalysisAssistantService {
    private final CurrentUserProvider users;private final Clock clock;private final AnalysisAssistantMapper mapper;
    private final AnalysisQuestionInterpreter interpreter;private final AnalysisDataQueries queries;private final ObjectMapper json;
    private final TransactionTemplate write;private final PredictionNotificationAdapter notifications;private final AnalysisAccessPolicy policy;
    private final AnalysisModelAdapter model;
    public AnalysisAssistantService(CurrentUserProvider users,Clock clock,AnalysisAssistantMapper mapper,AnalysisQuestionInterpreter interpreter,
        AnalysisDataQueries queries,ObjectMapper json,PlatformTransactionManager transactions,PredictionNotificationAdapter notifications,AnalysisAccessPolicy policy,AnalysisModelAdapter model) {
        this.users=users;this.clock=clock;this.mapper=mapper;this.interpreter=interpreter;this.queries=queries;this.json=json;
        write=new TransactionTemplate(transactions);this.notifications=notifications;this.policy=policy;this.model=model;
    }
    public record Accepted(String user,Question question,OffsetDateTime at) {}
    public Accepted accept(Question input) {
        if(input==null||input.question()==null)throw invalid("question");String text=input.question().strip();
        if(text.isEmpty()||text.codePointCount(0,text.length())>1000)throw invalid("question");
        Context c=input.context()==null?new Context("UI-26",null,null,null):input.context();
        if(c.screenId()!=null&&!c.screenId().matches("UI-(0[1-9]|[12][0-9]|3[0-3])"))throw invalid("context.screenId");
        if(c.baseMonth()!=null&&!c.baseMonth().matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("context.baseMonth");
        if(c.projectId()!=null&&(c.projectId().isBlank()||c.projectId().length()>20)||c.developerId()!=null&&(c.developerId().isBlank()||c.developerId().length()>20))throw invalid("context");
        return new Accepted(users.userId(),new Question(text,c),clock.instant().atZone(TimeConfiguration.SEOUL).toOffsetDateTime());
    }
    public Final execute(Accepted accepted,Consumer<Progress> progress) {
        var intent=interpreter.interpret(accepted.question().question(),accepted.question().context(),accepted.at().toLocalDate());Result result;
        progress.accept(new Progress("FINDING_DATA","데이터 찾는 중",accepted.at()));
        try {
            var interpretation=model.classify(intent.function(),intent.reason()==null&&accepted.question().question().matches(".*(분석|전망).*"));
            progress.accept(new Progress("CALCULATING","계산 중",accepted.at()));
            result=queries.read(accepted.user(),accepted.question(),intent,accepted.at());
            result=new Result(result.schemaVersion(),result.conclusion(),result.tables(),result.assumptions(),result.unknownReasons(),result.usedData(),result.nextActions(),result.context(),result.appliedScope(),result.status(),result.reason(),result.screenLinks(),interpretation,result.dataAsOf());
        } catch(ApiException e) {result=failure(accepted,e.status()<500?"NO_ANS":"ERR",e.status()<500?e.getMessage():"조회에 실패했습니다. 다시 시도해 주세요.",intent);}
        catch(Exception e){result=failure(accepted,"ERR","조회에 실패했습니다. 다시 시도해 주세요.",intent);}
        progress.accept(new Progress("SUMMARIZING","정리 중",accepted.at()));
        try{return persist(accepted,result,intent.prediction());}
        catch(Exception failed){
            try{return persist(accepted,failure(accepted,"ERR","질문 완료를 저장하지 못했습니다. 다시 시도하기 전에 질문 기록을 확인해 주세요.",intent),false);}
            catch(Exception alsoFailed){return new Final(false,null,"ERR",null,UUID.randomUUID().toString(),"저장하지 못했습니다. 재시도 전 질문 기록을 확인하세요.");}
        }
    }
    private Final persist(Accepted a,Result result,boolean prediction) {
        return write.execute(tx->{
            policy.require(a.user(),result.appliedScope());
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {policy.require(a.user(),result.appliedScope());}
                });
            String id=mapper.nextId();boolean provisional=result.tables().stream().anyMatch(t->Set.of("PROVISIONAL","PLANNED").contains(t.sourceState()));
            String description=String.join(" · ",result.usedData().stream().map(Evidence::queryName).distinct().toList());if(description.length()>500)description=description.substring(0,500);
            try {mapper.insert(new Insert(id,a.user(),a.question().question(),json.writeValueAsString(result),description,result.appliedScope().baseMonth().replace("-",""),provisional?"Y":"N",result.status(),a.at().toLocalDateTime(),LocalDateTime.now(clock.withZone(TimeConfiguration.SEOUL))));}
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("질문 결과를 저장할 수 없습니다.");}
            if(prediction&&result.status().equals("OK"))notifications.predictionCompleted(Long.parseLong(id),a.user());
            return new Final(true,id,result.status(),result,null,null);
        });
    }
    public Page page(String cursor,int limit) {
        if(limit<1||limit>50)throw invalid("limit");LocalDateTime at=null;String id=null;
        if(cursor!=null)try {String[] pair=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);if(pair.length!=2||!pair[1].matches("[1-9][0-9]{0,11}"))throw new IllegalArgumentException();at=LocalDateTime.parse(pair[0]);id=pair[1];}catch(Exception e){throw invalid("cursor");}
        var rows=mapper.page(users.userId(),at,id,limit+1);boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).toList();
        String next=null;if(more){var last=selected.get(selected.size()-1);next=Base64.getUrlEncoder().withoutPadding().encodeToString((last.questionAt()+"|"+last.qnaId()).getBytes(StandardCharsets.UTF_8));}
        String user=users.userId();
        return new Page(selected.stream().map(r->{var view=storedResult(user,r);boolean visible=!UNAVAILABLE.equals(view.reason());return new Summary(r.qnaId(),visible?safeQuestion(user,r.question(),view):UNAVAILABLE,r.questionAt().atZone(TimeConfiguration.SEOUL).toOffsetDateTime(),view.status(),visible&&r.provisional().equals("Y"));}).toList(),next,more);
    }
    public Detail detail(String id) {
        if(id==null||!id.matches("[1-9][0-9]{0,11}"))throw missing();String user=users.userId();var row=mapper.find(user,id);if(row==null)throw missing();
        var result=storedResult(user,row);boolean visible=!UNAVAILABLE.equals(result.reason());
        return new Detail(row.qnaId(),visible?safeQuestion(user,row.question(),result):UNAVAILABLE,row.questionAt().atZone(TimeConfiguration.SEOUL).toOffsetDateTime(),result.status(),visible&&row.provisional().equals("Y"),result,visible?row.description():"",visible&&row.baseMonth()!=null?row.baseMonth().substring(0,4)+"-"+row.baseMonth().substring(4):null,visible?"질문 당시 결과 · "+result.dataAsOf():UNAVAILABLE);
    }
    private static final String UNAVAILABLE="과거 답변을 현재 권한으로 표시할 수 없습니다. 다시 질문해 주세요.";
    private Result storedResult(String user,Stored row) {
        try {var result=json.readValue(row.answer(),Result.class);if(displayable(user,result))return result;}
        catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* Never echo corrupt stored text. */ }
        return unavailable(row.questionAt().atZone(TimeConfiguration.SEOUL).toOffsetDateTime());
    }
    public Final projectFinal(Final value) {
        if(value.result()==null || displayable(users.userId(),value.result()))return value;
        return new Final(value.persisted(),value.qnaId(),"NO_ANS",unavailable(value.result().dataAsOf()),null,UNAVAILABLE);
    }
    private boolean displayable(String user,Result value) {
        if(value==null || value.appliedScope()==null || value.appliedScope().targets()==null || value.appliedScope().targets().isEmpty())return false;
        try {
            policy.require(user,value.appliedScope());
            for(var evidence:value.usedData())policy.require(user,evidence.scope());
            for(var table:value.tables())for(var row:table.rows())if(row.target()!=null)policy.require(user,new Scope(List.of(row.target()),List.of(),null,null,value.appliedScope().baseDate(),value.appliedScope().baseMonth()));
            var functions=value.appliedScope().targets().stream().filter(t->t.targetType().equals("FUNCTION")).toList();
            if(functions.isEmpty())return false;
            boolean onlyAssignments=functions.stream().allMatch(t->t.targetId().equals("ASSIGNMENTS"));
            if(onlyAssignments && !policy.allows(user,new Target("FUNCTION","COMPANY","",List.of()))) {
                if(!value.conclusion().equals("등록 자료의 공통 조회 결과입니다. 기간·잠정 상태와 미확인 항목을 함께 확인하세요.") || !value.assumptions().isEmpty() || value.unknownReasons().stream().anyMatch(reason->!AnalysisDataQueries.PRIVATE_EXCLUSION.equals(reason)))return false;
                Set<String> allowed=Set.of("id","project","person","job","start","end","year","mm","type");
                if(value.tables().stream().anyMatch(t->t.columns().stream().anyMatch(c->!allowed.contains(c.key())) || t.rows().stream().anyMatch(r->r.cells().stream().anyMatch(c->!allowed.contains(c.columnKey())))))return false;
            }
            return true;
        } catch(ApiException denied) {if(denied.status()==401)throw denied;return false;}
        catch(NullPointerException | IllegalArgumentException malformed) {return false;}
    }
    private String safeQuestion(String user,String question,Result result) {return policy.allows(user,new Target("FUNCTION","COMPANY","",List.of()))?question:"현재 권한으로 조회한 질문 기록";}
    private Result unavailable(OffsetDateTime at) {
        if(at==null)at=clock.instant().atZone(TimeConfiguration.SEOUL).toOffsetDateTime();
        return new Result("1",UNAVAILABLE,List.of(),List.of(),List.of(UNAVAILABLE),List.of(),List.of("현재 권한으로 다시 질문해 주세요."),new Context("UI-26",null,null,null),new Scope(List.of(),List.of(),null,null,at.toLocalDate(),YearMonth.from(at).toString()),"NO_ANS",UNAVAILABLE,List.of(),new Interpreter("SERVER_RULE",null,"현재 권한으로 재검사"),at);
    }
    private Result failure(Accepted a,String status,String reason,AnalysisQuestionInterpreter.Intent i){return new Result("1",status.equals("ERR")?"오류 · "+reason:"답할 수 없음 · "+reason,List.of(),List.of(),List.of(reason),List.of(),List.of("질문 기록 확인 후 다시 질문해 주세요."),a.question().context(),new Scope(List.of(),List.of(),i.from().toString(),i.to().toString(),a.at().toLocalDate(),i.prediction()?YearMonth.from(a.at()).toString():i.to().toString()),status,reason,List.of(),new Interpreter("SERVER_RULE",null,"서버 규칙으로 해석·외부 모델 미사용"),a.at());}
    private static ApiException invalid(String field){return new ApiException(400,"INVALID_INPUT","질문은 1~1000자, 문맥과 조회 조건은 지정된 형식으로 입력해 주세요.",field,null);}
    private static ApiException missing(){return new ApiException(404,"NOT_FOUND","질문 기록을 찾을 수 없습니다.");}
}
