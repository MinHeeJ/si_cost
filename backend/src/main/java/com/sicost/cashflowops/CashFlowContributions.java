package com.sicost.cashflowops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.payroll.PayrollCalculation;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import static com.sicost.cashflowops.CashFlowDtos.*;
import static com.sicost.common.financial.FinancialCalculation.*;
import static com.sicost.cashflowops.CashFlowRepository.Snapshot;

public final class CashFlowContributions {
    private CashFlowContributions() {}
    public static List<Source> calculate(Snapshot snapshot,YearMonth month,YearMonth current,JsonNode financial,JsonNode receipts) {
        String m=month.toString();boolean closed=closed(snapshot,month);List<Source> out=new ArrayList<>();
        for(JsonNode r:receipts.path("receipts"))add(out,new Evidence("TB_BL_RCPT",text(r,"rcpt_id"),Item.CASH_SALES,date(r,"rcpt_dt"),m,m),nullableNumber(r,"rcpt_amt"),BigDecimal.ZERO,"ACTUAL","미취소 실제 입금", "/UI-13");
        if(!closed)for(JsonNode r:receipts.path("bills")) {
            BigDecimal amount=nullableNumber(r,"unpaid");if(amount!=null&&amount.signum()==0)continue;
            add(out,new Evidence("TB_BL_BILL",text(r,"bill_id"),Item.CASH_SALES,date(r,"pay_due_dt"),apiMonth(text(r,"bill_ym")),m),amount,BigDecimal.ZERO,"PLANNED","청구 총액 − 미취소 입금 전체합", "/UI-13");
        }
        for(JsonNode r:financial.path("items")) {
            LocalDate date=date(r,"io_dt");String sourceMonth=apiMonth(text(r,"fin_ym"));
            if(!m.equals(date==null?sourceMonth:YearMonth.from(date).toString()))continue;
            boolean income="NOPR_IN".equals(text(r,"fin_cls_cd"));
            if(income && date==null)continue;
            add(out,new Evidence("TB_FI_ITEM",text(r,"fin_ym")+":"+text(r,"item_sn"),income?Item.MISC:Item.EXPENSE,date,sourceMonth,m),
                sum(nullableNumber(r,"amt"),nullableNumber(r,"vat_amt")),nullableNumber(r,"vat_amt"),closed?"ACTUAL":"PLANNED",income?"AMT + VAT_AMT · IO_DT월":"AMT + VAT_AMT · IO_DT 없으면 FIN_YM", "/UI-10");
        }
        for(JsonNode r:financial.path("loans"))add(out,new Evidence("TB_FI_LOAN",text(r,"loan_sn"),Item.LOAN,date(r,"loan_dt"),m,m),
            nullableNumber(r,"amt")==null?null:nullableNumber(r,"amt").multiply("OUT".equals(text(r,"io_cd"))?BigDecimal.ONE.negate():BigDecimal.ONE),BigDecimal.ZERO,closed?"ACTUAL":"PLANNED","IN +AMT / OUT −AMT", "/UI-10");
        for(JsonNode r:financial.path("transfers"))add(out,new Evidence("TB_FI_XFER",text(r,"xfer_sn"),"SPCL".equals(text(r,"xfer_cls_cd"))?Item.UNPAID_LABOR:Item.EXPENSE,date(r,"xfer_dt"),m,m),nullableNumber(r,"amt"),BigDecimal.ZERO,closed?"ACTUAL":"PLANNED","이체일 AMT 1회", "/UI-10");
        for(JsonNode r:financial.path("cardMonths"))add(out,new Evidence("TB_FI_CARD_MON",text(r,"pay_ym"),Item.EXPENSE,null,m,m),nullableNumber(r,"cash_amt"),null,closed?"ACTUAL":"PLANNED","CASH_AMT만 반영 (직접 0 유효)", "/UI-10");
        if(financial.path("cardMonths").isEmpty()&&!financial.path("cardPayments").isEmpty())for(JsonNode r:financial.path("cardPayments"))
            add(out,new Evidence("TB_FI_CARD_PAY",text(r,"pay_ym")+":"+text(r,"card_id"),Item.EXPENSE,null,m,m),null,null,"UNKNOWN","카드 명세는 있으나 월 현금 합계가 없습니다.", "/UI-10");
        for(JsonNode r:financial.path("taxPayments")) {
            boolean paid="Y".equals(text(r,"pay_yn"));LocalDate day=date(r,paid?"pay_dt":"pay_due_dt");
            if(day==null||!YearMonth.from(day).equals(month)||(!paid&&closed))continue;
            BigDecimal amount=nullableNumber(r,"pay_amt");
            if(!paid&&amount==null) {
                if("MID".equals(text(r,"pay_type_cd")))amount=BigDecimal.ZERO;
                else {
                    BigDecimal annual=null,mid=BigDecimal.ZERO;
                    for(JsonNode t:financial.path("taxes"))if(Objects.equals(text(t,"tax_yr"),text(r,"tax_yr")))annual=nullableNumber(t,"est_tax_amt");
                    for(JsonNode t:financial.path("taxPayments"))if(Objects.equals(text(t,"tax_yr"),text(r,"tax_yr"))&&"MID".equals(text(t,"pay_type_cd")))mid=number(t,"pay_amt");
                    amount=annual==null?null:annual.subtract(mid).max(BigDecimal.ZERO);
                }
            }
            add(out,new Evidence("TB_FI_TAX_PAY",text(r,"tax_yr")+":"+text(r,"pay_type_cd"),Item.CORP_TAX,day,m,m),amount,BigDecimal.ZERO,paid?"ACTUAL":"PLANNED",paid?"확인 납부일 PAY_AMT":"납부예정 · 직접액 또는 max(0, 예상세액 − 중간예납)","/UI-10");
        }
        for(PayEvidence pay:payroll(snapshot,month,current))pay(out,pay,month,snapshot,false);
        for(PayEvidence pay:payroll(snapshot,month.minusMonths(1),current))pay(out,pay,month,snapshot,true);
        return out.stream().sorted(Comparator.comparing(Source::sourceType).thenComparing(Source::sourceId)
            .thenComparing(Source::attributionDate,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }
    public static boolean closed(Snapshot s,YearMonth m){return s.closes().stream().anyMatch(c->c.month().equals(db(m))&&"CLOSE".equals(c.status()));}
    public static BigDecimal standard(Snapshot s,String key,YearMonth m) {
        return s.standards().stream().filter(v->v.code().equals(key)&&v.fromMonth().compareTo(db(m))<=0)
            .max(Comparator.comparing(Standard::fromMonth)).map(Standard::value).orElse(null);
    }
    public static String standardMonth(Snapshot s,String key,YearMonth m) {
        return s.standards().stream().filter(v->v.code().equals(key)&&v.fromMonth().compareTo(db(m))<=0)
            .max(Comparator.comparing(Standard::fromMonth)).map(v->apiMonth(v.fromMonth())).orElse(null);
    }
    private record PayEvidence(Payroll row,String personId,String employeeType,String reference,boolean planned,String problem) {}
    private static List<PayEvidence> payroll(Snapshot s,YearMonth m,YearMonth current) {
        List<PayEvidence> result=new ArrayList<>();Set<String> actual=new HashSet<>();
        for(Payroll p:s.payroll())if(p.month().equals(db(m))) {actual.add(p.personId());result.add(new PayEvidence(p,p.personId(),p.employeeType(),null,false,null));}
        if(!m.isAfter(current)||closed(s,m))return result;
        for(Person person:s.people()) {
            if(actual.contains(person.personId())||!"Y".equals(person.useYn())||!person.employed(db(m)))continue;
            Payroll reference=s.payroll().stream().filter(p->p.personId().equals(person.personId())&&p.month().compareTo(db(current))<=0)
                .max(Comparator.comparing(Payroll::month)).orElse(null);
            String problem=reference==null?"참조 급여가 없습니다.":null;Payroll row=reference;
            if(reference!=null&&!Objects.equals(reference.employeeType(),person.employeeType())) {
                try {
                    Map<String,BigDecimal> rates=new HashMap<>();
                    for(var r:s.rates())if(r.codeVal()!=null)rates.put(r.code(),new BigDecimal(r.codeVal()));
                    BigDecimal base="OS".equals(reference.employeeType())?reference.vendorSupply():reference.pay();
                    var values=PayrollCalculation.calculate(new PayrollCalculation.Classification(person.employeeType(),person.directClass(),person.positionCode()),
                        new PayrollCalculation.Amounts(base,null,reference.selfInsurance(),reference.incomeTax(),base),rates,standard(s,"WTHLD_RT",m));
                    row=new Payroll(db(m),person.personId(),values.pay(),values.allowance(),values.companyInsurance(),values.selfInsurance(),values.retirement(),
                        values.incomeTax(),values.withholding(),values.realPay(),values.vendorSupply(),values.employeeType(),values.directClass(),null,null,null,null,null);
                } catch(IllegalArgumentException e){row=null;problem=e.getMessage();}
            }
            result.add(new PayEvidence(row,person.personId(),person.employeeType(),reference==null?null:apiMonth(reference.month()),true,problem));
        }
        return result;
    }
    private static void pay(List<Source> out,PayEvidence evidence,YearMonth month,Snapshot s,boolean deductions) {
        Payroll p=evidence.row();String type=evidence.employeeType();
        Item item=deductions?Item.EXPENSE:"FT".equals(type)?Item.REG_PAY:"OS".equals(type)?Item.OS_PAY:Item.CON_FL_PAY;
        BigDecimal amount=null,vat=BigDecimal.ZERO;
        if(p!=null) {
            if(deductions)amount=sum(p.companyInsurance(),p.selfInsurance(),p.withholdingTax(),p.incomeTax());
            else if("OS".equals(type)) {BigDecimal rate=standard(s,"VAT_RT",month);vat=rate==null||p.vendorSupply()==null?null:p.vendorSupply().multiply(rate).movePointLeft(2).setScale(0,RoundingMode.DOWN);amount=sum(p.vendorSupply(),vat);}
            else if("FL".equals(type))amount=subtract(sum(p.pay(),p.allowance()),p.withholdingTax());
            else amount=p.netPay();
        }
        String m=month.toString(),sourceMonth=deductions?month.minusMonths(1).toString():m;
        String formula=deductions?"전월 회사·본인보험 + 원천세 + 소득세 1회":"OS".equals(type)?"공급가 + 사람별 DOWN(공급가 × 유효 VAT_RT / 100)":"FL".equals(type)?"급여 + 수당 − 저장 원천세":"저장 REAL_PAY_AMT";
        if(evidence.planned())formula+=" · 현재구분 "+type+" 예정 · 참조급여 "+evidence.reference();
        if(evidence.problem()!=null)formula+=" · "+evidence.problem();
        var key=new Evidence("TB_HR_MON_PAY",sourceMonth.replace("-","")+":"+evidence.personId(),item,null,sourceMonth,m);
        Source source=source(key,amount,vat,evidence.planned()?"PLANNED":"ACTUAL",formula,"/UI-02",evidence.reference(),type);
        out.add(source);
    }
    private record Evidence(String type,String id,Item item,LocalDate date,String sourceMonth,String month) {}
    private static void add(List<Source> out,Evidence e,BigDecimal amount,BigDecimal vat,String status,String formula,String link){out.add(source(e,amount,vat,status,formula,link,null,null));}
    private static Source source(Evidence e,BigDecimal amount,BigDecimal vat,String status,String formula,String link,String reference,String employeeType) {
        boolean known=amount!=null;
        List<Issue> issues=known?List.of():List.of(new Issue("SOURCE_UNKNOWN",formula,e.month(),e.type(),e.id(),"amount"));
        return new Source(e.type(),e.id(),e.item(),e.date(),e.sourceMonth(),e.month(),known?status:"UNKNOWN",money(amount),money(vat),true,reference,employeeType,known,formula,link,issues);
    }
    public static BigDecimal sum(BigDecimal... values){BigDecimal n=BigDecimal.ZERO;for(BigDecimal v:values){if(v==null)return null;n=n.add(v);}return n;}
    public static BigDecimal subtract(BigDecimal a,BigDecimal b){return a==null||b==null?null:a.subtract(b);}
    private static LocalDate date(JsonNode r,String field){String v=text(r,field);return v==null?null:LocalDate.parse(v);}
    private static String db(YearMonth month){return month.toString().replace("-","");}
}
