package com.sicost.monthclosesops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.monthclosesops.MonthCloseMapper.*;

@Repository
public class MonthCloseRepository {
    private static final List<String> SOURCES=List.of("TB_CL_CLOSE","TB_CL_CLOSE_CHK","TB_CL_CLOSE_HIST","TB_PJ_ASGN","TB_PJ_ASGN_MON","TB_PJ_ASGN_HIST",
        "TB_HR_DEV","TB_HR_DEV_PRC","TB_HR_DEV_STAT","TB_HR_MON_PAY","TB_CO_MON_COST","TB_CO_MON_PL","TB_BL_BILL","TB_BL_RCPT",
        "TB_FI_ITEM","TB_FI_CARD","TB_FI_CARD_MON","TB_FI_CARD_PAY","TB_FI_LOAN","TB_FI_XFER","TB_PJ_PROJ","TB_PJ_PROJ_CHG","TB_PJ_PROJ_PART",
        "TB_PJ_TK_DSTRB","TB_PJ_DRCT_EXPS","TB_BS_PART","TB_BS_PART_STD","TB_CO_PART_SETL","TB_CO_PART_SETL_DTL","TB_CO_PREV_ADJ",
        "TB_FI_CORP_TAX","TB_FI_TAX_PAY","TB_SY_OPER_STD","TB_SY_CODE","TB_SY_CHG_HIST");
    private static final Map<String,Table> WRITES=Map.of(
        "TB_CL_CLOSE",table("TB_CL_CLOSE","CLOSE_YM","CLOSE_STAT_CD|CLOSE_USER_ID|CLOSE_DTTM"),
        "TB_CL_CLOSE_CHK",table("TB_CL_CLOSE_CHK","CLOSE_YM|CHK_ITEM_CD","CHK_STAT_CD|CHK_CNT|CHK_CN|LINK_SCRN_ID|CHK_DTTM"),
        "TB_CL_CLOSE_HIST",table("TB_CL_CLOSE_HIST","CLOSE_YM|HIST_SN","ACT_CD|ACT_RSN|REMAIN_CHK_CN"),
        "TB_PJ_ASGN_MON",table("TB_PJ_ASGN_MON","ASGN_ID|INPUT_YM","DEV_ID|PROJ_ID|INPUT_DAY_CNT|MON_MM|APLY_UPRC|SALES_AMT|FIX_YN"),
        "TB_CO_MON_COST",table("TB_CO_MON_COST","COST_YM|DEV_ID|PROJ_ID|ASGN_ID","ASGN_MM|FREE_MM|SALES_AMT|LABOR_ALLOC_AMT|TEAM_EXPS_AMT|PROJ_EXPS_AMT|DRCT_EXPS_AMT|COST_CLS_CD|CTRT_TYPE_CD|PART_ID|FIX_YN"),
        "TB_CO_MON_PL",table("TB_CO_MON_PL","PL_YM","SALES_AMT|ALLOC_COST_AMT|IDLE_COST_AMT|DRCT_EXPS_AMT|SALES_COST_AMT|GROSS_PRFT_AMT|SGA_INPUT_AMT|INDRCT_LABOR_AMT|RND_AMT|SGA_TOT_AMT|PREV_ADJ_AMT|OPRT_PRFT_AMT|NOPR_INCM_AMT|NOPR_EXPS_AMT|CORP_TAX_AMT|NET_PRFT_AMT|MIGR_YN|FIX_YN"));
    private static Table table(String name,String keys,String fields){return new Table(name,List.of(keys.split("\\|")),List.of(fields.split("\\|")));}
    private final MonthCloseMapper mapper;
    private final ObjectMapper json;
    private final RevisionService revisions;
    public MonthCloseRepository(MonthCloseMapper mapper,ObjectMapper json,RevisionService revisions){this.mapper=mapper;this.json=json;this.revisions=revisions;}
    public Map<String,List<Map<String,Object>>> load(boolean lock) {
        Map<String,List<Map<String,Object>>> result=new TreeMap<>();
        for(String table:SOURCES.stream().sorted().toList()) {
            List<Map<String,Object>> rows=new ArrayList<>();
            for(String text:mapper.rows(new Read(table,lock)))try{rows.add(json.readValue(text,new TypeReference<LinkedHashMap<String,Object>>(){}));}
            catch(java.io.IOException e){throw new IllegalStateException("마감 원천을 읽지 못했습니다.",e);}
            result.put(table,List.copyOf(rows));
        }
        return result;
    }
    public String revision(String month,Map<String,List<Map<String,Object>>> data) {
        var relevant=new TreeMap<>(data);
        // Command receipts are append-only response metadata, not changing business inputs.
        relevant.put("TB_SY_CHG_HIST",data.get("TB_SY_CHG_HIST").stream().filter(r->!"TB_CL_CLOSE_HIST".equals(s(r,"trgt_tbl_nm"))).toList());
        return revisions.revision(Arrays.asList(month,relevant));
    }
    public void requireRevision(String expected,String month,Map<String,List<Map<String,Object>>> data){revisions.requireExpected(expected,revision(month,data));}
    public void save(String table,Map<String,Object> row,WriteContext context,boolean existing) {
        Table definition=required(table);Map<String,Object> keys=new LinkedHashMap<>(),values=new LinkedHashMap<>();
        definition.keys().forEach(k->keys.put(k,typed(k,row.get(k.toLowerCase(Locale.ROOT)))));
        for(String field:definition.fields())if(row.containsKey(field.toLowerCase(Locale.ROOT))&&!(existing&&field.equals("MIGR_YN")))values.put(field,typed(field,row.get(field.toLowerCase(Locale.ROOT))));
        values.put(existing?"MOD_USER_ID":"REG_USER_ID",context.actor());values.put(existing?"MOD_DTTM":"REG_DTTM",context.at());
        if(table.equals("TB_CL_CLOSE_HIST")&&existing)throw new IllegalStateException("추가 전용 마감이력입니다.");
        Write write=new Write(table,context.month(),context.base(),keys,values,null,null);
        requireOne(existing?mapper.update(write):mapper.insert(write));
    }
    public void delete(String table,Map<String,Object> row,WriteContext context) {requireOne(mapper.delete(write(table,row,context,null)));}
    public void release(String table,Map<String,Object> row,WriteContext context,String owner) {requireOne(mapper.release(write(table,row,context,owner)));}
    private Write write(String table,Map<String,Object> row,WriteContext context,String owner) {
        Map<String,Object> keys=new LinkedHashMap<>();required(table).keys().forEach(k->keys.put(k,typed(k,row.get(k.toLowerCase(Locale.ROOT)))));
        return new Write(table,context.month(),context.base(),keys,Map.of("MOD_USER_ID",context.actor(),"MOD_DTTM",context.at()),owner,key(table,row));
    }
    public static String key(String table,Map<String,Object> row){return String.join(":",required(table).keys().stream().map(k->s(row,k.toLowerCase(Locale.ROOT))).toList());}
    private static Table required(String table){Table value=WRITES.get(table);if(value==null)throw new IllegalArgumentException("허용되지 않은 마감 테이블");return value;}
    private static Object typed(String field,Object value) {
        if(value==null)return null;
        if(field.endsWith("DTTM"))return value instanceof LocalDateTime?value:LocalDateTime.parse(value.toString().replace(' ','T'));
        if(field.endsWith("_AMT")||field.endsWith("_MM")||field.endsWith("_CNT")||Set.of("HIST_SN","APLY_UPRC").contains(field))return new BigDecimal(value.toString());
        return value.toString();
    }
    public record WriteContext(String month,String base,String actor,LocalDateTime at) {}
    public static String s(Map<String,Object> row,String field){return row.get(field)==null?null:row.get(field).toString();}
    public static BigDecimal n(Map<String,Object> row,String field){String value=s(row,field);return value==null?null:new BigDecimal(value);}
    public static void requireOne(int count){if(count!=1)throw new IllegalStateException("마감 저장 행수를 확인하지 못했습니다.");}
    public static ApiException conflict(String code,String message){return new ApiException(409,code,message);}
}
