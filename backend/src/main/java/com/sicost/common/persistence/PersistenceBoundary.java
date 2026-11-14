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
            "TB_PJ_STEP", "TB_PJ_WEEK_MEMO", "TB_PJ_ISSUE",
            "TB_PJ_DRCT_EXPS", "TB_SY_USER_SCOPE", "TB_PJ_ASGN", "TB_PJ_ASGN_MON", "TB_PJ_ASGN_HIST", "TB_CO_MON_COST", "TB_HR_MON_PAY", "TB_BL_BILL", "TB_BL_RCPT",
            "TB_FI_ITEM", "TB_FI_CORP_TAX", "TB_FI_TAX_PAY", "TB_CO_PREV_ADJ", "TB_FI_LOAN",
            "TB_FI_CARD", "TB_FI_CARD_PAY", "TB_FI_CARD_MON", "TB_FI_XFER", "TB_FI_BIZ_PLAN", "TB_CO_MON_PL",
            "TB_CO_PART_SETL", "TB_CO_PART_SETL_DTL");
    private static final Set<String> DELETABLE = Set.of("TB_SY_CODE_MAP", "TB_SY_USER_PREF", "TB_SY_USER_ROLE", "TB_SY_UPLD_ERR",
            "TB_HR_DEV_SKILL", "TB_HR_CAND_SKILL", "TB_HR_DEV_STAT",
            "TB_PJ_PROJ_PART", "TB_PJ_TK_DSTRB", "TB_PJ_DRCT_EXPS", "TB_SY_USER_SCOPE", "TB_PJ_ASGN_MON",
            "TB_FI_ITEM", "TB_CO_PREV_ADJ", "TB_FI_LOAN", "TB_FI_XFER", "TB_CO_PART_SETL_DTL");
    private static final Set<String> APPEND_ONLY = Set.of("TB_SY_CHG_HIST", "TB_SY_ACCS_LOG", "TB_PJ_PROJ_CHG", "TB_PJ_ASGN_HIST");
    private static final Pattern TARGET = Pattern.compile("(?is)^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(TB_[A-Z0-9_]+)\\b");
    private static final String ASSIGNMENT_VALUES = "INPUT_END_DT|CTRT_MM|BILL_UPRC|INPUT_TYPE_CD|EXT_PSBL_CD|EXT_CNT|EARLY_WTHDR_YN|ASGN_STAT_CD|MOD_USER_ID|MOD_DTTM";
    private static final String PAYROLL_VALUES = "PAY_AMT|ALWNC_AMT|INSR_CMPNY_AMT|INSR_SELF_AMT|RETIRE_RSV_AMT|INCM_TAX_AMT|WTHLD_TAX_AMT|REAL_PAY_AMT|VNDR_SPLY_AMT|EMP_TYPE_CD|DRCT_CLS_CD|UPLD_ID|MOD_USER_ID|MOD_DTTM";
    private static final String PAYROLL_KEY = "PAY_YM\\s*=\\s*\\?\\s+AND\\s+DEV_ID\\s*=\\s*\\?";
    private static final String CORRECTION = "(?is)\\s+SET\\s+CRCT_YN\\s*=\\s*'Y'\\s+WHERE\\s+ASGN_ID\\s*=\\s*\\?\\s+AND\\s+HIST_SN\\s*=\\s*\\?\\s+AND\\s+CRCT_YN\\s*=\\s*'N'\\s*";
    public static void requireAllowed(String sql) {
        if (sql.strip().equalsIgnoreCase("SET LOCAL lock_timeout = '10s'")) return;
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
        if (Set.of("TB_CO_PART_SETL", "TB_CO_PART_SETL_DTL").contains(tableName)) {
            requireSettlementWrite(tableName, operation, remainder);
        }
        if (tableName.equals("TB_CO_MON_PL")) {
            String normalized = remainder.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            String amounts = "SALES_AMT|SALES_COST_AMT|SGA_TOT_AMT|OPRT_PRFT_AMT|ALLOC_COST_AMT|IDLE_COST_AMT|DRCT_EXPS_AMT|GROSS_PRFT_AMT|SGA_INPUT_AMT|INDRCT_LABOR_AMT|RND_AMT|PREV_ADJ_AMT|NOPR_INCM_AMT|NOPR_EXPS_AMT|CORP_TAX_AMT|NET_PRFT_AMT";
            String columns = amounts.replace('|', ',');
            String range = "?< ?".replace(" ", "") + "AND?>=((SUBSTRING(?,1,4)::INT-1)::TEXT||'01')";
            String insert = "(PL_YM," + columns + ",MIGR_YN,FIX_YN,REG_USER_ID,REG_DTTM)SELECT?" + ",?".repeat(16) + ",'Y','Y',?,?WHERE" + range;
            String update = "SET" + String.join("=?,", amounts.split("\\|")) + "=?,MIGR_YN='Y',FIX_YN='Y',MOD_USER_ID=?,MOD_DTTM=?WHEREPL_YM=?ANDPL_YM<?ANDPL_YM>=((SUBSTRING(?,1,4)::INT-1)::TEXT||'01')";
            if (!(operation.startsWith("INSERT") && normalized.equals(insert))
                    && !(operation.equals("UPDATE") && normalized.equals(update))) {
                throw new IllegalStateException("BASE 이전 지정 이관 월의 원값 등록·수정만 허용합니다.");
            }
        }
        if (tableName.equals("TB_FI_BIZ_PLAN")) {
            String normalized = remainder.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            boolean insert = operation.startsWith("INSERT") && normalized.equals(
                "(PLAN_YM,PART_ID,SALES_TGT_AMT,OPRT_PRFT_TGT_AMT,UPLD_ID,REG_USER_ID,REG_DTTM)VALUES(?,'ALL',?,?,?,?,?)");
            boolean update = operation.equals("UPDATE") && normalized.equals(
                "SETSALES_TGT_AMT=?,OPRT_PRFT_TGT_AMT=?,UPLD_ID=?,MOD_USER_ID=?,MOD_DTTM=?WHEREPLAN_YM=?ANDPART_ID='ALL'");
            if (!insert && !update) throw new IllegalStateException("지정 월 ALL 목표의 등록·수정만 허용합니다.");
        }
        if (Set.of("TB_PJ_STEP", "TB_PJ_WEEK_MEMO", "TB_PJ_ISSUE").contains(tableName)) {
            requireProgressWrite(tableName, operation, remainder);
        }
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
                case "TB_FI_ITEM" -> "FIN_YM\\s*=\\s*\\?\\s+AND\\s+(?:ITEM_SN\\s*=\\s*\\?|UPLD_ID\\s+IS\\s+NOT\\s+NULL)";
                case "TB_CO_PREV_ADJ" -> "APLY_YM\\s*=\\s*\\?\\s+AND\\s+ADJ_ID\\s*=\\s*\\?";
                case "TB_FI_LOAN" -> "LOAN_SN\\s*=\\s*\\?\\s+AND\\s+LOAN_DT\\s*>=\\s*\\?\\s+AND\\s+LOAN_DT\\s*<\\s*\\?";
                case "TB_FI_XFER" -> "XFER_SN\\s*=\\s*\\?\\s+AND\\s+XFER_DT\\s*>=\\s*\\?\\s+AND\\s+XFER_DT\\s*<\\s*\\?";
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
    private static void requireSettlementWrite(String table, String operation, String remainder) {
        boolean detail = table.equals("TB_CO_PART_SETL_DTL");
        String keys = "PART_ID|SETL_YM" + (detail ? "|DEV_ID" : "");
        String fields = detail ? "EMP_TYPE_CD|PROJ_NM_LIST|MM|UPRC_AMT|PAY_AMT|SALES_EXPS_AMT|RETIRE_AMT|COST_AMT|DIFF_AMT|PRFT_AMT|RMRK"
            : "CORP_CARD_AMT|PART_PRFT_AMT|SETL_STAT_CD|CNFM_USER_ID|CNFM_DTTM|CNCL_RSN";
        if (operation.startsWith("INSERT")) {
            requireInsert(remainder, keys + "|" + fields + "|REG_USER_ID|REG_DTTM|MOD_USER_ID|MOD_DTTM");
            var insert = Pattern.compile("(?is)^\\s*\\(([^)]+)\\)\\s+VALUES\\s*\\(([^)]+)\\)\\s*$").matcher(remainder);
            if (!insert.matches()) throw new IllegalStateException("정산은 명시된 새 행만 등록할 수 있습니다.");
            var names = Arrays.stream(insert.group(1).toUpperCase(Locale.ROOT).split(",")).map(String::strip).toList();
            var values = insert.group(2).split(",", -1);
            if (!names.containsAll(Arrays.asList(keys.split("\\|"))) || !names.contains("REG_USER_ID")
                    || !names.contains("CORP_CARD_AMT") && !detail
                    || !names.contains("SETL_STAT_CD") && !detail || names.size() != values.length
                    || Arrays.stream(values).anyMatch(v -> !v.strip().matches("(?i)\\?|NULL|CURRENT_TIMESTAMP|'WORK'|'CNFM'|'AUTO'"))) {
                throw new IllegalStateException("정산 식별자·상태·명시 카드액·등록자를 보존해야 합니다.");
            }
        } else if (operation.startsWith("DELETE") && detail) {
            if (!remainder.matches("(?is)\\s+WHERE\\s+PART_ID\\s*=\\s*\\?\\s+AND\\s+SETL_YM\\s*=\\s*\\?\\s*")) {
                throw new IllegalStateException("같은 파트월의 상세 전체집합만 교체할 수 있습니다.");
            }
        } else if (operation.equals("UPDATE") && !detail) {
            if (!remainder.matches("(?is)\\s+SET\\s+" + assignments(fields + "|MOD_USER_ID|MOD_DTTM")
                    + "\\s+WHERE\\s+PART_ID\\s*=\\s*\\?\\s+AND\\s+SETL_YM\\s*=\\s*\\?\\s*")) {
                throw new IllegalStateException("정산 부모의 전체 키와 최초 등록값은 보존해야 합니다.");
            }
        } else throw new IllegalStateException("정산 부모 삭제와 상세 수정은 허용하지 않습니다.");
    }
    private static void requireProgressWrite(String table, String operation, String remainder) {
        String values = switch (table) {
            case "TB_PJ_STEP" -> "PLAN_BGN_DT|PLAN_END_DT|CMPL_DT";
            case "TB_PJ_WEEK_MEMO" -> "THIS_WEEK_CN|NEXT_WEEK_CN|REQ_CN|ACTL_PRGRS_RT";
            default -> "ISSUE_TTL|IMPR_CD|DDLN_DT|ISSUE_STAT_CD|ACTN_CN";
        };
        String keys = switch (table) {
            case "TB_PJ_STEP" -> "PROJ_ID|STEP_SN";
            case "TB_PJ_WEEK_MEMO" -> "PROJ_ID|MEMO_SN";
            default -> "ISSUE_ID|PROJ_ID";
        };
        if (operation.startsWith("INSERT")) {
            String initial = table.equals("TB_PJ_STEP") ? "|PROJ_STEP_CD|STEP_WGHT_RT"
                : table.equals("TB_PJ_WEEK_MEMO") ? "|WRT_DT" : "";
            requireInsert(remainder, keys + "|" + values + initial + "|REG_USER_ID|REG_DTTM|MOD_USER_ID|MOD_DTTM");
            // Only bound single-row VALUES; ISSUE_ID may be omitted to use the installed sequence.
            if (!remainder.matches("(?is)\\s*\\([A-Z_,\\s]+\\)\\s+VALUES\\s*\\(\\s*(?:\\?|NULL|DEFAULT)(?:\\s*,\\s*(?:\\?|NULL|DEFAULT))*\\s*\\)\\s*(?:RETURNING\\s+ISSUE_ID\\s*)?")) {
                throw new IllegalStateException("진행 자료는 바인딩된 새 행만 등록할 수 있습니다.");
            }
        } else {
            String key = table.equals("TB_PJ_STEP") ? "PROJ_ID\\s*=\\s*\\?\\s+AND\\s+STEP_SN\\s*=\\s*\\?"
                : table.equals("TB_PJ_WEEK_MEMO") ? "PROJ_ID\\s*=\\s*\\?\\s+AND\\s+MEMO_SN\\s*=\\s*\\?"
                : "(?:ISSUE_ID\\s*=\\s*\\?\\s+AND\\s+PROJ_ID\\s*=\\s*\\?|PROJ_ID\\s*=\\s*\\?\\s+AND\\s+ISSUE_ID\\s*=\\s*\\?)";
            String item = "(?:" + values + "|MOD_USER_ID|MOD_DTTM)\\s*=\\s*(?:\\?|NULL)";
            if (!operation.equals("UPDATE") || !remainder.matches("(?is)\\s+SET\\s+" + item
                    + "(?:\\s*,\\s*" + item + ")*\\s+WHERE\\s+" + key + "\\s*")) {
                throw new IllegalStateException("진행 자료의 전체 키와 최초 등록값은 보존해야 합니다.");
            }
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
        if (statement.getBoundSql(invocation.getArgs()[1]).getSql().matches("(?is).*\\b(?:INTO|UPDATE)\\s+TB_CO_MON_PL\\b.*")) {
            var value = org.apache.ibatis.reflection.SystemMetaObject.forObject(invocation.getArgs()[1]);
            String month = (String) value.getValue("month"), base = (String) value.getValue("base");
            if (month == null || base == null || !month.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])")
                    || !base.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])") || month.compareTo(base) >= 0
                    || Integer.parseInt(month.substring(0,4)) < Integer.parseInt(base.substring(0,4))-1) {
                throw new IllegalStateException("이관 허용 범위를 확인해 주세요.");
            }
        }
        requireAllowed(statement.getBoundSql(invocation.getArgs()[1]).getSql());
        return invocation.proceed();
    }
}
