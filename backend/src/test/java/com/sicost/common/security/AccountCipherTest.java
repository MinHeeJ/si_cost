package com.sicost.common.security;

import com.sicost.common.api.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountCipherTest {
    @TempDir Path directory;
    final AccountKeyMapper accounts=mock(AccountKeyMapper.class);
    AccountCipher cipher() { return new AccountCipher(accounts,()->null,directory.resolve("keys")); }
    String account() { byte[] bytes=new byte[8];new SecureRandom().nextBytes(bytes);StringBuilder number=new StringBuilder();for(byte b:bytes)number.append(Math.floorMod(b,10));return number.toString(); }
    @Test void aesGcmHasFreshIvAndStableOwnerOnlyKeyAcrossInstances() throws Exception {
        AccountCipher cipher=cipher(); String number=account();
        String first=cipher.encrypt(number),second=cipher.encrypt(number);
        assertThat(first).startsWith("v1.").isNotEqualTo(second);
        assertThat(Base64.getDecoder().decode(first.substring(3))).hasSize(12+number.length()+16);
        assertThat(cipher().decrypt(first)).isEqualTo(number);
        assertThat(cipher().masked(first)).isEqualTo("****"+number.substring(number.length()-4));
        assertThat(Files.getPosixFilePermissions(directory.resolve("keys/account.key"))).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
    @Test void simultaneousCreationReusesOneKey() throws Exception {
        String number=account();
        var a=CompletableFuture.supplyAsync(()->cipher().encrypt(number));
        var b=CompletableFuture.supplyAsync(()->cipher().encrypt(number));
        assertThat(cipher().decrypt(a.get())).isEqualTo(number);
        assertThat(cipher().decrypt(b.get())).isEqualTo(number);
    }
    @Test void invalidOverrideDoesNotFallBackToDevelopmentKey() {
        var cipher=new AccountCipher(accounts,()->"invalid",directory.resolve("keys"));
        assert503(()->cipher.encrypt(account()));
        verifyNoInteractions(accounts);
        assertThat(directory.resolve("keys")).doesNotExist();
    }
    @Test void missingKeyWithExistingCiphertextNeverRotates() {
        when(accounts.hasEncryptedAccounts()).thenReturn(true);
        assert503(()->cipher().encrypt(account()));
        assertThat(directory.resolve("keys/account.key")).doesNotExist();
    }
    @Test void missingKeyForDecryptionNeverCreatesOne() {
        String number=account();String encrypted=cipher().encrypt(number);
        try { Files.delete(directory.resolve("keys/account.key")); } catch(Exception e) { throw new IllegalStateException(e); }
        assert503(()->cipher().decrypt(encrypted));
        assert503(()->cipher().encrypt(number));
        assertThat(directory.resolve("keys/account.key")).doesNotExist();
    }
    @Test void authenticationFailureAndMalformedCiphertextDoNotLeak() {
        String encrypted=cipher().encrypt(account());
        byte[] payload=Base64.getDecoder().decode(encrypted.substring(3)); payload[payload.length-1]^=1;
        assert503(()->cipher().decrypt("v1."+Base64.getEncoder().encodeToString(payload)));
        assert503(()->cipher().decrypt("plaintext"));
    }
    @Test void keyFileFailureAndInsecurePermissionsFailClosed() throws Exception {
        String encrypted=cipher().encrypt(account());
        Files.setPosixFilePermissions(directory.resolve("keys/account.key"),PosixFilePermissions.fromString("rw-r--r--"));
        assert503(()->cipher().decrypt(encrypted));
        assert503(()->cipher().encrypt(account()));
    }
    void assert503(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.status()).isEqualTo(503);assertThat(e.code()).isEqualTo("ENCRYPTION_CONFIG_ERROR");
            assertThat(e.getMessage()).doesNotContain("v1.","account.key");
        });
    }
}
