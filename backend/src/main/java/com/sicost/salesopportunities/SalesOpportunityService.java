package com.sicost.salesopportunities;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.salesopportunitiesops.*;
import com.sicost.salesopportunitiesops.SalesCommandDtos.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;

@Service
public class SalesOpportunityService {
    private final SalesOpportunityRepository repository;
    private final SalesCommandRepository commands;
    private final SalesQuoteCalculation quotes;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final SalesOpportunityTentatives tentatives;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public SalesOpportunityService(SalesOpportunityRepository repository,SalesCommandRepository commands,SalesQuoteCalculation quotes,WriteCoordinator writes,RevisionService revisions,SalesOpportunityTentatives tentatives,CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock){this.repository=repository;this.commands=commands;this.quotes=quotes;this.writes=writes;this.revisions=revisions;this.tentatives=tentatives;this.users=users;this.audit=audit;this.clock=clock;}
    private YearMonth current(){return YearMonth.from(clock.instant().atZone(TimeConfiguration.SEOUL));}
    private SalesOpportunityMapper mapper(){return repository.mapper();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public SalesOpportunityDtos.Opportunity get(String id){users.currentUser();return view(repository.required(id),commands.quoteSources(current()));}
    private SalesOpportunityDtos.Opportunity view(Stored s,QuoteSources sources){
        List<DraftNeed> needs=commands.needs(s.id());var assignments=mapper().tentatives(s.id()).stream().map(t->new SalesOpportunityDtos.Tentative(t.id(),t.opportunityId(),t.needSn(),t.developerId(),t.startMonth(),t.status(),t.sourceScreenId(),t.processedAt())).toList();
        return new SalesOpportunityDtos.Opportunity(s.id(),s.name(),s.customerId(),s.partId(),s.stage(),str(s.probability()),s.manualProbability().equals("Y"),str(s.amount()),s.startMonth(),s.contractType(),s.months(),s.nextTask(),s.nextDue(),s.workplace(),s.memo(),s.status(),needs,assignments,mapper().approvals(s.id()),quotes.calculate(needs,sources),s.approvalStatus(),str(s.approvedProfitRate()),s.projectId(),s.resultDate(),s.lossReasonCode(),s.lossMemo(),revisions.revision(commands.snapshot(s.id())),s.modifiedAt(),s.registeredAt());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(Query q){
        users.currentUser();List<Stored> all=mapper().all(),ing=all.stream().filter(s->s.status().equals("ING")).toList();
        BigDecimal amount=BigDecimal.ZERO,weighted=BigDecimal.ZERO,people=BigDecimal.ZERO;int stale=0;LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        for(Stored s:ing){amount=amount.add(s.amount());weighted=weighted.add(s.amount().multiply(s.probability()).movePointLeft(2).setScale(0,RoundingMode.DOWN));
            if((s.modifiedAt()==null?s.registeredAt():s.modifiedAt()).isBefore(now.minusDays(14)))stale++;
            for(DraftNeed n:commands.needs(s.id()))people=people.add(BigDecimal.valueOf(n.count()).multiply(new BigDecimal(n.monthlyMm())).multiply(s.probability()).movePointLeft(2));}
        var totals=new ArrayList<StageTotal>();for(String stage:List.of("FIND","PROP","NEGO","PRE")){var rows=ing.stream().filter(s->s.stage().equals(stage)).toList();totals.add(new StageTotal(stage,rows.size(),str(rows.stream().map(Stored::amount).reduce(BigDecimal.ZERO,BigDecimal::add))));}
        List<Stored> filtered=all.stream().filter(s->q.result()!=null?s.status().equals(q.result()):q.status().equals("ING")?s.status().equals("ING"):s.status().equals(q.status())||q.includeStopped()&&s.status().equals("STOP")).sorted(order(q)).toList();
        QuoteSources source=commands.quoteSources(current());
        return new Page(filtered.stream().skip((long)(q.page()-1)*q.size()).limit(q.size()).map(s->view(s,source)).toList(),filtered.size(),q.page(),q.size(),new Summary(ing.size(),stale,str(amount),str(weighted),str(people)),totals);
    }
    private Comparator<Stored> order(Query q){String key=q.sort()==null?q.status().equals("ING")?"id":"resultDate":q.sort();String direction=q.direction()==null?key.equals("resultDate")?"DESC":"ASC":q.direction();
        Comparator<Stored> c=switch(key){case "amount"->compare(Stored::amount,direction);case "probability"->compare(Stored::probability,direction);case "startMonth"->compare(Stored::startMonth,direction);case "resultDate"->compare(Stored::resultDate,direction);default->compare(Stored::id,direction);};return c.thenComparing(Stored::id);}
    private <T extends Comparable<? super T>> Comparator<Stored> compare(Function<Stored,T> f,String direction){Comparator<T> values=direction.equals("DESC")?Comparator.reverseOrder():Comparator.naturalOrder();return Comparator.comparing(f,Comparator.nullsLast(values));}
    private static String str(BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
    public record ApprovalState(BigDecimal rate,String status,BigDecimal approved,String event) {}
    public static ApprovalState approval(Stored old,Quote q,boolean contentChanged){
        BigDecimal rate=q.storedProfitRate()==null?null:new BigDecimal(q.storedProfitRate());if(rate!=null&&rate.abs().compareTo(new BigDecimal("99999.99"))>0)throw SalesOpportunityInput.invalid("profitRate");
        if(old!=null&&old.approvalStatus().equals("REJ")&&!contentChanged&&Objects.equals(str(old.profitRate()),str(rate)))return new ApprovalState(rate,old.approvalStatus(),old.approvedProfitRate(),null);
        boolean low=rate==null||q.approvalThreshold()==null||new BigDecimal(q.revenue()).subtract(new BigDecimal(q.cost())).multiply(new BigDecimal("100")).compareTo(new BigDecimal(q.revenue()).multiply(new BigDecimal(q.approvalThreshold())))<0;
        if(old!=null&&old.approvalStatus().equals("OK")&&rate!=null&&old.approvedProfitRate()!=null&&q.reapprovalGap()!=null&&rate.subtract(old.approvedProfitRate()).abs().compareTo(new BigDecimal(q.reapprovalGap()))<=0)return new ApprovalState(rate,"OK",old.approvedProfitRate(),null);
        String status=low?"WAIT":"NONE",event=null;
        if(rate!=null&&status.equals("WAIT")&&(old==null||!old.approvalStatus().equals("WAIT")||old.profitRate()==null))event="REQ";
        if(rate!=null&&old!=null&&status.equals("NONE")&&Set.of("WAIT","OK","REJ").contains(old.approvalStatus()))event="AUTO_REL";
        return new ApprovalState(rate,status,null,event);
    }
    private Save normalized(Stored old,List<DraftNeed> needs){return new Save(old.name(),old.customerId(),old.partId(),old.stage(),str(old.probability()),old.manualProbability().equals("Y"),str(old.amount()),old.startMonth(),old.contractType(),old.months(),old.nextTask(),old.nextDue(),old.workplace(),old.memo(),normalize(needs),null,null);}
    private List<DraftNeed> normalize(List<DraftNeed> rows){return rows.stream().map(n->new DraftNeed(n.needSn(),n.jobCode(),n.gradeCode(),n.count(),n.startMonth(),n.months(),str(new BigDecimal(n.monthlyMm())),n.expectedRate()==null?null:str(new BigDecimal(n.expectedRate())))).sorted(Comparator.comparing(DraftNeed::needSn)).toList();}
    private Save defaultProbability(Save input,QuoteSources source) {
        if(input.manualProbability())return input;
        String code="STEP_PRBL_"+(List.of("FIND","PROP","NEGO","PRE").indexOf(input.stage())+1);
        BigDecimal probability=SalesQuoteCalculation.standard(source,code,false);
        if(probability==null)throw new ApiException(409,"SOURCE_CONFLICT","단계 기본 확률 기준을 확인해 주세요.","probability",null);
        return new Save(input.name(),input.customerId(),input.partId(),input.stage(),str(probability),false,input.amount(),input.startMonth(),
            input.contractType(),input.months(),input.nextTask(),input.nextDue(),input.workplace(),input.memo(),input.needs(),input.tentativeSelections(),input.expectedRevision());
    }
    private Save clean(Save i,List<DraftNeed> needs){return new Save(i.name(),i.customerId(),i.partId(),i.stage(),i.probability(),i.manualProbability(),i.amount(),i.startMonth(),i.contractType(),i.months(),i.nextTask(),i.nextDue(),i.workplace(),i.memo(),normalize(needs),null,null);}
    private void validate(Save input,Stored old,QuoteSources source){
        if((old==null||!old.customerId().equals(input.customerId()))&&!mapper().customer(input.customerId()))throw SalesOpportunityInput.invalid("customerId");
        if((old==null||!old.partId().equals(input.partId()))&&!mapper().part(input.partId()))throw SalesOpportunityInput.invalid("partId");
        BigDecimal base=source.standards().stream().filter(s->s.code().equals("CASH_BASE_YM")).map(Standard::value).findFirst().orElse(null);
        if(base==null)throw new ApiException(409,"SOURCE_CONFLICT","운영 시작월 기준을 확인해 주세요.");
        if(new BigDecimal(input.startMonth().replace("-","")).compareTo(base)<0)throw new ApiException(400,"INVALID_PERIOD","운영 시작월 이후를 입력해 주세요.","startMonth",null);
        List<DraftNeed> previous=old==null?Collections.emptyList():commands.needs(old.id());int index=0;
        for(DraftNeed n:input.needs()){int row=++index;DraftNeed prior=previous.stream().filter(p->Objects.equals(p.needSn(),n.needSn())).findFirst().orElse(null);
            if(n.needSn()!=null&&prior==null)throw SalesOpportunityInput.invalid("needs["+(row-1)+"].needSn");
            for(String group:List.of("JOB","GRADE")){String value=group.equals("JOB")?n.jobCode():n.gradeCode(),before=prior==null?null:group.equals("JOB")?prior.jobCode():prior.gradeCode();if(value!=null&&!value.equals(before)&&!mapper().code(new CodeKey(group,value)))throw rowError(row,group.equals("JOB")?"jobCode":"gradeCode");}
            if(new BigDecimal(n.startMonth().replace("-","")).compareTo(base)<0)throw rowError(row,"startMonth");
            if(n.expectedRate()==null&&source.codes().stream().noneMatch(c->c.group().equals("JOB")&&c.code().equals(n.jobCode())&&c.defaultRate()!=null&&c.defaultRate().matches("[1-9][0-9]{0,14}")))throw rowError(row,"expectedRate");
        }
    }
    private ApiException rowError(int row,String field){String label=switch(field){case "expectedRate"->"예상단가";case "jobCode"->"직무";case "gradeCode"->"등급";default->"시작월";};return new ApiException(400,"VALIDATION_ERROR",row+"번째 줄의 "+label+" 칸을 확인해 주세요.","needs["+(row-1)+"]."+field,Map.of("rowNumber",row));}
    @Transactional public SaveResult create(Save input){return save(null,input);}
    @Transactional public SaveResult update(String id,Save input){return save(id,input);}
    private SaveResult save(String suppliedId,Save input){
        String actor=users.userId();String id=suppliedId==null?repository.allocate():suppliedId;boolean create=suppliedId==null;
        Save[] effective={input};List<String> keys=repository.keys(id);Stored[] old=new Stored[1];List<DraftNeed> rows=new ArrayList<>();List<StoredTent> changes=new ArrayList<>();List<Selection>[] selected=new List[]{null};ApprovalState[] state=new ApprovalState[1];Quote[] quote=new Quote[1];boolean[] changed={true};var warnings=new ArrayList<String>();LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        Runnable validate=()->{
            if(!create){commands.lockOpportunity(id);old[0]=repository.required(id);if(!old[0].status().equals("ING"))throw new ApiException(409,"INVALID_STATE","종료 기회의 필요 인력은 수정할 수 없습니다.");}
            if(!keys.equals(repository.keys(id)))throw new ApiException(409,"STALE_WRITE","관련 자료가 변경되었습니다. 다시 조회해 주세요.");
            QuoteSources source=commands.quoteSources(current());validate(input,old[0],source);effective[0]=defaultProbability(input,source);int max=create?0:mapper().greatestNeedSn(id);
            for(DraftNeed n:input.needs()){Integer sn=n.needSn();if(sn==null){if(++max>999)throw new ApiException(409,"ID_EXHAUSTED","필요 인력 순번을 모두 사용했습니다.");sn=max;}rows.add(new DraftNeed(sn,n.jobCode(),n.gradeCode(),n.count(),n.startMonth(),n.months(),n.monthlyMm(),n.expectedRate()));}
            selected[0]=tentatives.resolve(input.tentativeSelections(),rows);List<StoredTent> existing=create?Collections.emptyList():mapper().tentatives(id);changes.addAll(tentatives.changes(existing,rows,selected[0],now));
            for(StoredTent t:existing)if(Set.of("CNVT","DONE").contains(t.status())&&rows.stream().noneMatch(n->n.needSn()==t.needSn()))throw new ApiException(409,"INVALID_STATE","전환·확정 가배정은 삭제할 수 없습니다.");
            boolean content=create||!clean(effective[0],rows).equals(normalized(old[0],commands.needs(id)));
            quote[0]=quotes.calculate(rows,source);state[0]=approval(old[0],quote[0],content);
            if((create||!old[0].stage().equals(input.stage()))&&Set.of("NEGO","PRE").contains(input.stage())&&(state[0].rate()==null||!Set.of("NONE","OK").contains(state[0].status())))throw new ApiException(409,"INVALID_STATE","견적 승인 전이라 협상 단계로 옮길 수 없습니다");
            changed[0]=content||!changes.isEmpty()||tentatives.additions(selected[0],existing)||!Objects.equals(str(old[0]==null?null:old[0].profitRate()),str(state[0].rate()))||old[0]!=null&&!old[0].approvalStatus().equals(state[0].status());
        };
        Runnable persist=()->{
            List<Field> before=create?Collections.emptyList():mapper().fields(id);Write write=new Write(id,effective[0],state[0].rate(),state[0].status(),state[0].approved(),actor,now);
            repository.one(create?mapper().insert(write):mapper().update(write));
            for(StoredTent t:changes)repository.one(mapper().updateTentative(new TentWrite(t,actor,now)));
            repository.auditDelta(before,mapper().fields(id));before=mapper().fields(id);
            List<DraftNeed> existing=create?Collections.emptyList():commands.needs(id);
            for(DraftNeed n:existing)if(rows.stream().noneMatch(r->r.needSn().equals(n.needSn()))){var key=new NeedKey(id,n.needSn());long expected=mapper().tentatives(id).stream().filter(t->t.needSn()==n.needSn()).count();if(mapper().deleteTentatives(key)!=expected)throw new IllegalStateException("가배정 삭제 결과를 확인해 주세요.");repository.one(mapper().deleteNeed(key));}
            for(DraftNeed n:rows){DraftNeed prior=existing.stream().filter(p->p.needSn().equals(n.needSn())).findFirst().orElse(null);NeedWrite nw=new NeedWrite(id,n,actor,now);if(prior==null)repository.one(mapper().insertNeed(nw));else if(!normalize(List.of(prior)).equals(normalize(List.of(n))))repository.one(mapper().updateNeed(nw));}
            warnings.addAll(tentatives.apply(id,selected[0]));repository.auditDelta(before,mapper().fields(id));
            if(state[0].event()!=null){int sn=commands.nextHistory(id);commands.approval(new ApprovalEvent(id,sn,state[0].event(),state[0].rate(),null,actor,now));for(var f:Map.of("APRV_ACT_CD",state[0].event(),"PRFT_RT",state[0].rate().toPlainString()).entrySet())audit.appendSales(new ChangeHistoryWriter.Change("TB_SL_APRV_HIST",id+":"+sn,"NEW",f.getKey(),null,f.getValue(),null));}
        };
        WriteCoordinator.WriteResult result=create?writes.create(keys,()->commands.snapshot(id),validate,persist):writes.write(keys,input.expectedRevision(),()->{commands.lockOpportunity(id);return commands.snapshot(id);},validate,()->changed[0],persist);
        warnings.addAll(quote[0].issues().stream().map(Issue::reason).distinct().toList());return new SaveResult(result.changed(),result.revision(),List.copyOf(warnings),id);
    }
}
