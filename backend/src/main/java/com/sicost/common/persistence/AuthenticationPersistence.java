package com.sicost.common.persistence;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Update;
import com.sicost.authentication.AuthenticationMapper;

/** Only the compiled, bound authentication commands may change authentication columns. */
final class AuthenticationPersistence {
    private AuthenticationPersistence() {}
    private static final Set<String> SQL=Arrays.stream(AuthenticationMapper.class.getMethods())
        .filter(method->method.isAnnotationPresent(Update.class))
        .map(method->String.join(" ",method.getAnnotation(Update.class).value()).replaceAll("#\\{[^}]+}","?"))
        .map(AuthenticationPersistence::normalized).collect(Collectors.toUnmodifiableSet());
    static boolean allowed(String sql) {return SQL.contains(normalized(sql));}
    private static String normalized(String sql) {return sql.replaceAll("\\s+","").toUpperCase(java.util.Locale.ROOT);}
}
