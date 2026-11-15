package com.sicost.common.persistence;

import java.util.*;
import java.util.regex.Pattern;

/** Only the management mapper may use these shapes; ordinary persistence policies remain unchanged. */
public final class MonthClosePersistence {
    private MonthClosePersistence() {}
    private static final Map<String,String> KEYS = Map.of(
        "TB_PJ_ASGN_MON","ASGN_ID|INPUT_YM", "TB_CO_MON_COST","COST_YM|DEV_ID|PROJ_ID|ASGN_ID",
        "TB_CO_MON_PL","PL_YM", "TB_CL_CLOSE","CLOSE_YM", "TB_CL_CLOSE_CHK","CLOSE_YM|CHK_ITEM_CD",
        "TB_CL_CLOSE_HIST","CLOSE_YM|HIST_SN");
    private static final Map<String,String> VALUES = Map.of(
        "TB_PJ_ASGN_MON","DEV_ID|PROJ_ID|INPUT_DAY_CNT|MON_MM|APLY_UPRC|SALES_AMT|FIX_YN",
        "TB_CO_MON_COST","ASGN_MM|FREE_MM|SALES_AMT|LABOR_ALLOC_AMT|TEAM_EXPS_AMT|PROJ_EXPS_AMT|DRCT_EXPS_AMT|COST_CLS_CD|CTRT_TYPE_CD|PART_ID|FIX_YN",
        "TB_CO_MON_PL","SALES_AMT|ALLOC_COST_AMT|IDLE_COST_AMT|DRCT_EXPS_AMT|SALES_COST_AMT|GROSS_PRFT_AMT|SGA_INPUT_AMT|INDRCT_LABOR_AMT|RND_AMT|SGA_TOT_AMT|PREV_ADJ_AMT|OPRT_PRFT_AMT|NOPR_INCM_AMT|NOPR_EXPS_AMT|CORP_TAX_AMT|NET_PRFT_AMT|MIGR_YN|FIX_YN",
        "TB_CL_CLOSE","CLOSE_STAT_CD|CLOSE_USER_ID|CLOSE_DTTM",
        "TB_CL_CLOSE_CHK","CHK_STAT_CD|CHK_CNT|CHK_CN|LINK_SCRN_ID|CHK_DTTM",
        "TB_CL_CLOSE_HIST","ACT_CD|ACT_RSN|REMAIN_CHK_CN");
    public static boolean supports(String sql) {
        var target=Pattern.compile("(?is)^\\s*(INSERT INTO|UPDATE|DELETE FROM)\\s+(TB_[A-Z_]+)\\b(.*)$").matcher(sql.replaceAll("\\s+"," "));
        if (!target.matches()) return false;
        String table=target.group(2).toUpperCase(Locale.ROOT);
        if (!KEYS.containsKey(table)) return false;
        requireAllowed(sql);
        return true;
    }
    public static void requireAllowed(String sql) {
        String text=normalized(sql);
        var target=Pattern.compile("(INSERT INTO|UPDATE|DELETE FROM) (TB_[A-Z_]+)\\b(.*)").matcher(text);
        if (!target.matches() || !KEYS.containsKey(target.group(2))) throw denied();
        String table=target.group(2),operation=target.group(1),tail=target.group(3);
        String keys=KEYS.get(table),fields=VALUES.get(table);
        boolean copy=Set.of("TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL").contains(table);
        if (operation.equals("INSERT INTO")) {
            var insert=Pattern.compile(" \\(([^)]+)\\) VALUES ?\\(([^)]+)\\)").matcher(tail);
            if (!insert.matches()) throw denied();
            var names=Arrays.stream(insert.group(1).split(",")).map(String::strip).toList();
            var values=Arrays.stream(insert.group(2).split(",")).map(String::strip).toList();
            var required=new HashSet<>(Arrays.asList(keys.split("\\|")));
            required.addAll(List.of("REG_USER_ID","REG_DTTM"));
            if (copy) required.add("FIX_YN");
            if (table.equals("TB_CO_MON_PL")) required.add("MIGR_YN");
            if (!names.containsAll(required) || names.size()!=new HashSet<>(names).size() || names.size()!=values.size()
                    || names.stream().anyMatch(n->!n.matches(keys+"|"+fields+"|REG_USER_ID|REG_DTTM"))
                    || values.stream().anyMatch(v->!v.matches("\\?|NULL|'[A-Z_]+'"))) throw denied();
            if (copy && !"'Y'".equals(values.get(names.indexOf("FIX_YN")))) throw denied();
            if (table.equals("TB_CO_MON_PL") && !"'N'".equals(values.get(names.indexOf("MIGR_YN")))) throw denied();
            return;
        }
        if (table.equals("TB_CL_CLOSE_HIST")) throw denied();
        String keyPredicate=String.join(" = \\? AND ",keys.split("\\|"))+" = \\?";
        if (copy && operation.equals("UPDATE") && tail.matches(" SET FIX_YN = 'N', MOD_USER_ID = \\?, MOD_DTTM = \\? WHERE "+keyPredicate
                +" AND FIX_YN = 'Y'"+(table.equals("TB_CO_MON_PL")?" AND MIGR_YN = 'N'":"")
                +" AND EXISTS \\(SELECT 1 FROM TB_SY_CHG_HIST H WHERE H.TRGT_TBL_NM = '"+table+"' AND H.TRGT_KEY = \\? AND H.CHG_ITEM_NM = 'FIX_YN' AND H.BFR_VAL = 'N' AND H.AFT_VAL = 'Y' AND H.CHG_RSN = \\?\\)")) return;
        if (operation.equals("DELETE FROM")) {
            if (!copy || !tail.matches(" WHERE "+keyPredicate+" AND FIX_YN = 'N'"+(table.equals("TB_CO_MON_PL")?" AND MIGR_YN = 'N'":""))) throw denied();
            return;
        }
        String allowed=copy?fields:fields+"|MOD_USER_ID|MOD_DTTM";
        if (copy) allowed+="|MOD_USER_ID|MOD_DTTM";
        String assignment="(?:"+allowed+") = (?:\\?|NULL|'[A-Z_]+')";
        if (!tail.matches(" SET "+assignment+"(?:, "+assignment+")* WHERE "+keyPredicate
                +(copy?" AND FIX_YN = 'N'":"")+(table.equals("TB_CO_MON_PL")?" AND MIGR_YN = 'N'":""))) throw denied();
        String set=tail.substring(0,tail.indexOf(" WHERE "));
        if (copy && set.contains("FIX_YN") && !set.matches(".*\\bFIX_YN = 'Y'(?:,.*)?")) throw denied();
        if (table.equals("TB_CO_MON_PL") && tail.substring(0,tail.indexOf(" WHERE ")).contains("MIGR_YN")) throw denied();
    }
    public static void requireScope(org.apache.ibatis.mapping.BoundSql bound, Object parameter) {
        var meta=org.apache.ibatis.reflection.SystemMetaObject.forObject(parameter);
        if (!meta.hasGetter("month") || !meta.hasGetter("base")) throw denied();
        Object month=meta.getValue("month"),base=meta.getValue("base");
        String pattern="(?!0000)[0-9]{4}(0[1-9]|1[0-2])";
        if (!(month instanceof String selected) || !(base instanceof String start)
                || !selected.matches(pattern) || !start.matches(pattern) || selected.compareTo(start)<0) throw denied();
        String text=normalized(bound.getSql());
        var target=Pattern.compile("(?:INSERT INTO|UPDATE|DELETE FROM) (TB_[A-Z_]+)\\b").matcher(text);
        if (!target.find() || !KEYS.containsKey(target.group(1))) throw denied();
        String table=target.group(1);
        String monthColumn=switch(table) {
            case "TB_PJ_ASGN_MON" -> "INPUT_YM";
            case "TB_CO_MON_COST" -> "COST_YM";
            case "TB_CO_MON_PL" -> "PL_YM";
            default -> "CLOSE_YM";
        };
        int index;
        if (text.startsWith("INSERT")) {
            int begin=text.indexOf('('),end=text.indexOf(')',begin);
            var columns=Arrays.stream(text.substring(begin+1,end).split(",")).map(String::strip).toList();
            var values=text.substring(text.indexOf('(',end)+1,text.lastIndexOf(')')).split(",");
            int column=columns.indexOf(monthColumn);
            if(column<0 || !values[column].strip().equals("?")) throw denied();
            index=0;for(int i=0;i<column;i++) if(values[i].strip().equals("?")) index++;
        } else {
            var predicate=Pattern.compile("\\b"+monthColumn+" = \\?").matcher(text);
            if(!predicate.find(text.indexOf(" WHERE "))) throw denied();
            index=(int)text.substring(0,predicate.end()-1).chars().filter(c->c=='?').count();
        }
        if (!month.equals(value(bound,meta,index))) throw denied();
        if(text.contains(" AND EXISTS (SELECT 1 FROM TB_SY_CHG_HIST H")) {
            String owner=Objects.toString(value(bound,meta,bound.getParameterMappings().size()-1),"");
            if(!owner.matches(pattern+":[1-9][0-9]{0,4}") || !owner.startsWith(month+":")) throw denied();
            int first=(int)text.substring(0,text.indexOf(" WHERE ")).chars().filter(c->c=='?').count();
            var keys=new ArrayList<String>();
            for(int i=0;i<KEYS.get(table).split("\\|").length;i++)keys.add(Objects.toString(value(bound,meta,first+i),""));
            if(!String.join(":",keys).equals(value(bound,meta,bound.getParameterMappings().size()-2))) throw denied();
        }
    }
    private static Object value(org.apache.ibatis.mapping.BoundSql bound,org.apache.ibatis.reflection.MetaObject meta,int index) {
        String property=bound.getParameterMappings().get(index).getProperty();
        return bound.hasAdditionalParameter(property)?bound.getAdditionalParameter(property):meta.getValue(property);
    }
    private static String normalized(String sql) {
        return sql.strip().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT)
            .replaceAll("\\s*=\\s*"," = ").replaceAll("\\s*,\\s*",", ").replaceAll("(TB_[A-Z_]+)\\(","$1 (");
    }
    private static IllegalStateException denied() { return new IllegalStateException("선택월의 승인된 마감 저장만 허용합니다."); }
}
