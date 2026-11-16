package com.sicost.common.notifications;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class PredictionNotificationAdapterTest {
    @Test void onlyExplicitPredictionCompletionCallbackPublishesToQuestionOwner() {
        var publisher=mock(NotificationPublisher.class);var adapter=new PredictionNotificationAdapter(publisher);verifyNoInteractions(publisher);
        adapter.predictionCompleted(123,"questioner");
        verify(publisher).publish(argThat(e->e.key().equals("AI:123:DONE")&&e.type().equals("AI_DONE")&&e.link().screenId().equals("UI-26")),
            eq(List.of(new NotificationRecipients.Recipient("questioner","questioner",null,List.of()))));
        verifyNoMoreInteractions(publisher);
    }
}
