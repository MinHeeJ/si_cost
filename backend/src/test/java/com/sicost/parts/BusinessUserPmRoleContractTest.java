package com.sicost.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.businessusers.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.businessusers.BusinessUserDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessUserController.class)
@ContextConfiguration(classes=BusinessUserController.class)
@Import({BusinessUserService.class,BusinessUserRepository.class,RevisionService.class,ChangeHistoryWriter.class,
    AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BusinessUserPmRoleContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean BusinessUserMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    Stored original;
    List<Role> roles;
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        original=new Stored("pm12","사용자","user@example.org","USE",null,"admin",LocalDateTime.of(2026,1,1,0,0),null,null);
        roles=new ArrayList<>(List.of(new Role("ADMIN","관리자","Y"),new Role("PM","PM","Y"),new Role("SALES_LDR","파트장","Y")));
        when(mapper.find(anyString(),anyBoolean())).thenReturn(original);when(mapper.roles(anyString())).thenAnswer(call->List.copyOf(roles));
        when(mapper.availableRoles()).thenReturn(List.of(new Role("PM","PM","Y"),new Role("SALES_LDR","파트장","Y")));
        when(mapper.connectedProjects("pm12")).thenReturn(List.of("ended-project","lost-project"));
        when(mapper.update(any())).thenReturn(1);when(mapper.unlink(any())).thenAnswer(call->{RoleLink link=call.getArgument(0);roles.removeIf(r->r.roleId().equals(link.roleId()));return 1;});
    }
    ObjectNode body(List<String> requested) {
        String revision=revisions.revision(new Snapshot(original.userId(),original.name(),original.email(),original.regDttm(),original.modDttm(),roles.stream().map(Role::roleId).sorted().toList()));
        ObjectNode body=json.createObjectNode().put("name",original.name()).put("email",original.email()).put("expectedRevision",revision);
        body.set("businessRoleIds",json.valueToTree(requested));return body;
    }
    @Test void remainingAnyLifecycleProjectBlocksPmRemovalAndPreservesOtherRoles() throws Exception {
        mvc.perform(put("/api/business-users/pm12").contentType("application/json").content(body(List.of("SALES_LDR")).toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_IS_PROJECT_PM"))
            .andExpect(jsonPath("$.field").value("businessRoleIds")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("먼저 해제")));
        verify(mapper,never()).update(any());verify(mapper,never()).unlink(any());verifyNoInteractions(history,access);
        var order=inOrder(mapper);order.verify(mapper).lockUser("pm12");order.verify(mapper).find("pm12",true);order.verify(mapper).roles("pm12");order.verify(mapper).availableRoles();order.verify(mapper).connectedProjects("pm12");
    }
    @Test void noConnectionsAllowsPmRemovalWithoutYearAndOnlyPmUnlinked() throws Exception {
        when(mapper.connectedProjects("pm12")).thenReturn(List.of());
        mvc.perform(put("/api/business-users/pm12").contentType("application/json").content(body(List.of("SALES_LDR")).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.user.businessRoleIds[0]").value("SALES_LDR"));
        verify(mapper).unlink(argThat(link->link.roleId().equals("PM")));verify(mapper,never()).unlink(argThat(link->!link.roleId().equals("PM")));
    }
    @Test void keepingPmWithLiveConnectionsDoesNotBlockUnrelatedNameEdit() throws Exception {
        ObjectNode input=body(List.of("PM","SALES_LDR")).put("name","변경 이름");
        mvc.perform(put("/api/business-users/pm12").contentType("application/json").content(input.toString())).andExpect(status().isOk());
        verify(mapper,never()).connectedProjects(anyString());verify(mapper,never()).unlink(any());verify(mapper).update(any());
    }
}
