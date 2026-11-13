package com.sicost.profitability;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static com.sicost.profitability.TurnkeyRevenueCalculationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real transport -> service -> pure calculations; only the source-set repository is replaced. */
class MonthlyCostContractTest {
    MonthlyCostRepository repository;
    MonthlyCostService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        repository=mock(MonthlyCostRepository.class);
        var payroll=new EstimatedPayrollAdapter();
        service=new MonthlyCostService(repository,new MonthlyCostCalculation(payroll),new TurnkeyRevenueCalculation(),payroll);
        mvc=MockMvcBuilders.standaloneSetup(new MonthlyCostController(service)).setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .build();
    }
    static StoredCost stored(String assignment, String labor, String revenue, String fixed) {
        return new StoredCost(MONTH,"actual","project",assignment,n("0.50"),n("0.00"),n(revenue),n(labor),n("7"),n("3"),n("10"),
            "PRE","TK","historical-part",fixed,"creator",LocalDateTime.parse("2025-12-31T10:00:00.123456"),
            "editor",LocalDateTime.parse("2026-01-01T11:00:00.654321"),null,null,null);
    }
    static Sources protectedSources(Sources s, List<StoredCost> costs, String authority) {
        var period=s.period();
        if ("BASE".equals(authority)) period=new ProfitabilityPeriod(period.year(),"202602",MONTH,MONTH,period.calculatedAt(),false);
        return new Sources(period,s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),
            s.projects(),s.projectParts(),s.turnkey(),costs,s.standards(),s.parts(),
            "CLOSE".equals(authority)?List.of(new CloseRow(MONTH,"CLOSE")):List.of(),s.allowanceCodes());
    }
    static Sources base() { return sources(person("FL"),List.of(pay("FL","999")),List.of(assignment("a","0.50")),List.of(project("TK","ACT"))); }

    @ParameterizedTest @ValueSource(strings={"CLOSE","FIX","BASE"})
    void authorityPreservesEverySnapshotNumberClassificationAndAuditAfterSourceChanges(String authority) throws Exception {
        var saved=List.of(stored("a","40","55","FIX".equals(authority)?"Y":"N"),stored("b","60","65","N"));
        var original=protectedSources(base(),saved,authority);
        var changed=protectedSources(tk("0",assignment("a","1.00")),saved,authority);
        assertThat(service.calculate(original).items().get(0).rows()).isEqualTo(service.calculate(changed).items().get(0).rows());
        when(repository.loadMonth(2026,1)).thenReturn(original);
        mvc.perform(get("/api/monthly-costs").param("year","2026").param("month","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].rows.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].rows[0].fixed").value("FIX".equals(authority)?"Y":"N"))
            .andExpect(jsonPath("$.data.items[0].rows[0].regDttm").value("2025-12-31T10:00:00.123456+09:00"))
            .andExpect(jsonPath("$.data.items[0].rows[0].modDttm").value("2026-01-01T11:00:00.654321+09:00"))
            .andExpect(jsonPath("$.data.items[0].rows[0].costClass").value("PRE"))
            .andExpect(jsonPath("$.data.items[0].rows[0].projectSalesPartId").value("historical-part"))
            .andExpect(jsonPath("$.data.items[0].laborSource.laborCost").value("100"))
            .andExpect(jsonPath("$.data.items[0].laborSource.pay").value("999"));
        assertThat(original.storedCosts()).isEqualTo(saved);
    }
    @Test void noSnapshotAndActualZeroSnapshotAreDifferentNormalResponses() throws Exception {
        when(repository.loadMonth(2026,1)).thenReturn(protectedSources(base(),List.of(),"CLOSE"));
        mvc.perform(get("/api/monthly-costs").param("year","2026").param("month","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].rows").isEmpty())
            .andExpect(jsonPath("$.data.items[0].completeness.costComplete").value(false))
            .andExpect(jsonPath("$.data.items[0].completeness.revenueComplete").value(false))
            .andExpect(jsonPath("$.data.items[0].knownLaborAllocated").isEmpty())
            .andExpect(jsonPath("$.data.items[0].completeness.issues[0].source").value("TB_CO_MON_COST"));
        var zero=new StoredCost(MONTH,"actual","IDLE","IDLE",n("1.00"),n("0.00"),n("0"),n("0"),n("0"),n("0"),n("0"),
            "IDLE",null,null,"N",null,null,null,null,null,null,null);
        when(repository.loadMonth(2026,1)).thenReturn(protectedSources(base(),List.of(zero),"CLOSE"));
        mvc.perform(get("/api/monthly-costs").param("year","2026").param("month","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].rows[0].totalCost").value("0"))
            .andExpect(jsonPath("$.data.items[0].completeness.costComplete").value(true))
            .andExpect(jsonPath("$.data.items[0].knownLaborAllocated").value("0"))
            .andExpect(jsonPath("$.data.items[0].rows[0].rowGenerated").value(false));
    }
    @Test void orphanMasterAndMissingPayrollKeepAllAssignmentKeysAndNullSalaryReferences() throws Exception {
        var s=protectedSources(base(),List.of(stored("a","40","55","N"),stored("b","60","65","Y")),"CLOSE");
        var orphan=new Sources(s.period(),s.actor(),s.asOfDate(),List.of(),s.prices(),s.states(),List.of(),List.of(),List.of(),List.of(),
            s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        when(repository.loadMonth(2026,1)).thenReturn(orphan);
        mvc.perform(get("/api/monthly-costs").param("year","2026").param("month","1").param("personId","actual"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].rows[0].personId").value("actual"))
            .andExpect(jsonPath("$.data.items[0].rows[0].assignmentId").value("a"))
            .andExpect(jsonPath("$.data.items[0].rows[1].assignmentId").value("b"))
            .andExpect(jsonPath("$.data.items[0].laborSource.pay").isEmpty())
            .andExpect(jsonPath("$.data.items[0].laborSource.laborCost").value("100"))
            .andExpect(jsonPath("$.data.items[0].completeness.costComplete").value(true));
        assertThat(service.calculate(orphan).items().get(0).completeness().issues()).extracting(MonthlyCostIssue::source)
            .contains("TB_HR_DEV","TB_HR_MON_PAY","TB_PJ_ASGN","TB_PJ_PROJ");
    }
    @Test void mixedFixedRowsNeverPartiallyReallocateAndMissingCoverageIsUnknown() {
        var s=protectedSources(base(),List.of(stored("different","100","120","Y")),"FIX");
        var result=service.calculate(s).items().get(0);
        assertThat(result.rows()).extracting(MonthlyCostRow::assignmentId).containsExactly("different");
        assertThat(result.completeness().costComplete()).isFalse();
        assertThat(result.completeness().allocationComplete()).isFalse();
        assertThat(service.summarize(List.of(result),false).profit()).isNull();
    }
    @Test void nextReadLoadsFreshDerivedPayrollAndMonthlySalesButClosedRowsStayStored() throws Exception {
        var p=person("FL");
        var initial=sources(p,List.of(pay("FL","100")),List.of(assignment("a","0.50")),List.of(project("MO","ACT")));
        var oldAssignment=assignment("a","0.50");
        var updatedAssignment=new Assignment(oldAssignment.month(),oldAssignment.personId(),oldAssignment.projectId(),oldAssignment.assignmentId(),
            oldAssignment.assignmentMm(),oldAssignment.appliedUnitPrice(),n("700"),oldAssignment.fixed(),oldAssignment.jobCode(),oldAssignment.inputType(),
            oldAssignment.contractPersonId(),oldAssignment.status(),oldAssignment.fromDate(),oldAssignment.toDate());
        var next=sources(p,List.of(pay("FL","200")),List.of(updatedAssignment),List.of(project("MO","ACT")));
        when(repository.loadMonth(2026,1)).thenReturn(initial,next,protectedSources(next,List.of(stored("a","40","55","N")),"CLOSE"));
        var query=new MonthlyCostInput.Query(2026,1,null);
        assertThat(service.getMonthlyCosts(query).items().get(0).knownLaborAllocated()).isEqualTo("100");
        var fresh=service.getMonthlyCosts(query).items().get(0);
        assertThat(fresh.knownLaborAllocated()).isEqualTo("200");assertThat(fresh.knownPersonalRevenue()).isEqualTo("700");
        assertThat(service.getMonthlyCosts(query).items().get(0).knownLaborAllocated()).isEqualTo("40");
        verify(repository,times(3)).loadMonth(2026,1);verifyNoMoreInteractions(repository);
    }
    @Test void unknownMonthPropagatesNullAndKnownMmAndAmountsSurviveWithoutLossClassification() {
        var complete=service.calculate(protectedSources(base(),List.of(stored("a","40","55","N")),"CLOSE")).items().get(0);
        var s=base();
        var february=new Sources(new ProfitabilityPeriod(2026,MONTH,"202602","202602",s.period().calculatedAt(),false),s.actor(),s.asOfDate(),
            s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),List.of(),
            s.standards(),s.parts(),List.of(new CloseRow("202602","CLOSE")),s.allowanceCodes());
        var missing=service.calculate(february).items().get(0);
        var amounts=service.summarize(List.of(complete,missing),true);
        assertThat(amounts.totalCost()).isNull();assertThat(amounts.revenue()).isNull();assertThat(amounts.profit()).isNull();
        assertThat(amounts.averageCost(2)).isNull();assertThat(amounts.confirmedLoss()).isFalse();
        assertThat(amounts.knownCost()).isEqualTo("50");assertThat(amounts.assignmentMm()).isEqualTo("0.50");
        assertThat(amounts.problemMonths()).containsExactly("202602");
        assertThat(service.summarize(List.of(complete),true).profit()).isEqualTo("5");
        assertThat(service.summarize(List.of(complete),true).averageCost(1)).isEqualTo("50");
        var loss=service.calculate(protectedSources(base(),List.of(stored("a","100","0","N")),"CLOSE")).items().get(0);
        assertThat(service.summarize(List.of(loss),true).confirmedLoss()).isTrue();
    }
    @Test void mixedPersonExclusionResolvesOnlyTkRevenueUnknownWithoutMutatingOriginal() {
        var s=tk(null,assignment("tk","0.50"));
        var mo=new Project("monthly","월단위","MO","ACT",null,s.asOfDate(),s.asOfDate(),n("100"),n("100"),n("0"),n("0"));
        var a=assignment("mo","0.50");
        var moAssignment=new Assignment(MONTH,a.personId(),"monthly",a.assignmentId(),a.assignmentMm(),a.appliedUnitPrice(),n("120"),"N",
            a.jobCode(),a.inputType(),a.contractPersonId(),a.status(),a.fromDate(),a.toDate());
        var mixed=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),List.of(s.assignments().get(0),moAssignment),
            List.of(s.assignments().get(0),moAssignment),List.of(s.projects().get(0),mo),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var raw=service.calculate(mixed).items();
        assertThat(service.summarize(raw,true).profit()).isNull();
        var view=service.summarize(raw,false);
        assertThat(view.revenue()).isEqualTo("120");assertThat(view.totalCost()).isEqualTo("115");assertThat(view.profit()).isEqualTo("5");
        assertThat(raw.get(0).completeness().revenueComplete()).isFalse();
    }
    @Test void requiredDiagnosticsHavePersonMonthSourceAndRelatedScreen() throws Exception {
        var unallocated=sources(person("FL"),List.of(pay("FL","100")),List.of(),List.of());
        var result=service.calculate(unallocated).items().get(0);
        assertThat(result.completeness().issues()).allSatisfy(i->{assertThat(i.personId()).isEqualTo("actual");
            assertThat(i.month()).isEqualTo(MONTH);assertThat(i.source()).isNotBlank();assertThat(i.relatedScreen()).isNotBlank();});
        assertThat(result.completeness().allocationComplete()).isFalse();
        when(repository.loadMonth(2026,1)).thenReturn(tk("0",assignment("a","1.00")));
        mvc.perform(get("/api/monthly-costs").param("year","2026").param("month","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].completeness.revenueComplete").value(false))
            .andExpect(jsonPath("$.data.items[0].rows[0].personalRevenue").isEmpty());
    }
    @Test void strictQueryValidationRejectsUnknownDuplicateMissingBadIntegerAndBodyBeforeAnyRead() throws Exception {
        for(String query:List.of("year=2026","month=1","year=2026&month=13","year=2026&month=0","year=2e3&month=1",
            "year=2026&month=1&year=2025","year=2026&month=1&includeTurnkey=false","year=2026&month=1&personId=")) {
            mvc.perform(get("/api/monthly-costs?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        mvc.perform(get("/api/monthly-costs?year=2026&month=1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    @Test void missingBasicPayIsDiagnosedWithoutHidingAnotherCompleteIndirectPerson() throws Exception {
        var p=person("FT");
        var indirect=new Person("indirect", "owned-indirect", "간접인력", "CT", null,null,null,"JOB",null,null,
            "DRCT",null,null,null,null,"WORK","N");
        var pay=new Payroll(MONTH,"indirect",n("100"),n("0"),n("0"),null,n("0"),null,null,null,null,"CT","INDRCT",null,null,null,null,null);
        var s=sources(p,List.of(pay),List.of(),List.of());
        var all=new Sources(s.period(),s.actor(),s.asOfDate(),List.of(p,indirect),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),
            s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var result=service.calculate(all).items();
        var unknown=result.stream().filter(m->m.personId().equals("actual")).findFirst().orElseThrow();
        var complete=result.stream().filter(m->m.personId().equals("indirect")).findFirst().orElseThrow();
        assertThat(unknown.completeness().costComplete()).isFalse();
        assertThat(unknown.completeness().issues()).anySatisfy(i->{
            assertThat(i.source()).isEqualTo("TB_HR_MON_PAY");assertThat(i.relatedScreen()).isEqualTo("UI-02");
            assertThat(i.personId()).isEqualTo("actual");assertThat(i.month()).isEqualTo(MONTH);
        });
        assertThat(complete.completeness().costComplete()).isTrue();
        assertThat(complete.rows()).singleElement().satisfies(r->{assertThat(r.costClass()).isEqualTo("INDRCT");
            assertThat(r.assignmentMm()).isEqualTo("0.00");assertThat(r.laborAllocated()).isEqualTo("100");});
        assertThat(service.summarize(List.of(unknown),true).confirmedLoss()).isFalse();
        assertThat(service.summarize(List.of(complete),true).confirmedLoss()).isTrue();
        when(repository.loadMonth(2026,1)).thenReturn(all);
        mvc.perform(get("/api/monthly-costs?year=2026&month=1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
    }
    @Test void noDataAndUnknownSelectedPersonReturnEmptyArrayNotSyntheticZeroOr404() throws Exception {
        var s=base();
        var noData=new Sources(new ProfitabilityPeriod(2026,"202602",null,null,s.period().calculatedAt(),true),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),
            s.payroll(),s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        when(repository.loadMonth(2026,1)).thenReturn(noData,s);
        mvc.perform(get("/api/monthly-costs?year=2026&month=1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.period.noData").value(true))
            .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/monthly-costs?year=2026&month=1&personId=missing")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void unexpectedRepositoryErrorsDoNotLeakPayrollSqlOrExceptionMessage() throws Exception {
        when(repository.loadMonth(2026,1)).thenThrow(new IllegalStateException("sensitive payroll SQL"));
        mvc.perform(get("/api/monthly-costs?year=2026&month=1")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }
}
