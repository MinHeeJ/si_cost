package com.sicost.common.persistence;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

@Component
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
public class PersistenceBoundary implements Interceptor {
    private static final Set<String> WRITABLE = Set.of("TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_CODE_MAP",
            "TB_SY_OPER_STD", "TB_SY_USER_PREF", "TB_SY_CHG_HIST");
    private static final Pattern TARGET = Pattern.compile("(?is)^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(TB_[A-Z0-9_]+)\\b");
    public static void requireAllowed(String sql) {
        // Group creation serializes duplicate-name checks and sort allocation before its INSERT.
        if (sql.strip().equalsIgnoreCase("LOCK TABLE tb_sy_code_grp IN SHARE ROW EXCLUSIVE MODE")) return;
        var match = TARGET.matcher(sql);
        if (!match.find() || !WRITABLE.contains(match.group(2).toUpperCase(Locale.ROOT))
                || (match.group(2).equalsIgnoreCase("TB_SY_CHG_HIST") && !match.group(1).toUpperCase(Locale.ROOT).startsWith("INSERT"))) {
            throw new IllegalStateException("허용되지 않은 영속성 쓰기입니다.");
        }
        String remainder = sql.substring(match.end());
        remainder = remainder.replaceAll("(?is)ON\\s+CONFLICT\\s*\\([^)]*\\)\\s+DO\\s+UPDATE\\s+SET", "");
        if (remainder.contains(";") || remainder.matches("(?is).*\\b(INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE)\\b.*")) {
            throw new IllegalStateException("단일 업무 쓰기만 허용합니다.");
        }
    }
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        requireAllowed(statement.getBoundSql(invocation.getArgs()[1]).getSql());
        return invocation.proceed();
    }
}
