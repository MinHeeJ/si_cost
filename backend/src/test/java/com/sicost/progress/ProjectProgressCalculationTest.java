package com.sicost.progress;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectLifecycle;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.progress.ProjectProgressCalculation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressCalculationTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    final ProjectProgressRepository repository=mock(ProjectProgressRepository.class);
    final ChangeHistoryWriter history=mock(ChangeHistoryWriter.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final ProjectProgressLifecycle lifecycle=new ProjectProgressLifecycle(repository,history,users,clock);
    final LocalDate start=LocalDate.of(2026,1,1);
    ProjectRow project;
    List<Template> templates;
    @BeforeEach void setup(){
        project=new ProjectRow("P-1","프로젝트","TK","ING",start,start.plusDays(199),new BigDecimal("1000"),start.atStartOfDay(),null,null,false);
        templates=new ArrayList<>();String[] weights={"5","15","20","35","15","5","5"};var codes=codes("TK");for(int i=0;i<codes.size();i++)templates.add(new Template(codes.get(i),codes.get(i),weights[i],"TK,IN","Y","N",i));
        when(users.userId()).thenReturn("admin");when(repository.required(anyString(),any())).thenReturn(project);when(repository.templates()).thenReturn(templates);when(repository.steps(anyString())).thenReturn(List.of());
    }
    @Test void eachStageGetsAtLeastOneDayAtMinimumDuration(){
        var p=new ProjectRow(project.projectId(),project.projectName(),"TK","ING",start,start.plusDays(6),project.budget(),project.registeredAt(),null,null,false);
        var rows=initialize(p,templates);assertThat(rows).hasSize(7);assertThat(rows).allMatch(r->r.startDate().equals(r.endDate()));assertThat(valid(p,rows)).isTrue();
    }
    @Test void halfwayRoundsOnlyFinalSumAndUsesElapsedCalendarDays(){var rows=initialize(project,templates);assertThat(plan(project,rows,start.minusDays(1))).isEqualTo("0.0");assertThat(plan(project,rows,start)).isEqualTo("0.0");assertThat(plan(project,rows,project.endDate())).isEqualTo("100.0");assertThat(plan(project,rows,project.endDate().plusDays(1))).isEqualTo("100.0");}
    @Test void oneDayFallbackBecomesFullOnThatDay(){var p=new ProjectRow("P-1","하루","IN","ING",start,start,null,start.atStartOfDay(),null,null,false);assertThat(plan(p,List.of(),start)).isEqualTo("100.0");assertThat(plan(p,List.of(),start.minusDays(1))).isEqualTo("0.0");}
    @Test void incompleteOrDuplicateSetDoesNotUsePartialWeights(){var rows=new ArrayList<>(initialize(project,templates));rows.remove(0);assertThat(valid(project,rows)).isFalse();assertThat(plan(project,rows,start.plusDays(99))).isEqualTo("49.7");}
    @Test void currentTemplateNameAndOrderNotHardcodedAndInactiveLegacyRowsStillCalculate(){templates.set(0,new Template("TK_KICK","현재 착수 명칭","5","TK,IN","Y","N",30));var rows=initialize(project,templates);assertThat(rows.get(6).stepName()).isEqualTo("현재 착수 명칭");assertThat(valid(project,rows)).isTrue();}
    @Test void registrationCreatesOnlyAbsentEligibleSetAndAuditsEveryStoredField(){lifecycle.registered(new ProjectLifecycle.Registered("P-1"));verify(repository,times(7)).insertStep(argThat(w->w.actor().equals("admin")));verify(history,times(28)).append(any());}
    @Test void registrationDoesNotFillExistingPartialSet(){when(repository.steps("P-1")).thenReturn(initialize(project,templates).subList(0,1));lifecycle.registered(new ProjectLifecycle.Registered("P-1"));verify(repository,never()).insertStep(any());verifyNoInteractions(history);}
    @Test void shortRegistrationKeepsProjectAndNoFakeZeroDaySteps(){var shortProject=new ProjectRow("P-1","짧은 계약","TK","ING",LocalDate.of(2026,7,1),LocalDate.of(2026,7,3),project.budget(),project.registeredAt(),null,null,false);when(repository.required(anyString(),any())).thenReturn(shortProject);lifecycle.registered(new ProjectLifecycle.Registered("P-1"));verify(repository,never()).insertStep(any());verifyNoInteractions(history);}
    @Test void periodExtensionChangesOnlyIncompleteEdgesAndPreservesInternalDates(){var rows=initialize(project,templates);when(repository.steps("P-1")).thenReturn(rows);lifecycle.periodChanged(new ProjectLifecycle.PeriodChanged("P-1",start.minusDays(1),project.endDate().plusDays(1)));verify(repository,times(2)).updateDates(any());verify(repository).updateDates(argThat(w->w.row().stepSn()==1&&w.row().startDate().equals(start.minusDays(1))&&w.row().endDate().equals(rows.get(0).endDate())));verify(repository).updateDates(argThat(w->w.row().stepSn()==7&&w.row().startDate().equals(rows.get(6).startDate())&&w.row().endDate().equals(project.endDate().plusDays(1))));verify(repository,never()).insertStep(any());}
    @Test void completedEdgePeriodChangeIsConflictWithoutAnyWrites(){var rows=new ArrayList<>(initialize(project,templates));var first=rows.get(0);rows.set(0,withDates(first,first.startDate(),first.endDate(),start.plusDays(9)));when(repository.steps("P-1")).thenReturn(rows);assertThatThrownBy(()->lifecycle.periodChanged(new ProjectLifecycle.PeriodChanged("P-1",start.plusDays(1),project.endDate()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STEP_PERIOD_CONFLICT"));verify(repository,never()).updateDates(any());verifyNoInteractions(history);}
    @Test void periodShrinkInvertingInternalStepIsConflictWithoutAnyWrites(){when(repository.steps("P-1")).thenReturn(initialize(project,templates));assertThatThrownBy(()->lifecycle.periodChanged(new ProjectLifecycle.PeriodChanged("P-1",start.plusDays(40),project.endDate()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));verify(repository,never()).updateDates(any());verifyNoInteractions(history);}
    @Test void periodCorrectionNeverAutoInitializesAbsentStages(){lifecycle.periodChanged(new ProjectLifecycle.PeriodChanged("P-1",start,project.endDate().plusDays(100)));verify(repository,never()).insertStep(any());verify(repository,never()).templates();verifyNoInteractions(history);}
}
