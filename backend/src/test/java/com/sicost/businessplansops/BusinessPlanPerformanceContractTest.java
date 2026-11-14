package com.sicost.businessplansops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
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
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(BusinessPlanPerformanceController.class)
@ContextConfiguration(classes=BusinessPlanPerformanceController.class)
@Import({BusinessPlanPerformanceService.class,BusinessPlanPerformanceRepository.class,BusinessPlanActuals.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BusinessPlanPerformanceContractTest {
    @Autowired MockMvc mvc;
    @MockBean BusinessPlanPerformanceMapper mapper;
    @MockBean MonthlyCostService costs;
    @MockBean CurrentUserProvider users;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(at.toInstant());when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.baseMonth()).thenReturn("202601");when(mapper.targets(any())).thenReturn(targets("100","20"));
        when(mapper.snapshots(any())).thenReturn(List.of());when(mapper.closes(any())).thenReturn(List.of());
        when(mapper.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","owned-bill","tkSales","99",false,"청구일")));
        when(mapper.annualTax(any())).thenReturn(n("120"));
        Sources sources=new Sources(new ProfitabilityPeriod(2026,"202601","202601","202607",at,false),"admin",LocalDate.of(2026,7,15),
            List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(costs.loadYear(any())).thenReturn(sources);when(costs.calculate(any())).thenReturn(new MonthlyCostsData(sources.period(),List.of()));
    }
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    static List<Target> targets(String sales,String profit) {List<Target> result=new ArrayList<>();for(int m=1;m<=12;m++)result.add(new Target("2026"+String.format("%02d",m),n(sales),n(profit)));return result;}
    ResultActions get(String month)throws Exception {return mvc.perform(request().param("year",month.substring(0,4)).param("month",month));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/business-plans/performance");}
    @Test void publicReadHasTwelveMonthsThreeSummariesAndSameExactActuals()throws Exception {
        get("2026-07").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.summary.length()").value(3))
            .andExpect(jsonPath("$.data.context.timezone").value("Asia/Seoul")).andExpect(jsonPath("$.data.context.readOnly").value(true))
            .andExpect(jsonPath("$.data.months[6].salesActualAmt").value("99"))
            .andExpect(jsonPath("$.data.months[6].actual.amounts.salesAmt").value("99"))
            .andExpect(jsonPath("$.data.summary[2].actualAmt").value("99"))
            .andExpect(jsonPath("$.data.months[6].salesUnderTarget").value(true))
            .andExpect(jsonPath("$.data.months[7].salesActualAmt").value(nullValue()))
            .andExpect(jsonPath("$.data.months[11].salesTargetAmt").value("100"));
        verify(users).currentUser();
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","REG_DTTM","aggregation","unknown"})
    void injectedFieldsRejectedBeforeReads(String key)throws Exception {
        mvc.perform(request().param("year","2026").param("month","2026-07").param(key,"forged"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,costs,users);
    }
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","0000-01","2026-7","2025-01"})
    void malformedOrMismatchedMonthRejected(String month)throws Exception {
        mvc.perform(request().param("year","2026").param("month",month)).andExpect(status().isBadRequest());verifyNoInteractions(mapper,costs);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","10000","garbage"})
    void invalidYearRejected(String year)throws Exception {
        mvc.perform(request().param("year",year).param("month","2026-07")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void missingInputsRejected()throws Exception {mvc.perform(request()).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void duplicateMonthRejected()throws Exception {mvc.perform(request().param("year","2026").param("month","2026-07","2026-06")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void duplicateYearRejected()throws Exception {mvc.perform(request().param("year","2026","2026").param("month","2026-07")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void futurePeriodRejectedWithoutCostReads()throws Exception {get("2026-08").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));verifyNoInteractions(costs);}
    @Test void beforeBaseYearRejectedWithoutCostReads()throws Exception {get("2025-12").andExpect(status().isBadRequest());verifyNoInteractions(costs);}
    @Test void explicitPastYearMonthIsNotOverwritten()throws Exception {when(mapper.baseMonth()).thenReturn("202501");get("2025-03").andExpect(status().isOk()).andExpect(jsonPath("$.data.context.month").value("2025-03")).andExpect(jsonPath("$.data.months[3].actual.sourceKind").value("FUTURE"));}
    @Test void unregisteredRetainsActuals()throws Exception {when(mapper.targets(any())).thenReturn(List.of());get("2026-07").andExpect(jsonPath("$.data.targetStatus").value("UNREGISTERED")).andExpect(jsonPath("$.data.months[6].salesActualAmt").value("99")).andExpect(jsonPath("$.data.summary[0].achievementRate").value(nullValue()));}
    @Test void partialTargetsKeepKnownValuesAndInvalidateCumulativeTarget()throws Exception {when(mapper.targets(any())).thenReturn(List.of(new Target("202607",n("100"),n("20"))));get("2026-07").andExpect(jsonPath("$.data.targetStatus").value("INCOMPLETE")).andExpect(jsonPath("$.data.months[6].salesTargetAmt").value("100")).andExpect(jsonPath("$.data.summary[0].targetAmt").value(nullValue())).andExpect(jsonPath("$.data.months[0].salesTargetAmt").value(nullValue()));}
    @Test void registeredZeroTargetsAreNotUnregisteredOrRed()throws Exception {when(mapper.targets(any())).thenReturn(targets("0","0"));get("2026-07").andExpect(jsonPath("$.data.targetStatus").value("ZERO_TARGETS")).andExpect(jsonPath("$.data.months[6].salesUnderTarget").value(nullValue())).andExpect(jsonPath("$.data.months[6].salesAchievementRate").value(nullValue()));}
    @Test void negativeTargetsHaveNoAchievementButNegativeProfitTargetRemainsInMargin()throws Exception {when(mapper.targets(any())).thenReturn(targets("100","-20"));get("2026-07").andExpect(jsonPath("$.data.summary[1].achievementRate").value(nullValue())).andExpect(jsonPath("$.data.months[6].operatingMarginDifference").value("120.0"));}
    @Test void roundingToHundredDoesNotRemoveUnderTargetFlag()throws Exception {when(mapper.targets(any())).thenReturn(targets("99001","20"));when(mapper.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","owned","tkSales","99000",false,null)));get("2026-07").andExpect(jsonPath("$.data.months[6].salesAchievementRate").value("100.0")).andExpect(jsonPath("$.data.months[6].salesUnderTarget").value(true));}
    @Test void closeWithoutFixedCopyNeverFallsBackToBills()throws Exception {when(mapper.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));get("2026-07").andExpect(jsonPath("$.data.months[6].salesActualAmt").value(nullValue())).andExpect(jsonPath("$.data.months[6].actual.sourceKind").value("MISSING"));}
    @Test void closedSnapshotWinsAndPreservesOptionalNull()throws Exception {when(mapper.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(mapper.snapshots(any())).thenReturn(List.of(copy("202607","Y","N")));get("2026-07").andExpect(jsonPath("$.data.months[6].salesActualAmt").value("500")).andExpect(jsonPath("$.data.months[6].actual.amounts.grossPrftAmt").value(nullValue())).andExpect(jsonPath("$.data.months[6].actual.sourceKind").value("CLOSED_SNAPSHOT"));}
    @Test void migratedOptionalNullIsNotDerived()throws Exception {when(mapper.baseMonth()).thenReturn("202607");when(mapper.snapshots(any())).thenReturn(List.of(copy("202601","Y","Y")));get("2026-07").andExpect(jsonPath("$.data.months[0].actual.sourceKind").value("MIGRATED")).andExpect(jsonPath("$.data.months[0].actual.amounts.grossPrftAmt").value(nullValue())).andExpect(jsonPath("$.data.months[1].salesActualAmt").value(nullValue())).andExpect(jsonPath("$.data.summary[0].actualAmt").value("599"));}
    @Test void unFixedPlIsIgnoredAndRepeatedReadOnlyCallsUseSelects()throws Exception {when(mapper.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));get("2026-07").andExpect(jsonPath("$.data.months[6].salesActualAmt").value("99"));get("2026-07");verify(mapper,times(2)).baseMonth();verify(mapper,times(2)).targets(any());verify(mapper,times(2)).snapshots(any());verify(mapper,times(2)).closes(any());verify(mapper,times(2)).financialSources(any());verify(mapper,times(2)).annualTax(any());verifyNoMoreInteractions(mapper);}
    @Test void internalFailuresAreNotLeaked()throws Exception {when(mapper.targets(any())).thenThrow(new IllegalStateException("private SQL"));get("2026-07").andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));}
    static Snapshot copy(String month,String fixed,String migrated) {return new Snapshot(month,fixed,migrated,n("500"),null,null,null,n("200"),null,null,null,null,n("100"),null,n("-5"),null,null,n("1"),null);}
}
