package com.sicost.operatingstandards;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;
import static com.sicost.operatingstandards.OperatingStandardsInput.*;

@Service
public class OperatingStandardsService {
    private final OperatingStandardsRepository mapper;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public OperatingStandardsService(OperatingStandardsRepository mapper, RevisionService revisions, ChangeHistoryWriter history,
                                     CurrentUserProvider users, CommitInvalidation invalidation, Clock clock) {
        this.mapper=mapper; this.revisions=revisions; this.history=history; this.users=users; this.invalidation=invalidation; this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Items list(String month) {
        users.currentUser();
        return new Items(views(mapper.all(),month==null?YearMonth.now(clock).toString():PeriodValidator.month(month).toString()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public History history(String key) {
        users.currentUser(); InputValidator.text(key,30,"key");
        List<Stored> rows=mapper.rows(key,false); if(rows.isEmpty()) throw missing();
        String current=YearMonth.now(clock).toString().replace("-",""); Stored active=effective(rows,current);
        return new History(rows.stream().map(row->new Version(row.key(),value(row),row.unitNm(),DatabaseMonthCodec.toApi(row.month()),
            row.month().compareTo(current)>0?"예정":row==active?"현재":"지난",revisions.revision(row))).toList(),revisions.revision(rows));
    }
    @Transactional
    public Result save(Save input) {
        String actor=users.userId(); boolean cash=CASH.contains(input.changes().get(0).key());
        Set<String> keys=new TreeSet<>(CASH);
        input.changes().forEach(change->{keys.add(change.key());GROUPS.stream().filter(g->g.contains(change.key())).forEach(keys::addAll);});
        keys.forEach(mapper::lockKey);
        Map<String,List<Stored>> stored=new TreeMap<>(); keys.forEach(key->stored.put(key,mapper.rows(key,true)));
        for(Change change:input.changes()) {
            List<Stored> rows=stored.get(change.key());
            if(rows.isEmpty()) throw InputValidator.invalid(change.field()+"key");
            revisions.requireExpected(change.expectedRevision(),revisions.revision(rows));
        }
        List<Stored> cashMonth=stored.get("CASH_BASE_YM"),cashAmount=stored.get("CASH_BASE_AMT");
        if(cashMonth.size()!=1 || cashAmount.size()!=1) throw new IllegalStateException("자금 기준 데이터가 올바르지 않습니다.");
        String base=value(cashMonth.get(0));
        DatabaseMonthCodec.toApi(base);
        List<String> closed=mapper.closedMonths();
        String month=cash?null:DatabaseMonthCodec.toDatabase(input.effectiveMonth());
        if(cash) {if(closed.contains(base)) throw closed("changes");}
        else {
            String minimum=base;
            if(!closed.isEmpty()) {
                if(closed.get(closed.size()-1).equals("999912")) throw closed("effectiveMonth");
                String next=YearMonth.parse(DatabaseMonthCodec.toApi(closed.get(closed.size()-1))).plusMonths(1).toString().replace("-","");
                if(next.compareTo(minimum)>0)minimum=next;
            }
            if(month.compareTo(minimum)<0) throw closed("effectiveMonth");
            validateBoundaries(stored,input,month);
        }
        boolean changed=false; LocalDateTime now=LocalDateTime.now(clock);
        for(Change change:input.changes()) {
            List<Stored> rows=stored.get(change.key()); String target=cash?rows.get(0).month():month;
            Stored old=rows.stream().filter(row->row.month().equals(target)).findFirst().orElse(null);
            Stored prior=cash?rows.get(0):effective(rows,target);
            if(prior!=null && prior.value().compareTo(change.value())==0) continue;
            Mutation mutation=new Mutation(rows.get(0),target,change.value(),actor,now);
            int count=old==null?mapper.insert(mutation):mapper.update(mutation);
            if(count!=1) throw new IllegalStateException("기준값을 저장하지 못했습니다.");
            history.append(new ChangeHistoryWriter.Change("TB_SY_OPER_STD",change.key()+":"+target,old==null?"NEW":"UPD","STD_VAL",
                old==null?null:value(old),format(change.key(),change.value()),null));
            changed=true;
        }
        if(changed) {
            invalidation.afterCommit("operating-standards"); invalidation.afterCommit("parameters");
            if(cash)invalidation.afterCommit("period-context");
        }
        return new Result(changed,changed?"저장했습니다.":"바뀐 내용이 없습니다.",views(mapper.all(),YearMonth.now(clock).toString()));
    }
    private void validateBoundaries(Map<String,List<Stored>> stored,Save input,String month) {
        Map<String,BigDecimal> changes=new HashMap<>(); input.changes().forEach(c->changes.put(c.key(),c.value()));
        for(List<String> group:GROUPS) {
            if(Collections.disjoint(group,changes.keySet()))continue;
            String field=input.changes().stream().filter(change->group.contains(change.key())).findFirst().orElseThrow().field()+"value";
            Set<String> boundaries=new TreeSet<>();boundaries.add(month);
            group.forEach(key->stored.get(key).stream().map(Stored::month).filter(m->m.compareTo(month)>0).forEach(boundaries::add));
            for(String boundary:boundaries) {
                Map<String,BigDecimal> values=new HashMap<>();
                for(String key:group) {
                    Stored row=effective(stored.get(key),boundary);
                    BigDecimal amount=row==null?null:row.value();
                    if(changes.containsKey(key) && (row==null || row.month().compareTo(month)<=0)) amount=changes.get(key);
                    if(amount==null) throw InputValidator.invalid(field);
                    validateValue(key,amount,field);values.put(key,amount);
                }
                if(group.get(0).equals("DUN_D1")) {
                    if(values.get("DUN_D1").compareTo(BigDecimal.ONE)<0 || values.get("DUN_D1").compareTo(values.get("DUN_D2"))>=0
                        || values.get("DUN_D2").compareTo(values.get("DUN_D3"))>=0) throw InputValidator.invalid(field);
                }
                if(group.get(0).startsWith("AI_SCORE_") && values.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100"))!=0)
                    throw InputValidator.invalid(field);
            }
        }
    }

    private List<Standard> views(List<Stored> all,String month) {
        Map<String,List<Stored>> groups=new TreeMap<>();all.forEach(row->groups.computeIfAbsent(row.key(),key->new ArrayList<>()).add(row));
        return groups.entrySet().stream().map(entry->{
            List<Stored> rows=entry.getValue();Stored meta=rows.get(0),active=effective(rows,month.replace("-",""));
            return new Standard(meta.key(),meta.name(),active==null?null:value(active),meta.unitNm(),active==null?null:DatabaseMonthCodec.toApi(active.month()),meta.description(),revisions.revision(rows));
        }).toList();
    }
    static Stored effective(List<Stored> rows,String month) {
        return rows.stream().filter(row->row.month().compareTo(month)<=0).max(Comparator.comparing(Stored::month)).orElse(null);
    }
    static String value(Stored row) {return format(row.key(),row.value());}
    static String format(String key,BigDecimal value) {
        return key.equals("CASH_BASE_YM")?String.format(Locale.ROOT,"%06d",value.toBigIntegerExact()):value.stripTrailingZeros().toPlainString();
    }
    private ApiException missing() {return new ApiException(404,"NOT_FOUND","등록된 운영 기준이 없습니다.");}
    private ApiException closed(String field) {return new ApiException(409,"CLOSED_MONTH","마감된 기간의 기준은 변경할 수 없습니다.",field,null);}
}
