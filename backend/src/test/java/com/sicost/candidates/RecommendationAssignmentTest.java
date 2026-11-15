package com.sicost.candidates;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.RecommendationReference;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static com.sicost.candidates.RecommendationActionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationAssignmentTest {
    final RecommendationActionRepository repository=mock(RecommendationActionRepository.class);
    final RecommendationActionMapper mapper=mock(RecommendationActionMapper.class);
    final AssignmentActionsService assignments=mock(AssignmentActionsService.class);
    final ChangeHistoryWriter history=mock(ChangeHistoryWriter.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final RecommendationAssignmentSelection selection=new RecommendationAssignmentSelection(repository,mapper,assignments,history,users,Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"),ZoneId.of("Asia/Seoul")));
    Row row;Create input;Save saved;
    @BeforeEach void setup(){
        row=new Row("101",3,"AI",null,null,"P-0101","BE","202607","D-0101","{}",null,null,null,null,LocalDateTime.of(2026,7,1,9,0));
        input=new Create(2026,"P-0101","D-0101","BE",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),new BigDecimal("1.00"),null,"NEW",null,new RecommendationReference("101",3));
        when(repository.required(any())).thenReturn(row);when(repository.lock(any(),any())).thenReturn(row);when(repository.current(any())).thenReturn(new Revisions("expected","target","candidate"));when(mapper.targetOpen(any())).thenReturn(true);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        Assignment assignment=mock(Assignment.class);when(assignment.id()).thenReturn("A-0101");when(assignment.developerId()).thenReturn("D-0101");when(assignment.projectId()).thenReturn("P-0101");when(assignment.billingUnitPrice()).thenReturn("101");saved=new Save(assignment,true,List.of(),List.of());
    }
    @Test void matchingDeveloperAndProjectRecordsActualIdAndFinalInput(){
        assertThat(selection.create(input,"A-0101",()->saved)).isSameAs(saved);
        verify(repository).requireCurrent(eq(row),eq(new Revisions("expected","target","candidate")));
        verify(repository).select(eq(row),argThat(c->c.action().equals("ASGN")&&c.actor().equals("admin")),argThat(p->p.get("contractMm").equals("1")&&p.get("projectId").equals("P-0101")),eq(Map.of("assignmentId","A-0101")));
        verify(history).appendRecommendationAssignment(argThat(c->c.item().equals("billingUnitPrice")&&"101".equals(c.after())));
    }
    @Test void editedDeveloperDoesNotSelectOriginalCard(){
        Create other=new Create(input.year(),input.projectId(),"D-OTHER",input.jobCode(),input.startDate(),input.endDate(),input.contractMm(),null,"NEW",null,input.recommendationRef());
        selection.create(other,"A-0101",()->saved);verify(repository,never()).select(any(),any(),any(),any());verify(repository,never()).lock(any(),any());verifyNoInteractions(history);
    }
    @Test void editedProjectDoesNotSelectOriginalCard(){
        Create other=new Create(input.year(),"P-OTHER",input.developerId(),input.jobCode(),input.startDate(),input.endDate(),input.contractMm(),null,"NEW",null,input.recommendationRef());selection.create(other,"A-0101",()->saved);verify(repository,never()).select(any(),any(),any(),any());
    }
    @Test void ordinaryFailureDoesNotFinalizeCard(){
        assertThatThrownBy(()->selection.create(input,"A-0101",()->{throw new IllegalStateException("save failed");})).isInstanceOf(IllegalStateException.class);verify(repository,never()).select(any(),any(),any(),any());
    }
    @Test void successfulReplayReturnsAuditedIdWithoutNewOrdinaryWrite(){
        when(repository.replay(any(),eq("ASGN"),any())).thenReturn(true);when(repository.values(any())).thenReturn(Map.of("assignmentId","A-0101"));when(assignments.saved("A-0101",2026,false)).thenReturn(saved);
        Supplier<Save> ordinary=mock(Supplier.class);var result=selection.create(input,"A-unused",ordinary);assertThat(result.changed()).isFalse();assertThat(result.assignment().id()).isEqualTo("A-0101");verifyNoInteractions(ordinary,history);verify(repository,never()).requireCurrent(any(),any());
    }
    @Test void normalWireCreateKeepsNullReferenceAndBusinessIdFormatting()throws Exception{
        var json=JsonMapper.builder().findAndAddModules().build();var b=json.createObjectNode().put("year",2026).put("projectId","P-0001").put("developerId","D-0002").put("jobCode","BE").put("startDate","2026-07-01").put("endDate","2026-07-31").put("contractMm","1.00");
        assertThat(AssignmentActionsInput.create(b).recommendationRef()).isNull();b.putObject("recommendationRef").put("rcmdId","101").put("rank",3);var value=AssignmentActionsInput.create(b);assertThat(value.projectId()).isEqualTo("P-0001");assertThat(value.recommendationRef()).isEqualTo(new RecommendationReference("101",3));
        b.withObject("recommendationRef").put("actor","other");assertThatThrownBy(()->AssignmentActionsInput.create(b)).isInstanceOf(ApiException.class);
    }
}
