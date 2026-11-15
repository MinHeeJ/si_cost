package com.sicost.candidatesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.apiops.WorkforceForecastService;
import com.sicost.forecast.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.candidatesops.CandidateMatchingDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateMatchingController.class)
@ContextConfiguration(classes=CandidateMatchingController.class)
@Import({CandidateMatchingService.class,CandidateMatchingRepository.class,WorkforceForecastService.class,
    SalesForecastRepository.class,SalesWorkforceProjectionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class CandidateMatchingContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CandidateMatchingMapper mapper;
    @MockBean SalesForecastMapper workforce;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    static BigDecimal n(String value) { return new BigDecimal(value); }
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        doAnswer(c->Clock.fixed(Instant.parse("2026-11-30T16:00:00Z"),c.getArgument(0))).when(clock).withZone(any());
        when(mapper.candidate("C-1")).thenReturn(candidate("FL","DONE"));
        when(mapper.standard(any())).thenAnswer(c->((StandardQuery)c.getArgument(0)).code().equals("SHORT_PERSON")?n("0.4"):n("15"));
        when(mapper.allowances()).thenReturn(List.of(new CodeValue("TEAM_FEE",n("50000")),new CodeValue("PEXP_FEE",n("50000"))));
        when(mapper.needs("DEV")).thenReturn(List.of(need("O-1",1,"MD",2,"202612",2,"1000000")));
        when(mapper.counts(any())).thenReturn(new Counts(2,3));
        when(mapper.jobs()).thenReturn(List.of(new JobOrder("DEV",1)));
        when(workforce.people()).thenReturn(List.of()); when(workforce.leaves()).thenReturn(List.of());
        when(workforce.assignmentIds()).thenReturn(List.of());
        when(workforce.standards(any())).thenReturn(List.of(new SalesForecastSources.Standard("SHORT_PERSON",n("0.4")),new SalesForecastSources.Standard("SURPLUS_PERSON",n("1")),new SalesForecastSources.Standard("HIRE_LEAD_MON",n("2"))));
        when(workforce.opportunities()).thenReturn(List.of(new SalesForecastSources.Opportunity("O-1","기회",null,"ING","202701",6,n("100"),n("100"),null)));
        when(workforce.needs()).thenReturn(List.of(new SalesForecastSources.Need("O-1",1,"DEV","202701",6,2,n("1"))));
    }
    Candidate candidate(String type,String status) { return new Candidate("C-1","DEV","MD",type,n("750000"),LocalDate.parse("2027-01-16"),status,2); }
    Need need(String id,int serial,String grade,Integer order,String start,int months,String rate) {
        return new Need(id,"기회 "+id,serial,n("80"),"DEV",grade,start,months,rate==null?null:n(rate),"1000000",order);
    }
    @Test void positionsPublicAdminAndTypedMoneyWithMidmonthDate() throws Exception {
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.limit").value(4))
            .andExpect(jsonPath("$.data.items[0].candidateCost").value("850000"))
            .andExpect(jsonPath("$.data.items[0].profitRate").value("15.0"))
            .andExpect(jsonPath("$.data.items[0].belowApprovalThreshold").value(false))
            .andExpect(jsonPath("$.data.items[0].availableDate").value("2027-01-16"))
            .andExpect(jsonPath("$.data.items[0].startMonth").value("2027-01"));
        verify(users).currentUser(); verifyNoInteractions(monthly);
    }
    @Test void positionsAuthRejectsForgedActorBeforeReadingCandidate() throws Exception {
        mvc.perform(get("/api/candidates/C-1/positions").param("actor","other"))
            .andExpect(DurableOpenApi.response("GET","/api/candidates/{id}/positions",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,workforce,monthly,users);
    }
    @Test void shortagesAuthRejectsForgedActorBeforeReadingWorkforce() throws Exception {
        mvc.perform(get("/api/candidates/shortages").param("actor","other"))
            .andExpect(DurableOpenApi.response("GET","/api/candidates/shortages",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,workforce,monthly,users);
    }
    @Test void shortageUsesNextSixMonthsNoUnfilledAndMonthEndCounts() throws Exception {
        mvc.perform(get("/api/candidates/shortages")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from").value("2027-01")).andExpect(jsonPath("$.data.months").value(6))
            .andExpect(jsonPath("$.data.includeUnfilled").value(false))
            .andExpect(jsonPath("$.data.items[0].firstShortageMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.items[0].maximumShortage").value("2.0"))
            .andExpect(jsonPath("$.data.items[0].availableUntil").value("2027-01-31"))
            .andExpect(jsonPath("$.data.items[0].availableCandidateCount").value(2))
            .andExpect(jsonPath("$.data.items[0].activeCandidateCount").value(3));
        verify(mapper).counts(new CountQuery("DEV",LocalDate.parse("2027-01-31")));
        verify(workforce).standards("202612"); verify(users,times(2)).currentUser();
    }
    @ParameterizedTest @ValueSource(strings={"HOLD","CTRT"})
    void inactiveCandidatesDoNotMatch(String status) throws Exception {
        when(mapper.candidate("C-1")).thenReturn(candidate("FL",status));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(mapper,never()).needs(any()); verify(mapper,never()).allowances();
    }
    @Test void outsourcedCostHasNoExtraAllowance() throws Exception {
        when(mapper.candidate("C-1")).thenReturn(candidate("OS","BEFORE"));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(jsonPath("$.data.items[0].candidateCost").value("750000"));
        verify(mapper,never()).allowances();
    }
    @Test void changedCommonAllowanceIsReadImmediately() throws Exception {
        when(mapper.allowances()).thenReturn(List.of(new CodeValue("TEAM_FEE",n("50001")),new CodeValue("PEXP_FEE",n("50000"))));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(jsonPath("$.data.items[0].profitRate").value("15.0"))
            .andExpect(jsonPath("$.data.items[0].belowApprovalThreshold").value(true));
    }
    @Test void rankingUsesGradeThenExactFractionThenIdsAndLimitsFour() throws Exception {
        when(mapper.needs("DEV")).thenReturn(List.of(need("O-Z",1,null,null,"202701",2,"9999999"),
            need("O-3",1,"MD",2,"202701",2,"2000000"),need("O-2",2,"MD",2,"202701",2,"2000000"),
            need("O-2",1,"MD",2,"202701",2,"2000000"),need("O-1",1,"MD",2,"202701",2,"1000000"),
            need("O-0",1,"HI",3,"202701",2,"9999999")));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(jsonPath("$.data.total").value(4))
            .andExpect(jsonPath("$.data.items[0].opportunityId").value("O-2"))
            .andExpect(jsonPath("$.data.items[0].needSn").value(1))
            .andExpect(jsonPath("$.data.items[1].needSn").value(2))
            .andExpect(jsonPath("$.data.items[2].opportunityId").value("O-3"))
            .andExpect(jsonPath("$.data.items[3].opportunityId").value("O-1"));
    }
    @Test void endedBeforeAvailabilityIsExcludedButSameMonthEndIsIncluded() throws Exception {
        when(mapper.needs("DEV")).thenReturn(List.of(need("O-old",1,"MD",2,"202612",1,"1000000"),need("O-same",1,"MD",2,"202612",2,"1000000")));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].opportunityId").value("O-same"));
    }
    @Test void nullRateUsesLiveJobDefault() throws Exception {
        when(mapper.needs("DEV")).thenReturn(List.of(need("O-1",1,null,null,"202701",2,null)));
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(jsonPath("$.data.items[0].effectiveRate").value("1000000"))
            .andExpect(jsonPath("$.data.items[0].gradeCode").doesNotExist());
    }
    @Test void missingAllowanceIsUnknownNotZeroOrNoMatch() throws Exception {
        when(mapper.allowances()).thenReturn(List.of());
        mvc.perform(get("/api/candidates/C-1/positions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.issues[0].status").value("UNKNOWN")).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void missingStandardDoesNotFabricateShortageZero() throws Exception {
        doReturn(null).when(mapper).standard(any());
        mvc.perform(get("/api/candidates/shortages")).andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.issues[0].field").value("SHORT_PERSON"));
    }
    @Test void strictShortageThresholdExcludesEquality() throws Exception {
        when(workforce.needs()).thenReturn(List.of(new SalesForecastSources.Need("O-1",1,"DEV","202701",6,1,n("0.4"))));
        mvc.perform(get("/api/candidates/shortages")).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void shortageUsesTheSharedRoundedJobDifference() throws Exception {
        when(workforce.needs()).thenReturn(List.of(new SalesForecastSources.Need("O-1",1,"DEV","202701",6,1,n("0.44"))));
        mvc.perform(get("/api/candidates/shortages")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void linkedProjectExcludesOpportunityDemandAcrossScreens() throws Exception {
        when(workforce.opportunities()).thenReturn(List.of(new SalesForecastSources.Opportunity("O-1","기회",null,"ING","202701",6,n("100"),n("100"),"P-1")));
        mvc.perform(get("/api/candidates/shortages")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void supplySubtractsFutureConfirmedMonths() throws Exception {
        when(workforce.people()).thenReturn(List.of(new SalesForecastSources.Person("D-1","DEV","Y","DRCT",null,null)));
        when(workforce.assignmentIds()).thenReturn(List.of("A-1"));
        when(workforce.projects()).thenReturn(List.of(new SalesForecastSources.Project("P-1","프로젝트","IN","PRE",LocalDate.parse("2027-01-01"),LocalDate.parse("2027-06-30"),n("0"),n("0"),null)));
        var source=new AssignmentMonthlyService.Source("A-1","P-1","D-1","DEV",LocalDate.parse("2027-01-01"),LocalDate.parse("2027-06-30"),n("0.5"),null,"FREE",null,null,0,"N","ACT");
        when(monthly.required("A-1")).thenReturn(source);
        var monthMapper=mock(com.sicost.common.assignments.AssignmentMonthlyMapper.class);when(monthly.mapper()).thenReturn(monthMapper);
        when(monthMapper.histories("A-1")).thenReturn(List.of());
        when(monthly.preview(eq(source),anyList(),eq("PRE"),eq("IN"))).thenReturn(List.of(
            new AssignmentMonthlyService.Month("2027-01","P-1","D-1",31,"0.50","0.50",null,"0","N")));
        mvc.perform(get("/api/candidates/shortages")).andExpect(jsonPath("$.data.items[0].maximumShortage").value("1.5"));
        verify(monthly).preview(eq(source),anyList(),eq("PRE"),eq("IN"));
    }
    @Test void anyDayLeaveRemovesWholeMonthSupply() throws Exception {
        when(workforce.people()).thenReturn(List.of(new SalesForecastSources.Person("D-1","DEV","Y","DRCT",null,null)));
        when(workforce.leaves()).thenReturn(List.of(new SalesForecastSources.Leave("D-1",LocalDate.parse("2027-01-16"),LocalDate.parse("2027-01-16"))));
        mvc.perform(get("/api/candidates/shortages")).andExpect(jsonPath("$.data.items[0].maximumShortage").value("2.0"));
    }
    @Test void shortageOrderingIsNumericRatherThanLexical() throws Exception {
        when(workforce.needs()).thenReturn(List.of(
            new SalesForecastSources.Need("O-1",1,"DEV","202701",6,2,n("1")),
            new SalesForecastSources.Need("O-1",2,"PM","202701",6,10,n("1"))));
        mvc.perform(get("/api/candidates/shortages").param("sort","maximumShortage").param("direction","DESC"))
            .andExpect(jsonPath("$.data.items[0].jobCode").value("PM")).andExpect(jsonPath("$.data.items[1].jobCode").value("DEV"));
    }
    @Test void shortagePaginationKeepsWholeTotal() throws Exception {
        mvc.perform(get("/api/candidates/shortages").param("page","2").param("size","20"))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @ParameterizedTest @CsvSource({"page,0","page,-1","page,2147483648","size,10","sort,name","direction,asc"})
    void invalidShortageQuery(String field,String value) throws Exception {
        mvc.perform(get("/api/candidates/shortages").param(field,value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(mapper,workforce,users);
    }
    @ParameterizedTest @ValueSource(strings={"actor","from","months","year","status"})
    void unknownQueryRejectedOnBothOperations(String field) throws Exception {
        for (String path:List.of("/api/candidates/shortages","/api/candidates/C-1/positions")) {
            var response=mvc.perform(get(path).param(field,"injected")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field)).andReturn().getResponse();
            assertThat(json.readTree(response.getContentAsString()).get("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        }
        verifyNoInteractions(mapper,workforce,users);
    }
    @Test void positionsHaveNoPagingParameters() throws Exception {
        mvc.perform(get("/api/candidates/C-1/positions").param("page","1")).andExpect(status().isBadRequest());
    }
    @Test void notFoundUses404() throws Exception {
        mvc.perform(get("/api/candidates/absent/positions")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void oversizedIdentityRejectedWithoutTruncation() throws Exception {
        mvc.perform(get("/api/candidates/"+"a".repeat(21)+"/positions")).andExpect(status().isBadRequest()); verifyNoInteractions(mapper,users);
    }
    @Test void databaseFailureDoesNotLeakSql() throws Exception {
        when(mapper.candidate(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT private FROM tb_hr_cand"));
        var result=mvc.perform(get("/api/candidates/C-1/positions")).andExpect(status().isInternalServerError()).andReturn().getResponse();
        assertThat(result.getContentAsString()).doesNotContain("SELECT","private","tb_hr_cand");
    }
}
