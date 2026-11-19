package com.sicost.basic147;

import com.sicost.permission.*;
import com.sicost.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionServiceTest {
    PermissionMapper mapper=mock(PermissionMapper.class);
    PermissionService policy=new PermissionService(mapper,Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"),ZoneId.of("Asia/Seoul")));
    PermissionMapper.Menu menu(String id,String parent,String screen,String use,int order,boolean inquiry,boolean registration,boolean update) {return new PermissionMapper.Menu(id,parent,screen,use,order,false,inquiry,registration,update,false,false,false,false);}
    @BeforeEach void principal() {SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("real-user",null,List.of()));}
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void renegotiationPartFieldsKeepEmployeeTypeAndOnlySensitiveRolesReceiveMonthlyCost() {
        for(String role:List.of("SALES_LDR","FIN","EXEC","ADMIN")) {
            boolean sensitive=!role.equals("SALES_LDR");
            when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role(
                role,sensitive?"ALL":"PART",sensitive?"Y":"N")));
            var fields=policy.capabilities().fields().get("UI-32");
            assertThat(fields).contains("employeeType","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain");
            assertThat(fields.contains("monthlyLaborCost")).isEqualTo(sensitive);
        }
    }
    @Test void renegotiationExceptionDoesNotExposeLaborTotalsInPersonalCostDetails() throws Exception {
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("SALES_LDR","PART","N")));
        var projection=new PermissionProjectionService(policy);var json=new ObjectMapper();
        var source=json.readTree("{\"knownLaborAllocated\":\"7312345\",\"laborAllocated\":\"0\",\"pay\":null,\"knownPersonalRevenue\":\"9000000\",\"availableFields\":[\"knownLaborAllocated\",\"knownPersonalRevenue\"]}");
        var result=projection.publicView("UI-03",source.deepCopy());
        assertThat(result.has("knownLaborAllocated")).isFalse();
        assertThat(result.has("laborAllocated")).isFalse();assertThat(result.has("pay")).isFalse();
        assertThat(result.path("availableFields").toString()).doesNotContain("knownLaborAllocated");
        assertThat(result.path("knownPersonalRevenue").asText()).isEqualTo("9000000");
        assertThat(source.path("knownLaborAllocated").asText()).isEqualTo("7312345");
        assertThat(projection.publicView("UI-32",json.readTree("{\"marginRate\":\"10\",\"monthlyLaborCost\":\"7312345\"}")).toString()).contains("marginRate").doesNotContain("monthlyLaborCost");
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("FIN","ALL","Y")));
        assertThat(projection.publicView("UI-03",source.deepCopy()).path("knownLaborAllocated").asText()).isEqualTo("7312345");
    }
    @Test void administratorAndAllDoNotInventAMissingMenuGrant() {
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("ADMIN","ALL","Y")));
        assertThat(policy.capabilities().screens()).isEmpty();assertThat(policy.allows("UI-23","UPD")).isFalse();assertThatThrownBy(()->policy.require("UI-23","INQ")).isInstanceOf(ApiException.class);
    }
    @Test void inactiveAncestorAndMissingInquiryBlockAllActions() {
        when(mapper.menus("real-user")).thenReturn(List.of(menu("root",null,null,"N",1,true,false,false),menu("child","root","UI-23","Y",1,true,true,true)));
        assertThat(policy.capabilities().screens()).isEmpty();
        when(mapper.menus("real-user")).thenReturn(List.of(menu("standalone",null,"UI-33","Y",1,false,true,true)));
        assertThat(policy.capabilities().screens()).isEmpty();
    }
    @Test void firstAllowedScreenUsesHierarchicalMenuOrderNotFlatChildSort() {
        when(mapper.menus("real-user")).thenReturn(List.of(menu("r1",null,null,"Y",1,true,false,false),menu("b","r2","UI-02","Y",1,true,false,false),menu("r2",null,null,"Y",2,true,false,false),menu("a","r1","UI-01","Y",9,true,false,false)));
        assertThat(policy.capabilities().screens().keySet()).containsExactly("UI-01","UI-02");
    }
    @Test void partHeadOfficeAndProjectScopesUnionAndRateProjectionPreservesDeniedValues() throws Exception {
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("custom-part","PART","N"),new PermissionMapper.Role("PM","PROJ","N")));
        when(mapper.parts("real-user","202611")).thenReturn(List.of("HQ","PART-1"));when(mapper.projects("real-user")).thenReturn(List.of("P-1"));
        assertThat(policy.capabilities().scope().partIds()).containsExactlyInAnyOrder("HQ","PART-1");assertThat(policy.capabilities().scope().projectIds()).containsExactly("P-1");
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("PM","PROJ","N")));
        assertThat(policy.candidateRateAllowed()).isFalse();var projection=new PermissionProjectionService(policy);var json=new ObjectMapper();
        var input=json.readTree("{\"name\":\"합성\",\"desiredRate\":null}");assertThat(projection.candidateInput(input,"7312345",false).path("desiredRate").asText()).isEqualTo("7312345");assertThat(input.path("desiredRate").isNull()).isTrue();
        assertThatThrownBy(()->projection.candidateInput(json.readTree("{\"desiredRate\":\"0\"}"),"7312345",false)).isInstanceOf(ApiException.class);
        assertThat(projection.candidateColumns(List.of("name","desiredRate"))).containsExactly("name");
        when(mapper.roles("real-user")).thenReturn(List.of(new PermissionMapper.Role("PM","PROJ","N"),new PermissionMapper.Role("FIN","ALL","Y")));
        assertThat(policy.candidateRateAllowed()).isTrue();
    }
}
