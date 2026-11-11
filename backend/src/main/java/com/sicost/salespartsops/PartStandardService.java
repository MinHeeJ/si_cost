package com.sicost.salespartsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salespartsops.PartStandardDtos.*;

@Service
public class PartStandardService {
    private final PartStandardRepository repository;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public PartStandardService(PartStandardRepository repository, RevisionService revisions, ChangeHistoryWriter history,
                               CurrentUserProvider users, CommitInvalidation invalidation, Clock clock) {
        this.repository=repository;this.revisions=revisions;this.history=history;this.users=users;this.invalidation=invalidation;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Options options() { users.currentUser();return new Options(repository.rows().options()); }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Defaults defaults(String requested) {
        users.currentUser();
        YearMonth min=minimum(false), month=requested==null?YearMonth.now(clock):MasterDataValidator.month(requested.trim(),"effectiveMonth");
        if(requested==null && month.isBefore(min)) month=min;
        List<DefaultError> errors=new ArrayList<>();
        String rate=defaultValue("PART_DSTRB_RT","profitRate",month,errors);
        String expense=defaultValue("PART_SALES_EXPS","salesExpenseAmount",month,errors);
        return new Defaults(month.toString(),min.toString(),rate,expense,errors);
    }
    private String defaultValue(String source,String field,YearMonth month,List<DefaultError> errors) {
        BigDecimal value=repository.rows().operatingValue(source,db(month));
        String reason=null;
        if(value==null) reason="선택월에 적용할 운영기준이 없습니다. 직접 입력해 주세요.";
        else {
            BigDecimal normalized=value.stripTrailingZeros();
            int scale=field.equals("profitRate")?2:0;
            BigDecimal maximum=new BigDecimal(field.equals("profitRate")?"100":"999999999999999");
            if(value.signum()<0 || value.compareTo(maximum)>0) reason="운영기준이 허용 범위를 벗어났습니다. 직접 입력해 주세요.";
            else if(normalized.scale()>scale) reason="운영기준의 소수 자릿수가 올바르지 않습니다. 직접 입력해 주세요.";
        }
        if(reason!=null) {errors.add(new DefaultError(source,field,reason));return null;}
        return MasterDataValidator.decimalString(value);
    }
    private YearMonth minimum(boolean saving) {
        BigDecimal cash=repository.rows().operatingValue("CASH_BASE_YM","999912");
        if(cash==null) throw new IllegalStateException("자금 기준월이 없습니다.");
        String base;
        try {base=String.format(Locale.ROOT,"%06d",cash.toBigIntegerExact());}
        catch(ArithmeticException e) {throw new IllegalStateException("자금 기준월이 올바르지 않습니다.");}
        YearMonth min=YearMonth.parse(DatabaseMonthCodec.toApi(base));
        String close=repository.rows().lastClose();
        if("999912".equals(close)) {
            if(saving) throw closed();
            throw new ApiException(400,"VALIDATION_ERROR","지원 범위 내에 새로 적용할 수 있는 미마감 월이 없습니다.","effectiveMonth",null);
        }
        if(close!=null) {
            YearMonth next=YearMonth.parse(DatabaseMonthCodec.toApi(close)).plusMonths(1);
            if(next.isAfter(min))min=next;
        }
        return min;
    }
    @Transactional
    public Result save(String id,JsonNode body) {
        PartStandardInput.allow(body);
        id=InputValidator.text(id,30,"id");
        String expected=PartStandardInput.text(body,"expectedRevision",200);
        JsonNode selected=body.get("leaderUserId");
        String leader=selected!=null && selected.isTextual()?selected.textValue().trim():null;
        PartStandardMapper mapper=repository.rows();
        if(leader!=null && !leader.isEmpty() && leader.length()<=30) mapper.lockUser(leader);
        Header part=mapper.header(id,true);
        if(part==null || "Y".equals(part.hqYn())) throw new ApiException(404,"NOT_FOUND","일반 영업 파트를 찾을 수 없습니다.");
        List<Stored> timeline=mapper.timeline(id,true);
        revisions.requireExpected(expected,revision(part,timeline));
        Save input=PartStandardInput.parse(body);
        if(!mapper.eligible(input.leaderUserId())) throw new ApiException(400,"VALIDATION_ERROR","사용 중인 파트장 자격 사용자를 선택해 주세요.","leaderUserId",null);
        repository.lockCashAndClose();
        YearMonth min=minimum(true), month=YearMonth.parse(input.effectiveMonth());
        if(month.isBefore(min)) throw closed();
        String start=db(month);
        if(timeline.isEmpty() || start.compareTo(timeline.get(0).month())<0)
            throw new ApiException(400,"VALIDATION_ERROR","최초 기준 시작월 이전에는 기준을 추가할 수 없습니다.","effectiveMonth",null);
        if(timeline.stream().anyMatch(row->row.month().equals(start)))
            throw new ApiException(409,"STANDARD_MONTH_EXISTS","이미 등록된 시작월입니다. 다른 월을 선택해 주세요.","effectiveMonth",null);
        Stored prior=timeline.stream().filter(row->row.month().compareTo(start)<0).max(Comparator.comparing(Stored::month)).orElseThrow();
        boolean financial=prior.profitRate().compareTo(input.profitRate())!=0 || prior.salesExpenseAmount().compareTo(input.salesExpenseAmount())!=0;
        if(!financial && prior.leaderUserId().equals(input.leaderUserId())) return new Result(false,"지금 기준과 같습니다.",detail(part,timeline,min));
        if(financial) {
            repository.lockSettlement();
            List<String> blocks=mapper.blockedMonths(id,start);
            if(!blocks.isEmpty()) {
                String last=blocks.get(blocks.size()-1);
                Map<String,Object> details=new LinkedHashMap<>();
                details.put("partName",part.name());details.put("blockedMonths",blocks.stream().map(DatabaseMonthCodec::toApi).toList());
                details.put("minMonth",last.equals("999912")?null:YearMonth.parse(DatabaseMonthCodec.toApi(last)).plusMonths(1).toString());
                throw new ApiException(409,"PART_SETTLEMENT_CONFIRMED","확정 또는 자동 정산된 미마감 월이 있어 율·관리비를 변경할 수 없습니다.","effectiveMonth",details);
            }
        }
        Stored next=timeline.stream().filter(row->row.month().compareTo(start)>0).min(Comparator.comparing(Stored::month)).orElse(null);
        String end=next==null?null:db(YearMonth.parse(DatabaseMonthCodec.toApi(next.month())).minusMonths(1));
        String priorEnd=db(month.minusMonths(1));
        String actor=users.userId();LocalDateTime now=LocalDateTime.now(clock);
        if(mapper.endPrevious(new Mutation(id,prior.month(),priorEnd,input,actor,now))!=1) throw new IllegalStateException("이전 기준을 변경하지 못했습니다.");
        if(mapper.insert(new Mutation(id,start,end,input,actor,now))!=1) throw new IllegalStateException("새 기준을 저장하지 못했습니다.");
        history.append(new ChangeHistoryWriter.Change("TB_BS_PART_STD",id+":"+prior.month(),"UPD","APLY_END_YM",prior.endMonth(),priorEnd,null));
        history.append(new ChangeHistoryWriter.Change("TB_BS_PART_STD",id+":"+start,"NEW","기준",null,
            input.effectiveMonth()+" / "+input.leaderUserId()+" / "+MasterDataValidator.decimalString(input.profitRate())+" / "+MasterDataValidator.decimalString(input.salesExpenseAmount()),null));
        invalidation.afterCommit("sales-parts");
        return new Result(true,"저장했습니다.",detail(part,mapper.timeline(id,false),min));
    }
    public String revision(Header part,List<Stored> timeline) {return revisions.revision(new Snapshot(part,timeline));}
    private Detail detail(Header header,List<Stored> rows,YearMonth min) {
        YearMonth now=YearMonth.now(clock);String current=db(now);
        Stored active=rows.stream().filter(row->row.month().compareTo(current)<=0 && (row.endMonth()==null || row.endMonth().compareTo(current)>=0))
            .max(Comparator.comparing(Stored::month)).orElse(null);
        Stored future=rows.stream().filter(row->row.month().compareTo(current)>0).min(Comparator.comparing(Stored::month)).orElse(null);
        List<Standard> versions=rows.stream().sorted(Comparator.comparing(Stored::month).reversed()).map(row->view(row,current,active)).toList();
        String revision=revision(header,rows);
        return new Detail(new Part(header.id(),header.name(),header.useYn(),now.toString(),active==null?null:view(active,current,active),
            future==null?null:view(future,current,active),repository.rows().personCount(header.id()),repository.rows().projectCount(header.id(),current),revision),versions,min.toString(),revision);
    }
    private Standard view(Stored row,String current,Stored active) {
        return new Standard(DatabaseMonthCodec.toApi(row.month()),row.endMonth()==null?null:DatabaseMonthCodec.toApi(row.endMonth()),
            row.leaderUserId(),repository.rows().leaderName(row.leaderUserId()),MasterDataValidator.decimalString(row.profitRate()),
            MasterDataValidator.decimalString(row.salesExpenseAmount()),row.month().compareTo(current)>0?"FUTURE":row.equals(active)?"CURRENT":"PAST");
    }
    private static String db(YearMonth month) {return month.toString().replace("-","");}
    private static ApiException closed() {return new ApiException(409,"CLOSED_MONTH","최소 적용월 이전이거나 지원 범위 내 미마감 월이 없습니다.","effectiveMonth",null);}
}
