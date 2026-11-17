package com.sicost.authentication;

import com.sicost.common.CostManagementApplication;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryEntry;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.receivablesops.DunningDeliveryMail;
import java.time.*;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit operator entry point only. Not an application runner and never an HTTP endpoint. */
public final class AuthenticationAdminCli {
    private AuthenticationAdminCli() {}
    public static void main(String[] args) {
        if(args.length<2 || !java.util.Set.of("reset","unlock").contains(args[0]) || (args[0].equals("reset") && args.length!=3) || (args[0].equals("unlock") && args.length!=2)) {
            System.err.println("Usage: AuthenticationAdminCli reset USER_ID EMAIL | unlock USER_ID (hidden input or AUTH_INITIAL_PASSWORD)");System.exit(2);return;
        }
        char[] secret=null;
        try {
            if(args[0].equals("reset")) {
                if(System.console()!=null)secret=System.console().readPassword("Initial password: ");
                else {String value=System.getenv("AUTH_INITIAL_PASSWORD");if(value!=null)secret=value.toCharArray();}
                if(secret==null)throw new IllegalArgumentException();
                AuthenticationPasswordPolicy.newPassword(new String(secret),new String(secret));
                if(!DunningDeliveryMail.address(args[2]))throw new IllegalArgumentException();
            }
            try(var context=new SpringApplicationBuilder(CostManagementApplication.class).web(WebApplicationType.NONE)
                    .properties("spring.main.banner-mode=off","logging.level.root=ERROR","spring.task.scheduling.enabled=false").run()) {
                var mapper=context.getBean(AuthenticationMapper.class);var history=context.getBean(ChangeHistoryMapper.class);
                var transaction=new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                final String password=secret==null?null:new String(secret);
                transaction.executeWithoutResult(status -> {
                    String id=AuthenticationPasswordPolicy.userId(args[1]);var account=mapper.lock(id);
                    if(account==null || !mapper.administrator(id) || !java.util.Set.of("USE","LOCK").contains(account.status()))throw new ApiException(409,"OPERATOR_TARGET_INVALID","기존 ADMIN USE/LOCK 계정만 처리할 수 있습니다.");
                    var now=LocalDateTime.ofInstant(context.getBean(Clock.class).instant(),TimeConfiguration.SEOUL);
                    if(args[0].equals("unlock")) {
                        if(!"LOCK".equals(account.status()))throw new IllegalArgumentException();
                        mapper.failure(id,0,"USE","SYSTEM",now);
                    } else {
                        mapper.password(id,new BCryptPasswordEncoder(10).encode(password),"Y","SYSTEM",now);
                        mapper.operatorEmail(id,args[2],now);
                    }
                    if(history.insert(new ChangeHistoryEntry("TB_SY_USER",id,"UPD","운영자 복구",null,args[0].equals("unlock")?"잠금 해제":"인증 초기화","명시 운영자 명령","SYSTEM",now))!=1)throw new IllegalStateException();
                });
            }
            System.out.println("Operator action completed. Authenticate through the normal login flow.");
        } catch(Exception error) {System.err.println("Operator action failed. Check target status, ADMIN role, input policy and database availability.");System.exit(1);}
        finally {if(secret!=null)Arrays.fill(secret,'\0');}
    }
}
