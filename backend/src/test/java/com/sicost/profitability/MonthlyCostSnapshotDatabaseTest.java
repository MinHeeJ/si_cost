package com.sicost.profitability;

import com.sicost.common.api.ApiExceptionHandler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Existing independent DB only: no schema/seed replay, source edits, or test-owned business inserts. */
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL", matches=".+")
class MonthlyCostSnapshotDatabaseTest {
    CostDatabaseSupport db;
    MonthlyCostRepository repository;
    MonthlyCostMapper mapper;
    MonthlyCostService service;
    Map<String,List<String>> before;
    @BeforeEach void connect() throws Exception {
        db=new CostDatabaseSupport();
        repository=db.context.getBean(MonthlyCostRepository.class);
        mapper=db.context.getBean(MonthlyCostMapper.class);
        var payroll=new EstimatedPayrollAdapter();
        service=new MonthlyCostService(repository,new MonthlyCostCalculation(payroll),new TurnkeyRevenueCalculation(),payroll);
        before=db.snapshot();
    }
    @AfterEach void allKeysValuesAndAuditTimesRemainUntouched() {
        if(db!=null) try { if(before!=null) assertTrue(before.equals(db.snapshot()),"원천·설정·감사 자료가 변경되었습니다."); }
        finally { db.close(); }
    }
    @Test void turnkeyAndPartPhysicalKeysAndEverySourceValueMatchTheExistingSchema() {
        keys("tb_pj_tk_dstrb","proj_id","dstrb_step_cd","row_sn","dstrb_ym");
        keys("tb_pj_proj_part","proj_id","aply_bgn_ym");
        assertThat(columns("tb_pj_tk_dstrb")).contains("row_type_cd","dev_id","dstrb_amt","item_nm","upld_id").doesNotContain("job_cd");
        assertThat(columns("tb_pj_proj_part")).contains("part_id").doesNotContain("aply_end_ym");
        var cells=mapper.turnkey();
        assertThat(cells).as("runner의 기존 TK 배분 원천이 필요합니다.").isNotEmpty();
        for(var cell:cells) {
            var raw=db.sql.queryForMap("SELECT * FROM tb_pj_tk_dstrb WHERE proj_id=? AND dstrb_step_cd=? AND row_sn=? AND dstrb_ym=?",
                cell.projectId(),cell.step(),new BigDecimal(cell.sequence()),cell.month());
            assertThat(cell.rowType()).isEqualTo(raw.get("row_type_cd"));assertThat(cell.personId()).isEqualTo(raw.get("dev_id"));
            assertThat(cell.itemName()).isEqualTo(raw.get("item_nm"));assertThat(cell.amount()).isEqualTo(raw.get("dstrb_amt"));
            assertThat(cell.uploadId()).isEqualTo(Objects.toString(raw.get("upld_id"),null));
        }
        assertThat(mapper.projectParts()).as("runner의 기존 프로젝트 파트 이력이 필요합니다.").isNotEmpty();
        for(var part:mapper.projectParts()) assertThat(part.partId()).isEqualTo(db.sql.queryForObject(
            "SELECT part_id FROM tb_pj_proj_part WHERE proj_id=? AND aply_bgn_ym=?",String.class,part.projectId(),part.fromMonth()));
    }
    @Test void protectedServiceKeepsEverySnapshotKeyAndSalaryIsCountedOnlyOnce() {
        db.read(()->{
            var s=repository.loadYear(null);var result=service.calculate(s);
            int protectedCount=0;
            for(var item:result.items()) {
                if(!s.protectedPersonMonth(item.month(),item.personId())) continue;
                var stored=s.storedCosts().stream().filter(c->c.month().equals(item.month())&&c.personId().equals(item.personId()))
                    .sorted(Comparator.comparing(StoredCost::projectId).thenComparing(StoredCost::assignmentId)).toList();
                if(stored.isEmpty()) {
                    assertThat(item.completeness().costComplete()).isFalse();assertThat(item.completeness().revenueComplete()).isFalse();
                    assertThat(item.rows()).isEmpty();assertThat(item.laborSource().laborCost()).isNull();continue;
                }
                assertThat(item.rows()).containsExactlyElementsOf(stored.stream().map(StoredCost::toRow).toList());
                var labor=stored.stream().map(StoredCost::laborAllocated).reduce(BigDecimal.ZERO,BigDecimal::add);
                assertThat(item.laborSource().laborCost()).isEqualTo(money(labor));
                assertThat(item.knownLaborAllocated()).isEqualTo(money(labor));
                assertThat(item.rows()).allMatch(r->!r.rowGenerated());
                assertThat(item.assignmentMm()).isEqualTo(mm(item.rows().stream().filter(r->!MonthlyCostService.synthetic(r))
                    .map(r->new BigDecimal(r.assignmentMm())).reduce(BigDecimal.ZERO,BigDecimal::add)));
                protectedCount++;
            }
            assertThat(protectedCount).as("현재 조회연도의 CLOSE/FIX 사본 자료가 필요합니다.").isPositive();
            return true;
        });
    }
    @Test void commonMonthlyHttpReadRunsInsideOneReadOnlySnapshotWithoutFiltersOrSourceWrites() throws Exception {
        var json=db.json.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mvc=MockMvcBuilders.standaloneSetup(new MonthlyCostController(service)).setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json)).build();
        db.read(()->{
            var sources=repository.loadYear(null);
            assertThat(db.sql.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            assertThat(db.sql.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            var result=service.calculate(sources);
            assertThat(result.items()).isNotEmpty();
            var month=YearMonth.from(sources.asOfDate());
            var expected=service.getMonthlyCosts(new MonthlyCostInput.Query(month.getYear(),month.getMonthValue(),null));
            try {
                var response=mvc.perform(get("/api/monthly-costs").param("year",Integer.toString(month.getYear()))
                    .param("month",Integer.toString(month.getMonthValue())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
                var actual=db.json.readTree(response.getContentAsByteArray()).get("data");
                var expectedTree=json.valueToTree(expected);
                // Request calculation instants differ even inside one DB snapshot; source data must not.
                ((com.fasterxml.jackson.databind.node.ObjectNode)actual.get("period")).remove("calculatedAt");
                ((com.fasterxml.jackson.databind.node.ObjectNode)expectedTree.get("period")).remove("calculatedAt");
                assertThat(actual).isEqualTo(expectedTree);
            } catch(Exception e) {throw new AssertionError("공통 월 API 검증 실패",e);}
            return true;
        });
    }
    @Test void existingSelectedPersonAmountsAndFallbackFormulasAreConservedWithoutItemOrContractPersonDuplication() {
        db.read(()->{
            var s=repository.loadYear(null);var result=service.calculate(s);
            Set<String> witnessed=new HashSet<>();
            for(var item:result.items()) {
                if(s.protectedPersonMonth(item.month(),item.personId())) continue;
                for(var project:s.projects()) {
                    if(!"TK".equals(project.contractType())) continue;
                    var all=s.turnkey().stream().filter(c->c.projectId().equals(project.projectId())).toList();
                    String step=List.of("PLAN","CTRT","EST").stream().filter(t->all.stream().anyMatch(c->c.step().equals(t))).findFirst().orElse(null);
                    var selected=all.stream().filter(c->Objects.equals(c.step(),step)&&"DEV".equals(c.rowType())&&item.personId().equals(c.personId())).toList();
                    var paid=item.rows().stream().filter(r->r.projectId().equals(project.projectId())&&"ALLOC".equals(r.costClass())&&!"FREE".equals(r.inputType()))
                        .sorted(Comparator.comparing(MonthlyCostRow::assignmentId)).toList();
                    item.rows().stream().filter(r->r.projectId().equals(project.projectId())&&"FREE".equals(r.inputType()))
                        .forEach(r->assertThat(r.personalRevenue()).isEqualTo("0"));
                    if(!selected.isEmpty()) {
                        BigDecimal amount=selected.stream().filter(c->c.month().equals(item.month())).map(TurnkeyCell::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
                        if(paid.isEmpty()) {
                            if(amount.signum()!=0) {assertThat(item.completeness().issues()).anyMatch(i->i.source().equals("TB_PJ_TK_DSTRB"));
                                assertThat(item.completeness().allocationComplete()).isFalse();witnessed.add("unallocated");}
                            continue;
                        }
                        BigDecimal mm=paid.stream().map(r->new BigDecimal(r.assignmentMm())).reduce(BigDecimal.ZERO,BigDecimal::add);
                        BigDecimal allocated=BigDecimal.ZERO;
                        for(int i=0;i<paid.size();i++) {
                            var r=paid.get(i);
                            BigDecimal expected=i==paid.size()-1?amount.subtract(allocated):amount.multiply(new BigDecimal(r.assignmentMm())).divide(mm,0,RoundingMode.DOWN);
                            assertThat(r.personalRevenue()).isEqualTo(money(expected));allocated=allocated.add(expected);
                        }
                        assertThat(allocated).isEqualByComparingTo(amount);
                        if(paid.size()>1) witnessed.add("OQ-CALC-003-formula");
                        if(selected.stream().noneMatch(c->c.month().equals(item.month()))) witnessed.add("empty-month");
                    } else for(var r:paid) {
                        if(project.budget()==null||project.budget().signum()==0) {
                            assertThat(r.personalRevenue()).isNull();assertThat(r.revenueComplete()).isFalse();witnessed.add("budget-unknown");
                        } else if(r.laborAllocated()!=null) {
                            var expected=new BigDecimal(r.laborAllocated()).multiply(project.contractAmount()).divide(project.budget(),0,RoundingMode.DOWN);
                            assertThat(r.personalRevenue()).isEqualTo(money(expected));witnessed.add("OQ-CALC-004-formula");
                        }
                    }
                }
                for(var row:item.rows()) {
                    if("ALLOC".equals(row.costClass())&&Set.of("MO","DP").contains(Objects.toString(row.contractType(),""))&&!"FREE".equals(row.inputType())) {
                        var source=s.assignments().stream().filter(a->a.assignmentId().equals(row.assignmentId())&&a.month().equals(row.month())).findFirst().orElseThrow();
                        assertThat(row.personalRevenue()).isEqualTo(money(source.salesAmount()));
                    }
                }
            }
            assertThat(witnessed).as("runner의 독립 기존 DB에 TK 복수유상/대체식/빈월/예산미확인/미배분 조건이 필요합니다. OQ 원금은 생성하지 않습니다.")
                .contains("OQ-CALC-003-formula","OQ-CALC-004-formula","empty-month","budget-unknown","unallocated");
            return true;
        });
    }
    @Test void closedMissingRowsAndValidZeroRowsAreBothRepresentedByTheRealService() {
        db.read(()->{
            var s=repository.loadYear(null);var result=service.calculate(s);
            assertThat(result.items()).as("CLOSE 사본 누락 인력 fixture 필요").anyMatch(m->s.protectedMonth(m.month())&&m.rows().isEmpty()
                &&!m.completeness().costComplete()&&!m.completeness().revenueComplete());
            assertThat(result.items()).as("CLOSE 유효0원 사본 인력 fixture 필요").anyMatch(m->s.protectedMonth(m.month())&&!m.rows().isEmpty()
                &&m.completeness().costComplete()&&m.rows().stream().allMatch(r->"0".equals(r.totalCost())));
            return true;
        });
    }
    private List<String> columns(String table) {
        return db.sql.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?",String.class,table);
    }
    private void keys(String table,String... expected) {
        assertThat(db.sql.queryForList("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey) WHERE i.indrelid=CAST(? AS regclass) AND i.indisprimary ORDER BY array_position(i.indkey::smallint[],a.attnum)",String.class,table)).containsExactly(expected);
    }
}
