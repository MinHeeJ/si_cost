package com.sicost.profitability;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static com.sicost.profitability.IndividualProfitabilityCalculationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/controller/validation/service/calculations; only persistent sources are replaced. */
class IndividualProfitabilityContractTest {
    MonthlyCostRepository repository;
    MonthlyCostMapper mapper;
    SharedQueriesService shared;
    IndividualProfitabilityService service;
    MockMvc mvc;
    Sources source;
    @BeforeEach void setup() {
        repository=mock(MonthlyCostRepository.class);mapper=mock(MonthlyCostMapper.class);shared=mock(SharedQueriesService.class);
        var payroll=new EstimatedPayrollAdapter();var turnkey=new TurnkeyRevenueCalculation();
        var monthly=new MonthlyCostService(repository,new MonthlyCostCalculation(payroll),turnkey,payroll);
        service=new IndividualProfitabilityService(monthly,new IndividualProfitabilityCalculation(turnkey),shared,mapper);
        mvc=MockMvcBuilders.standaloneSetup(new IndividualProfitabilityController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        source=sources(person("FT"),List.of(pay("FT","4970000")),List.of(assignment("a","0.60"),assignment("b","0.40")),List.of(project("MO","ACT")));
        when(repository.loadYear(any())).thenAnswer(call -> {
            Integer year=call.getArgument(0);
            if(year!=null) MonthlyCostRepository.period(year,MONTH,source.period().calculatedAt().toInstant());
            return source;
        });
        when(shared.getCodes(anyString(),eq(true))).thenAnswer(call -> new Items<>(switch((String)call.getArgument(0)) {
            case "JOB" -> List.of(code("JOB","직무"));case "EMP_TYPE" -> List.of(code("FT","정규직"),code("CT","계약직"),code("FL","프리랜서"),code("OS","외주"));
            case "GRADE" -> List.of(code("JR","초급"),code("MID","중급"),code("SR","고급"),code("EX","특급"));default -> List.of();
        }));
    }
    static CodeLookup code(String code,String label) { return new CodeLookup(code,label,null,null,null,null,null); }
    @Test void listAndDetailExposeTheSameServerOwnedPayrollAllocationExpenseTurnkeyAndAverageBasis() throws Exception {
        for (String path : List.of("/api/individual-profitability", "/api/individual-profitability/actual")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calculationBasis").value(org.hamcrest.Matchers.equalTo(IndividualProfitabilityCalculation.BASIS)))
                .andExpect(jsonPath("$.data.calculationBasis", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("FT=급여+수당+회사보험+퇴직충당금"))))
                .andExpect(jsonPath("$.data.calculationBasis", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("마지막 행에 잔액"))))
                .andExpect(jsonPath("$.data.calculationBasis", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("저장된 각 금액×원본 월 MM"))))
                .andExpect(jsonPath("$.data.calculationBasis", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("저장 계약금액÷저장 예산"))))
                .andExpect(jsonPath("$.data.calculationBasis", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("사람별 monthBasis"))));
        }
    }
    @Test void defaultsExposeOneParentExactAmountsFullEnvelopeAndOneSourceLoad() throws Exception {
        mvc.perform(get("/api/individual-profitability")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.context.view").value("CUMULATIVE")).andExpect(jsonPath("$.data.context.includeTurnkey").value(true))
            .andExpect(jsonPath("$.data.context.period.toMonth").value(MONTH))
            .andExpect(jsonPath("$.data.items[0].metrics.personalCost").value("4970000"))
            .andExpect(jsonPath("$.data.items[0].metrics.pay").value("4970000"))
            .andExpect(jsonPath("$.data.items[0].metrics.mm").value("1.00"))
            .andExpect(jsonPath("$.data.summary.companyHeadcount").value(1))
            .andExpect(jsonPath("$.data.totals.personalCost").value("4970000"))
            .andExpect(jsonPath("$.data.contributions").isArray()).andExpect(jsonPath("$.data.calculationBasis").isArray());
        verify(repository).loadYear(null);verifyNoMoreInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"includeTurnkey=yes","includeTurnkey=TRUE","year=2026&year=2025","field=x","size=30","page=0",
        "sort=pay","direction=desc","view=ANNUAL","show=UNKNOWN","year=2027","year=2025","year=+2026","employeeType=UNLISTED","jobCode=UNLISTED","companyId=UNLISTED","partId=HQ"})
    void rejectsInvalidInputsWithoutSensitiveErrorLeakage(String query) throws Exception {
        mvc.perform(get("/api/individual-profitability?"+query)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.field").exists())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT "))));
    }
    @Test void duplicateUnknownAndBodyRequestsDoNotReachSources() throws Exception {
        mvc.perform(get("/api/individual-profitability?year=2026&year=2026")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/individual-profitability?actor=forged")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/individual-profitability").content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void overlargePageIsEmptyButSummaryTotalAndTotalsAreNotPageDependent() throws Exception {
        var first=service.getIndividualProfitability(query());var next=service.getIndividualProfitability(query("page","100","size","20"));
        assertThat(next.items()).isEmpty();assertThat(next.total()).isEqualTo(first.total());
        assertThat(next.summary()).isEqualTo(first.summary());assertThat(next.totals()).isEqualTo(first.totals());
        mvc.perform(get("/api/individual-profitability?page=2147483647&size=100")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void detailContainsOriginalMonthlyRowsAndPayrollOnceRegardlessOfParentAverage() throws Exception {
        mvc.perform(get("/api/individual-profitability/actual?view=MONTHLY_AVERAGE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.person.personId").value("actual"))
            .andExpect(jsonPath("$.data.months[0].rows.length()").value(2))
            .andExpect(jsonPath("$.data.months[0].laborSource.pay").value("4970000"))
            .andExpect(jsonPath("$.data.months[0].rows[0].assignmentId").value("a"))
            .andExpect(jsonPath("$.data.months[0].rows[1].assignmentId").value("b"));
        mvc.perform(get("/api/individual-profitability/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        mvc.perform(get("/api/individual-profitability/actual?page=1")).andExpect(status().isBadRequest());
    }
    @Test void realCompanyAndPersonPartFiltersValidateMasterWithoutSavingIt() throws Exception {
        source=new Sources(source.period(),source.actor(),source.asOfDate(),source.people(),source.prices(),source.states(),source.payroll(),source.assignments(),
            source.projectMonths(),source.projects(),source.projectParts(),source.turnkey(),source.storedCosts(),source.standards(),List.of(new Part("person-part","파트","Y","Y")),source.closes(),source.allowanceCodes());
        when(mapper.companies()).thenReturn(List.of(new Company("vendor","협력사")));
        mvc.perform(get("/api/individual-profitability?companyId=SELF&partId=person-part&name=%20시험%20")).andExpect(status().isOk());
        mvc.perform(get("/api/individual-profitability?companyId=vendor")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/individual-profitability?partId=UNASSIGNED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(mapper).companies();verifyNoMoreInteractions(mapper);
    }
    @Test void historicalYearEndsDecemberAndCurrentYearStopsAtSeoulCurrentMonth() {
        var instant=OffsetDateTime.parse("2026-11-01T00:00:00+09:00").toInstant();
        assertThat(MonthlyCostRepository.period(2025,"202501",instant).toMonth()).isEqualTo("202512");
        assertThat(MonthlyCostRepository.period(null,"202501",instant).toMonth()).isEqualTo("202611");
        assertThatThrownBy(()->MonthlyCostRepository.period(2027,"202501",instant)).isInstanceOf(com.sicost.common.api.ApiException.class);
    }
    @Test void unexpectedErrorsRemainGenericAndDoNotLeakSalaryOrSql() throws Exception {
        when(repository.loadYear(any())).thenThrow(new IllegalStateException("sensitive payroll SELECT secret"));
        mvc.perform(get("/api/individual-profitability")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }
    @Test void materializedSnapshotCannotMixConcurrentCommittedSourceChangesAndProjectionDoesNotReadAgain() throws Exception {
        var original=source;
        var changed=sources(person("FT"),List.of(pay("FT","9000000")),original.assignments(),original.projects());
        var current=new java.util.concurrent.atomic.AtomicReference<>(original);
        when(repository.loadYear(any())).thenAnswer(call -> current.get());
        var captured=service.snapshot(query());
        var writer=new Thread(() -> current.set(changed));writer.start();writer.join();
        var first=service.project(captured,query(),true);
        var firstAverage=service.project(captured,query("view","MONTHLY_AVERAGE"),true);
        assertThat(first.items().get(0).metrics().personalCost()).isEqualTo("4970000");
        assertThat(first.totals().personalCost()).isEqualTo("4970000");
        assertThat(firstAverage.items().get(0).metrics().personalCost()).isEqualTo("4970000");
        verify(repository,times(1)).loadYear(null);
        var next=service.getIndividualProfitability(query());
        assertThat(next.items().get(0).metrics().personalCost()).isEqualTo("9000000");
        verify(repository,times(2)).loadYear(null);verifyNoMoreInteractions(repository);
    }
    @Test void growingPopulationStillUsesOneBulkSourceLoadAndConstantCodeLookups() throws Exception {
        var people=new ArrayList<Person>();people.add(person("FT"));
        for(int i=0;i<100;i++) people.add(identity("owned-"+i,"인력"+i,null));
        source=new Sources(source.period(),source.actor(),source.asOfDate(),people,source.prices(),source.states(),source.payroll(),source.assignments(),
            source.projectMonths(),source.projects(),source.projectParts(),source.turnkey(),source.storedCosts(),source.standards(),source.parts(),source.closes(),source.allowanceCodes());
        mvc.perform(get("/api/individual-profitability")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(101));
        verify(repository).loadYear(null);verifyNoMoreInteractions(repository);
        verify(shared).getCodes("JOB",true);verify(shared).getCodes("GRADE",true);verify(shared).getCodes("EMP_TYPE",true);
        verifyNoMoreInteractions(shared);verifyNoInteractions(mapper);
    }
}
