package com.sicost.basic147;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.permission.*;
import com.sicost.salespartsettlements.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;

class PermissionViewsTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @BeforeEach void request() {RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));}
    @AfterEach void clear() {RequestContextHolder.resetRequestAttributes();}
    @Test void rateUsesExactRestrictedAllowlistNotNullMoneyOrPrivateEvidence() throws Exception {
        ObjectNode data=(ObjectNode)json.readTree("{\"baseMonth\":\"2026-11\",\"calculatedAt\":\"2026-11-17\",\"summary\":{\"personCount\":1,\"weightedMarginRate\":\"10.0\"},\"items\":[{\"name\":\"synthetic\",\"monthlyLaborCost\":\"5400000\",\"managementNo\":\"private-reference\",\"employeeType\":\"FT\",\"unitPrice\":\"6000000\",\"marginRate\":\"10.0\"}],\"issues\":[{\"message\":\"private-evidence\"}],\"unevaluatedAssignments\":[{\"message\":\"private-evidence\"}]}");
        PermissionViews.rate(data);
        assertThat(data.toString()).doesNotContain("monthlyLaborCost","5400000","managementNo","employeeType","private-evidence","issues","unevaluatedAssignments");
        assertThat(data.path("items").path(0).path("unitPrice").asText()).isEqualTo("6000000");
        assertThat(data.path("items").path(0).path("marginRate").asText()).isEqualTo("10.0");
        assertFields(data);assertFields(data.path("summary"));assertFields(data.path("items").path(0));
    }
    @Test void settlementCalculatesSummaryBeforePaginationAndOmitsAllMoneyEvidence() {
        var rows=List.of(row("allowed-positive","50"),row("allowed-negative","150"));
        Totals totals=SalesPartSettlementCalculator.totals(rows,"17",null,false);
        PermissionViewMetadata.totals(totals,rows);
        Result source=result(totals,List.of(rows.get(0)));
        ObjectNode view=json.valueToTree(source);PermissionViews.settlement(view,source,json);
        assertThat(view.path("summary").path("personCount").asInt()).isEqualTo(2);
        assertThat(view.path("summary").path("surplusPersonCount").asInt()).isEqualTo(1);
        assertThat(view.path("summary").path("deficitPersonCount").asInt()).isEqualTo(1);
        assertThat(view.path("items").path(0).path("profitStatus").asText()).isEqualTo("SURPLUS");
        assertThat(view.toString()).doesNotContain("payAmount","costAmount","cardAmount","profitAmount","profitShareAmount","salesExpenseAmount","retirementAmount","basis","developerId","managementNo","remark");
        assertThat(view.path("canConfirm").asBoolean()).isFalse();assertThat(view.path("readOnly").asBoolean()).isTrue();
        assertFields(view);assertFields(view.path("standard"));assertFields(view.path("summary"));assertFields(view.path("items").path(0));
        assertThat(source.items().get(0).payAmount()).isEqualTo("50");
    }
    @Test void zeroProfitDoesNotInventAClassificationOrSurplusCount() {
        var rows=List.of(row("zero","90"));var totals=SalesPartSettlementCalculator.totals(rows,"0",null,false);
        PermissionViewMetadata.totals(totals,rows);var source=result(totals,rows);ObjectNode view=json.valueToTree(source);
        PermissionViews.settlement(view,source,json);
        assertThat(view.path("items").path(0).has("profitStatus")).isFalse();
        assertThat(view.path("summary").has("surplusPersonCount")).isFalse();
    }
    @Test void calculationSourceCannotLeakItsThreadContextOnExceptionOrNesting() {
        assertThat(PermissionCalculationContext.sourceRead()).isFalse();
        assertThatThrownBy(()->PermissionCalculationContext.source(()->{assertThat(PermissionCalculationContext.sourceRead()).isTrue();throw new IllegalStateException("synthetic");})).isInstanceOf(IllegalStateException.class);
        assertThat(PermissionCalculationContext.sourceRead()).isFalse();
        PermissionCalculationContext.source(()->{PermissionCalculationContext.source(()->true);assertThat(PermissionCalculationContext.sourceRead()).isTrue();return true;});
        assertThat(PermissionCalculationContext.sourceRead()).isFalse();
    }
    private Row row(String name,String pay) {
        var a=SalesPartSettlementCalculator.amounts("CT",new BigDecimal(pay),new BigDecimal("100"),new Standard("leader","Leader","50","10","2026-11",true));
        return new Row("DEV-"+name,name,"synthetic","CT",List.of("owned project"),null,"1.00","100",a.pay(),a.expense(),a.retirement(),a.cost(),a.profit(),a.share(),"private remark",null,List.of());
    }
    private Result result(Totals totals,List<Row> rows) {return new Result("PART-1","2026-11","WORK",new Standard("leader","Leader","50","10","2026-11",true),"17","opaque",null,null,null,null,false,List.of(),true,true,false,totals,totals,rows,new Page(1,1,totals.personCount()),List.of());}
    private void assertFields(JsonNode value) {Set<String> actual=new HashSet<>();value.fieldNames().forEachRemaining(actual::add);actual.remove("availableFields");Set<String> declared=new HashSet<>();value.path("availableFields").forEach(v->declared.add(v.asText()));assertThat(declared).isEqualTo(actual);}
}
