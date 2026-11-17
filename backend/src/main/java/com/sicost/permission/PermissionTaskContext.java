package com.sicost.permission;

import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

/** Fixed internal commands only; never a browser-selected identity or menu bypass. */
public final class PermissionTaskContext {
    public enum Command { DAILY_OPERATIONS, RECEIVABLE_DUNNING }
    private static final ThreadLocal<Command> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Runnable> USER_CHECK=new ThreadLocal<>();
    private PermissionTaskContext() {}
    public static boolean systemCommand() {return CURRENT.get()!=null && RequestContextHolder.getRequestAttributes()==null;}
    public static void checkpoint() {
        if(systemCommand())return;
        Runnable check=USER_CHECK.get();if(check==null)throw new IllegalStateException("작업 주체와 권한 증명이 필요합니다.");check.run();
    }
    public static <T> T user(Runnable check,Supplier<T> work) {
        var actor=SecurityContextHolder.getContext().getAuthentication();
        if(actor==null || !actor.isAuthenticated() || actor instanceof org.springframework.security.authentication.AnonymousAuthenticationToken || CURRENT.get()!=null)throw new IllegalStateException("실제 사용자 작업 문맥이 필요합니다.");
        Runnable previous=USER_CHECK.get();try {USER_CHECK.set(check);check.run();return work.get();}
        finally {if(previous==null)USER_CHECK.remove();else USER_CHECK.set(previous);}
    }
    public static <T> T system(Command command,Supplier<T> work) {
        var principal=SecurityContextHolder.getContext().getAuthentication();
        if(command==null || RequestContextHolder.getRequestAttributes()!=null || principal!=null && principal.isAuthenticated())
            throw new IllegalStateException("내부 명령은 사용자 요청 문맥에서 실행할 수 없습니다.");
        Command previous=CURRENT.get();
        try {CURRENT.set(command);return work.get();}
        finally {if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}
