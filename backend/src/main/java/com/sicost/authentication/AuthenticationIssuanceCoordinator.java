package com.sicost.authentication;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Serializes each account through commit and synchronous delivery without holding a DB row lock.
 * Entries exist only while a caller owns or waits for that account; no credentials are retained.
 */
@Component
public final class AuthenticationIssuanceCoordinator {
    private static final class Entry {
        final ReentrantLock lock=new ReentrantLock(true);
        int references;
    }
    private final ConcurrentHashMap<String,Entry> accounts=new ConcurrentHashMap<>();
    public <T> T run(String userId,Supplier<T> action) {
        Entry entry=accounts.compute(userId,(key,current)->{
            if(current==null)current=new Entry();
            current.references++;return current;
        });
        entry.lock.lock();
        try {return action.get();}
        finally {
            entry.lock.unlock();
            accounts.compute(userId,(key,current)->{
                if(current!=entry)throw new IllegalStateException("발급 직렬화 상태를 확인할 수 없습니다.");
                return --current.references==0?null:current;
            });
        }
    }
}
