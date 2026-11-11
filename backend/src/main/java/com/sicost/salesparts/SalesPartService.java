package com.sicost.salesparts;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salesparts.SalesPartDtos.*;

@Service
public class SalesPartService {
    private final SalesPartRepository repository;
    private final RevisionService revisions;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public SalesPartService(SalesPartRepository repository,RevisionService revisions,ChangeHistoryWriter history,
                            CurrentUserProvider users,CommitInvalidation invalidation,Clock clock) {
        this.repository=repository;this.revisions=revisions;this.history=history;
        this.users=users;this.invalidation=invalidation;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(Query query) {
        users.currentUser();String month=current();
        List<Part> parts=repository.list(query).stream().map(row->view(row,repository.standards(row.id(),false),month)).sorted(comparator(query)).toList();
        long offset=(long)(query.page()-1)*query.size();
        return new Page(parts.stream().skip(offset).limit(query.size()).toList(),parts.size(),query.page(),query.size());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(String id) {
        users.currentUser();Stored row=requirePart(id,false);
        return detail(row,repository.standards(row.id(),false));
    }
    @Transactional
    public Result create(Create input) {
        String actor=users.userId();
        repository.lockUser(input.leaderUserId());
        repository.lockName(input.name());
        repository.lockClose();
        if(!repository.eligible(input.leaderUserId()))
            throw new ApiException(400,"VALIDATION_ERROR","사용 중인 영업 파트장 자격의 사용자를 선택해 주세요.","leaderUserId",null);
        String minimum=minimum(true);
        if(input.effectiveMonth().compareTo(minimum)<0) throw closed();
        if(repository.nameExists(input.name())) throw duplicate();
        String id=repository.nextId();
        Registration registration=new Registration(id,input,DatabaseMonthCodec.toDatabase(input.effectiveMonth()),actor,LocalDateTime.now(clock));
        try { requireOne(repository.insertPart(registration)); }
        catch(DuplicateKeyException e) { throw duplicate(); }
        requireOne(repository.insertStandard(registration));
        history.append(new ChangeHistoryWriter.Change("TB_BS_PART",id,"NEW","등록",null,id+" · "+input.name(),null));
        history.append(new ChangeHistoryWriter.Change("TB_BS_PART_STD",id+":"+registration.month(),"NEW","등록",null,id+":"+registration.month(),null));
        invalidation.afterCommit("sales-parts");
        return new Result(true,"등록했습니다.",detail(requirePart(id,false),repository.standards(id,false)));
    }
    @Transactional
    public Result patch(String id,Patch input) {
        String actor=users.userId();Stored row=requirePart(id,true);
        List<StoredStandard> standards=repository.standards(row.id(),true);
        revisions.requireExpected(input.expectedRevision(),revision(row,standards));
        String before=input.name()!=null?row.name():row.useYn();String after=input.name()!=null?input.name():input.useYn();
        if(Objects.equals(before,after))return new Result(false,"바뀐 내용이 없습니다.",detail(row,standards));
        if(input.name()!=null) {
            repository.lockName(input.name());
            if(repository.nameExists(input.name()))throw duplicate();
        } else if(input.useYn().equals("N")) {
            Counts refs=repository.counts(id,current(),true);
            if(refs.personCount()>0 || refs.projectCount()>0)
                throw new ApiException(409,"REFERENCE_IN_USE","소속 인력 또는 진행·예정 프로젝트가 있어 중지할 수 없습니다.","useYn",
                        Map.of("references",List.of(Map.of("type","person","count",refs.personCount()),Map.of("type","project","count",refs.projectCount()))));
        }
        try { requireOne(repository.update(new Mutation(id,input.name(),input.useYn(),actor,LocalDateTime.now(clock)))); }
        catch(DuplicateKeyException e) { throw duplicate(); }
        String type=input.name()!=null?"UPD":input.useYn().equals("N")?"STOP":"RESUME";
        history.append(new ChangeHistoryWriter.Change("TB_BS_PART",id,type,input.name()!=null?"PART_NM":"USE_YN",before,after,null));
        invalidation.afterCommit("sales-parts");
        return new Result(true,"저장했습니다.",detail(requirePart(id,false),repository.standards(id,false)));
    }
    private Stored requirePart(String id,boolean lock) {
        InputValidator.text(id,30,"id");Stored row=repository.part(id,lock);
        if(row==null)throw new ApiException(404,"NOT_FOUND","영업 파트를 찾을 수 없습니다.");return row;
    }
    private String current() { return YearMonth.now(clock).toString(); }
    private String revision(Stored part,List<StoredStandard> rows) {
        return revisions.revision(new Snapshot(part,rows.stream().sorted(Comparator.comparing(StoredStandard::month)).toList()));
    }
    private String minimum(boolean saving) {
        String base=DatabaseMonthCodec.toApi(repository.cashMonth());String close=repository.lastClose();
        if(close==null)return base;
        if(close.equals("999912")) {
            if(saving)throw closed();
            throw new ApiException(400,"VALIDATION_ERROR","지원 가능한 미마감 적용월이 없습니다.","effectiveMonth",null);
        }
        String next=YearMonth.parse(DatabaseMonthCodec.toApi(close)).plusMonths(1).toString();
        return next.compareTo(base)>0?next:base;
    }
    private Detail detail(Stored row,List<StoredStandard> standards) {
        String month=current();Part part=view(row,standards,month);
        return new Detail(part,standards.stream().sorted(Comparator.comparing(StoredStandard::month).reversed()).map(s->standard(s,month)).toList(),minimum(false),part.revision());
    }
    private Part view(Stored row,List<StoredStandard> standards,String month) {
        String db=DatabaseMonthCodec.toDatabase(month);
        StoredStandard active=standards.stream().filter(s->s.month().compareTo(db)<=0 && (s.endMonth()==null || s.endMonth().compareTo(db)>=0))
                .max(Comparator.comparing(StoredStandard::month)).orElse(null);
        StoredStandard future=standards.stream().filter(s->s.month().compareTo(db)>0).min(Comparator.comparing(StoredStandard::month)).orElse(null);
        Counts counts=repository.counts(row.id(),month,false);
        return new Part(row.id(),row.name(),row.useYn(),month,active==null?null:standard(active,month),future==null?null:standard(future,month),
                counts.personCount(),counts.projectCount(),revision(row,standards));
    }
    private Standard standard(StoredStandard row,String month) {
        String db=DatabaseMonthCodec.toDatabase(month);
        String status=row.month().compareTo(db)>0?"FUTURE":row.endMonth()!=null && row.endMonth().compareTo(db)<0?"PAST":"CURRENT";
        return new Standard(DatabaseMonthCodec.toApi(row.month()),row.endMonth()==null?null:DatabaseMonthCodec.toApi(row.endMonth()),
                row.leaderUserId(),repository.leaderName(row.leaderUserId()),MasterDataValidator.decimalString(row.profitRate()),
                MasterDataValidator.decimalString(row.salesExpenseAmount()),status);
    }
    private Comparator<Part> comparator(Query query) {
        Comparator<Part> result=switch(query.sort()) {
            case "name" -> compare(Part::name,query);
            case "useYn" -> compare(Part::useYn,query);
            case "personCount" -> compare(Part::personCount,query);
            case "projectCount" -> compare(Part::projectCount,query);
            case "leaderName" -> compare(p->p.currentStandard()==null?null:p.currentStandard().leaderName(),query);
            case "effectiveMonth" -> compare(p->p.currentStandard()==null?null:p.currentStandard().effectiveMonth(),query);
            case "profitRate" -> compare(p->p.currentStandard()==null?null:new BigDecimal(p.currentStandard().profitRate()),query);
            case "salesExpenseAmount" -> compare(p->p.currentStandard()==null?null:new BigDecimal(p.currentStandard().salesExpenseAmount()),query);
            default -> throw InputValidator.invalid("sort");
        };
        return result.thenComparing(Part::id);
    }
    private <T extends Comparable<? super T>> Comparator<Part> compare(java.util.function.Function<Part,T> field,Query query) {
        Comparator<T> order=query.direction().equals("DESC")?Comparator.reverseOrder():Comparator.naturalOrder();
        return Comparator.comparing(field,Comparator.nullsLast(order));
    }
    private void requireOne(int count) { if(count!=1)throw new IllegalStateException("영업 파트를 저장하지 못했습니다."); }
    private ApiException duplicate() { return new ApiException(409,"DUPLICATE_PART_NAME","이미 등록된 파트명입니다.","name",null); }
    private ApiException closed() { return new ApiException(409,"CLOSED_MONTH","마감된 기간입니다. 최소 적용월 이후를 선택해 주세요.","effectiveMonth",null); }
}
