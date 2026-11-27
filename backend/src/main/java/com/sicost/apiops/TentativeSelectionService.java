package com.sicost.apiops;

import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.apiops.TentativeSelectionDtos.*;

@Service
public class TentativeSelectionService {
    private final TentativeSelectionMapper mapper;
    private final AssignmentCandidateRepository revisions;
    private final SettlementSourceLocks locks;
    private final AssignmentMonthlyService monthly;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    public TentativeSelectionService(TentativeSelectionMapper mapper,AssignmentCandidateRepository revisions,SettlementSourceLocks locks,AssignmentMonthlyService monthly,ChangeHistoryWriter history,CurrentUserProvider users,CommitInvalidation invalidation,Clock clock){this.mapper=mapper;this.revisions=revisions;this.locks=locks;this.monthly=monthly;this.history=history;this.users=users;this.invalidation=invalidation;this.clock=clock;}
    @Transactional
    public Result create(TentativeSelectionInput.Create input){
        String actor=users.currentUser().userId();
        var keys=new TreeSet<String>();keys.add("DEV:"+input.developerId());keys.add("OPP:"+input.opportunityId());
        keys.addAll(List.of("operating-standards:CASH_BASE_YM","code-group:JOB","code-group:GRADE","PAY:"+AssignmentMonthlyService.db(input.startMonth().toString())));
        locks.lock(keys);mapper.lockOpportunity(input.opportunityId());mapper.lockDeveloper(input.developerId());
        var active=mapper.active(input.developerId()).stream().filter(t->t.opportunityId().equals(input.opportunityId())).toList();
        if(active.size()>1)throw new ApiException(409,"INVALID_STATE","같은 기회의 활성 가배정 충돌을 확인해 주세요.");
        revisions.requireRevision(input.expectedRevision(),input.opportunityId());
        Need need=mapper.need(input);if(need==null)throw new ApiException(404,"NOT_FOUND","필요 인력 행이 없습니다.");
        if(!"ING".equals(need.status()))throw new ApiException(409,"INVALID_STATE","진행 중인 기회만 가배정할 수 있습니다.");
        if(!mapper.eligible(input.developerId()))throw StaffingQueryInput.invalid("developerId","VALIDATION_ERROR");
        YearMonth start=YearMonth.parse(AssignmentMonthlyService.api(need.startMonth())),end=start.plusMonths(need.months()-1);
        if(end.getYear()>9999||input.startMonth().isBefore(monthly.cashBase())||input.startMonth().isBefore(start)||input.startMonth().isAfter(end)||"UI-29".equals(input.sourceScreenId())&&!input.startMonth().equals(start))throw StaffingQueryInput.invalid("startMonth","INVALID_PERIOD");
        if(!active.isEmpty()&&active.get(0).opportunityId().equals(input.opportunityId())&&active.get(0).needSn()==input.needSn())return new Result(false,revisions.revision(input.opportunityId()),List.of(),input.opportunityId(),active.get(0).id());
        if(mapper.count(input)>=need.count())throw new ApiException(409,"INVALID_STATE","필요 인원수를 초과할 수 없습니다.","needSn",null);
        List<String> warnings=new ArrayList<>();
        if(!active.isEmpty())warnings.add("같은 기회 안에서 기존 가배정을 선택한 자리로 이동했습니다.");
        BigDecimal confirmed=BigDecimal.ZERO;
        for(String id:mapper.assignmentIds(input.developerId())){
            var source=monthly.required(id);
            for(var m:monthly.preview(source,monthly.mapper().histories(id),"ING","MO"))if(input.startMonth().toString().equals(m.month()))confirmed=confirmed.add(new BigDecimal(m.monthlyMm()));
        }
        if(confirmed.compareTo(BigDecimal.ONE)>=0)warnings.add("시작월 확정 투입 MM이 1 이상입니다. 가배정은 실제 투입을 변경하지 않습니다.");
        String id=mapper.nextId();if(id==null||!id.matches("[1-9][0-9]{0,9}")||new BigInteger(id).compareTo(new BigInteger("9999999999"))>0)throw new ApiException(409,"ID_EXHAUSTED","가배정 ID를 모두 사용했습니다.");
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        for(Active old:active){
            Write release=new Write(old.id(),old.opportunityId(),old.needSn(),old.developerId(),old.startMonth(),input.sourceScreenId(),actor,now);
            one(mapper.release(release));audit(old.id(),"UPD","TENT_STAT_CD","ACT","REL",id);audit(old.id(),"UPD","PROC_DTTM",null,now.toString(),id);
        }
        Write write=new Write(id,input.opportunityId(),input.needSn(),input.developerId(),AssignmentMonthlyService.db(input.startMonth().toString()),input.sourceScreenId(),actor,now);
        one(mapper.insert(write));one(mapper.touch(write));
        audit(id,"NEW","OPP_ID",null,input.opportunityId(),id);audit(id,"NEW","NEED_SN",null,String.valueOf(input.needSn()),id);audit(id,"NEW","DEV_ID",null,input.developerId(),id);audit(id,"NEW","BGN_YM",null,write.startMonth(),id);audit(id,"NEW","TENT_STAT_CD",null,"ACT",id);audit(id,"NEW","SRC_SCRN_ID",null,input.sourceScreenId(),id);
        invalidation.afterCommit("OPP:"+input.opportunityId());
        for(String key:List.of("sales-opportunities","assignment-candidates","workforce-forecasts","workforce-operations","candidates"))invalidation.afterCommit(key);
        return new Result(true,revisions.revision(input.opportunityId()),List.copyOf(warnings),input.opportunityId(),id);
    }
    private void audit(String id,String type,String field,String before,String after,String origin){history.append(new ChangeHistoryWriter.Change("TB_SL_TENT_ASGN",id,type,field,before,after,"가배정 이동·선택; sourceTentId="+origin));}
    private void one(int count){if(count!=1)throw new IllegalStateException("가배정을 저장하지 못했습니다.");}
}
