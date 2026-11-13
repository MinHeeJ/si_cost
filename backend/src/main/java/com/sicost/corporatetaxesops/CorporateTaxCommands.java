package com.sicost.corporatetaxesops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@Service
public class CorporateTaxCommands {
    private final CorporateTaxRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CorporateTaxViews views;
    private final CurrentUserProvider users;
    private final Clock clock;
    public CorporateTaxCommands(CorporateTaxRepository repository,WriteCoordinator writes,RevisionService revisions,CorporateTaxViews views,CurrentUserProvider users,Clock clock){this.repository=repository;this.writes=writes;this.revisions=revisions;this.views=views;this.users=users;this.clock=clock;}
    @Transactional
    public DefaultsResult initialize(YearMonth month){
        repository.lockClose();String year=String.format("%04d",month.getYear());boolean[] changed={false};
        writes.create(List.of("FINANCIAL_MONTH:"+db(month),"CORPORATE_TAX:"+year,"TAX_PAYMENT:"+year+":MID","TAX_PAYMENT:"+year+":CNFM","operating-standards:CASH_BASE_YM"),()->repository.snapshot(year),()->writable(month),()->{
            String actor=users.userId();LocalDateTime now=now();
            if(repository.annual(year)==null){StoredAnnual previous=repository.annual(String.format("%04d",month.getYear()-1));repository.insertAnnual(new AnnualWrite(year,previous==null?BigDecimal.ZERO:previous.estimatedTax(),actor,now));changed[0]=true;}
            for(String type:List.of("MID","CNFM"))if(repository.payments(year).stream().noneMatch(p->p.payType().equals(type))){LocalDate due=type.equals("MID")?LocalDate.of(month.getYear(),8,31):LocalDate.of(month.getYear()+1,3,31);repository.insertPayment(new PaymentWrite(year,type,due,actor,now));changed[0]=true;}
        });
        return new DefaultsResult(changed[0],views.annual(repository.annual(year)),repository.payments(year).stream().map(views::payment).toList(),revisions.revision(repository.snapshot(year)));
    }
    @Transactional
    public ConfirmationResult confirm(String year,String type,Confirmation input,Set<String> discovered){
        repository.lockClose();Set<String> keys=new TreeSet<>(discovered);
        keys.add("CORPORATE_TAX:"+year);keys.add("TAX_PAYMENT:"+year+":"+type);keys.add("TAX_PAYMENT:"+year+":MID");keys.add("FINANCIAL_MONTH:"+db(input.month()));keys.add("CASH_MONTH:"+db(YearMonth.from(input.paidDate())));keys.add("operating-standards:CASH_BASE_YM");
        StoredPayment[] before=new StoredPayment[1];
        try { writes.write(keys,input.expectedRevision(),()->views.confirmationSnapshot(year,type),()->{
            before[0]=repository.payment(year,type);String oldCash="CASH_MONTH:"+db(YearMonth.from(before[0].dueDate()));
            if(!keys.contains(oldCash)){keys.add(oldCash);throw new ExpandedScope(keys);}
            if(before[0].payYn().equals("Y"))throw conflict("TAX_ALREADY_PAID",input.month(),"이미 납부 확인된 일정입니다.",year,type);
            writable(input.month());LocalDate today=today();
            if(input.paidDate().isAfter(today))throw CorporateTaxInput.invalid("paidDate");
            writable(YearMonth.from(input.paidDate()));
            if(input.payAmount()==null||input.payAmount().scale()>0||input.payAmount().signum()<0||input.payAmount().precision()>15)throw CorporateTaxInput.invalid("payAmount");
        },()->true,()->repository.confirm(new ConfirmationWrite(year,type,input.paidDate(),input.payAmount(),users.userId(),now()),before[0]));
        } catch(ApiException error) {
            if(error.code().equals("STALE_WRITE"))throw conflict("STALE_WRITE",input.month(),"다른 사용자가 변경했습니다. 최신 납부 내역을 확인해 주세요.",year,type);
            throw error;
        }
        Payment payment=views.payment(repository.payment(year,type));return new ConfirmationResult(true,payment,payment.confirmationRevision());
    }
    public void writable(YearMonth month){
        YearMonth current=YearMonth.from(today());
        if(month.getYear()!=current.getYear()||month.isAfter(current)||month.isBefore(repository.cashBase()))throw conflict("PERIOD_READ_ONLY",month,"올해 운영 시작 이후의 편집 가능한 월만 변경할 수 있습니다.",null,null);
        if(repository.closed(month))throw conflict("MONTH_CLOSED",month,"마감된 월은 변경할 수 없습니다.",null,null);
    }
    private ApiException conflict(String code,YearMonth month,String message,String year,String type){
        Map<String,Object> financial=new LinkedHashMap<>();financial.put("month",month.toString());financial.put("business","CORPORATE_TAX");
        if(year!=null){financial.put("latestRevision",views.confirmationRevision(year,type));financial.put("latestPayment",views.payment(repository.payment(year,type)));}
        return new ApiException(409,code,message,null,Map.of("financial",financial));
    }
    public static final class ExpandedScope extends RuntimeException {private final Set<String> keys;ExpandedScope(Set<String> keys){this.keys=Set.copyOf(keys);}public Set<String> keys(){return keys;}}
    private LocalDate today(){return LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    private static String db(YearMonth month){return month.toString().replace("-","");}
}
