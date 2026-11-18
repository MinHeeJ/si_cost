package com.sicost.authentication;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.history.AccessLogMapper;
import com.sicost.common.history.AccessLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuthenticationAuditService {
    static final String ATTEMPTED_ID=AuthenticationAuditService.class.getName()+".attempted-id";
    private final AccessLogMapper mapper;
    private final Clock clock;
    public AuthenticationAuditService(AccessLogMapper mapper,Clock clock) {this.mapper=mapper;this.clock=clock;}
    @Transactional(propagation=Propagation.REQUIRED)
    public void append(String actor,String action,HttpServletRequest request) {
        if(!Set.of("LOGIN","LOGIN_FAIL","LOGOUT").contains(action))throw new IllegalArgumentException();
        String ip=request.getRemoteAddr();if(ip==null || !ip.matches("[0-9A-Fa-f:.%]{1,45}"))ip=null;
        String description="인증; requestId="+request.getAttribute(com.sicost.common.api.RequestIdFilter.SERVER_ATTRIBUTE);
        if(action.equals("LOGIN_FAIL") && request.getAttribute(ATTEMPTED_ID) instanceof String id) {
            try {description+="; attemptedIdDigest="+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException("인증 실패 기록을 생성하지 못했습니다.");}
        }
        if(mapper.insert(new AccessLogWriter.Entry(actor,action,"UI-22",description,ip,LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
            throw new IllegalStateException("인증 접근기록을 저장하지 못했습니다.");
    }
}
