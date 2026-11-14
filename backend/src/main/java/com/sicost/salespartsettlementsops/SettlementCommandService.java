package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.SettlementSourceLocks;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.history.ChangeHistoryWriter.Change;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.salespartsettlements.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static com.sicost.salespartsettlementsops.SettlementCommandMapper.*;

@Service
public class SettlementCommandService {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Mutation(boolean changed, String partId, String month, String status, String cardAmount,
        String partProfitAmount, String confirmedBy, String confirmedAt, String lastCancellationReason,
        String revision, List<Issue> issues) {}
    private final SettlementCommandMapper mapper;
    private final SalesPartSettlementService reads;
    private final SalesPartSettlementRepository repository;
    private final SettlementSourceLocks locks;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final SettlementSnapshotCodec codec;
    private final CommitInvalidation invalidation;
    public SettlementCommandService(SettlementCommandMapper mapper, SalesPartSettlementService reads,
        SalesPartSettlementRepository repository, SettlementSourceLocks locks, RevisionService revisions,
        ChangeHistoryWriter history, CurrentUserProvider users, Clock clock, SettlementSnapshotCodec codec,
        CommitInvalidation invalidation) {
        this.mapper=mapper;this.reads=reads;this.repository=repository;this.locks=locks;this.revisions=revisions;
        this.history=history;this.users=users;this.clock=clock;this.codec=codec;this.invalidation=invalidation;
    }
    @Transactional
    public Mutation saveCard(String partId, JsonNode body) { return execute(partId, body, "CARD"); }
    @Transactional
    public Mutation confirm(String partId, JsonNode body) { return execute(partId, body, "CONFIRM"); }
    @Transactional
    public Mutation cancel(String partId, JsonNode body) { return execute(partId, body, "CANCEL"); }
    private Mutation execute(String partId, JsonNode body, String operation) {
        var input=SettlementCommandInput.parse(body,operation.equals("CANCEL"));
        String month=DatabaseMonthCodec.toDatabase(input.month());
        locks.lock(mapper.sourceKeys(month));
        Result before=reads.previewSettlement(partId,input.month());
        revisions.requireExpected(input.expectedRevision(),before.revision());
        var stored=repository.load(input.month(),true);
        Parent parent=stored.parents().stream().filter(p->p.partId().equals(partId)).findFirst().orElse(null);
        if (stored.sources().protectedMonth(month)) throw conflict("CLOSED_MONTH","마감된 월은 변경할 수 없습니다.");
        if (!input.month().substring(0,4).equals(String.valueOf(stored.sources().asOfDate().getYear())))
            throw conflict("STATE_CONFLICT","과거 연도는 조회 전용입니다.");
        boolean confirmed=!before.status().equals("WORK");
        if(operation.equals("CONFIRM") && confirmed) return mutation(false,before);
        if(operation.equals("CANCEL") && !confirmed) throw conflict("STATE_CONFLICT","작업중인 정산은 확정 취소할 수 없습니다.");
        if(operation.equals("CARD") && (confirmed||!before.canSaveCard())) throw conflict("STATE_CONFLICT","카드를 저장할 수 없는 정산 상태입니다.");
        if(operation.equals("CONFIRM") && !before.canConfirm()) throw conflict("STATE_CONFLICT","필수 금액·파트 기준·대상을 확인해 주세요.");
        if(operation.equals("CARD") && new BigDecimal(before.cardAmount()).compareTo(input.cardAmount())==0) return mutation(false,before);
        String actor=users.userId(); LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        boolean confirming=operation.equals("CONFIRM"), cancelling=operation.equals("CANCEL");
        BigDecimal card=cancelling?new BigDecimal(before.cardAmount()):input.cardAmount();
        BigDecimal profit=confirming?number(before.summary().personProfitShareAmount(),"partProfitAmount").subtract(card):null;
        if(profit!=null) range(profit,15,0,"partProfitAmount",null);
        var write=new ParentWrite(partId,month,card,profit,confirming?"CNFM":"WORK",confirming?actor:null,confirming?now:null,cancelling?input.reason():operation.equals("CARD")?before.lastCancellationReason():null,actor,now);
        List<DetailWrite> details=new ArrayList<>();
        if(confirming) {
            Set<String> memberIds=new HashSet<>();
            stored.sources().people().stream().filter(p->partId.equals(p.personPartId())).forEach(p->memberIds.add(p.personId()));
            if(stored.details().stream().anyMatch(d->!partId.equals(d.partId())&&memberIds.contains(d.developerId())&&stored.parents().stream().anyMatch(p->p.partId().equals(d.partId())&&p.confirmed())))
                throw conflict("STATE_CONFLICT","다른 파트에 확정된 인력월이 있습니다. 다시 조회해 주세요.");
            for(Row row:before.items()) details.add(detail(write,row));
        }
        List<Change> changes=new ArrayList<>();String reason=cancelling?input.reason():confirming?"수동확정":"카드저장";
        audit(changes,new AuditTarget("TB_CO_PART_SETL",partId+":"+month,parent==null?"NEW":"UPD",reason),parentFields(parent),parentFields(write));
        if(cancelling||confirming) {
            for(Detail old:stored.details().stream().filter(d->d.partId().equals(partId)).toList())
                audit(changes,new AuditTarget("TB_CO_PART_SETL_DTL",partId+":"+month+":"+old.developerId(),"DEL",reason),detailFields(old),Map.of());
            for(DetailWrite row:details) audit(changes,new AuditTarget("TB_CO_PART_SETL_DTL",partId+":"+month+":"+row.developerId(),"NEW",reason),Map.of(),detailFields(row));
        }
        history.preflightSettlement(changes);
        changes.forEach(history::append);
        if((parent==null?mapper.insertParent(write):mapper.updateParent(write))!=1) throw new IllegalStateException("정산 부모를 저장하지 못했습니다.");
        if(cancelling||confirming) mapper.deleteDetails(new Identity(partId,month));
        for(DetailWrite row:details) if(mapper.insertDetail(row)!=1) throw new IllegalStateException("정산 상세를 저장하지 못했습니다.");
        invalidation.afterCommit("settlements");
        return mutation(true,reads.previewSettlement(partId,input.month()));
    }
    private DetailWrite detail(ParentWrite parent, Row row) {
        String projects=codec.projects(row.developerId(),row.projectNames());
        BigDecimal mm=number(row.mm(),"MM");range(mm,6,2,"MM",row.developerId());
        String[] values={row.unitPrice(),row.payAmount(),row.salesExpenseAmount(),row.retirementAmount(),row.costAmount(),row.profitAmount(),row.profitShareAmount()};
        String[] fields={"UPRC_AMT","PAY_AMT","SALES_EXPS_AMT","RETIRE_AMT","COST_AMT","DIFF_AMT","PRFT_AMT"};
        List<BigDecimal> amounts=new ArrayList<>();
        for(int i=0;i<values.length;i++) {var amount=number(values[i],fields[i]);range(amount,15,0,fields[i],row.developerId());amounts.add(amount);}
        String remark=row.remark();
        if(remark!=null&&remark.codePointCount(0,remark.length())>100) throw conflict("STATE_CONFLICT","비고가 100자를 초과합니다.");
        return new DetailWrite(parent.partId(),parent.month(),row.developerId(),row.employeeType(),projects,mm,amounts.get(0),amounts.get(1),amounts.get(2),amounts.get(3),amounts.get(4),amounts.get(5),amounts.get(6),remark,parent.actor(),parent.at());
    }
    private static void range(BigDecimal value,int precision,int scale,String field,String developer) {
        try {if(value.setScale(scale).precision()>precision) throw new ArithmeticException();}
        catch(ArithmeticException e) {throw new ApiException(409,"STATE_CONFLICT","정산 값의 저장 범위를 초과했습니다.",field,Map.of("developerId",developer==null?"":developer,"field",field));}
    }
    private static BigDecimal number(String value,String field) {
        if(value==null)throw new ApiException(409,"STATE_CONFLICT","필수 정산 금액이 미확인입니다.",field,null);
        return new BigDecimal(value);
    }
    private static Map<String,String> fields(String[] keys,Object... values) {
        Map<String,String> map=new LinkedHashMap<>();
        for(int i=0;i<keys.length;i++)map.put(keys[i],values[i]==null?null:values[i] instanceof BigDecimal n?n.toPlainString():values[i].toString());
        return map;
    }
    private static final String[] PARENT={"CORP_CARD_AMT","PART_PRFT_AMT","SETL_STAT_CD","CNFM_USER_ID","CNFM_DTTM","CNCL_RSN"};
    private static final String[] DETAIL={"EMP_TYPE_CD","PROJ_NM_LIST","MM","UPRC_AMT","PAY_AMT","SALES_EXPS_AMT","RETIRE_AMT","COST_AMT","DIFF_AMT","PRFT_AMT","RMRK"};
    private static Map<String,String> parentFields(Parent p) {return p==null?Map.of():fields(PARENT,p.cardAmount(),p.partProfitAmount(),p.status(),p.confirmedBy(),p.confirmedAt(),p.cancellationReason());}
    private static Map<String,String> parentFields(ParentWrite p) {return fields(PARENT,p.card(),p.profit(),p.status(),p.confirmedBy(),p.confirmedAt(),p.reason());}
    private static Map<String,String> detailFields(Detail p) {return fields(DETAIL,p.employeeType(),p.projectText(),p.mm(),p.unitPrice(),p.pay(),p.expense(),p.retirement(),p.cost(),p.profit(),p.share(),p.remark());}
    private static Map<String,String> detailFields(DetailWrite p) {return fields(DETAIL,p.employeeType(),p.projects(),p.mm(),p.unitPrice(),p.pay(),p.expense(),p.retirement(),p.cost(),p.profit(),p.share(),p.remark());}
    private record AuditTarget(String table,String key,String type,String reason) {}
    private static void audit(List<Change> output,AuditTarget target,Map<String,String> before,Map<String,String> after) {
        Set<String> fields=new LinkedHashSet<>(before.keySet());fields.addAll(after.keySet());
        for(String field:fields)if(!Objects.equals(before.get(field),after.get(field)))output.add(new Change(target.table(),target.key(),target.type(),field,before.get(field),after.get(field),target.reason()));
    }
    private static Mutation mutation(boolean changed,Result r) {
        boolean confirmed=!r.status().equals("WORK");
        return new Mutation(changed,r.partId(),r.month(),r.status(),r.cardAmount(),
            confirmed?r.summary().partProfitAmount():null,confirmed?r.confirmedBy():null,
            confirmed?r.confirmedAt():null,r.lastCancellationReason(),r.revision(),r.issues());
    }
    private static ApiException conflict(String code,String message) {return new ApiException(409,code,message);}
}
