package com.sicost.monthclosesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.history.ChangeHistoryWriter.Change;
import com.sicost.salespartsettlementsops.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.monthclosesops.MonthCloseDtos.*;
import static com.sicost.monthclosesops.MonthCloseRepository.*;
import static com.sicost.monthclosesops.MonthCloseCalculation.row;

@Service
public class MonthCloseService {
    public static final List<String> INVALIDATED=List.of("month-close","period-context","payroll","assignments","monthly-costs","projects","profit-loss","corporate-tax","settlements","cash-flow","forecasts");
    private static final List<String> COPIES=List.of("TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MonthCloseRepository repository;
    private final MonthCloseCalculation calculation;
    private final CloseCheckService checks;
    private final MonthCloseBoundary boundary;
    private final SettlementClosingService settlements;
    private final ChangeHistoryWriter history;
    private final AccessLogWriter access;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final CommitInvalidation invalidation;
    public MonthCloseService(MonthCloseRepository repository,MonthCloseCalculation calculation,CloseCheckService checks,MonthCloseBoundary boundary,
        SettlementClosingService settlements,ChangeHistoryWriter history,AccessLogWriter access,CurrentUserProvider users,Clock clock,CommitInvalidation invalidation) {
        this.repository=repository;this.calculation=calculation;this.checks=checks;this.boundary=boundary;this.settlements=settlements;this.history=history;this.access=access;this.users=users;this.clock=clock;this.invalidation=invalidation;
    }
    @Transactional
    public ChecksSaved saveMonthCloseChecks(String month,JsonNode body) {
        MonthCloseInput.month(month);var command=MonthCloseInput.parse(body,"CHECKS");boundary.exclusive();
        var data=repository.load(true);String base=scope(month,data);requireWork(month,data);repository.requireRevision(command.expectedRevision(),month,data);
        var source=calculation.sources(month,data);var latest=checks.calculate(month,data,source);CloseCheckService.validate(latest);
        var context=context(month,base);ensureParent(data,context);saveChecks(data,latest,context);
        var after=repository.load(false);var stored=storedChecks(month,after);
        if(stored.size()!=6)throw new IllegalStateException("6개 점검 저장을 확인하지 못했습니다.");
        INVALIDATED.forEach(invalidation::afterCommit);
        return new ChecksSaved(month,"WORK",repository.revision(month,after),stored,TIME.format(context.at()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview createMonthClosePreview(String month,JsonNode body) {
        MonthCloseInput.month(month);var command=MonthCloseInput.parse(body,"PREVIEW");var data=repository.load(false);scope(month,data);
        repository.requireRevision(command.expectedRevision(),month,data);requireWork(month,data);requireNext(month,data);
        return preview(month,data,calculation.plan(month,data));
    }
    @Transactional
    public Commit saveMonthClose(String month,JsonNode body,HttpServletRequest request) {
        MonthCloseInput.month(month);var command=MonthCloseInput.parse(body,"CLOSE");boundary.exclusive();var data=repository.load(true);
        String base=scope(month,data);Commit replay=replay(month,"CLOSE",command,data);if(replay!=null)return replay;
        requireWork(month,data);requireNext(month,data);repository.requireRevision(command.expectedRevision(),month,data);
        var plan=calculation.plan(month,data);var preview=preview(month,data,plan);
        if(!preview.blockingIssues().isEmpty())throw issueConflict(preview.blockingIssues());
        if(preview.warningCount()>0&&!command.accepted())throw conflict("WARNINGS_NOT_ACCEPTED","남은 점검을 명시적으로 확인해 주세요.");
        var context=context(month,base);int sequence=nextSequence(month,data);String owner=context.month()+":"+sequence;
        String remaining=String.join(",",preview.remainingCheckNames());if(remaining.codePointCount(0,remaining.length())>500)throw conflict("STATE_CONFLICT","남은 점검 이름이 500자를 초과했습니다.");
        ensureParent(data,context);
        replaceCopies(data,plan.candidates(),context,owner);
        for(Part part:preview.parts())if(part.willAutoConfirm())settlements.confirmForClose(new SettlementCommandService.CloseContext(part.partId(),month,context.actor(),context.at()));
        saveChecks(data,preview.checks(),context);
        repository.save("TB_CL_CLOSE",row("close_ym",context.month(),"close_stat_cd","CLOSE","close_user_id",context.actor(),"close_dttm",context.at()),context,true);
        History entry=new History(month,sequence,"CLOSE",context.actor(),TIME.format(context.at()),null,remaining.isEmpty()?null:remaining);
        saveHistory(entry,context);
        access.appendMonthClose(new AccessLogWriter.MonthCloseEvent("CLOSE",context.month(),sequence),context.actor(),context.at(),request);
        var after=repository.load(false);
        Commit result=new Commit(month,"CLOSE",repository.revision(month,after),command.operationId(),entry,false,preview.snapshots(),true,INVALIDATED);
        receipt(result,command,context);INVALIDATED.forEach(invalidation::afterCommit);
        return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReopenPreview createMonthCloseReopenPreview(String month,JsonNode body) {
        MonthCloseInput.month(month);var command=MonthCloseInput.parse(body,"REOPEN_PREVIEW");var data=repository.load(false);scope(month,data);
        repository.requireRevision(command.expectedRevision(),month,data);return reopenPreview(month,data);
    }
    @Transactional
    public Commit saveMonthCloseReopen(String month,JsonNode body,HttpServletRequest request) {
        MonthCloseInput.month(month);var command=MonthCloseInput.parse(body,"OPEN");boundary.exclusive();var data=repository.load(true);
        String base=scope(month,data);Commit replay=replay(month,"OPEN",command,data);if(replay!=null)return replay;
        repository.requireRevision(command.expectedRevision(),month,data);var preview=reopenPreview(month,data);
        if(!preview.canReopen())throw issueConflict(preview.blockingIssues());
        if(preview.adjustmentCount()>0&&!command.accepted())throw conflict("ADJUSTMENTS_NOT_ACCEPTED","모든 후속 조정 영향을 명시적으로 확인해 주세요.");
        var context=context(month,base);int sequence=nextSequence(month,data);String closeOwner=lastCloseOwner(month,data),openOwner=context.month()+":"+sequence;
        for(String table:COPIES)for(var saved:monthRows(table,context.month(),data))if(owned(table,saved,closeOwner,data)) {
            repository.release(table,saved,context,closeOwner);
            history.appendMonthClose(new Change(table,key(table,saved),"UPD","FIX_YN","Y","N",openOwner),context.actor(),context.at());
        }
        repository.save("TB_CL_CLOSE",row("close_ym",context.month(),"close_stat_cd","WORK","close_user_id",null,"close_dttm",null),context,true);
        History entry=new History(month,sequence,"OPEN",context.actor(),TIME.format(context.at()),command.reason(),null);saveHistory(entry,context);
        access.appendMonthClose(new AccessLogWriter.MonthCloseEvent("UNCLOSE",context.month(),sequence),context.actor(),context.at(),request);
        var after=repository.load(false);
        Commit result=new Commit(month,"WORK",repository.revision(month,after),command.operationId(),entry,false,preview.snapshots(),true,INVALIDATED);
        receipt(result,command,context);INVALIDATED.forEach(invalidation::afterCommit);return result;
    }
    public Preview preview(String month,Map<String,List<Map<String,Object>>> data,MonthCloseCalculation.Plan plan) {
        var latest=checks.calculate(month,data,plan.sources());CloseCheckService.validate(latest);
        var names=latest.stream().filter(c->"WARN".equals(c.status())).map(Check::name).toList();
        List<SnapshotCount> counts=new ArrayList<>();
        for(String table:COPIES)counts.add(new SnapshotCount(table,(int)plan.candidates().stream().filter(c->table.equals(c.table())).count(),(int)monthRows(table,month.replace("-",""),data).stream().filter(r->"Y".equals(s(r,"fix_yn"))).count()));
        int automatic=(int)plan.parts().stream().filter(Part::willAutoConfirm).count();
        counts.add(new SnapshotCount("TB_CO_PART_SETL",automatic,0));
        int detail=0;for(Part part:plan.parts())if(part.willAutoConfirm())detail+=settlements.previewSettlement(part.partId(),month).items().size();
        counts.add(new SnapshotCount("TB_CO_PART_SETL_DTL",detail,0));
        return new Preview(month,repository.revision(month,data),plan.issues().isEmpty(),latest,names.size(),names,plan.parts(),plan.parts().size(),automatic,List.copyOf(counts),plan.estimated(),plan.cashImpact(),plan.issues());
    }
    public ReopenPreview reopenPreview(String month,Map<String,List<Map<String,Object>>> data) {
        String db=month.replace("-","");var parent=parent(db,data);
        if(parent==null||!"CLOSE".equals(s(parent,"close_stat_cd")))throw new ApiException(404,"NOT_FOUND","마감된 월이 아닙니다.");
        List<Issue> issues=new ArrayList<>();
        if(!Objects.equals(db,latest(data)))issues.add(new Issue("NOT_LATEST_CLOSE","전체 기간의 가장 최근 마감월만 해제할 수 있습니다.",month,"TB_CL_CLOSE",db,null));
        String owner=lastCloseOwner(month,data);List<SnapshotCount> counts=new ArrayList<>();
        for(String table:COPIES) {
            var fixed=monthRows(table,db,data).stream().filter(r->"Y".equals(s(r,"fix_yn"))).toList();
            int owned=(int)fixed.stream().filter(r->owned(table,r,owner,data)).count();
            String recorded=receiptValue(owner,table+".rowCount",data),independent=receiptValue(owner,table+".preservedIndependentFixedCount",data);
            if(owner==null||recorded==null||independent==null||owned!=Integer.parseInt(recorded)||fixed.size()!=owned+Integer.parseInt(independent))
                issues.add(new Issue("SNAPSHOT_OWNERSHIP_UNKNOWN",month+" 마감 당시 고정한 자료를 확인할 수 없어 해제할 수 없습니다. 마감 이력을 확인해 주세요.",month,table,db,"FIX_YN"));
            counts.add(new SnapshotCount(table,owned,fixed.size()-owned));
        }
        List<Adjustment> adjustments=data.get("TB_CO_PREV_ADJ").stream().filter(r->db.equals(s(r,"trgt_ym"))&&s(r,"aply_ym").compareTo(db)>0)
            .sorted(Comparator.comparing((Map<String,Object> r)->s(r,"aply_ym")).thenComparing(r->n(r,"adj_id")))
            .map(r->new Adjustment(s(r,"adj_id"),month,MonthCloseCalculation.api(s(r,"aply_ym")),s(r,"adj_amt"),s(r,"adj_rsn"))).toList();
        String amount=adjustments.stream().map(a->new BigDecimal(a.amount())).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString();
        return new ReopenPreview(month,repository.revision(month,data),issues.isEmpty(),s(parent,"close_user_id"),time(s(parent,"close_dttm")),List.copyOf(counts),true,adjustments,adjustments.size(),amount,adjustments.stream().map(Adjustment::applyMonth).distinct().toList(),List.copyOf(issues));
    }
    private void replaceCopies(Map<String,List<Map<String,Object>>> data,List<MonthCloseCalculation.Candidate> candidates,WriteContext context,String owner) {
        for(String table:COPIES) {
            Map<String,Map<String,Object>> desired=new LinkedHashMap<>();candidates.stream().filter(c->table.equals(c.table())).forEach(c->desired.put(key(table,c.row()),c.row()));
            for(var old:monthRows(table,context.month(),data))if(!"Y".equals(s(old,"fix_yn"))&&!desired.containsKey(key(table,old))) {
                auditFields(table,old,Map.of(),context,owner);repository.delete(table,old,context);
            }
            for(var candidate:desired.values()) {
                var old=monthRows(table,context.month(),data).stream().filter(r->key(table,r).equals(key(table,candidate))).findFirst().orElse(null);
                if(old!=null&&"Y".equals(s(old,"fix_yn")))continue;
                auditFields(table,old==null?Map.of():old,candidate,context,owner);
                repository.save(table,candidate,context,old!=null);
                history.appendMonthClose(new Change(table,key(table,candidate),"UPD","FIX_YN","N","Y",owner),context.actor(),context.at());
            }
        }
    }
    private void auditFields(String table,Map<String,Object> before,Map<String,Object> after,WriteContext context,String owner) {
        Set<String> fields=new TreeSet<>(before.keySet());fields.addAll(after.keySet());
        String target=key(table,after.isEmpty()?before:after);
        for(String field:fields) {
            if(field.equals("fix_yn")||field.startsWith("reg_")||field.startsWith("mod_")||Set.of("asgn_id","input_ym","cost_ym","pl_ym").contains(field)||table.equals("TB_CO_MON_COST")&&Set.of("dev_id","proj_id").contains(field))continue;
            String a=s(before,field),b=s(after,field);if(Objects.equals(a,b))continue;
            history.appendMonthClose(new Change(table,target,before.isEmpty()?"NEW":after.isEmpty()?"DEL":"UPD",field.toUpperCase(Locale.ROOT),a,b,owner),context.actor(),context.at());
        }
    }
    private void ensureParent(Map<String,List<Map<String,Object>>> data,WriteContext context) {
        if(parent(context.month(),data)==null)repository.save("TB_CL_CLOSE",row("close_ym",context.month(),"close_stat_cd","WORK","close_user_id",null,"close_dttm",null),context,false);
    }
    private void saveChecks(Map<String,List<Map<String,Object>>> data,List<Check> latest,WriteContext context) {
        CloseCheckService.validate(latest);
        for(Check c:latest)repository.save("TB_CL_CLOSE_CHK",row("close_ym",context.month(),"chk_item_cd",c.code(),"chk_stat_cd",c.status(),"chk_cnt",c.count(),"chk_cn",c.description(),"link_scrn_id",c.linkScreenId(),"chk_dttm",context.at()),context,data.get("TB_CL_CLOSE_CHK").stream().anyMatch(r->context.month().equals(s(r,"close_ym"))&&c.code().equals(s(r,"chk_item_cd"))));
    }
    private void saveHistory(History entry,WriteContext context){repository.save("TB_CL_CLOSE_HIST",row("close_ym",context.month(),"hist_sn",entry.historySequence(),"act_cd",entry.action(),"act_rsn",entry.reason(),"remain_chk_cn",entry.remainingChecks()),context,false);}
    private void receipt(Commit result,MonthCloseInput.Command command,WriteContext context) {
        String owner=context.month()+":"+result.history().historySequence();Map<String,String> fields=new LinkedHashMap<>();
        fields.put("operationId",result.operationId());fields.put("expectedRevision",command.expectedRevision());fields.put("ACT_CD",result.history().action());
        fields.put(result.status().equals("CLOSE")?"acceptWarnings":"acceptAdjustments",command.accepted().toString());
        fields.put("status",result.status());fields.put("revision",result.revision());fields.put("settlementsPreserved",Boolean.toString(result.settlementsPreserved()));
        for(var copy:result.snapshots()){fields.put(copy.table()+".rowCount",Integer.toString(copy.rowCount()));fields.put(copy.table()+".preservedIndependentFixedCount",Integer.toString(copy.preservedIndependentFixedCount()));}
        fields.forEach((field,value)->history.appendMonthClose(new Change("TB_CL_CLOSE_HIST",owner,"NEW",field,null,value,null),context.actor(),context.at()));
    }
    private Commit replay(String month,String action,MonthCloseInput.Command command,Map<String,List<Map<String,Object>>> data) {
        var matches=data.get("TB_SY_CHG_HIST").stream().filter(r->"TB_CL_CLOSE_HIST".equals(s(r,"trgt_tbl_nm"))&&"operationId".equals(s(r,"chg_item_nm"))&&command.operationId().equals(s(r,"aft_val"))).toList();
        if(matches.isEmpty())return null;if(matches.size()!=1)throw conflict("OPERATION_CONFLICT","명령 식별자가 충돌했습니다.");
        String owner=s(matches.get(0),"trgt_key"),db=month.replace("-","");
        var previous=data.get("TB_CL_CLOSE_HIST").stream().filter(r->(s(r,"close_ym")+":"+s(r,"hist_sn")).equals(owner)).findFirst().orElse(null);
        int max=data.get("TB_CL_CLOSE_HIST").stream().filter(r->db.equals(s(r,"close_ym"))).mapToInt(r->n(r,"hist_sn").intValueExact()).max().orElse(0);
        if(previous==null||!owner.startsWith(db+":")||n(previous,"hist_sn").intValueExact()!=max||!action.equals(s(previous,"act_cd"))
            ||!command.expectedRevision().equals(receiptValue(owner,"expectedRevision",data))||!command.accepted().toString().equals(receiptValue(owner,action.equals("CLOSE")?"acceptWarnings":"acceptAdjustments",data))||!Objects.equals(command.reason(),s(previous,"act_rsn")))throw conflict("OPERATION_CONFLICT","다른 입력 또는 더 이상 최신이 아닌 명령입니다.");
        String state=receiptValue(owner,"status",data);var parent=parent(db,data);
        if(parent==null||!Objects.equals(state,s(parent,"close_stat_cd")))throw conflict("OPERATION_CONFLICT","이 명령 이후 마감 상태가 변경되었습니다.");
        List<SnapshotCount> counts=new ArrayList<>();for(String table:List.of("TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL","TB_CO_PART_SETL","TB_CO_PART_SETL_DTL")) {
            String count=receiptValue(owner,table+".rowCount",data);if(count!=null)counts.add(new SnapshotCount(table,Integer.parseInt(count),Integer.parseInt(receiptValue(owner,table+".preservedIndependentFixedCount",data))));
        }
        History entry=new History(month,max,action,s(previous,"reg_user_id"),time(s(previous,"reg_dttm")),s(previous,"act_rsn"),s(previous,"remain_chk_cn"));
        return new Commit(month,state,receiptValue(owner,"revision",data),command.operationId(),entry,true,List.copyOf(counts),true,INVALIDATED);
    }
    private static String receiptValue(String owner,String field,Map<String,List<Map<String,Object>>> data) {
        return data.get("TB_SY_CHG_HIST").stream().filter(r->"TB_CL_CLOSE_HIST".equals(s(r,"trgt_tbl_nm"))&&Objects.equals(owner,s(r,"trgt_key"))&&field.equals(s(r,"chg_item_nm"))).map(r->s(r,"aft_val")).findFirst().orElse(null);
    }
    private static String lastCloseOwner(String month,Map<String,List<Map<String,Object>>> data) {
        String db=month.replace("-","");return data.get("TB_CL_CLOSE_HIST").stream().filter(r->db.equals(s(r,"close_ym"))&&"CLOSE".equals(s(r,"act_cd"))).max(Comparator.comparing(r->n(r,"hist_sn"))).map(r->db+":"+s(r,"hist_sn")).orElse(null);
    }
    private static boolean owned(String table,Map<String,Object> copy,String owner,Map<String,List<Map<String,Object>>> data) {
        return owner!=null&&"Y".equals(s(copy,"fix_yn"))&&data.get("TB_SY_CHG_HIST").stream().anyMatch(r->table.equals(s(r,"trgt_tbl_nm"))&&key(table,copy).equals(s(r,"trgt_key"))&&"FIX_YN".equals(s(r,"chg_item_nm"))&&"N".equals(s(r,"bfr_val"))&&"Y".equals(s(r,"aft_val"))&&owner.equals(s(r,"chg_rsn")));
    }
    public String scope(String month,Map<String,List<Map<String,Object>>> data) {
        YearMonth selected=MonthCloseInput.month(month);String raw=MonthCloseCalculation.standard(data,"CASH_BASE_YM");
        if(raw==null)throw new IllegalStateException("운영 시작월이 없습니다.");String base=new BigDecimal(raw).toBigIntegerExact().toString();
        if(selected.isBefore(YearMonth.parse(MonthCloseCalculation.api(base)))||selected.isAfter(YearMonth.from(clock.instant().atZone(TimeConfiguration.SEOUL))))throw conflict("PERIOD_OUT_OF_SCOPE","운영 시작월부터 현재월까지 관리할 수 있습니다.");
        return base;
    }
    public static String next(Map<String,List<Map<String,Object>>> data,String base) {
        YearMonth expected=YearMonth.parse(MonthCloseCalculation.api(base));
        for(String db:data.get("TB_CL_CLOSE").stream().filter(r->"CLOSE".equals(s(r,"close_stat_cd"))).map(r->s(r,"close_ym")).filter(m->m.compareTo(base)>=0).sorted().toList()) {
            String wanted=expected.toString().replace("-","");if(!wanted.equals(db))throw new ApiException(409,"CLOSE_SEQUENCE_GAP","마감월 사이에 공백이 있습니다.",null,Map.of("missingMonth",expected.toString(),"closedMonth",MonthCloseCalculation.api(db)));
            expected=expected.plusMonths(1);
        }
        return expected.toString();
    }
    private void requireNext(String month,Map<String,List<Map<String,Object>>> data){String wanted=next(data,scope(month,data));if(!wanted.equals(month))throw conflict("NOT_NEXT_CLOSE_MONTH","다음 마감월 "+wanted+"부터 처리해 주세요.");}
    private static String latest(Map<String,List<Map<String,Object>>> data){return data.get("TB_CL_CLOSE").stream().filter(r->"CLOSE".equals(s(r,"close_stat_cd"))).map(r->s(r,"close_ym")).max(String::compareTo).orElse(null);}
    private static Map<String,Object> parent(String month,Map<String,List<Map<String,Object>>> data){return data.get("TB_CL_CLOSE").stream().filter(r->month.equals(s(r,"close_ym"))).findFirst().orElse(null);}
    private static void requireWork(String month,Map<String,List<Map<String,Object>>> data){var row=parent(month.replace("-",""),data);if(row!=null&&!"WORK".equals(s(row,"close_stat_cd")))throw conflict("CLOSED_MONTH","마감된 월은 다시 점검하거나 마감할 수 없습니다.");}
    public static int nextSequence(String month,Map<String,List<Map<String,Object>>> data){int max=data.get("TB_CL_CLOSE_HIST").stream().filter(r->month.replace("-","").equals(s(r,"close_ym"))).mapToInt(r->n(r,"hist_sn").intValueExact()).max().orElse(0);if(max>=99999)throw conflict("ID_EXHAUSTED","월 마감 이력번호를 모두 사용했습니다.");return max+1;}
    private WriteContext context(String month,String base){return new WriteContext(month.replace("-",""),base,users.userId(),LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL).truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    public static List<Map<String,Object>> monthRows(String table,String month,Map<String,List<Map<String,Object>>> data){String field=switch(table){case "TB_PJ_ASGN_MON"->"input_ym";case "TB_CO_MON_COST"->"cost_ym";default->"pl_ym";};return data.get(table).stream().filter(r->month.equals(s(r,field))).toList();}
    public static List<Check> storedChecks(String month,Map<String,List<Map<String,Object>>> data) {
        String db=month.replace("-","");List<Check> result=new ArrayList<>();
        for(int i=0;i<6;i++){String code=CloseCheckService.CODES.get(i);int index=i;data.get("TB_CL_CLOSE_CHK").stream().filter(r->db.equals(s(r,"close_ym"))&&code.equals(s(r,"chk_item_cd"))).findFirst().ifPresent(r->result.add(new Check(code,CloseCheckService.NAMES.get(index),s(r,"chk_stat_cd"),n(r,"chk_cnt")==null?null:n(r,"chk_cnt").longValueExact(),s(r,"chk_cn"),time(s(r,"chk_dttm")),s(r,"link_scrn_id"),month,"")));}
        return List.copyOf(result);
    }
    private static String time(String value){return value==null?null:TIME.format(LocalDateTime.parse(value.replace(' ','T')));}
    private static ApiException issueConflict(List<Issue> issues){Issue first=issues.get(0);return new ApiException(409,first.code(),first.message(),first.field(),Map.of("issues",issues));}
}
