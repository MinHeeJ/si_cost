package com.sicost.salespartsettlements;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SalesPartSettlementQueryTest {
    SalesPartSettlementRepository repository;
    SalesPartSettlementService service;
    SalesPartSettlementRepository.Snapshot snapshot;
    MockMvc mvc;
    @BeforeEach void setup() {
        repository=mock(SalesPartSettlementRepository.class);
        var monthlyRepository=mock(MonthlyCostRepository.class);
        var payroll=new EstimatedPayrollAdapter();var turnkey=new TurnkeyRevenueCalculation();
        var monthly=new MonthlyCostService(monthlyRepository,new MonthlyCostCalculation(payroll),turnkey,payroll);
        var json=JsonMapper.builder().findAndAddModules().build();
        service=new SalesPartSettlementService(repository,new SalesPartSettlementSources(monthly,turnkey),new SalesPartSettlementSnapshotReader(json),new RevisionService(json));
        mvc=MockMvcBuilders.standaloneSetup(new SalesPartSettlementController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        snapshot=fixture("202607",1);
        when(repository.load(any(),anyBoolean())).thenAnswer(call->snapshot);
    }
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    static SalesPartSettlementRepository.Snapshot fixture(String month,int count) {
        var people=new ArrayList<Person>();var pays=new ArrayList<Payroll>();var assignments=new ArrayList<Assignment>();var first=new ArrayList<FirstInput>();
        for(int i=0;i<count;i++) {
            String id="dev-"+i;
            people.add(new Person(id,id,"인력"+i,"FT",null,null,null,"JOB",null,null,"DRCT","part","파트",null,null,"RETIRE","N"));
            pays.add(new Payroll(month,id,n("120"),null,null,null,null,null,null,null,null,"FT","DRCT",null,null,null,null,null));
            assignments.add(new Assignment(month,id,"project","asgn-"+i,n("0.50"),n("999"),n("1000"),"N","JOB","PAID","contract-person","ACT",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31)));
            first.add(new FirstInput(id,LocalDate.of(2026,1,1)));
        }
        var sources=new Sources(new ProfitabilityPeriod(2026,"202601",month,month,OffsetDateTime.parse("2026-11-14T09:00:00+09:00"),false),"actor",LocalDate.of(2026,11,14),people,List.of(),List.of(),pays,assignments,assignments,
            List.of(new Project("project","프로젝트, 이름","MO","ING",null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),n("1000"),n("1000"),null,null)),List.of(new ProjectPart("project","202601","HQ")),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var part=new PartRow("part","파트","N","Y","actor",LocalDateTime.of(2026,1,1,0,0),null,null);
        var hq=new PartRow("HQ","본사","Y","Y","actor",part.regDttm(),null,null);
        var standard=new StandardRow("part","202601",null,"leader","파트장",n("40"),n("10"),"actor",part.regDttm(),null,null);
        return new SalesPartSettlementRepository.Snapshot(sources,List.of(part,hq),List.of(standard),List.of(),List.of(),List.of(),first,List.of(new RevisionRow("source","[]")));
    }
    static SalesPartSettlementInput.Query query(String...pairs) {
        var values=new LinkedMultiValueMap<String,String>();for(int i=0;i<pairs.length;i+=2)values.add(pairs[i],pairs[i+1]);
        return SalesPartSettlementInput.query(values,true);
    }
    @Test void publicListUsesExactEnvelopeAndExcludesHeadquartersWithoutCreatingParents() throws Exception {
        mvc.perform(get("/api/sales-part-settlements")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.month").value("2026-07")).andExpect(jsonPath("$.data.parts.length()").value(1))
            .andExpect(jsonPath("$.data.eligibleCount").value(1)).andExpect(jsonPath("$.data.unconfirmedCount").value(1));
        verify(repository).load(null,false);verifyNoMoreInteractions(repository);
    }
    @Test void detailUsesCurrentPersonPartAndPartCostNotCompanyInsuranceOrProjectPart() throws Exception {
        mvc.perform(get("/api/sales-part-settlements/part")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].developerId").value("dev-0"))
            .andExpect(jsonPath("$.data.items[0].mm").value("0.50"))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value("1000"))
            .andExpect(jsonPath("$.data.items[0].payAmount").value("120"))
            .andExpect(jsonPath("$.data.items[0].retirementAmount").value("10"))
            .andExpect(jsonPath("$.data.items[0].costAmount").value("140"))
            .andExpect(jsonPath("$.data.items[0].profitShareAmount").value("344"))
            .andExpect(jsonPath("$.data.cardAmount").value("0")).andExpect(jsonPath("$.data.revision").isNotEmpty());
        assertThat(snapshot.parents()).isEmpty();assertThat(snapshot.details()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"month=2026-13","month=202607","month=2026-07&month=2026-07","page=1","actor=x","year=2026"})
    void invalidTabsQueriesNeverReadOrWrite(String query) throws Exception {
        mvc.perform(get("/api/sales-part-settlements?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").exists());verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"page=0","size=21","sort=mm&sort=name","direction=desc","direction=SIDEWAYS","partScope=x","userId=x","actor=x","year=2026"})
    void invalidDetailQueriesNeverReadOrWrite(String query) throws Exception {
        mvc.perform(get("/api/sales-part-settlements/part?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").exists());verifyNoInteractions(repository);
    }
    @Test void rejectsGetBodyAndUnknownPartAndHeadquarters() throws Exception {
        mvc.perform(get("/api/sales-part-settlements").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sales-part-settlements/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/sales-part-settlements/HQ")).andExpect(status().isNotFound());
    }
    @Test void pagesKeepWholePopulationSummaryTotalsAndRevision() {
        snapshot=fixture("202607",61);var before=snapshot;
        var first=service.getSalesPartSettlement("part",query("size","20"));
        assertThat(first.items()).hasSize(20);assertThat(first.summary().personCount()).isEqualTo(61);
        for(String size:List.of("20","50","100")) {
            var result=service.getSalesPartSettlement("part",query("size",size,"page","2","sort","mm","direction","DESC"));
            assertThat(result.totals()).isEqualTo(first.totals());assertThat(result.summary()).isEqualTo(first.summary());assertThat(result.revision()).isEqualTo(first.revision());
        }
        assertThat(snapshot).isEqualTo(before);
    }
    @Test void stopResumeInterpretsMonthEndAndNumericHistoryTieBreak() {
        var p=snapshot.parts().get(0);var t=LocalDateTime.of(2026,8,15,10,0);
        var history=List.of(new UseHistory("part","9","Y","N",t),new UseHistory("part","10","N","Y",t),new UseHistory("part","11","Y","N",t),new UseHistory("part","12","N","Y",t.plusMonths(1)));
        assertThat(SalesPartSettlementService.activeAtMonthEnd(p,history,"202607")).isTrue();
        assertThat(SalesPartSettlementService.activeAtMonthEnd(p,history,"202608")).isFalse();
        assertThat(SalesPartSettlementService.activeAtMonthEnd(p,history,"202609")).isTrue();
    }
    @Test void missingAndOverlappingStandardsAreNotTargetsButConfirmedHistoryRemains() {
        var s=snapshot;var parent=new Parent("part","202607",n("0"),n("0"),"CNFM","actor","확정자",LocalDateTime.of(2026,7,31,0,0),null,"actor",null,null,null);
        snapshot=new SalesPartSettlementRepository.Snapshot(s.sources(),s.parts(),List.of(),s.history(),List.of(parent),s.details(),s.firstInputs(),s.revisionSources());
        var tabs=service.listEligibleParts(null);assertThat(tabs.eligibleCount()).isZero();assertThat(tabs.parts().get(0).historyOnly()).isTrue();assertThat(tabs.issues()).isNotEmpty();
        snapshot=new SalesPartSettlementRepository.Snapshot(s.sources(),s.parts(),List.of(s.standards().get(0),s.standards().get(0)),s.history(),List.of(),s.details(),s.firstInputs(),s.revisionSources());
        assertThat(service.listEligibleParts(null).eligibleCount()).isZero();
    }
    @Test void confirmedSnapshotNeverRecalculatesAmountsProjectsOrTypeAndRevisionIgnoresPayChanges() {
        var s=snapshot;var parent=new Parent("part","202607",n("5"),n("999"),"AUTO","actor","확정자",LocalDateTime.of(2026,7,31,0,0),null,"actor",null,null,null);
        var detail=new Detail("part","202607","dev-0","현재 이름","현재 번호","OS","[\"저장, 프로젝트\"]",n("0.75"),n("100"),n("10"),n("0"),n("0"),n("10"),n("90"),n("36"),"급여 예정",null,null,null,null);
        snapshot=new SalesPartSettlementRepository.Snapshot(s.sources(),s.parts(),s.standards(),s.history(),List.of(parent),List.of(detail),s.firstInputs(),s.revisionSources());
        var first=service.getSalesPartSettlement("part",query());
        assertThat(first.items().get(0).projectNames()).containsExactly("저장, 프로젝트");assertThat(first.items().get(0).employeeType()).isEqualTo("OS");
        assertThat(first.totals().partProfitAmount()).isEqualTo("999");assertThat(first.issues()).anyMatch(i->i.code().equals("SNAPSHOT_MISMATCH"));
        assertThat(first.items().get(0).basis().distributionRate()).isNull();
        snapshot=new SalesPartSettlementRepository.Snapshot(fixture("202607",0).sources(),s.parts(),List.of(),s.history(),List.of(parent),List.of(detail),List.of(),List.of(new RevisionRow("pay","changed")));
        var next=service.getSalesPartSettlement("part",query());assertThat(next.items()).isEqualTo(first.items());assertThat(next.revision()).isEqualTo(first.revision());
        assertThat(next.standard().distributionRate()).isNull();
    }
    @Test void unexpectedFailureDoesNotLeakSql() throws Exception {
        when(repository.load(any(),anyBoolean())).thenThrow(new IllegalStateException("SELECT secret"));
        mvc.perform(get("/api/sales-part-settlements")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }
    @Test void stopResumeTabsAreVerifiedThroughHttpForJulyAugustAndSeptember() throws Exception {
        var history=List.of(new UseHistory("part","1","Y","N",LocalDateTime.of(2026,8,15,0,0)),new UseHistory("part","2","N","Y",LocalDateTime.of(2026,9,1,0,0)));
        for(String month:List.of("202607","202608","202609")) {
            var s=fixture(month,1);
            snapshot=new SalesPartSettlementRepository.Snapshot(s.sources(),s.parts(),s.standards(),history,s.parents(),s.details(),s.firstInputs(),s.revisionSources());
            mvc.perform(get("/api/sales-part-settlements?month="+month.substring(0,4)+"-"+month.substring(4)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligibleCount").value(month.equals("202608")?0:1));
        }
    }
    @Test void closedMonthWithoutSnapshotIsUnknownAndDoesNotCalculateOrCreateZeroSnapshot() throws Exception {
        var s=snapshot;var source=s.sources();
        var closed=new Sources(source.period(),source.actor(),source.asOfDate(),source.people(),source.prices(),source.states(),source.payroll(),source.assignments(),source.projectMonths(),source.projects(),source.projectParts(),source.turnkey(),source.storedCosts(),source.standards(),source.parts(),List.of(new com.sicost.shared.SharedQueryDtos.CloseRow("202607","CLOSE")),source.allowanceCodes());
        snapshot=new SalesPartSettlementRepository.Snapshot(closed,s.parts(),s.standards(),s.history(),s.parents(),s.details(),s.firstInputs(),s.revisionSources());
        mvc.perform(get("/api/sales-part-settlements/part")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.totals.partProfitAmount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.canSaveCard").value(false)).andExpect(jsonPath("$.data.issues[0].code").value("CLOSED_SNAPSHOT_MISSING"));
        assertThat(snapshot.parents()).isEmpty();assertThat(snapshot.details()).isEmpty();
    }
    @Test void internalPreviewRetainsEveryPersonAndRevisionIncludesOffPageStoredSourceChanges() {
        snapshot=fixture("202607",101);
        var first=service.getSalesPartSettlement("part",query("size","20"));
        assertThat(service.previewSettlement("part",null).items()).hasSize(101);
        var s=snapshot;
        snapshot=new SalesPartSettlementRepository.Snapshot(s.sources(),s.parts(),s.standards(),s.history(),s.parents(),s.details(),s.firstInputs(),List.of(new RevisionRow("pay","changed without MOD_DTTM")));
        assertThat(service.getSalesPartSettlement("part",query("page","9")).revision()).isNotEqualTo(first.revision());
    }
}
