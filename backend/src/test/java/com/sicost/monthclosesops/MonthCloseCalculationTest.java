package com.sicost.monthclosesops;

import com.sicost.businessplansops.*;
import com.sicost.cashflowops.CashFlowService;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.corporatetaxesops.*;
import com.sicost.profitability.*;
import com.sicost.salespartsettlements.SalesPartSettlementDtos.TabsResult;
import com.sicost.salespartsettlementsops.SettlementClosingService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real cost and profit/loss engines, with source adapters isolated from PostgreSQL. */
class MonthCloseCalculationTest {
    MonthlyCostRepository repository;
    MonthlyCostService costs;
    MonthCloseCalculation calculation;
    Sources sources;
    Map<String,List<Map<String,Object>>> data;

    @BeforeEach void setup() {
        repository=mock(MonthlyCostRepository.class);
        var payroll=new EstimatedPayrollAdapter();
        costs=spy(new MonthlyCostService(repository,new MonthlyCostCalculation(payroll),new TurnkeyRevenueCalculation(),payroll));
        var financial=mock(BusinessPlanPerformanceMapper.class);
        when(financial.financialSources(any())).thenReturn(List.of());
        var taxes=mock(CorporateTaxMapper.class);
        when(taxes.annual("2026")).thenReturn(new CorporateTaxDtos.StoredAnnual("2026",BigDecimal.ZERO,null,null,null,null));
        when(taxes.months("2026")).thenReturn(List.of(new CorporateTaxDtos.StoredMonth("2026-01","WORK",null,"N","N")));
        var settlements=mock(SettlementClosingService.class);
        when(settlements.listEligibleParts("2026-01")).thenReturn(new TabsResult("2026-01","2026-01","2026-11",List.of(),0,0,0,List.of()));
        calculation=spy(new MonthCloseCalculation(repository,costs,mock(AssignmentMonthlyService.class),financial,
            new BusinessPlanActuals(),taxes,new CorporateTaxAllocationService(),settlements,mock(CashFlowService.class)));
        doReturn(new MonthCloseDtos.CashImpact(null,null,null,null,null,null,null,null)).when(calculation).cashImpact(anyString());
        data=new HashMap<>();
        data.put("TB_PJ_ASGN",List.of());
        data.put("TB_PJ_ASGN_MON",List.of());
        data.put("TB_CO_MON_PL",List.of());
        source("FT",true);
    }
    void source(String type,boolean uploaded) {
        var person=new Person("person","owned","원가 시험 인력",type,null,null,null,"BE",null,null,"DRCT",null,null,null,null,"WORK","Y");
        var pay=new Payroll("202601","person",new BigDecimal("1237"),BigDecimal.ZERO,BigDecimal.ZERO,null,
            BigDecimal.ZERO,null,null,null,BigDecimal.ZERO,type,"DRCT",null,null,null,null,null);
        sources=new Sources(new ProfitabilityPeriod(2026,"202601","202601","202601",OffsetDateTime.parse("2026-11-23T09:00:00+09:00"),false),
            "actor",LocalDate.of(2026,11,23),List.of(person),List.of(),List.of(),uploaded?List.of(pay):List.of(),
            List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(repository.loadRange("2026-01")).thenReturn(sources);
    }
    @Test void reviewFlagsDoNotBlockCalculatedAmountsOrReplaceThemWithZero() {
        var original=costs.calculate(sources).items().get(0);
        var flagged=new MonthlyPersonCost(original.personId(),original.month(),original.rows(),original.laborSource(),
            new MonthlyCostCompleteness(false,false,false,true,List.of(new MonthlyCostIssue("person","202601","확인 필요","원가","UI-03","1237"))),
            original.knownLaborAllocated(),original.knownPersonalRevenue(),original.assignmentMm(),true,false);
        doReturn(new MonthlyCostsData(sources.period(),List.of(flagged))).when(costs).calculate(any());
        var plan=calculation.plan("2026-01",data);
        assertThat(plan.issues()).isEmpty();
        assertThat(plan.candidates()).filteredOn(c->c.table().equals("TB_CO_MON_COST")).singleElement().satisfies(c->{
            assertThat(c.row().get("labor_alloc_amt")).isEqualTo("1237");
            assertThat(c.row().get("fix_yn")).isEqualTo("Y");
        });
        assertThat(plan.candidates()).filteredOn(c->c.table().equals("TB_CO_MON_PL")).singleElement()
            .satisfies(c->assertThat(c.row().get("idle_cost_amt")).isEqualTo("1237"));
    }
    @Test void absentAmountStaysNullAndNamesThePersonMonthAndRepairScreen() {
        source("FT",false);
        var plan=calculation.plan("2026-01",data);
        assertThat(plan.issues()).anySatisfy(i->{
            assertThat(i.code()).isEqualTo("AMOUNT_UNCONFIRMED");
            assertThat(i.message()).isEqualTo("원가 시험 인력의 2026-01 원가 금액이 없어 마감할 수 없습니다. 개인 원가·수익표에서 확인해 주세요.");
        });
        assertThat(plan.candidates()).filteredOn(c->c.table().equals("TB_CO_MON_COST")).singleElement()
            .satisfies(c->assertThat(c.row().get("labor_alloc_amt")).isNull());
    }
    @Test void legitimateZeroNonSalariedMonthDoesNotRequireAnInventedCostRow() {
        source("OS",true);
        var plan=calculation.plan("2026-01",data);
        assertThat(plan.issues()).isEmpty();
        assertThat(plan.candidates()).noneMatch(c->c.table().equals("TB_CO_MON_COST"));
    }
    @Test void migratedProfitLossRemainsABlockerWithBusinessLanguage() {
        data.put("TB_CO_MON_PL",List.of(MonthCloseCalculation.row("pl_ym","202601","fix_yn","N","migr_yn","Y")));
        assertThat(calculation.plan("2026-01",data).issues()).anySatisfy(i->{
            assertThat(i.code()).isEqualTo("FIXED_DATA_CONFLICT");
            assertThat(i.message()).contains("2026-01","이관된 자료","월별 손익").doesNotContain("TB_","FIX_YN");
        });
    }
}
