package com.sicost.rolesops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.rolesops.RoleStopDtos.*;

@Service
public class RoleStopService {
    private static final Set<String> DEFAULT_ROLES=Set.of("ADMIN","EXEC","FIN","SALES_LDR","PM");
    private final RoleStopRepository repository;
    private final WriteCoordinator writes;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final AccessLogWriter access;
    private final Clock clock;
    public RoleStopService(RoleStopRepository repository,WriteCoordinator writes,CurrentUserProvider users,
                           ChangeHistoryWriter history,AccessLogWriter access,Clock clock) {
        this.repository=repository;this.writes=writes;this.users=users;this.history=history;this.access=access;this.clock=clock;
    }
    @Transactional
    public Result stop(String requestedId,Request input,HttpServletRequest request) {
        String actor=users.userId();String id=InputValidator.text(requestedId,20,"id");
        var result=writes.write(List.of(SettlementSourceLocks.ROLE_RESOURCE,SettlementSourceLocks.MENU_RESOURCE),
            input.expectedRevision(),()->repository.storedSnapshot(id),()->{
                var row=repository.snapshot(id).role();
                if(DEFAULT_ROLES.contains(id) || "Y".equals(row.get("systemRole")))
                    throw new ApiException(409,"SYSTEM_ROLE_PROTECTED","시스템 역할은 사용 중지할 수 없습니다.");
                long count=repository.connectedUsers(id);
                if(count>0)throw new ApiException(409,"ROLE_IN_USE",count+"명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요.",null,Map.of("connectedUserCount",count));
            },()->"Y".equals(repository.snapshot(id).role().get("use")),()->{
                repository.stop(new Mutation(id,actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)));
                history.append(new ChangeHistoryWriter.Change("TB_SY_ROLE",id,"STOP","USE_YN","Y","N",null));
                access.append(new AccessLogWriter.Event("AUTH_CHG","UI-24",id,null,null),request);
            });
        return new Result(repository.detail(id,result.revision()),result.revision(),result.changed());
    }
}
