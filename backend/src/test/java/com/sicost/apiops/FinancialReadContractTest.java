package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.apiops.FinancialReadDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialReadController.class)
@ContextConfiguration(classes=FinancialReadController.class)
@Import({FinancialReadService.class,FinancialReadRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class FinancialReadContractTest {
    @Autowired MockMvc mvc;
    @MockBean FinancialReadMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    @BeforeEach void setup(){
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.sourceTotals(any())).thenReturn(totals());when(mapper.estimatedTax(any())).thenReturn(new BigDecimal("20000000"));
        when(mapper.taxMonths(any())).thenReturn(months());when(mapper.history(any())).thenReturn(List.of());
    }
    static SourceTotals totals(){return new SourceTotals(n("1999999999999998"),n("2"),n("3"),n("4"),n("5"),n("6"),n("-7"),n("8"),n("9"),n("10"),n("11"),n("12"),n("23"),n("13"),n("14"),n("15"));}
    static BigDecimal n(String value){return new BigDecimal(value);}
    static List<TaxMonth> months(){List<TaxMonth> rows=new ArrayList<>();for(int m=1;m<=12;m++)rows.add(new TaxMonth("2026"+String.format("%02d",m),"WORK",null));return rows;}
    ResultActions contributions()throws Exception{return mvc.perform(get("/api/financial-contributions").param("month","2026-07"));}
    ResultActions history(String scope)throws Exception{return mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope",scope));}
    @Test void publicContributionsAreExactStringsWithoutCredentials()throws Exception{
        contributions().andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.sgaAmount").value("1999999999999998")).andExpect(jsonPath("$.data.sgaVatAmount").value("2"))
            .andExpect(jsonPath("$.data.nonOperatingIncome").value("3")).andExpect(jsonPath("$.data.nonOperatingIncomeVat").value("4"))
            .andExpect(jsonPath("$.data.nonOperatingExpense").value("5")).andExpect(jsonPath("$.data.nonOperatingExpenseVat").value("6"))
            .andExpect(jsonPath("$.data.adjustmentProfit").value("-7")).andExpect(jsonPath("$.data.cashFinancialExpense").value("8"))
            .andExpect(jsonPath("$.data.cashMiscIncome").value("9")).andExpect(jsonPath("$.data.cashCardExpense").value("10"))
            .andExpect(jsonPath("$.data.cashTransferExpense").value("11")).andExpect(jsonPath("$.data.cashUnpaidLabor").value("12"))
            .andExpect(jsonPath("$.data.transferTotal").value("23")).andExpect(jsonPath("$.data.cashLoanIn").value("13"))
            .andExpect(jsonPath("$.data.cashLoanOut").value("14")).andExpect(jsonPath("$.data.cashLoanNet").value("-1"))
            .andExpect(jsonPath("$.data.cashTaxPayment").value("15")).andExpect(jsonPath("$.data.corporateTaxAllocation").value("1666666"));
        verify(users).currentUser();
    }
    @ParameterizedTest @ValueSource(strings={"MONTH","TAX","CARDS"})
    void publicHistoryReturnsFieldValuesAndFilteredTotal(String scope)throws Exception{
        var row=new HistoryEntry("9007199254740993","TB_FI_TAX_PAY","2026:CNFM","UPD","PAY_AMT",null,"0",null,"admin","2026-07-15 09:00:00");
        when(mapper.history(any())).thenReturn(List.of(row));when(mapper.historyCount(any())).thenReturn(103L);
        history(scope).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(103)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.items[0].historyId").value(row.historyId()))
            .andExpect(jsonPath("$.data.items[0].beforeValue").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.items[0].afterValue").value("0"));
        verify(users).currentUser();verify(mapper).history(argThat(q->q.scope().equals(scope)&&q.offset()==0));
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","random"})
    void unknownQueriesCannotSpoofEitherRead(String field)throws Exception{
        for(String path:List.of("/api/financial-contributions","/api/financial-history"))mvc.perform(get(path).param("month","2026-07").param(field,"forged"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"0000-01","2026-00","2026-13","2026-7","2026-07-01",""})
    void malformedMonthsRejectedForBothReads(String month)throws Exception{
        for(String path:List.of("/api/financial-contributions","/api/financial-history"))mvc.perform(get(path).param("month",month).param(path.endsWith("history")?"scope":"month",path.endsWith("history")?"MONTH":month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(mapper);
    }
    @Test void missingMonthAndFutureMonthRejected()throws Exception{
        mvc.perform(get("/api/financial-contributions")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/financial-history").param("scope","MONTH")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/financial-contributions").param("month","2026-08")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"", "month", "ALL"})
    void invalidHistoryScope(String scope)throws Exception{history(scope).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("scope"));verifyNoInteractions(mapper);}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","2147483648"})
    void invalidPage(String page)throws Exception{mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","MONTH").param("page",page)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));verifyNoInteractions(mapper);}
    @Test void invalidSizeAndDuplicateQueryRejected()throws Exception{
        mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","MONTH").param("size","30")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));
        mvc.perform(get("/api/financial-contributions").param("month","2026-07","2026-06")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void beyondLastPageIsEmptyButKeepsTotalAndLongOffset()throws Exception{
        when(mapper.historyCount(any())).thenReturn(5L);
        mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","MONTH").param("page","2147483647").param("size","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(5));
        verify(mapper).history(argThat(q->q.offset()==214748364600L));
    }
    @Test void readsOnlyInvokeSelectsWithoutTransactionWrites()throws Exception{
        contributions();history("MONTH");verify(mapper).sourceTotals(any());verify(mapper).estimatedTax(any());verify(mapper).taxMonths(any());verify(mapper).history(any());verify(mapper).historyCount(any());verifyNoMoreInteractions(mapper);
    }
    @Test void missingAnnualAndUncertainCashStayNull()throws Exception{
        when(mapper.estimatedTax(any())).thenReturn(null);var t=totals();
        when(mapper.sourceTotals(any())).thenReturn(new SourceTotals(t.sgaAmount(),t.sgaVatAmount(),t.nonOperatingIncome(),t.nonOperatingIncomeVat(),t.nonOperatingExpense(),t.nonOperatingExpenseVat(),t.adjustmentProfit(),t.cashFinancialExpense(),t.cashMiscIncome(),t.cashCardExpense(),t.cashTransferExpense(),t.cashUnpaidLabor(),t.transferTotal(),t.cashLoanIn(),t.cashLoanOut(),null));
        contributions().andExpect(jsonPath("$.data.corporateTaxAllocation").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.cashTaxPayment").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.verified").value(false));
    }
    @Test void finalOpenMonthGetsRemainderAndNegativeAllocationIsNotClamped()throws Exception{
        List<TaxMonth> months=months();for(int m=1;m<12;m++)months.set(m-1,new TaxMonth("2026"+String.format("%02d",m),"CLOSE",n("2000000")));
        when(mapper.taxMonths(any())).thenReturn(months);
        // July is closed: the stored copy is returned, never overwritten by a fresh allocation.
        contributions().andExpect(jsonPath("$.data.corporateTaxAllocation").value("2000000"));
        months.set(6,new TaxMonth("202607","WORK",null));months.set(11,new TaxMonth("202612","CLOSE",n("2000000")));
        contributions().andExpect(jsonPath("$.data.corporateTaxAllocation").value("-2000000"));
    }
    @Test void nullClosedCopyNeverBecomesZero()throws Exception{
        var months=months();months.set(0,new TaxMonth("202601","CLOSE",null));when(mapper.taxMonths(any())).thenReturn(months);
        contributions().andExpect(jsonPath("$.data.corporateTaxAllocation").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.verified").value(false));
    }
    @Test void internalSqlFailureDoesNotLeak()throws Exception{
        when(mapper.sourceTotals(any())).thenThrow(new IllegalStateException("private SQL"));contributions().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
    @Test void settlementHistoryRequiresExactPartOnlyForItsOwnScope() throws Exception {
        mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","PART_SETTLEMENT").param("partId","PART-001"))
            .andExpect(status().isOk());
        verify(mapper).history(argThat(q->q.scope().equals("PART_SETTLEMENT")&&q.partId().equals("PART-001")));
        for(String scope:List.of("MONTH","TAX","CARDS","BUSINESS_PLAN","HISTORICAL_PROFIT_LOSS"))
            mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope",scope).param("partId","PART-001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","PART_SETTLEMENT")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/financial-history").param("month","2026-07").param("scope","PART_SETTLEMENT").param("partId","PART-001").param("uploadId","1")).andExpect(status().isBadRequest());
    }
}
