package com.sicost.common.persistence;

import java.util.Arrays;
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
            "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ACCS_LOG",
            "TB_HR_DEV", "TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT",
            "TB_HR_CAND", "TB_HR_CAND_SKILL", "TB_HR_CAND_CNTC",
            "TB_PJ_PROJ", "TB_PJ_PROJ_PART", "TB_PJ_PROJ_CHG", "TB_PJ_MNPW_PLAN", "TB_PJ_TK_DSTRB",
            "TB_PJ_DRCT_EXPS", "TB_SY_USER_SCOPE", "TB_PJ_ASGN", "TB_PJ_ASGN_MON", "TB_PJ_ASGN_HIST", "TB_CO_MON_COST", "TB_HR_MON_PAY");
    private static final Set<String> DELETABLE = Set.of("TB_SY_CODE_MAP", "TB_SY_USER_PREF", "TB_SY_USER_ROLE", "TB_SY_UPLD_ERR",
            "TB_HR_DEV_SKILL", "TB_HR_CAND_SKILL", "TB_HR_DEV_STAT",
            "TB_PJ_PROJ_PART", "TB_PJ_TK_DSTRB", "TB_PJ_DRCT_EXPS", "TB_SY_USER_SCOPE", "TB_PJ_ASGN_MON");
    private static final Set<String> APPEND_ONLY = Set.of("TB_SY_CHG_HIST", "TB_SY_ACCS_LOG", "TB_PJ_PROJ_CHG", "TB_PJ_ASGN_HIST");
    private static final Pattern TARGET = Pattern.compile("(?is)^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(TB_[A-Z0-9_]+)\\b");
    private static final String ASSIGNMENT_VALUES = "INPUT_END_DT|CTRT_MM|BILL_UPRC|INPUT_TYPE_CD|EXT_PSBL_CD|EXT_CNT|EARLY_WTHDR_YN|ASGN_STAT_CD|MOD_USER_ID|MOD_DTTM";
    private static final String PAYROLL_VALUES = "PAY_AMT|ALWNC_AMT|INSR_CMPNY_AMT|INSR_SELF_AMT|RETIRE_RSV_AMT|INCM_TAX_AMT|WTHLD_TAX_AMT|REAL_PAY_AMT|VNDR_SPLY_AMT|EMP_TYPE_CD|DRCT_CLS_CD|UPLD_ID|MOD_USER_ID|MOD_DTTM";
    private static final String PAYROLL_KEY = "PAY_YM\\s*=\\s*\\?\\s+AND\\s+DEV_ID\\s*=\\s*\\?";
    private static final String CORRECTION = "(?is)\\s+SET\\s+CRCT_YN\\s*=\\s*'Y'\\s+WHERE\\s+ASGN_ID\\s*=\\s*\\?\\s+AND\\s+HIST_SN\\s*=\\s*\\?\\s+AND\\s+CRCT_YN\\s*=\\s*'N'\\s*";
    public static void requireAllowed(String sql) {
        // Group creation serializes duplicate-name checks and sort allocation before its INSERT.
        if (sql.strip().equalsIgnoreCase("LOCK TABLE tb_sy_code_grp IN SHARE ROW EXCLUSIVE MODE")) return;
        if (sql.strip().equalsIgnoreCase("LOCK TABLE tb_cl_close IN SHARE MODE")
                || sql.strip().equalsIgnoreCase("LOCK TABLE tb_co_part_setl IN SHARE MODE")) return;
        var match = TARGET.matcher(sql);
        boolean correction = match.find() && match.group(2).equalsIgnoreCase("TB_PJ_ASGN_HIST")
                && match.group(1).equalsIgnoreCase("UPDATE") && sql.substring(match.end()).matches(CORRECTION);
        match.reset();
        if (!match.find() || !WRITABLE.contains(match.group(2).toUpperCase(Locale.ROOT))
                || (APPEND_ONLY.contains(match.group(2).toUpperCase(Locale.ROOT))
                    && !match.group(1).toUpperCase(Locale.ROOT).startsWith("INSERT") && !correction)
                || (match.group(1).toUpperCase(Locale.ROOT).startsWith("DELETE")
                    && !DELETABLE.contains(match.group(2).toUpperCase(Locale.ROOT))
                    && !match.group(2).equalsIgnoreCase("TB_HR_MON_PAY"))) {
            throw new IllegalStateException("허용되지 않은 영속성 쓰기입니다.");
        }
        String remainder = sql.substring(match.end());
        String tableName = match.group(2).toUpperCase(Locale.ROOT);
        String operation = match.group(1).toUpperCase(Locale.ROOT);
        if (tableName.equals("TB_PJ_ASGN")) {
            if (operation.startsWith("INSERT")) {
                requireInsert(remainder, "ASGN_ID|PROJ_ID|DEV_ID|JOB_CD|INPUT_BGN_DT|CTRT_DEV_ID|REG_USER_ID|REG_DTTM|" + ASSIGNMENT_VALUES);
                if (remainder.matches("(?is).*\\bON\\s+CONFLICT\\b.*")) throw new IllegalStateException("투입 등록은 기존 행을 덮어쓸 수 없습니다.");
            } else if (!operation.equals("UPDATE") || !remainder.matches("(?is)\\s+SET\\s+" + assignments(ASSIGNMENT_VALUES)
                    + "\\s+WHERE\\s+ASGN_ID\\s*=\\s*\\?(?:\\s+AND\\s+PROJ_ID\\s*=\\s*\\?)?(?:\\s+AND\\s+ASGN_STAT_CD\\s*=\\s*'ACT')?\\s*")) {
                throw new IllegalStateException("투입 식별자는 불변이며 지정 투입의 승인된 항목만 변경할 수 있습니다.");
            }
        }
        if (tableName.equals("TB_HR_MON_PAY") && !operation.startsWith("DELETE")) {
            if (operation.startsWith("INSERT")) {
                requireInsert(remainder, "PAY_YM|DEV_ID|REG_USER_ID|REG_DTTM|" + PAYROLL_VALUES);
                var conflict = Pattern.compile("(?is)\\bON\\s+CONFLICT\\b").matcher(remainder);
                if (conflict.find() && !remainder.substring(conflict.start()).matches("(?is)ON\\s+CONFLICT\\s*\\(\\s*PAY_YM\\s*,\\s*DEV_ID\\s*\\)\\s+DO\\s+UPDATE\\s+SET\\s+" + assignments(PAYROLL_VALUES) + "\\s*")) {
                    throw new IllegalStateException("급여 교체는 전체 키와 기존 등록 정보를 보존해야 합니다.");
                }
            } else if (!remainder.matches("(?is)\\s+SET\\s+" + assignments(PAYROLL_VALUES) + "\\s+WHERE\\s+" + PAYROLL_KEY + "\\s*")) {
                throw new IllegalStateException("지정 월과 개발자의 급여 항목만 변경할 수 있습니다.");
            }
        }
        if ((tableName.equals("TB_PJ_DRCT_EXPS") || tableName.equals("TB_SY_USER_SCOPE")) && operation.equals("UPDATE")) {
            throw new IllegalStateException("경비와 담당 연결은 등록 또는 지정 행 삭제만 허용합니다.");
        }
        if (tableName.equals("TB_CO_MON_COST") && (!operation.equals("UPDATE") || !remainder.matches(
                "(?is)\\s+SET\\s+COST_CLS_CD\\s*=\\s*'IDLE'(?:\\s*,\\s*MOD_(?:USER_ID|DTTM)\\s*=\\s*\\?)*\\s+WHERE\\s+PROJ_ID\\s*=\\s*\\?\\s+AND\\s+COST_CLS_CD\\s*=\\s*'PRE'\\s+AND\\s+FIX_YN\\s*(?:<>\\s*'Y'|!=\\s*'Y'|=\\s*'N')(?:\\s+AND\\s+COST_YM\\s*(?:=|>=|<=|>|<)\\s*\\?)*\\s*"))) {
            throw new IllegalStateException("원가는 실주 PRE 분류의 IDLE 변경만 허용합니다.");
        }
        if (match.group(1).toUpperCase(Locale.ROOT).startsWith("DELETE")) {
            String table = match.group(2).toUpperCase(Locale.ROOT);
            String bounded = switch (table) {
                case "TB_PJ_DRCT_EXPS" -> "PROJ_ID\\s*=\\s*\\?\\s+AND\\s+EXPS_YM\\s*=\\s*\\?\\s+AND\\s+EXPS_SN\\s*=\\s*\\?";
                case "TB_PJ_TK_DSTRB" -> "PROJ_ID\\s*=\\s*\\?\\s+AND\\s+DSTRB_STEP_CD\\s*=\\s*\\?";
                case "TB_PJ_PROJ_PART" -> "PROJ_ID\\s*=\\s*\\?\\s+AND\\s+APLY_BGN_YM\\s*(?:=|>=|>)\\s*\\?(?:\\s+AND\\s+APLY_BGN_YM\\s*(?:<=|<)\\s*\\?)?";
                case "TB_SY_USER_SCOPE" -> "USER_ID\\s*=\\s*\\?\\s+AND\\s+SCOPE_SN\\s*=\\s*\\?\\s+AND\\s+SCOPE_TYPE_CD\\s*=\\s*'PROJ'(?:\\s+AND\\s+PROJ_ID\\s*=\\s*\\?)?";
                case "TB_PJ_ASGN_MON" -> "ASGN_ID\\s*=\\s*\\?\\s+AND\\s+INPUT_YM\\s*=\\s*\\?\\s+AND\\s+FIX_YN\\s*(?:<>\\s*'Y'|!=\\s*'Y'|=\\s*'N')";
                case "TB_HR_MON_PAY" -> "PAY_YM\\s*=\\s*\\?(?:\\s+AND\\s+DEV_ID\\s*=\\s*\\?)?";
                default -> null;
            };
            if (bounded != null && !remainder.matches("(?is)\\s+WHERE\\s+" + bounded + "\\s*")) {
                throw new IllegalStateException("승인된 자식 범위만 삭제할 수 있습니다.");
            }
            if (table.equals("TB_HR_DEV_SKILL") || table.equals("TB_HR_CAND_SKILL")) {
                String key = table.equals("TB_HR_DEV_SKILL") ? "DEV_ID" : "CAND_ID";
                if (!remainder.matches("(?is)\\s+WHERE\\s+" + key + "\\s*=\\s*\\?\\s*")) {
                    throw new IllegalStateException("한 사람의 스킬 집합만 교체할 수 있습니다.");
                }
            }
            if (table.equals("TB_HR_DEV_STAT") && !remainder.matches("(?is)\\s+WHERE\\s+DEV_ID\\s*=\\s*\\?\\s+AND\\s+STAT_SN\\s*=\\s*\\?\\s+AND\\s+WORK_STAT_CD\\s*(?:IN\\s*\\(\\s*'WORK'\\s*,\\s*'RETIRE'\\s*\\)|=\\s*'(?:WORK|RETIRE)')\\s*")) {
                throw new IllegalStateException("지정 개발자의 복귀 또는 퇴사 이력 한 건만 취소할 수 있습니다.");
            }
            if (table.equals("TB_SY_USER_ROLE") && (!remainder.matches("(?is).*\\bUSER_ID\\s*=\\s*\\?.*")
                    || !remainder.matches("(?is).*\\bROLE_ID\\s+IN\\s*\\(\\s*'PM'\\s*,\\s*'SALES_LDR'\\s*\\).*"))) {
                throw new IllegalStateException("업무 자격 연결만 해제할 수 있습니다.");
            }
            if (table.equals("TB_SY_UPLD_ERR") && !remainder.matches("(?is).*\\bUPLD_ID\\s*=\\s*\\?.*")) {
                throw new IllegalStateException("동일 업로드의 검사 결과만 교체할 수 있습니다.");
            }
        }
        if (APPEND_ONLY.contains(tableName)
                && remainder.matches("(?is).*\\bON\\s+CONFLICT\\b.*")) {
            throw new IllegalStateException("기록은 추가만 허용합니다.");
        }
        remainder = remainder.replaceAll("(?is)ON\\s+CONFLICT\\s*\\([^)]*\\)\\s+DO\\s+UPDATE\\s+SET", "");
        if (remainder.contains(";") || remainder.matches("(?is).*\\b(INSERT|UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE)\\b.*")) {
            throw new IllegalStateException("단일 업무 쓰기만 허용합니다.");
        }
    }
    private static String assignments(String columns) {
        String value = "(?:\\?|NULL|'[A-Z]+'|[0-9]+|EXCLUDED\\.(?:" + columns + "))";
        String item = "(?:" + columns + ")\\s*=\\s*" + value;
        String assignment = "(?:" + item + (columns.equals(ASSIGNMENT_VALUES) ? "|EXT_CNT\\s*=\\s*EXT_CNT\\s*\\+\\s*1" : "") + ")";
        return assignment + "(?:\\s*,\\s*" + assignment + ")*";
    }
    private static void requireInsert(String remainder, String columns) {
        var insert = Pattern.compile("(?is)^\\s*\\(([^)]+)\\)\\s+VALUES\\s*\\(").matcher(remainder);
        if (!insert.find()) throw new IllegalStateException("명시된 열과 바인딩 값으로만 등록할 수 있습니다.");
        var names = Arrays.stream(insert.group(1).split(",", -1)).map(String::strip).map(s -> s.toUpperCase(Locale.ROOT)).toList();
        if (names.stream().distinct().count() != names.size() || names.stream().anyMatch(n -> !n.matches(columns))) {
            throw new IllegalStateException("승인된 등록 열만 사용할 수 있습니다.");
        }
    }
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        requireAllowed(statement.getBoundSql(invocation.getArgs()[1]).getSql());
        return invocation.proceed();
    }
}
