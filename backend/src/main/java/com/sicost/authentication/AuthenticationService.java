package com.sicost.authentication;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import jakarta.servlet.http.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.authentication.AuthenticationState.Step;

@Service
public class AuthenticationService {
    private final AuthenticationMapper mapper;
    private final AuthenticationMailService mail;
    private final AuthenticationAuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
    private final SecureRandom random=new SecureRandom();
    private AuthenticationIssuanceCoordinator issuance=new AuthenticationIssuanceCoordinator();
    @org.springframework.beans.factory.annotation.Autowired
    public void setIssuance(AuthenticationIssuanceCoordinator issuance) {this.issuance=issuance;}
    private final String dummy=encoder.encode(UUID.randomUUID().toString());
    private final HttpSessionSecurityContextRepository contexts=new HttpSessionSecurityContextRepository();
    public AuthenticationService(AuthenticationMapper mapper,AuthenticationMailService mail,AuthenticationAuditService audit,Clock clock,PlatformTransactionManager manager) {
        this.mapper=mapper;this.mail=mail;this.audit=audit;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    public AuthenticationState state(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        return session==null?null:(AuthenticationState)session.getAttribute(AuthenticationState.KEY);
    }
    public AuthenticationState validate(HttpServletRequest request,boolean complete) {
        AuthenticationState state=state(request);
        if(state==null)throw new ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        synchronized(state) {
            if(!clock.instant().isBefore(expires(state))) {invalidate(request);throw new ApiException(401,"SESSION_EXPIRED","세션이 만료되었습니다. 다시 로그인해 주세요.");}
            var account=mapper.find(state.userId);
            if(account==null || !"USE".equals(account.status()) || !Objects.equals(account.passwordHash(),state.passwordProof)) {
                invalidate(request);throw new ApiException(401,"AUTH_REQUIRED","인증 정보가 변경되었습니다. 다시 로그인해 주세요.");
            }
            boolean administrator=mapper.administrator(state.userId);
            if(!administrator && state.step==Step.MFA) {
                if(state.codeProof!=null)transaction.executeWithoutResult(status->mapper.discard(state.userId,state.codeProof));
                state.codeProof=null;
                if(!"Y".equals(account.temporary())) {
                    if(state.resumeAuthenticated)state.step=Step.AUTHENTICATED;
                    else {invalidate(request);throw new ApiException(401,"AUTH_REQUIRED","인증 단계가 변경되었습니다. 다시 로그인해 주세요.");}
                }
            }
            if(administrator && !state.mfaVerified) {if(state.step==Step.AUTHENTICATED)state.resumeAuthenticated=true;state.step=Step.MFA;}
            else if("Y".equals(account.temporary()))state.step=Step.CHANGE_PASSWORD;

            if(complete && state.step!=Step.AUTHENTICATED)throw incomplete(state.step);
            return state;
        }
    }
    private ApiException incomplete(Step step) {return new ApiException(401,"AUTH_INCOMPLETE","인증 단계를 완료해 주세요.");}
    public Instant expires(AuthenticationState state) {
        BigDecimal minutes=mapper.sessionMinutes(java.time.YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""));
        try {
            if(minutes==null || minutes.signum()<=0)throw new ArithmeticException();
            return state.activity.plusSeconds(Math.multiplyExact(minutes.longValueExact(),60));
        } catch(ArithmeticException | DateTimeException e) {throw new ApiException(503,"SESSION_CONFIGURATION_INVALID","세션 운영 기준을 확인해 주세요.");}
    }
    public void activity(AuthenticationState state) {synchronized(state) {state.activity=clock.instant();}}
    public Instant serverTime() {return clock.instant();}
    public Map<String,Object> login(String inputId,String password,HttpServletRequest request,HttpServletResponse response) {
        String id=AuthenticationPasswordPolicy.userId(inputId);AuthenticationPasswordPolicy.input(password,"password");
        request.setAttribute(AuthenticationAuditService.ATTEMPTED_ID,id);
        AuthenticationMapper.Account[] verified=new AuthenticationMapper.Account[1];
        // Commit failure/lock before raising an authentication error (including audit failures).
        String outcome=transaction.execute(status -> {
            var account=mapper.lock(id);
            if(account==null) {encoder.matches(password,dummy);return "INVALID";}
            if(!"USE".equals(account.status()))return account.status();
            if(!matches(password,account.passwordHash())) {
                int count=Math.min(5,account.errors()+1);
                if(count==5)mapper.failure(id,count,"LOCK",id,now());
                else mapper.errors(id,count);
                return "INVALID";
            }
            mapper.passwordVerified(id,mapper.administrator(id)?"Y":"N");verified[0]=account;return "OK";
        });
        if(!"OK".equals(outcome)) {
            audit.append(mapper.find(id)==null?"ANONYMOUS":id,"LOGIN_FAIL",request);
            String message=switch(outcome) {case "LOCK" -> "잠긴 계정입니다. 관리자에게 문의해 주세요.";case "STOP" -> "사용 중지된 계정입니다.";default -> "아이디 또는 비밀번호가 맞지 않습니다.";};
            throw new ApiException(401,"LOGIN_FAILED",message);
        }
        restart(request,response);
        var account=verified[0];
        var state=new AuthenticationState(id,account.passwordHash(),Step.CHANGE_PASSWORD,clock.instant());
        // Validate SESSION_MIN before establishing even a pending proof.
        expires(state);
        request.getSession(true).setAttribute(AuthenticationState.KEY,state);rotate(request,response);
        if(mapper.administrator(id)) {state.step=Step.MFA;return issueCode(state,request);}
        if("Y".equals(account.temporary()))return view(state);
        return finish(state,request,response);
    }
    public Map<String,Object> resend(HttpServletRequest request) {
        var state=validate(request,false);
        if(state.step!=Step.MFA)throw incomplete(state.step);
        return issueCode(state,request);
    }
    private Map<String,Object> issueCode(AuthenticationState state,HttpServletRequest request) {
        return issuance.run(state.userId,()->{
            var current=validate(request,false);
            if(current!=state || current.step!=Step.MFA)throw incomplete(current.step);
            return issueCodeLocked(state,request);
        });
    }
    private Map<String,Object> issueCodeLocked(AuthenticationState state,HttpServletRequest request) {
        mail.requireConfigured();
        String code=String.format(java.util.Locale.ROOT,"%06d",random.nextInt(1000000));
        String hash=encoder.encode(code);
        String email=transaction.execute(status -> {
            var account=mapper.lock(state.userId);requireProof(account,state);
            if(!mapper.administrator(state.userId))throw incomplete(Step.LOGIN);
            mapper.code(state.userId,hash,now().plusMinutes(5),state.codeProof==null?0:account.codeErrors());
            return account.email();
        });
        state.codeProof=hash;
        String delivery=mail.send(email,"SI-COST 관리자 인증번호","인증번호: "+code+"\n5분 안에 입력해 주세요. 최신 발급 번호만 유효합니다.");
        if(!"ACCEPTED".equals(delivery))throw new ApiException(503,"MFA_DELIVERY_FAILED","인증 메일 발송에 실패했거나 접수 여부가 불명입니다. 다시 보내기를 이용해 주세요.");
        return view(state);
    }
    public Map<String,Object> verify(String code,HttpServletRequest request,HttpServletResponse response) {
        if(code==null || !code.matches("[0-9]{6}"))throw AuthenticationPasswordPolicy.invalid("code","인증번호6자리를 넣어 주세요.");
        var state=validate(request,false);if(state.step!=Step.MFA)throw incomplete(state.step);
        String result=transaction.execute(status -> {
            var account=mapper.lock(state.userId);requireProof(account,state);
            if(state.codeProof==null || !Objects.equals(state.codeProof,account.codeHash()))return "STALE";
            if(account.codeExpiresAt()==null || !now().isBefore(account.codeExpiresAt()))return "EXPIRED";
            if(!encoder.matches(code,account.codeHash())) {
                int count=Math.min(5,account.codeErrors()+1);
                if(count==5)mapper.discard(state.userId,state.codeProof);
                else mapper.code(state.userId,state.codeProof,account.codeExpiresAt(),count);
                return Integer.toString(count);
            }
            mapper.discard(state.userId,state.codeProof);return "OK";
        });
        if(!"OK".equals(result)) {
            if("5".equals(result)) {invalidate(request);throw new ApiException(401,"AUTH_REQUIRED","인증번호를5번 틀렸습니다. 처음부터 다시 로그인해 주세요.");}
            if("EXPIRED".equals(result) || "STALE".equals(result))throw new ApiException(401,"MFA_EXPIRED","인증번호 시간이 지났습니다. [다시 보내기]를 눌러 주세요.","code",null);
            throw new ApiException(401,"MFA_INVALID","인증번호가 맞지 않습니다. ("+result+"/5)","code",null);
        }
        state.codeProof=null;state.mfaVerified=true;rotate(request,response);
        if("Y".equals(mapper.find(state.userId).temporary())) {state.step=Step.CHANGE_PASSWORD;return view(state);}
        return finish(state,request,response);
    }
    public Map<String,Object> change(String current,String password,String confirm,boolean required,HttpServletRequest request,HttpServletResponse response) {
        if(!required)AuthenticationPasswordPolicy.input(current,"currentPassword");
        AuthenticationPasswordPolicy.newPassword(password,confirm);
        var pending=state(request);
        if(pending==null)throw new ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        synchronized(pending) {return changeLocked(current,password,required,request,response);}
    }
    private Map<String,Object> changeLocked(String current,String password,boolean required,HttpServletRequest request,HttpServletResponse response) {
        var state=validate(request,!required);
        if(required && state.step!=Step.CHANGE_PASSWORD)throw incomplete(state.step);
        String hash=transaction.execute(status -> {
            var account=mapper.lock(state.userId);requireProof(account,state);
            if(!required && !matches(current,account.passwordHash()))throw AuthenticationPasswordPolicy.invalid("currentPassword","현재 비밀번호가 맞지 않습니다.");
            if(matches(password,account.passwordHash()))throw AuthenticationPasswordPolicy.invalid("newPassword","현재 비밀번호와 다른 값을 입력해 주세요.");
            String value=encoder.encode(password);mapper.password(state.userId,value,"N",state.userId,now());return value;
        });
        state.passwordProof=hash;rotate(request,response);
        if(!required)activity(state);
        return required?finish(state,request,response):view(state);
    }
    private Map<String,Object> finish(AuthenticationState state,HttpServletRequest request,HttpServletResponse response) {
        // Required audit must succeed before a business principal is installed.
        transaction.executeWithoutResult(status -> {
            var account=mapper.lock(state.userId);requireProof(account,state);
            if(!state.resumeAuthenticated) {audit.append(state.userId,"LOGIN",request);mapper.login(state.userId,now());}
        });
        state.resumeAuthenticated=false;state.step=Step.AUTHENTICATED;state.activity=clock.instant();rotate(request,response);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(state.userId,null,List.of()));
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
        return view(state);
    }
    public Map<String,Object> session(HttpServletRequest request) {
        if(state(request)==null) {
            if(request.getRequestedSessionId()!=null && !request.isRequestedSessionIdValid())throw new ApiException(401,"SESSION_EXPIRED","세션이 만료되었습니다. 다시 로그인해 주세요.");
            return new LinkedHashMap<>(Map.of("nextStep","LOGIN","serverTime",clock.instant().toString()));
        }
        return view(validate(request,false));
    }
    public Map<String,Object> extend(HttpServletRequest request) {var state=validate(request,true);activity(state);return view(state);}
    public Map<String,Object> dismissRecommendation(HttpServletRequest request) {
        var state=validate(request,true);
        synchronized(state) {state.recommendationDismissed=true;activity(state);return view(state);}
    }
    private Map<String,Object> view(AuthenticationState state) {
        var result=new LinkedHashMap<String,Object>();result.put("nextStep",state.step.name());result.put("serverTime",clock.instant().toString());
        var account=mapper.find(state.userId);
        if(state.step==Step.AUTHENTICATED) {
            result.put("expiresAt",expires(state).toString());
            result.put("passwordChangedAt",account.changedAt()==null?null:account.changedAt().toString());
            result.put("passwordChangeRecommended",!state.recommendationDismissed && (account.changedAt()==null || ChronoUnit.DAYS.between(account.changedAt().toLocalDate(),now().toLocalDate())>=90));
        } else if(state.step==Step.MFA) {
            result.put("maskedEmail",maskedEmail(account.email()));
            result.put("stageExpiresAt",state.codeProof!=null && Objects.equals(state.codeProof,account.codeHash()) && account.codeExpiresAt()!=null?account.codeExpiresAt().atZone(TimeConfiguration.SEOUL).toInstant().toString():clock.instant().toString());
        } else {
            result.put("stageExpiresAt",expires(state).toString());
        }
        return result;
    }
    public static String maskedEmail(String email) {if(email==null || !email.contains("@"))return "***";int at=email.indexOf('@');return (at>0?email.substring(0,1):"")+"***"+email.substring(at);}
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        var state=state(request);
        if(state==null) {restart(request,response);return;}
        synchronized(state) {
            boolean authenticated=false;
            try {
                authenticated=state.step==Step.AUTHENTICATED && clock.instant().isBefore(expires(state));
            } finally {
                // The in-memory transition also deduplicates two concurrent logout requests.
                state.step=Step.LOGIN;
                try {restart(request,response);} finally {if(authenticated)audit.append(state.userId,"LOGOUT",request);}
            }
        }
    }
    public void restart(HttpServletRequest request,HttpServletResponse response) {
        var state=state(request);
        try {if(state!=null && state.codeProof!=null)transaction.executeWithoutResult(status -> mapper.discard(state.userId,state.codeProof));}
        finally {
            invalidate(request);
            Cookie cookie=new Cookie("JSESSIONID","");cookie.setPath(request.getContextPath().isEmpty()?"/":request.getContextPath());cookie.setMaxAge(0);cookie.setHttpOnly(true);cookie.setSecure(request.isSecure());cookie.setAttribute("SameSite","Lax");response.addCookie(cookie);
        }
    }
    public void invalidate(HttpServletRequest request) {var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();}
    private void rotate(HttpServletRequest request,HttpServletResponse response) {request.changeSessionId();new HttpSessionCsrfTokenRepository().saveToken(null,request,response);}
    private boolean matches(String value,String hash) {return value!=null && hash!=null && hash.matches("\\$2[aby]\\$10\\$.{53}") && encoder.matches(value,hash);}
    private void requireProof(AuthenticationMapper.Account account,AuthenticationState state) {
        if(account==null || !"USE".equals(account.status()) || !Objects.equals(account.passwordHash(),state.passwordProof))throw new ApiException(401,"AUTH_REQUIRED","다시 로그인해 주세요.");
        if(mapper.administrator(state.userId) && state.step!=Step.MFA && !state.mfaVerified)throw incomplete(Step.MFA);
    }
}
