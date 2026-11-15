package com.sicost.automationrunsops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DailyAutomationController.class)
@ContextConfiguration(classes=DailyAutomationController.class)
@Import({DailyAutomationService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DailyAutomationDisabledContractTest {
    @Autowired MockMvc mvc;
    @MockBean DailyAutomationRepository repository;
    @MockBean DailyAutomationSessionLocks locks;
    @MockBean DailyAutomationStages stages;
    @MockBean(name="dailyAutomationExecutor") TaskExecutor executor;
    @MockBean Clock clock;
    @MockBean CurrentUserProvider users;
    @Test void disabledByDefaultReturns404EvenForTemporaryAdminAndNeverExecutes() throws Exception {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        mvc.perform(post("/api/automation-runs/DAILY_OPERATIONS/run-today")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/automation-runs/RECEIVABLE_DUNNING/run-today")).andExpect(status().isNotFound());
        verifyNoInteractions(repository,locks,stages,executor,clock,users);
    }
}
