package com.sicost.corporatetaxesops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.concurrent.RevisionService;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.stereotype.Service;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;
import static com.sicost.corporatetaxesops.CorporateTaxAllocationService.money;

@Service
public class CorporateTaxViews {
    private final CorporateTaxRepository repository;
    private final RevisionService revisions;
    private final Clock clock;
    public CorporateTaxViews(CorporateTaxRepository repository,RevisionService revisions,Clock clock){this.repository=repository;this.revisions=revisions;this.clock=clock;}
    public Snapshot confirmationSnapshot(String year,String type){Snapshot all=repository.snapshot(year);return new Snapshot(all.annual(),all.payments().stream().filter(p->p.payType().equals(type)||p.payType().equals("MID")).toList());}
    public String confirmationRevision(String year,String type){return revisions.revision(confirmationSnapshot(year,type));}
    public Annual annual(StoredAnnual a){return new Annual(a.year(),money(a.estimatedTax()),new Audit(a.regUserId(),text(a.regDttm()),a.modUserId(),text(a.modDttm())));}
    public Payment payment(StoredPayment p){
        StoredAnnual annual=repository.annual(p.year());BigDecimal automatic;
        if(p.payType().equals("MID"))automatic=BigDecimal.ZERO;
        else if(annual==null)automatic=null;
        else {BigDecimal mid=repository.payments(p.year()).stream().filter(x->x.payType().equals("MID")).map(x->x.payAmount()==null?BigDecimal.ZERO:x.payAmount()).findFirst().orElse(BigDecimal.ZERO);automatic=annual.estimatedTax().subtract(mid).max(BigDecimal.ZERO);}
        boolean paid=p.payYn().equals("Y");BigDecimal display=p.payAmount()==null?automatic:p.payAmount();
        String status=paid?"납부":p.dueDate().isBefore(LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)))?"확인 필요":"예정";
        return new Payment(p.year(),p.payType(),p.dueDate().toString(),money(p.payAmount()),text(p.paidDate()),p.payYn(),money(display),money(automatic),p.payAmount()!=null,status,confirmationRevision(p.year(),p.payType()),new Audit(p.regUserId(),text(p.regDttm()),p.modUserId(),text(p.modDttm())));
    }
    private static String text(Object value){return value==null?null:value.toString();}
}
