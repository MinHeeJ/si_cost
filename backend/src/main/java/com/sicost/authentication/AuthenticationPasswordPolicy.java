package com.sicost.authentication;

import com.sicost.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class AuthenticationPasswordPolicy {
    private AuthenticationPasswordPolicy() {}
    public static String userId(String id) {
        if(id==null || id.trim().isEmpty() || id.trim().codePointCount(0,id.trim().length())>30)
            throw invalid("userId","아이디를 입력해 주세요.");
        return id.trim();
    }
    public static void input(String value,String field) {
        if(value==null || value.isEmpty()) throw invalid(field,"비밀번호를 입력해 주세요.");
        if(value.getBytes(StandardCharsets.UTF_8).length>72) throw invalid(field,"비밀번호는 UTF-8 72바이트 이하여야 합니다.");
    }
    public static void newPassword(String value,String confirm) {
        input(value,"newPassword");
        if(value.codePointCount(0,value.length())<8 || !value.matches("(?s).*[A-Za-z].*") || !value.matches("(?s).*[0-9].*")
                || value.chars().noneMatch(c -> c>=33 && c<=126 && !Character.isLetterOrDigit(c))
                || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c) || Character.getType(c)==Character.FORMAT))
            throw invalid("newPassword","8자 이상 영문·숫자·특수문자를 포함하고 공백 없이 입력해 주세요.");
        if(!value.equals(confirm)) throw invalid("confirmPassword","새 비밀번호가 일치하지 않습니다.");
    }
    public static ApiException invalid(String field,String message) { return new ApiException(400,"VALIDATION_ERROR",message,field,null); }
    public static String temporary(SecureRandom random) {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        while(true) {
            StringBuilder value=new StringBuilder();
            for(int i=0;i<16;i++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String result=value.toString();
            try {newPassword(result,result);return result;} catch(ApiException ignored) { /* rejection sampling */ }
        }
    }
}
