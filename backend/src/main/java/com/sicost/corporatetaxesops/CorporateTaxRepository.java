package com.sicost.corporatetaxesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.shared.SharedQueriesMapper;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@Repository
public class CorporateTaxRepository {
    private final CorporateTaxMapper mapper;
    private final SharedQueriesMapper shared;
    private final MasterLockMapper master;
    private final ChangeHistoryWriter history;
    public CorporateTaxRepository(CorporateTaxMapper mapper,SharedQueriesMapper shared,MasterLockMapper master,ChangeHistoryWriter history){this.mapper=mapper;this.shared=shared;this.master=master;this.history=history;}
    public StoredAnnual annual(String year){return mapper.annual(year);}
    public List<StoredPayment> payments(String year){return mapper.payments(year);}
    public StoredPayment payment(String year,String type){return payments(year).stream().filter(p->p.payType().equals(type)).findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","납부 일정을 찾을 수 없습니다."));}
    public Snapshot snapshot(String year){return new Snapshot(annual(year),payments(year));}
    public List<StoredMonth> months(String year){return mapper.months(year);}
    public YearMonth cashBase(){String value=shared.cashBaseMonth();if(value==null)throw new IllegalStateException("운영 시작월을 확인할 수 없습니다.");return YearMonth.of(Integer.parseInt(value.substring(0,4)),Integer.parseInt(value.substring(4)));}
    public boolean closed(YearMonth month){String key=month.toString().replace("-","");return shared.monthCloses(key,key).stream().anyMatch(r->r.status().equals("CLOSE"));}
    public void lockClose(){mapper.lockTimeout();master.lockClose();}
    public void insertAnnual(AnnualWrite w){one(mapper.insertAnnual(w));audit("TB_FI_CORP_TAX",w.year(),"NEW","EST_TAX_AMT",null,w.estimatedTax().toPlainString());}
    public void insertPayment(PaymentWrite w){one(mapper.insertPayment(w));String key=w.year()+":"+w.payType();audit("TB_FI_TAX_PAY",key,"NEW","PAY_DUE_DT",null,w.dueDate().toString());audit("TB_FI_TAX_PAY",key,"NEW","PAY_YN",null,"N");}
    public void confirm(ConfirmationWrite w,StoredPayment before){
        one(mapper.confirm(w));String key=w.year()+":"+w.payType();
        audit("TB_FI_TAX_PAY",key,"UPD","PAY_DT",text(before.paidDate()),text(w.paidDate()));
        audit("TB_FI_TAX_PAY",key,"UPD","PAY_AMT",text(before.payAmount()),text(w.payAmount()));
        audit("TB_FI_TAX_PAY",key,"UPD","PAY_YN",before.payYn(),"Y");
    }
    private String text(Object value){return value==null?null:value.toString();}
    private void audit(String table,String key,String type,String field,String before,String after){
        var change=new ChangeHistoryWriter.Change(table,key,type,field,before,after,null);
        if(table.equals("TB_FI_TAX_PAY")&&type.equals("UPD"))history.appendRequired(change);else history.append(change);
    }
    private void one(int count){if(count!=1)throw new IllegalStateException("법인세와 필수 이력을 저장하지 못했습니다.");}
}
