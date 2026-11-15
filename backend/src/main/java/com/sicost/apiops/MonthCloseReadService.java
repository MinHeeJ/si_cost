package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.monthclosesops.*;
import com.sicost.monthclosesops.MonthCloseDtos.Check;
import com.sicost.monthclosesops.MonthCloseDtos.History;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.MonthCloseReadDtos.*;
import static com.sicost.monthclosesops.MonthCloseRepository.s;

@Service
public class MonthCloseReadService {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MonthCloseRepository repository;
    private final MonthCloseCalculation calculation;
    private final CloseCheckService checks;
    private final CurrentUserProvider users;
    private final Clock clock;
    public MonthCloseReadService(MonthCloseRepository repository,MonthCloseCalculation calculation,CloseCheckService checks,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.calculation=calculation;this.checks=checks;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result getMonthCloses(Query query) {
        users.currentUser();YearMonth current=YearMonth.from(clock.instant().atZone(TimeConfiguration.SEOUL));
        String month=query.month()==null?current.toString():query.month();YearMonth selected=MonthCloseInput.month(month);
        var data=repository.load(false);
        String raw=MonthCloseCalculation.standard(data,"CASH_BASE_YM");
        if(raw==null)throw new IllegalStateException("운영 시작월이 없습니다.");
        String base=new BigDecimal(raw).toBigIntegerExact().toString();
        if(selected.isBefore(YearMonth.parse(MonthCloseCalculation.api(base)))||selected.isAfter(current))
            throw new ApiException(409,"PERIOD_OUT_OF_SCOPE","운영 시작월부터 현재월까지 관리할 수 있습니다.","month",null);
        String db=month.replace("-","");
        var parent=data.get("TB_CL_CLOSE").stream().filter(r->db.equals(s(r,"close_ym"))).findFirst().orElse(null);
        boolean closed=parent!=null&&"CLOSE".equals(s(parent,"close_stat_cd"));
        String latest=data.get("TB_CL_CLOSE").stream().filter(r->"CLOSE".equals(s(r,"close_stat_cd"))).map(r->s(r,"close_ym")).max(String::compareTo).map(MonthCloseCalculation::api).orElse(null);
        String next=MonthCloseService.next(data,base);
        List<Check> result;
        if(closed) {
            result=MonthCloseService.storedChecks(month,data).stream().map(c->new Check(c.code(),c.name(),c.status(),c.count(),c.description(),c.checkedAt(),c.linkScreenId(),month,filter(c.code()))).toList();
        } else {
            result=checks.calculate(month,data,calculation.sources(month,data)).stream().map(c->new Check(c.code(),c.name(),c.status(),c.count(),description(c),savedAt(month,c.code(),data),c.linkScreenId(),c.linkMonth(),c.linkFilter())).toList();
        }
        if(result.size()!=6||!result.stream().map(Check::code).toList().equals(CloseCheckService.CODES))throw new IllegalStateException("저장된 6개 마감 점검을 확인하지 못했습니다.");
        return new Result(month,closed?"CLOSE":"WORK",closed?s(parent,"close_user_id"):null,closed?time(s(parent,"close_dttm")):null,
            next,latest,true,!closed,!closed&&month.equals(next),closed&&month.equals(latest),repository.revision(month,data),result,
            closed?"CLOSE_SNAPSHOT":"CURRENT",(int)result.stream().filter(c->"WARN".equals(c.status())).count(),history(query,month,data),List.of());
    }
    private static String description(Check c){return c.code().equals("PAY")?c.count()+"명 업로드 완료":c.code().equals("FI")?c.count()+"건 입력":c.description();}
    private static String savedAt(String month,String code,Map<String,List<Map<String,Object>>> data) {
        return data.get("TB_CL_CLOSE_CHK").stream().filter(r->month.replace("-","").equals(s(r,"close_ym"))&&code.equals(s(r,"chk_item_cd"))).map(r->time(s(r,"chk_dttm"))).filter(Objects::nonNull).findFirst().orElse(null);
    }
    private static String filter(String code){return switch(code){case "MM_OVER"->"MM_OVER";case "BILL"->"BILL_NUMBER_MISSING";case "BILL_MISS"->"BILL_MISS";default->"";};}
    private static HistoryPage history(Query query,String month,Map<String,List<Map<String,Object>>> data) {
        List<History> all=data.get("TB_CL_CLOSE_HIST").stream().filter(r->s(r,"close_ym").startsWith(month.substring(0,4)))
            .map(r->new History(MonthCloseCalculation.api(s(r,"close_ym")),new BigDecimal(s(r,"hist_sn")).intValueExact(),s(r,"act_cd"),s(r,"reg_user_id"),time(s(r,"reg_dttm")),s(r,"act_rsn"),s(r,"remain_chk_cn")))
            .sorted(Comparator.comparing(History::occurredAt).reversed().thenComparing(History::month).thenComparingInt(History::historySequence)).toList();
        long offset=((long)query.page()-1)*query.size();
        List<History> page=offset>=all.size()?List.of():all.subList((int)offset,(int)Math.min(offset+query.size(),all.size()));
        return new HistoryPage(page,all.size(),query.page(),query.size(),offset+page.size()<all.size());
    }
    private static String time(String text){return text==null?null:TIME.format(LocalDateTime.parse(text.replace(' ','T')));}
}
