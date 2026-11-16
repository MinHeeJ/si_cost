package com.sicost.common.notifications;

import java.util.List;
import org.junit.jupiter.api.Test;
import static com.sicost.common.notifications.NotificationRecipients.*;
import static org.assertj.core.api.Assertions.*;

class NotificationRecipientsTest {
    Role role(String user,String role) {return new Role(user,user,user+"@example.test",role);}
    @Test void ordinaryLeaderRequiresBothEffectiveLeaderAndSalesQualification() {
        var roles=List.of(role("pm","PM"),role("leader","SALES_LDR"),role("another","SALES_LDR"),role("admin","ADMIN"));
        var groups=dunning("P",new Part("PART",false,"leader"),roles,List.of(new Scope("pm","PROJ",null,"P")));
        assertThat(groups.get(0).recipients()).extracting(Recipient::userId).containsExactly("pm");
        assertThat(groups.get(1).recipients()).extracting(Recipient::userId).containsExactly("leader","pm");
        assertThat(groups.get(1).recipients().get(0).emailAddress()).isEqualTo("leader@example.test");
    }
    @Test void missingOrdinaryLeaderDoesNotFallBackToAdminOrExecutive() {
        var groups=dunning("P",new Part("PART",false,"missing"),List.of(role("admin","ADMIN"),role("exec","EXEC")),List.of());
        assertThat(groups.get(1).recipients()).isEmpty();
        assertThat(groups.get(1).unassignedReasons()).contains("영업파트 SALES_LDR 미지정");
    }
    @Test void headquartersUsesAllScopedSalesLeadersWithoutUnscopedLeaders() {
        var groups=dunning("P",new Part("HQ",true,null),List.of(role("one","SALES_LDR"),role("two","SALES_LDR"),role("other","SALES_LDR")),
            List.of(new Scope("one","PART","HQ",null),new Scope("two","PART","HQ",null)));
        assertThat(groups.get(1).recipients()).extracting(Recipient::userId).containsExactly("one","two");
    }
    @Test void headquartersWithoutLeadersAndMissingPartUseOnlyExplicitExecutiveFallback() {
        var roles=List.of(role("admin","ADMIN"),role("exec","EXEC"));
        assertThat(dunning("P",new Part("HQ",true,null),roles,List.of()).get(1).recipients()).extracting(Recipient::userId).containsExactly("exec");
        assertThat(dunning("P",null,roles,List.of()).get(1).recipients()).extracting(Recipient::userId).containsExactly("exec");
    }
    @Test void repeatedScopesAndMultipleRolesDeduplicatePeopleAndPreserveAllQualifications() {
        var scope=new Scope("same","PROJ",null,"P");
        var groups=dunning("P",null,List.of(role("same","PM"),role("same","EXEC"),role("same","FIN")),List.of(scope,scope));
        assertThat(groups.get(2).recipients()).hasSize(1);
        assertThat(groups.get(2).recipients().get(0).qualifications()).containsExactly("EXEC","FIN","PM");
    }
    @Test void cashAndWithdrawalAddOnlyTheirExplicitCompanyRoles() {
        var roles=List.of(role("admin","ADMIN"),role("exec","EXEC"),role("fin","FIN"),role("pm","PM"));
        assertThat(cash(roles)).extracting(Recipient::userId).containsExactly("admin","exec","fin");
        assertThat(withdrawal("P",null,roles,List.of(new Scope("pm","PROJ",null,"P")))).extracting(Recipient::userId).containsExactly("admin","exec","fin","pm");
    }
    @Test void emptyRecipientsRemainEmptyAndNeverUseTemporaryAdministratorAsFallback() {
        assertThat(dunning("P",null,List.of(role("admin","ADMIN")),List.of()).get(2).recipients()).isEmpty();
        assertThat(cash(List.of())).isEmpty();
    }
}
