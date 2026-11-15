package com.sicost.monthclosesops;

import com.sicost.apiops.BillingCheckService;
import com.sicost.apiops.BillingReviewRepository;
import com.sicost.profitability.MonthlyCostDtos.Assignment;
import com.sicost.profitability.MonthlyCostDtos.Sources;
import com.sicost.salespartsettlements.SalesPartSettlementDtos.Tab;
import com.sicost.salespartsettlements.SalesPartSettlementDtos.TabsResult;
import com.sicost.salespartsettlementsops.SettlementClosingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.sicost.monthclosesops.MonthCloseCalculation.row;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloseCheckServiceTest {
    BillingCheckService billing;
    BillingReviewRepository bills;
    SettlementClosingService settlements;
    CloseCheckService service;
    Sources sources;
    Map<String,List<Map<String,Object>>> data;
    @BeforeEach void setup() {
        billing=mock(BillingCheckService.class);bills=mock(BillingReviewRepository.class);settlements=mock(SettlementClosingService.class);
        service=new CloseCheckService(billing,bills,settlements);sources=mock(Sources.class);
        data=new HashMap<>();for(String table:List.of("TB_HR_MON_PAY","TB_BL_BILL","TB_FI_ITEM"))data.put(table,new ArrayList<>());
        when(sources.assignments()).thenReturn(List.of());when(bills.projects()).thenReturn(List.of());
        when(bills.standard("MM_OVER","202601")).thenReturn(BigDecimal.ONE);
        when(bills.standard("BILL_MISS_MIN","202601")).thenReturn(BigDecimal.ZERO);
        when(bills.standard("BILL_MISS_RT","202601")).thenReturn(BigDecimal.ZERO);
        when(settlements.listEligibleParts("2026-01")).thenReturn(new TabsResult("2026-01","2026-01","2026-07",List.of(),0,0,0,List.of()));
    }
    @Test void zeroPayrollAndFinancialItemsAreOkAndDescriptionsAreTheStoredContract() {
        var checks=service.calculate("2026-01",data,sources);
        assertThat(checks).extracting(MonthCloseDtos.Check::code).containsExactlyElementsOf(CloseCheckService.CODES);
        assertThat(checks).allMatch(c->c.status().equals("OK")&&c.count()==0);
        assertThat(checks.get(0).description()).isEqualTo("0명 업로드 완료");assertThat(checks.get(0).linkScreenId()).isNull();
        assertThat(checks.get(3).description()).isEqualTo("0건 입력");CloseCheckService.validate(checks);
    }
    @Test void mmUsesFullPersonSumAndStrictGreaterThanAndCanceledRowsAreExcluded() {
        when(sources.assignments()).thenReturn(List.of(assignment("same","0.40","ACT"),assignment("same","0.60","ACT"),assignment("over","1.01","ACT"),assignment("cancel","2.00","CNCL")));
        var check=service.calculate("2026-01",data,sources).get(1);
        assertThat(check.count()).isEqualTo(1);assertThat(check.status()).isEqualTo("WARN");assertThat(check.linkFilter()).isEqualTo("MM_OVER");
    }
    @Test void selectedMonthOnlyCountsDistinctPayrollNonCanceledBlankInvoicesAndPendingEligibleParts() {
        data.get("TB_HR_MON_PAY").addAll(List.of(row("pay_ym","202601","dev_id","own-person"),row("pay_ym","202601","dev_id","own-person"),row("pay_ym","202602","dev_id","other-month")));
        data.get("TB_BL_BILL").addAll(List.of(row("bill_ym","202601","cncl_yn","N","tax_invc_no"," "),row("bill_ym","202601","cncl_yn","Y","tax_invc_no",null),row("bill_ym","202601","cncl_yn","N","tax_invc_no","issued")));
        data.get("TB_FI_ITEM").add(row("fin_ym","202601","item_sn",1));
        var tabs=List.of(new Tab("own-pending","미확정",true,false,"WORK",null,List.of()),new Tab("own-confirmed","확정",true,false,"CNFM",null,List.of()),new Tab("own-ineligible","제외",false,true,"WORK",null,List.of()));
        when(settlements.listEligibleParts("2026-01")).thenReturn(new TabsResult("2026-01","2026-01","2026-07",tabs,2,1,1,List.of()));
        var checks=service.calculate("2026-01",data,sources);
        assertThat(checks.get(0).count()).isEqualTo(1);assertThat(checks.get(2).count()).isEqualTo(1);
        assertThat(checks.get(3).description()).isEqualTo("1건 입력");assertThat(checks.get(4).description()).isEqualTo("2개 파트 중 1개 미확정");
        assertThat(checks.get(4).linkFilter()).isEqualTo("own-pending");verifyNoInteractions(billing);
    }
    private Assignment assignment(String person,String mm,String state) {
        return new Assignment("202601",person,"own-project",UUID.randomUUID().toString(),new BigDecimal(mm),BigDecimal.TEN,BigDecimal.TEN,"N","BE","PAID",null,state,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31));
    }
}
