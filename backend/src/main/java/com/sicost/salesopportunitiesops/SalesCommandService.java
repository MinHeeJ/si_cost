package com.sicost.salesopportunitiesops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

@Service
public class SalesCommandService {
    public record Context(Opportunity before,Quote quote,QuoteSources sources,String actor,LocalDateTime now,List<DraftNeed> needs) {}
    public record Transition(String stage,BigDecimal probability,String manual,String status,BigDecimal rate,String approval,
                             BigDecimal approved,LocalDate resultDate,String lossReason,String lossMemo,String projectId) {}
    private final SalesCommandRepository repository;
    private final WriteCoordinator writes;
    private final SalesQuoteCalculation quotes;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public SalesCommandService(SalesCommandRepository repository,WriteCoordinator writes,SalesQuoteCalculation quotes,
                               CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock){this.repository=repository;this.writes=writes;this.quotes=quotes;this.users=users;this.audit=audit;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Quote preview(Preview input){users.currentUser();QuoteSources sources=repository.quoteSources(current());validateMonths(input.needs(),sources);return quotes.calculate(input.needs(),sources);}
    private YearMonth current(){return YearMonth.from(clock.instant().atZone(TimeConfiguration.SEOUL));}
    private void validateMonths(List<DraftNeed> needs,QuoteSources sources){
        BigDecimal base=sources.standards().stream().filter(s->s.code().equals("CASH_BASE_YM")).map(Standard::value).findFirst().orElse(null);
        if(base==null)return;
        for(DraftNeed n:needs)if(n.startMonth()!=null&&new BigDecimal(n.startMonth().replace("-","")).compareTo(base)<0)throw new ApiException(400,"INVALID_PERIOD","운영 시작월 이후를 입력해 주세요.","startMonth",null);
    }
    @Transactional
    public SaveResult mutate(String id,String revision,boolean guarded,Function<Context,Opportunity> prepare,Consumer<Context> sideEffects){
        SalesCommandInput.id(id);String actor=users.userId();var keys=repository.keys(id);Context[] context=new Context[1];Opportunity[] next=new Opportunity[1];
        var result=writes.writeGuarded(new WriteCoordinator.GuardedWrite(keys,revision,()->repository.snapshot(id),()->{
            repository.lockOpportunity(id);
            if(!keys.equals(repository.keys(id)))throw new ApiException(409,"STALE_WRITE","관련 자료가 변경되었습니다. 다시 조회해 주세요.");
            Opportunity before=repository.required(id);QuoteSources source=repository.quoteSources(current());List<DraftNeed> needs=repository.needs(id);
            Quote quote=quotes.calculate(needs,source);context[0]=new Context(before,quote,source,actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL),needs);
            if(guarded&&quoteChanged(before,quote))throw new ApiException(409,"QUOTE_CHANGED","견적을 갱신해 주세요. 먼저 기회를 저장해 주세요.");
        },()->next[0]=prepare.apply(context[0]),()->!next[0].equals(context[0].before()),()->{
            Context c=context[0];sideEffects.accept(c);repository.update(new Mutation(next[0],actor,c.now()));auditOpportunity(c.before(),next[0]);
        }));
        return new SaveResult(result.changed(),result.revision(),context[0].quote().issues().stream().map(Issue::reason).distinct().toList(),id);
    }
    public boolean quoteChanged(Opportunity old,Quote q){
        BigDecimal rate=q.storedProfitRate()==null?null:new BigDecimal(q.storedProfitRate());
        if(!decimalEqual(old.profitRate(),rate))return true;
        return "NONE".equals(old.approvalStatus())&&Set.of("APPROVAL_REQUIRED","LOSS","UNKNOWN").contains(q.judgement())
            || "WAIT".equals(old.approvalStatus())&&Set.of("BELOW_TARGET","MET").contains(q.judgement());
    }
    private boolean decimalEqual(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    public void requireOpen(Opportunity o){if(!o.status().equals("ING"))throw state("진행 기회만 처리할 수 있습니다.");}
    public void requireApproved(Context c){requireOpen(c.before());if(c.quote().storedProfitRate()==null||Set.of("UNKNOWN","INCOMPLETE").contains(c.quote().judgement())||Set.of("WAIT","REJ").contains(c.before().approvalStatus()))throw state("견적 원가와 승인을 확인한 후 처리해 주세요.");}
    private ApiException state(String message){return new ApiException(409,"INVALID_STATE",message);}
    public static Transition unchanged(Opportunity o){return new Transition(o.stage(),o.probability(),o.manualProbability(),o.status(),o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),o.resultDate(),o.lossReasonCode(),o.lossMemo(),o.projectId());}
    public static Opportunity with(Opportunity o,Transition t){return new Opportunity(o.id(),o.name(),o.customerId(),o.partId(),t.stage(),t.probability(),t.manual(),o.amount(),o.startMonth(),o.contractType(),o.months(),o.workplace(),o.memo(),t.status(),t.rate(),t.approval(),t.approved(),t.resultDate(),t.lossReason(),t.lossMemo(),t.projectId(),o.registeredBy());}
    @Transactional public SaveResult stage(String id,Stage input){
        Opportunity initial=repository.required(id);boolean upward=List.of("FIND","PROP","NEGO","PRE").indexOf(input.stage())>List.of("FIND","PROP","NEGO","PRE").indexOf(initial.stage());
        return mutate(id,input.expectedRevision(),upward,c->{
            Opportunity o=c.before();requireOpen(o);
            boolean changedStage=!o.stage().equals(input.stage());
            if(changedStage&&Set.of("NEGO","PRE").contains(input.stage()))requireApproved(c);
            boolean manual=input.manualProbability()==null?"Y".equals(o.manualProbability()):input.manualProbability();
            if(input.probability()!=null&&input.manualProbability()==null)manual=true;
            BigDecimal probability=manual?input.probability()==null?o.probability():new BigDecimal(input.probability()):SalesQuoteCalculation.standard(c.sources(),"STEP_PRBL_"+(List.of("FIND","PROP","NEGO","PRE").indexOf(input.stage())+1),false);
            if(probability==null)throw state("단계 기본 확률 기준을 확인해 주세요.");
            if(!changedStage&&probability.compareTo(o.probability())==0&&manual=="Y".equals(o.manualProbability()))return o;
            return with(o,new Transition(input.stage(),probability,manual?"Y":"N",o.status(),o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),o.resultDate(),o.lossReasonCode(),o.lossMemo(),o.projectId()));
        },c->{});
    }
    @Transactional public SaveResult approval(String id,Approval input){
        return mutate(id,input.expectedRevision(),true,c->{
            Opportunity o=c.before();requireOpen(o);
            if(o.approvalStatus().equals(input.action()))return o;
            if(!o.approvalStatus().equals("WAIT")||c.quote().storedProfitRate()==null||Set.of("UNKNOWN","INCOMPLETE").contains(c.quote().judgement()))throw state("견적 원가를 확인한 승인 대기 기회만 승인·반려할 수 있습니다.");
            return with(o,new Transition(o.stage(),o.probability(),o.manualProbability(),o.status(),o.profitRate(),input.action(),input.action().equals("OK")?o.profitRate():null,o.resultDate(),o.lossReasonCode(),o.lossMemo(),o.projectId()));
        },c->{
            appendApproval(c,input.action(),input.reason());
            if(input.action().equals("REJ")){
                String suffix=" · 상세 /UI-29 기회 "+id;String prefix=c.before().name()+" 견적 반려: ";String reason=Objects.toString(input.reason(),"");
                int limit=300-prefix.codePointCount(0,prefix.length())-suffix.codePointCount(0,suffix.length());
                String summary=reason.codePointCount(0,reason.length())>limit?reason.substring(0,reason.offsetByCodePoints(0,Math.max(0,limit-1)))+"…":reason;
                repository.notify(new Notification(id,c.before().registeredBy(),prefix+summary+suffix,c.actor(),c.now()));
                audit.appendSales(new ChangeHistoryWriter.Change("TB_SY_NOTI",id,"NEW","QUOTE_REJ",null,c.before().registeredBy(),null));
            }
        });
    }
    public void appendApproval(Context c,String action,String reason){
        if(c.quote().storedProfitRate()==null)return;
        int sn=repository.nextHistory(c.before().id());BigDecimal rate=new BigDecimal(c.quote().storedProfitRate());
        repository.approval(new ApprovalEvent(c.before().id(),sn,action,rate,reason,c.actor(),c.now()));
        audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_APRV_HIST",c.before().id()+":"+sn,"NEW","APRV_ACT_CD",null,action,reason));
        audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_APRV_HIST",c.before().id()+":"+sn,"NEW","PRFT_RT",null,rate.toPlainString(),reason));
        if(reason!=null)audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_APRV_HIST",c.before().id()+":"+sn,"NEW","ACT_RSN",null,reason,null));
    }
    @Transactional public SaveResult lose(String id,Loss input){return mutate(id,input.expectedRevision(),false,c->{
        var o=c.before();if(o.status().equals("LOSE")&&Objects.equals(o.lossReasonCode(),input.reasonCode())&&Objects.equals(o.lossMemo(),input.memo()))return o;
        requireOpen(o);if(!repository.activeLossReason(input.reasonCode()))throw SalesCommandInput.invalid("reasonCode");
        return with(o,new Transition(o.stage(),o.probability(),o.manualProbability(),"LOSE",o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),c.now().toLocalDate(),input.reasonCode(),input.memo(),o.projectId()));
    },c->transitionTentatives(c,"REL"));}
    @Transactional public SaveResult stop(String id,Revision input){return mutate(id,input.expectedRevision(),false,c->{
        var o=c.before();if(o.status().equals("STOP"))return o;requireOpen(o);
        return with(o,new Transition(o.stage(),o.probability(),o.manualProbability(),"STOP",o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),c.now().toLocalDate(),o.lossReasonCode(),o.lossMemo(),o.projectId()));
    },c->transitionTentatives(c,"REL"));}
    @Transactional public SaveResult resume(String id,Revision input){return mutate(id,input.expectedRevision(),false,c->{
        var o=c.before();if(!o.status().equals("STOP"))throw state("사용 중지된 기회만 다시 사용할 수 있습니다.");
        String status=Set.of("LOSS","APPROVAL_REQUIRED","UNKNOWN","INCOMPLETE").contains(c.quote().judgement())?"WAIT":"NONE";
        return with(o,new Transition(o.stage(),o.probability(),o.manualProbability(),"ING",number(c.quote().storedProfitRate()),status,null,null,o.lossReasonCode(),o.lossMemo(),o.projectId()));
    },c->{if(Set.of("LOSS","APPROVAL_REQUIRED").contains(c.quote().judgement()))appendApproval(c,"REQ",null);});}
    public void transitionTentatives(Context c,String status){for(Tentative t:repository.tentatives(c.before().id()))if(t.status().equals("ACT")){
        repository.transition(new TentativeMutation(t.id(),c.before().id(),status,c.actor(),c.now()));
        audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",t.id(),"UPD","TENT_STAT_CD",t.status(),status,c.before().id()));
        audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",t.id(),"UPD","PROC_DTTM",t.processedAt()==null?null:t.processedAt().toString(),c.now().toString(),c.before().id()));
    }}
    private BigDecimal number(String s){return s==null?null:new BigDecimal(s);}
    private void auditOpportunity(Opportunity before,Opportunity after){
        String[] names={"SALES_STEP_CD","WIN_PRBL_RT","PRBL_MANUAL_YN","OPP_STAT_CD","EXPCT_PRFT_RT","APRV_STAT_CD","APRV_PRFT_RT","RSLT_DT","LOSE_RSN_CD","LOSE_MEMO","PROJ_ID"};
        Object[] a=values(before),b=values(after);for(int i=0;i<names.length;i++)if(!Objects.equals(a[i],b[i]))audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_OPP",before.id(),"UPD",names[i],a[i]==null?null:a[i].toString(),b[i]==null?null:b[i].toString(),null));
    }
    private Object[] values(Opportunity o){return new Object[]{o.stage(),o.probability(),o.manualProbability(),o.status(),o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),o.resultDate(),o.lossReasonCode(),o.lossMemo(),o.projectId()};}
}
