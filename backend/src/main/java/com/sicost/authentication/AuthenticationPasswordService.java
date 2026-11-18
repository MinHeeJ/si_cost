package com.sicost.authentication;

import com.sicost.common.api.ApiException;
import com.sicost.common.TimeConfiguration;
import com.sicost.permission.PermissionService;
import com.sicost.receivablesops.DunningDeliveryMail;
import java.security.SecureRandom;
import java.time.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthenticationPasswordService {
    public record Delivery(boolean passwordIssued,String mailStatus,boolean unlockRequired,String maskedEmail,String message) {}
    private final AuthenticationMapper mapper;
    private final AuthenticationMailService mail;
    private final PermissionService permissions;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SecureRandom random=new SecureRandom();
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
    private AuthenticationIssuanceCoordinator issuance=new AuthenticationIssuanceCoordinator();
    @org.springframework.beans.factory.annotation.Autowired
    public void setIssuance(AuthenticationIssuanceCoordinator issuance) {this.issuance=issuance;}
    public AuthenticationPasswordService(AuthenticationMapper mapper,AuthenticationMailService mail,PermissionService permissions,Clock clock,PlatformTransactionManager manager) {
        this.mapper=mapper;this.mail=mail;this.permissions=permissions;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Delivery reset(String id,boolean confirmed) {
        permissions.require("UI-23","UPD");
        if(!confirmed)throw AuthenticationPasswordPolicy.invalid("confirmed","등록 이메일을 확인하고 초기화를 승인해 주세요.");
        return issue(id,null,permissions.userId());
    }
    public Delivery initial(String id,String actor) {return issue(id,null,actor);}
    public void recover(String inputId,String email) {
        String id=AuthenticationPasswordPolicy.userId(inputId);
        if(!DunningDeliveryMail.address(email))throw AuthenticationPasswordPolicy.invalid("email","등록 이메일을 입력해 주세요.");
        mail.requireConfigured();
        issue(id,email,"ANONYMOUS");
    }
    private Delivery issue(String id,String expectedEmail,String actor) {
        return issuance.run(id,()->issueLocked(id,expectedEmail,actor));
    }
    private Delivery issueLocked(String id,String expectedEmail,String actor) {
        mail.requireConfigured();
        String secret=AuthenticationPasswordPolicy.temporary(random);
        var account=transaction.execute(status -> {
            var row=mapper.lock(id);
            if(expectedEmail!=null && (row==null || !expectedEmail.equals(row.email()) || "STOP".equals(row.status())))return null;
            if(row==null)throw new ApiException(404,"NOT_FOUND","사용자를 찾을 수 없습니다.");
            if(!java.util.Set.of("USE","LOCK").contains(row.status()))throw new ApiException(409,"USER_STOPPED","사용 중지 계정은 초기화할 수 없습니다.");
            if(!DunningDeliveryMail.address(row.email()))throw new ApiException(409,"EMAIL_INVALID","등록 이메일을 확인해 주세요.");
            mapper.password(id,encoder.encode(secret),"Y",actor,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL));
            return row;
        });
        if(account==null)return null;
        String delivery=mail.send(account.email(),"SI-COST 임시 비밀번호","임시 비밀번호: "+secret+"\n최신 발급값만 유효합니다. 로그인 후 새 비밀번호로 변경해 주세요.");
        return new Delivery(true,delivery,"LOCK".equals(account.status()),AuthenticationService.maskedEmail(account.email()),
            "ACCEPTED".equals(delivery)?"임시 비밀번호를 발송했습니다.":"비밀번호는 발급됐지만 메일 발송에 실패했거나 접수 여부가 불명입니다. 새 비밀번호 다시 발급을 이용해 주세요.");
    }
}
