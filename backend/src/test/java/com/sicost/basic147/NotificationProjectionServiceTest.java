package com.sicost.basic147;

import com.sicost.permission.*;
import com.sicost.notificationsops.NotificationDtos.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationProjectionServiceTest {
    PermissionService policies;PermissionMapper scopes;NotificationTargetMapper targets;NotificationProjectionService projection;
    @BeforeEach void fixture() {
        policies=mock(PermissionService.class);scopes=mock(PermissionMapper.class);targets=mock(NotificationTargetMapper.class);
        when(policies.userId()).thenReturn("synthetic");
        projection=new NotificationProjectionService(policies,scopes,targets,Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"),ZoneId.of("Asia/Seoul")));
    }
    void grants(Map<String,Set<String>> menus,boolean all,Set<String> parts,Set<String> projects) {
        when(policies.capabilities()).thenReturn(new PermissionService.Capabilities(menus,new PermissionService.Scope(all,parts,projects),false,Map.of(),Map.of(),Set.of()));
    }
    Item item(String type,String screen,String key) {return new Item("1",type,"saved private name 991234","SYS",false,null,null,new Link(screen,key));}
    @Test void lostInquiryRedactsWithoutEvenLookingUpTheOldTarget() {
        grants(Map.of(),false,Set.of(),Set.of());var result=projection.project(item("DUN","UI-13","bill"));
        assertThat(result.content()).isEqualTo(NotificationProjectionService.UNAVAILABLE);assertThat(result.link()).isNull();verifyNoInteractions(targets,scopes);
    }
    @Test void partAuthorizationUsesTheBillMonthRatherThanTheCurrentMonth() {
        grants(Map.of("UI-13",Set.of("INQ")),false,Set.of("own-part"),Set.of());
        when(targets.bill("bill")).thenReturn(new NotificationTargetMapper.Target("project",null,"202608",null,null));
        when(scopes.projectPart("project","202608")).thenReturn("other-part");
        assertThat(projection.project(item("DUN","UI-13","bill")).link()).isNull();verify(scopes).projectPart("project","202608");
        when(scopes.projectPart("project","202608")).thenReturn("own-part");
        assertThat(projection.project(item("DUN","UI-13","bill")).content()).isEqualTo("saved private name 991234");
    }
    @Test void projectScopeIsReevaluatedAndCompletionNeverReplaysSavedNumbers() {
        grants(Map.of("UI-13",Set.of("INQ")),false,Set.of(),Set.of("own"));
        when(targets.bill("bill")).thenReturn(new NotificationTargetMapper.Target("own",null,"202611",null,null));
        assertThat(projection.project(item("DUN","UI-13","bill")).link()).isNotNull();
        grants(Map.of(),false,Set.of(),Set.of());
        assertThat(projection.project(item("DUN","UI-13","bill")).content()).isEqualTo(NotificationProjectionService.UNAVAILABLE);
        var original=item("AI_DONE","UI-26","7");var completion=projection.project(original);
        assertThat(completion.content()).isEqualTo("AI 예측 완료");assertThat(completion.link()).isNull();assertThat(original.content()).isEqualTo("saved private name 991234");
        assertThat(projection.project(item("UPLD_DONE","UI-15","7")).content()).isEqualTo("업로드 반영 완료");
    }
    @Test void unassignedOpportunityAndMalformedLinksAreDeniedWithoutNullScopeFallback() {
        grants(Map.of("UI-29",Set.of("INQ")),false,Set.of(),Set.of("own"));
        when(targets.opportunity("opportunity")).thenReturn(new NotificationTargetMapper.Target(null,null,null,null,null));
        assertThat(projection.project(item("QUOTE_REJ","UI-29","opportunity")).link()).isNull();
        assertThat(projection.project(item("QUOTE_REJ","UI-29","../other")).content()).isEqualTo(NotificationProjectionService.UNAVAILABLE);
    }
}
