package com.sicost.basic147;

import com.sicost.authentication.AuthenticationPasswordPolicy;
import com.sicost.common.api.ApiException;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthenticationPasswordPolicyTest {
    @Test void loginPreservesCaseAndPasswordBytes() {
        assertThat(AuthenticationPasswordPolicy.userId("  Existing.Case-ID  ")).isEqualTo("Existing.Case-ID");
        AuthenticationPasswordPolicy.input("가".repeat(24),"password");
        assertThatThrownBy(()->AuthenticationPasswordPolicy.input("가".repeat(25),"password")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->AuthenticationPasswordPolicy.input("","password")).hasMessage("비밀번호를 입력해 주세요.");
    }
    @Test void rejectsInvisibleWhitespaceMissingCategoriesAndMismatchedConfirmation() {
        for(String value:new String[]{"Good123! ","Good123!\u200b","Good123!\u00a0","OnlyLetters!","12345678!","Abcdef123","Short1!"})
            assertThatThrownBy(()->AuthenticationPasswordPolicy.newPassword(value,value)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->AuthenticationPasswordPolicy.newPassword("Valid123!","other")).isInstanceOf(ApiException.class);
        AuthenticationPasswordPolicy.newPassword("Valid123!","Valid123!");
    }
    @Test void randomTemporaryPasswordsHaveEveryRequiredClassWithoutFixedValue() {
        var random=new SecureRandom();var values=new java.util.HashSet<String>();
        for(int i=0;i<50;i++) {String value=AuthenticationPasswordPolicy.temporary(random);assertThat(value).hasSize(16);AuthenticationPasswordPolicy.newPassword(value,value);values.add(value);}
        assertThat(values).hasSize(50);
    }
    @Test void legacyAdministratorSelectionFailsClosedInsteadOfSubstitutingAUser() {
        var mapper=new com.sicost.common.context.CurrentUserMapper() {
            public String findName(String id){return id;} public java.util.List<String> findActiveRoles(String id){return java.util.List.of();}
        };
        assertThatThrownBy(mapper::findAdministrator).isInstanceOf(ApiException.class);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThatThrownBy(()->new com.sicost.common.context.CurrentUserProvider(mapper).currentUser()).isInstanceOf(ApiException.class);
    }
}
