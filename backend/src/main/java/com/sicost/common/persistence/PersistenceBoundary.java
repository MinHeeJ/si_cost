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
            "TB_SY_OPER_STD", "TB_SY_USER_PREF", "TB_SY_CHG_HIST", "TB_SY_USER", "TB_SY_USER_ROLE",
            "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ACCS_LOG");
    private static final Set<String> DELETABLE = Set.of("TB_SY_CODE_MAP", "TB_SY_USER_PREF", "TB_SY_USER_ROLE", "TB_SY_UPLD_ERR");
    private static final Pattern TARGET = Pattern.compile("(?is)^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(TB_[A-Z0-9_]+)\\b");
    public static void requireAllowed(String sql) {
        // Group creation serializes duplicate-name checks and sort allocation before its INSERT.
        if (sql.strip().equalsIgnoreCase("LOCK TABLE tb_sy_code_grp IN SHARE ROW EXCLUSIVE MODE")) return;
        if (sql.strip().equalsIgnoreCase("LOCK TABLE tb_cl_close IN SHARE MODE")
                || sql.strip().equalsIgnoreCase("LOCK TABLE tb_co_part_setl IN SHARE MODE")) return;
        var match = TARGET.matcher(sql);
        if (!match.find() || !WRITABLE.contains(match.group(2).toUpperCase(Locale.ROOT))
                || ((match.group(2).equalsIgnoreCase("TB_SY_CHG_HIST") || match.group(2).equalsIgnoreCase("TB_SY_ACCS_LOG"))
                    && !match.group(1).toUpperCase(Locale.ROOT).startsWith("INSERT"))
                || (match.group(1).toUpperCase(Locale.ROOT).startsWith("DELETE")
                    && !DELETABLE.contains(match.group(2).toUpperCase(Locale.ROOT)))) {
            throw new IllegalStateException("허용되지 않은 영속성 쓰기입니다.");
        }
        String remainder = sql.substring(match.end());
        if (match.group(1).toUpperCase(Locale.ROOT).startsWith("DELETE")) {
            String table = match.group(2).toUpperCase(Locale.ROOT);
            if (table.equals("TB_SY_USER_ROLE") && (!remainder.matches("(?is).*\\bUSER_ID\\s*=\\s*\\?.*")
                    || !remainder.matches("(?is).*\\bROLE_ID\\s+IN\\s*\\(\\s*'PM'\\s*,\\s*'SALES_LDR'\\s*\\).*"))) {
                throw new IllegalStateException("업무 자격 연결만 해제할 수 있습니다.");
            }
            if (table.equals("TB_SY_UPLD_ERR") && !remainder.matches("(?is).*\\bUPLD_ID\\s*=\\s*\\?.*")) {
                throw new IllegalStateException("동일 업로드의 검사 결과만 교체할 수 있습니다.");
            }
        }
        if ((match.group(2).equalsIgnoreCase("TB_SY_CHG_HIST") || match.group(2).equalsIgnoreCase("TB_SY_ACCS_LOG"))
                && remainder.matches("(?is).*\\bON\\s+CONFLICT\\b.*")) {
            throw new IllegalStateException("기록은 추가만 허용합니다.");
        }
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
