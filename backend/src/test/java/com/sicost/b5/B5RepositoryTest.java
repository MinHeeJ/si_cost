package com.sicost.b5;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.assignmentsops.*;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.sicost.b5.B5Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class B5RepositoryTest {
    final B5Mapper mapper=mock(B5Mapper.class);
    final AssignmentActionsRepository assignments=mock(AssignmentActionsRepository.class);
    final AssignmentActionsMapper assignmentMapper=mock(AssignmentActionsMapper.class);
    final AssignmentActionsService actions=mock(AssignmentActionsService.class);
    final AssignmentMonthlyService months=mock(AssignmentMonthlyService.class);
    final ChangeHistoryWriter audit=mock(ChangeHistoryWriter.class);
    final MasterIdService ids=mock(MasterIdService.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final CommitInvalidation invalidation=mock(CommitInvalidation.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    final B5Repository repository=new B5Repository(mapper,assignments,actions,months,audit,ids,users,clock,invalidation,new RevisionService(JsonMapper.builder().findAndAddModules().build()));
    @BeforeEach void setup(){when(users.userId()).thenReturn("admin");when(assignments.mapper()).thenReturn(assignmentMapper);when(assignmentMapper.insert(any())).thenReturn(1);when(assignmentMapper.event(any())).thenReturn(1);when(ids.nextAssignmentId()).thenReturn("A-1234");when(mapper.markApplied(anyLong(),any(),any())).thenReturn(1);when(assignments.project(any())).thenReturn(new AssignmentActionsDtos.Project("P-test","프로젝트","ING","MO",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31)));}
    Checked checked(){Source source=new Source("","P-test","D-test","DEV",LocalDate.of(2026,6,1),LocalDate.of(2026,6,30),BigDecimal.ONE,BigDecimal.TEN,"NEW",null,null,0,"N","ACT");return new Checked(List.of(new B5Dtos.Row(2,null,source,List.of())),new Summary(1,1,0,0,0,0),List.of(),List.of(),List.of());}
    Upload upload(String status){return new Upload(8,"B5","UI-12","assignments.xlsx",123,"admin",status,1,1,0,0);}
    @Test void insertSourceBeforeInputThenMonthAndExactNewAuditMapping(){repository.apply(upload("CHECKED"),checked(),2026);var order=inOrder(assignmentMapper,months,mapper);order.verify(assignmentMapper).insert(argThat(w->w.source().id().equals("A-1234")&&w.source().extensionCount()==0&&w.source().status().equals("ACT")&&w.source().earlyWithdrawal().equals("N")));order.verify(assignmentMapper).event(argThat(e->e.type().equals("INPUT")&&e.serial()==1&&e.price().compareTo(BigDecimal.TEN)==0));order.verify(months).synchronize(any(),eq("ING"),eq("MO"));order.verify(mapper).markApplied(eq(8L),eq("admin"),any());verify(audit).append(argThat(e->e.tableName().equals("TB_PJ_ASGN")&&e.targetKey().equals("A-1234")&&e.type().equals("NEW")&&e.reason().equals("B5 업로드:8;행:2")));}
    @Test void replayReconstructsOnlyImmutableReceiptNotCurrentSource(){when(mapper.receipts(8,"B5 결과")).thenReturn(List.of(new Receipt("행:2","A-first"),new Receipt("철수:A-old","A-old")));when(mapper.issues(8)).thenReturn(List.of());var result=repository.result(upload("APPLIED"));assertThat(result.results()).containsExactly(new ResultRow(2,"A-first"));assertThat(result.withdrawnIds()).containsExactly("A-old");verifyNoInteractions(assignments,actions,months,ids);}
    @Test void checkedResultNeverReturnsBusinessMappings(){when(mapper.issues(8)).thenReturn(List.of(new Issue(1,"WARN",1,"[파일]","x","미지열")));var result=repository.result(upload("CHECKED"));assertThat(result.changed()).isFalse();assertThat(result.results()).isEmpty();assertThat(result.summary().fileWarningCount()).isEqualTo(1);verify(mapper,never()).receipts(anyLong(),any());}
    @Test void missingAppliedReceiptIsNotSilentlyReconstructedFromMutableRows(){when(mapper.issues(8)).thenReturn(List.of());when(mapper.receipts(8,"B5 결과")).thenReturn(List.of());assertThatThrownBy(()->repository.result(upload("APPLIED"))).isInstanceOf(IllegalStateException.class);verifyNoInteractions(assignments);}
    @Test void mandatoryAuditFailurePreventsMonthAndApplied(){doThrow(new IllegalStateException("audit")).when(audit).append(argThat(e->e.tableName().equals("TB_PJ_ASGN")));assertThatThrownBy(()->repository.apply(upload("CHECKED"),checked(),2026)).isInstanceOf(IllegalStateException.class);verify(months,never()).synchronize(any(),any(),any());verify(mapper,never()).markApplied(anyLong(),any(),any());}
    @Test void selectedWithdrawalFailurePreventsEveryNewInsert(){var withdrawal=new AssignmentActionsDtos.Withdrawal("A-old","revision",LocalDate.of(2026,5,31),"ETC",null);var c=checked();var selected=new Checked(c.rows(),c.summary(),c.issues(),c.substitutionPreviews(),List.of(withdrawal));when(actions.change(any(),any())).thenThrow(new IllegalStateException("early"));assertThatThrownBy(()->repository.apply(upload("CHECKED"),selected,2026)).isInstanceOf(IllegalStateException.class);verify(assignmentMapper,never()).insert(any());verify(mapper,never()).markApplied(anyLong(),any(),any());}
}
