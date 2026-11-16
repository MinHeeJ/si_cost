package com.sicost.businessusersops;

import com.sicost.businessusers.BusinessUserDtos.Result;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.businessusersops.UserAdministrationDtos.*;

@Service
public class UserAdministrationService {
    private final UserAdministrationRepository repository;
    private final WriteCoordinator writes;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public UserAdministrationService(UserAdministrationRepository repository,WriteCoordinator writes,CurrentUserProvider users,
                                     ChangeHistoryWriter history,Clock clock,PlatformTransactionManager manager) {
        this.repository=repository;this.writes=writes;this.users=users;this.history=history;this.clock=clock;
        transactions=new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Scopes scopes(String id) {
        id=InputValidator.text(id,30,"id");users.currentUser();
        var user=repository.detail(id);
        return new Scopes(id,repository.parts(id,month()),repository.projects(id),user.revision());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Activities activities(ActivityQuery query) {
        users.currentUser();repository.required(query.userId());return repository.activities(query);
    }
    public Result change(String id,String expected,String action) {
        String userId=InputValidator.text(id,30,"id");
        InputValidator.text(expected,200,"expectedRevision");
        if(!Set.of("STOP","RESUME","UNLOCK").contains(action))throw InputValidator.invalid("action");
        String actor=users.userId();
        return command(userId,expected,action,actor);
    }
    public StatusResults bulk(StatusRequest request) {
        String actor=users.userId();
        List<StatusResult> results=new ArrayList<>();
        for(var item:request.items()) {
            try {
                var result=command(item.userId(),item.expectedRevision(),request.action(),actor);
                results.add(new StatusResult(item.userId(),result.changed()?"APPLIED":"UNCHANGED",
                    result.changed()?"APPLIED":"UNCHANGED",result.message(),result.changed(),result.user().revision()));
            } catch(ApiException e) {
                results.add(new StatusResult(item.userId(),"REJECTED",e.code(),e.getMessage(),false,null));
            } catch(RuntimeException e) {
                results.add(new StatusResult(item.userId(),"REJECTED","INTERNAL_ERROR","상태와 변경 이력을 저장하지 못했습니다.",false,null));
            }
        }
        int applied=(int)results.stream().filter(StatusResult::changed).count();
        int unchanged=(int)results.stream().filter(r->r.outcome().equals("UNCHANGED")).count();
        return new StatusResults(applied,unchanged,results.size()-applied-unchanged,List.copyOf(results));
    }
    private Result command(String id,String expected,String action,String actor) {
        return transactions.execute(transaction -> {
            var before=new ArrayList<String>();
            var result=writes.write(List.of("USER:"+id),expected,()->repository.snapshot(id),()-> {
                String status=repository.required(id).status();before.add(status);
                if(action.equals("RESUME") && status.equals("LOCK"))
                    throw new ApiException(409,"INVALID_STATE","잠금 해제를 사용하세요.");
                if(action.equals("UNLOCK") && !status.equals("LOCK"))
                    throw new ApiException(409,"INVALID_STATE","잠긴 사용자만 잠금 해제할 수 있습니다.");
                if(action.equals("STOP") && !status.equals("STOP")) {
                    var parts=repository.leaders(id,month());
                    if(!parts.isEmpty())throw new ApiException(409,"USER_IS_PART_LEADER",
                        String.join(", ",parts)+": 영업 파트에서 먼저 파트장을 변경해 주세요.",null,Map.of("partNames",parts));
                    if(repository.lastAdministrator(id))throw new ApiException(409,"LAST_ACTIVE_ADMIN","마지막 사용 가능한 관리자는 중지할 수 없습니다.");
                }
            },()->!before.get(0).equals(action.equals("STOP")?"STOP":"USE"),()-> {
                String next=action.equals("STOP")?"STOP":"USE";
                repository.change(new StateWrite(id,next,action.equals("UNLOCK"),actor,
                    LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)));
                history.append(new ChangeHistoryWriter.Change("TB_SY_USER",id,action.equals("STOP")?"STOP":action.equals("RESUME")?"RESUME":"UPD",
                    "USER_STAT_CD",before.get(0),next,null));
            });
            return new Result(result.changed(),result.changed()?"저장했습니다.":"바뀐 내용이 없습니다.",repository.detail(id));
        });
    }
    private String month() {return YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");}
}
