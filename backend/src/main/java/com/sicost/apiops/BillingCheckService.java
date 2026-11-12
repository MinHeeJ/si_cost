package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.BillingReviewDtos.*;

@Service
public class BillingCheckService {
    private final BillingReviewRepository repository;
    private final AssignmentMonthlyService monthly;
    private final CurrentUserProvider users;
    private final Clock clock;
    public BillingCheckService(BillingReviewRepository repository,AssignmentMonthlyService monthly,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.monthly=monthly;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ChecksPage getBillingChecks(PageQuery query) {
        users.currentUser();
        YearMonth today=YearMonth.from(ProjectInputPolicy.today(clock)),base=base();
        List<String> months=new ArrayList<>();
        YearMonth first=base.isAfter(YearMonth.of(today.getYear(),1))?base:YearMonth.of(today.getYear(),1);
        for(YearMonth month=first;!month.isAfter(today);month=month.plusMonths(1))months.add(month.toString());
        var closes=repository.closes(db(first.toString()),db(today.toString()));
        String defaultMonth=months.isEmpty()?null:closes.stream().filter(c->"CLOSE".equals(c.status()))
            .map(c->DatabaseMonthCodec.toApi(c.month())).filter(months::contains).max(String::compareTo).orElse(today.toString());
        String month=query.month()==null?(defaultMonth==null?today.toString():defaultMonth):query.month();
        if(query.month()!=null&&!months.contains(month))throw new ApiException(400,"INVALID_PERIOD","올해 운영 시작월부터 이번 달까지 선택해 주세요.","month",null);
        if(months.isEmpty())return new ChecksPage(month,months,null,false,summary(List.of()),List.of(),0,query.page(),query.size(),List.of("운영 시작 전입니다."));
        List<CheckRow> rows=rows(month);
        return new ChecksPage(month,List.copyOf(months),defaultMonth,!closed(month),summary(rows),page(rows,query),rows.size(),query.page(),query.size(),List.of());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<CheckRow> allChecks(String month) {
        var page=getBillingChecks(new PageQuery(month,1,100));
        return page.availableMonths().isEmpty()?page.items():rows(page.month());
    }
    private List<CheckRow> rows(String month) {
        BigDecimal minimum=repository.standard("BILL_MISS_MIN",db(month)),rate=repository.standard("BILL_MISS_RT",db(month));
        if(minimum==null||rate==null||minimum.signum()<0||rate.signum()<0)throw settings();
        var result=new ArrayList<CheckRow>();
        for(Project project:repository.projects()) {
            CheckRow row=evaluate(project,month,minimum,rate);if(row!=null)result.add(row);
        }
        result.sort(Comparator.comparingInt((CheckRow r)->rank(r.verdict())).thenComparing(CheckRow::projectName).thenComparing(CheckRow::projectId));
        return List.copyOf(result);
    }
    public CheckRow evaluate(Project project,String month,BigDecimal minimum,BigDecimal rate) {
        List<BillingSource> sources=new ArrayList<>();Map<String,YearMonth> firstPaid=new HashMap<>();
        for(AssignmentReference reference:repository.assignments(project.id())) {
            var source=monthly.required(reference.id());
            var histories=monthly.mapper().histories(reference.id());
            var preview=monthly.preview(source,histories,project.status(),project.contractType());
            if("ACT".equals(source.status())&&!"FREE".equals(source.inputType()))firstPaid.merge(source.developerId(),YearMonth.from(source.startDate()),BillingCheckService::earlier);
            for(var value:preview) {
                boolean protectedSource=monthly.protectedMonth(value.month(),value.fixed());
                if(!protectedSource&&(!"ACT".equals(source.status())||"FREE".equals(source.inputType())))continue;
                if(value.appliedUnitPrice()==null||new BigDecimal(value.monthlyMm()).signum()<=0)continue;
                BigDecimal price=new BigDecimal(value.appliedUnitPrice());
                if("FREE".equals(source.inputType())&&(!protectedSource||price.signum()==0))continue;
                firstPaid.merge(source.developerId(),YearMonth.parse(value.month()),BillingCheckService::earlier);
                if(value.month().equals(month)) {
                    BigDecimal amount=price.multiply(new BigDecimal(value.monthlyMm())).setScale(0,RoundingMode.DOWN);
                    sources.add(new BillingSource(source.id(),source.developerId(),reference.developerName(),reference.managementNo(),month,
                        decimal(price),value.monthlyMm(),decimal(amount),protectedSource));
                }
            }
        }
        CheckKey key=new CheckKey(project.id(),db(month));var bills=repository.bills(key);
        if(sources.isEmpty()&&bills.isEmpty())return null;
        BigDecimal e=sources.stream().map(s->new BigDecimal(s.supplyAmount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal b=bills.stream().map(CheckBill::supplyAmount).reduce(BigDecimal.ZERO,BigDecimal::add),d=e.subtract(b);
        BigDecimal tolerance=minimum.max(e.multiply(rate).movePointLeft(2));
        String verdict=bills.isEmpty()?"청구 없음":d.abs().compareTo(tolerance)<=0?"맞음":d.signum()>0?"적게 청구":"많이 청구";
        String explanation="E="+decimal(e)+" · B="+decimal(b)+" · D="+decimal(d)+" · T="+decimal(tolerance)+" · 유상 배정별 절사합 · 취소 청구/무상 제외 · 보호 스냅샷 보존 · 예정 포함";
        var newcomers=sources.stream().filter(s->YearMonth.parse(month).equals(firstPaid.get(s.developerId()))).toList();
        BigDecimal newcomerAmount=newcomers.stream().map(s->new BigDecimal(s.supplyAmount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if("적게 청구".equals(verdict)&&!newcomers.isEmpty()&&newcomerAmount.subtract(d).abs().compareTo(new BigDecimal("10000").max(e.movePointLeft(2)))<=0) {
            Map<String,String> names=new LinkedHashMap<>();newcomers.forEach(s->names.putIfAbsent(s.developerId(),s.developerName()+"("+s.managementNo()+")"));
            explanation+=" · 새로 투입한 "+String.join(", ",names.values())+"이 누락됐을 가능성 (설명용·자동 확정 아님)";
        }
        String revision=repository.checkRevision(key);
        var context=new CheckContext(project.id(),month,revision);
        Remedy remedy=remedy(project,context,bills,e,d,verdict);
        return new CheckRow(project.id(),project.name(),project.contractType(),(int)sources.stream().map(BillingSource::developerId).distinct().count(),
            sources.stream().map(s->new BigDecimal(s.mm())).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2).toPlainString(),
            decimal(e),decimal(b),decimal(d),decimal(tolerance),bills.size(),verdict,explanation,List.copyOf(sources),remedy,revision);
    }
    private Remedy remedy(Project project,CheckContext context,List<CheckBill> bills,BigDecimal estimate,BigDecimal difference,String verdict) {
        List<String> related=bills.stream().map(CheckBill::id).toList();
        CheckBill last=bills.isEmpty()?null:bills.get(0);
        String id=last==null?null:last.id(),expected=last==null?null:repository.billRevision(id);
        if(closed(context.month()))return new Remedy("DETAIL_ONLY",project.id(),context.month(),id,null,expected,context,"마감·이관 월은 보완할 수 없습니다. 관련 청구를 확인하고 후속 UI-10 전월조정/계좌이체 경로에서 처리해 주세요.",related);
        if("맞음".equals(verdict))return new Remedy("NONE",project.id(),context.month(),id,null,expected,context,null,related);
        BigDecimal proposed=last==null?estimate:last.supplyAmount().add(difference);
        String reason=null;
        if(proposed.signum()<=0)reason="제안 공급가가 0 이하입니다. 상세 확인 후 취소·재등록해 주세요.";
        else if(proposed.compareTo(new BigDecimal("999999999999999"))>0)reason="제안 공급가가 저장 범위를 초과합니다. 상세를 확인해 주세요.";
        else if(last!=null) {
            BigDecimal total=proposed.add(proposed.multiply(last.vatRate()).movePointLeft(2).setScale(0,RoundingMode.DOWN));
            if(total.compareTo(last.receiptAmount())<0)reason="제안 합계가 정상 입금합보다 작습니다. 상세 확인 후 취소·재등록해 주세요.";
            else if(total.compareTo(new BigDecimal("999999999999999"))>0)reason="제안 합계가 저장 범위를 초과합니다. 상세를 확인해 주세요.";
        }
        return new Remedy(reason==null?(last==null?"CREATE":"UPDATE"):"DETAIL_ONLY",project.id(),context.month(),id,reason==null?decimal(proposed):null,expected,context,reason,related);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public CheckRow validateContext(CheckContext context) {
        String month=ProjectInputPolicy.month(context.month(),"month").toString();
        Project project=repository.requiredProject(context.projectId());
        BigDecimal minimum=repository.standard("BILL_MISS_MIN",db(month)),rate=repository.standard("BILL_MISS_RT",db(month));
        if(minimum==null||rate==null||minimum.signum()<0||rate.signum()<0)throw settings();
        String actual=repository.checkRevision(new CheckKey(context.projectId(),db(month)));
        if(!Objects.equals(actual,context.checkRevision()))throw new ApiException(409,"BILL_CHECK_CHANGED","점검 원천이 변경되었습니다. 다시 조회해 주세요.",null,Map.of("billing",Map.of("month",month,"reason","입금·청구·투입 원천 변경")));
        if(!Set.of("MO","DP").contains(project.contractType())||Set.of("PRE","LOSE").contains(project.status()))throw new ApiException(409,"INVALID_BILL_STATE","보완할 수 없는 프로젝트 상태입니다.");
        CheckRow row=evaluate(project,month,minimum,rate);
        if(row==null)throw new ApiException(409,"BILL_CHECK_CHANGED","점검 대상이 변경되었습니다. 다시 조회해 주세요.");
        return row;
    }
    private boolean closed(String month) {return YearMonth.parse(month).isBefore(base())||repository.closes(db(month),db(month)).stream().anyMatch(c->"CLOSE".equals(c.status()));}
    private YearMonth base() {String base=repository.cashBaseMonth();if(base==null)throw new ApiException(500,"INTERNAL_ERROR","운영 시작월 설정을 확인해 주세요.");return YearMonth.parse(DatabaseMonthCodec.toApi(base));}
    private static YearMonth earlier(YearMonth a,YearMonth b) {return a.isBefore(b)?a:b;}
    public static String decimal(BigDecimal value) {return value.stripTrailingZeros().toPlainString();}
    private static String db(String month) {return DatabaseMonthCodec.toDatabase(month);}
    private static ApiException settings() {return new ApiException(500,"INTERNAL_ERROR","청구 누락 허용오차 설정(BILL_MISS_MIN/BILL_MISS_RT)을 확인해 주세요.");}
    public static int rank(String verdict) {return switch(verdict){case "청구 없음"->0;case "적게 청구"->1;case "많이 청구"->2;default->3;};}
    private static ChecksSummary summary(List<CheckRow> rows) {
        int matched=(int)rows.stream().filter(r->"맞음".equals(r.verdict())).count(),missing=(int)rows.stream().filter(r->"청구 없음".equals(r.verdict())).count();
        BigDecimal amount=rows.stream().filter(r->Set.of("청구 없음","적게 청구").contains(r.verdict())).map(r->new BigDecimal(r.differenceAmount()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ChecksSummary(rows.size(),matched,missing,rows.size()-matched-missing,decimal(amount),rows.size()-matched);
    }
    static <T> List<T> page(List<T> rows,PageQuery query) {
        long offset=(long)(query.page()-1)*query.size();int start=(int)Math.min(offset,rows.size());return List.copyOf(rows.subList(start,(int)Math.min(offset+query.size(),rows.size())));
    }
}
