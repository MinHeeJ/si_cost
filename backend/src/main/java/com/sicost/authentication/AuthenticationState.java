package com.sicost.authentication;

import java.io.Serializable;
import java.time.Instant;

/** Server-only password proof; never serialized as a response or logged. */
public final class AuthenticationState implements Serializable,jakarta.servlet.http.HttpSessionBindingListener {
    public static final String KEY = AuthenticationState.class.getName();
    public enum Step { LOGIN, MFA, CHANGE_PASSWORD, AUTHENTICATED }
    final String userId;
    String passwordProof;
    String codeProof;
    boolean mfaVerified;
    boolean resumeAuthenticated;
    boolean recommendationDismissed;
    Step step;
    Instant activity;
    volatile boolean revoked;
    public AuthenticationState(String id, String proof, Step step, Instant activity) {
        this.userId=id; this.passwordProof=proof; this.step=step; this.activity=activity;
    }
    @Override public String toString() { return "AuthenticationState[redacted]"; }
    @Override public void valueUnbound(jakarta.servlet.http.HttpSessionBindingEvent event) {revoked=true;}
}
