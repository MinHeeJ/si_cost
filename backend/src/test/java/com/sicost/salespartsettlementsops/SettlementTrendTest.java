package com.sicost.salespartsettlementsops;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.salespartsettlements.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SettlementTrendTest {
    SalesPartSettlementService reads;SettlementTrendService service;MockMvc mvc;
    @BeforeEach void setup() {
        reads=mock(SalesPartSettlementService.class);service=new SettlementTrendService(reads);
        mvc=MockMvcBuilders.standaloneSetup(new SettlementCommandController(mock(SettlementCommandService.class),service)).setControllerAdvice(new ApiExceptionHandler()).build();
        when(reads.listEligibleParts(any())).thenReturn(new TabsResult("2026-03","2026-02","2026-07",List.of(),0,0,0,List.of()));
        when(reads.previewSettlement(eq("part"),anyString())).thenAnswer(call->month(call.getArgument(1)));
    }
    Result month(String month) {
        List<Row> rows=month.equals("2026-02")?List.of(SettlementCommandTest.row("past-person","과거 프로젝트")):List.of();
        var r=SettlementCommandTest.result(month.equals("2026-02")?"CNFM":"WORK","5",true,true,false,rows);
        var totals=new Totals(rows.size(),0,"1.00","100","10","2","0","12","88",rows.isEmpty()?"0":"35","5",rows.isEmpty()?"-5":"30");
        return new Result("part",month,r.status(),r.standard(),"5","revision",r.confirmedBy(),null,r.confirmedAt(),null,false,List.of(),true,true,false,totals,totals,rows,new Page(1,50,rows.size()),List.of());
    }
    @Test void historicalOnlyPersonRemainsWithFiveMetricsAndBeforeBaseNulls() throws Exception {
        mvc.perform(get("/api/sales-part-settlements/part/trend")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months.length()").value(3)).andExpect(jsonPath("$.data.items[0].developerId").value("past-person"))
            .andExpect(jsonPath("$.data.items[0].metrics.length()").value(5))
            .andExpect(jsonPath("$.data.items[0].metrics[0].months[0].source").value("BEFORE_BASE"))
            .andExpect(jsonPath("$.data.items[0].metrics[0].months[0].value").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].metrics[0].months[1].source").value("CNFM"))
            .andExpect(jsonPath("$.data.items[0].metrics[0].months[2].source").value("NO_INPUT"))
            .andExpect(jsonPath("$.data.periodPartProfitAmount").value("25"));
        verify(reads,never()).previewSettlement("part","2026-01");
    }
    @Test void pageDoesNotChangeWholePeriodOrMonthlyTotals() {
        var first=service.get("part",new SalesPartSettlementInput.Query(null,1,20,"name","ASC"));
        var second=service.get("part",new SalesPartSettlementInput.Query(null,2,20,"name","ASC"));
        assertThat(first.items()).hasSize(1);assertThat(second.items()).isEmpty();assertThat(second.periodPartProfitAmount()).isEqualTo(first.periodPartProfitAmount());assertThat(second.monthlyTotals()).isEqualTo(first.monthlyTotals());assertThat(second.pagination().total()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"month=2026-13","month=202607","month=2026-02&month=2026-03","page=0","size=21","sort=name","year=2026","actor=user"})
    void rejectsUnsupportedOrMalformedQueriesWithoutReadingSources(String query) throws Exception {
        mvc.perform(get("/api/sales-part-settlements/part/trend?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").exists());verifyNoInteractions(reads);
    }
    @Test void unrelatedPersonsMissingCostDoesNotInvalidateNoInputMonths() throws Exception {
        var march=month("2026-03");
        var issue=issue("MISSING","다른 인력 급여 미확인","payAmount","other-person","2026-03","TB_HR_MON_PAY",true);
        when(reads.previewSettlement("part","2026-03")).thenReturn(new Result("part","2026-03","WORK",march.standard(),"5","revision",null,null,null,null,false,List.of(),true,false,false,march.summary(),march.totals(),List.of(),march.pagination(),List.of(issue)));
        mvc.perform(get("/api/sales-part-settlements/part/trend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].metrics[0].months[2].source").value("NO_INPUT"))
            .andExpect(jsonPath("$.data.items[0].metrics[0].total").value("1.00"))
            .andExpect(jsonPath("$.data.items[0].metrics[4].total").value("35"));
    }
    @Test void missingPartReturns404FromTheRealTrendHandler() throws Exception {
        when(reads.previewSettlement(eq("missing"),anyString())).thenThrow(new com.sicost.common.api.ApiException(404,"NOT_FOUND","일반 영업 파트를 찾을 수 없습니다."));
        mvc.perform(get("/api/sales-part-settlements/missing/trend"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.requestId").isString());
    }
    @Test void unknownRequiredMonthPropagatesOnlyDependentTotals() {
        var known=month("2026-02");var r=known.items().get(0);
        var incomplete=new Row(r.developerId(),r.name(),r.managementNo(),r.employeeType(),r.projectNames(),null,r.mm(),null,r.payAmount(),r.salesExpenseAmount(),r.retirementAmount(),r.costAmount(),null,null,r.remark(),r.basis(),List.of(issue("MISSING","매출 미확인","unitPrice",r.developerId(),"2026-02","TB_PJ_ASGN",true)));
        var totals=new Totals(1,0,"1.00",null,"10","2","0","12",null,null,"5",null);
        when(reads.previewSettlement("part","2026-02")).thenReturn(new Result("part","2026-02","WORK",known.standard(),"5","revision",null,null,null,null,false,List.of(),true,false,false,totals,totals,List.of(incomplete),known.pagination(),incomplete.issues()));
        var result=service.get("part",new SalesPartSettlementInput.Query(null,1,50,"name","ASC"));
        assertThat(result.periodPartProfitAmount()).isNull();
        assertThat(result.items().get(0).metrics().get(0).total()).isEqualTo("1.00");
        assertThat(result.items().get(0).metrics().get(1).total()).isNull();assertThat(result.items().get(0).metrics().get(1).remark()).contains("2026-02");
    }
}
