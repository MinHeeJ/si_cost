package com.sicost.common.security;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AccountCipher {
    private static final Object KEY_LOCK = new Object();
    private final SecureRandom random = new SecureRandom();
    private final Supplier<String> override;
    private final AccountKeyMapper accounts;
    private final Path directory;
    @Autowired
    public AccountCipher(AccountKeyMapper accounts) {
        this(accounts, () -> System.getenv("ACCOUNT_ENCRYPTION_KEY_BASE64"),
                Path.of(System.getProperty("java.io.tmpdir"), "sicost-account-encryption"));
    }
    AccountCipher(AccountKeyMapper accounts, Supplier<String> override, Path directory) {
        this.accounts = accounts; this.override = override; this.directory = directory;
    }
    public String encrypt(String number) {
        String value = InputValidator.text(number, 20, "accountNumber");
        if (!value.matches("(?=.*[0-9])[0-9-]{8,20}")) throw InputValidator.invalid("accountNumber");
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw unavailable(); }
    }
    public String decrypt(String stored) {
        if (stored == null) return null;
        try {
            if (!stored.startsWith("v1.") || stored.length() > 200) throw unavailable();
            byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
            if (bytes.length < 29) throw unavailable();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"), new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            String result = new String(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8);
            if (!result.matches("(?=.*[0-9])[0-9-]{8,20}")) throw unavailable();
            return result;
        } catch (Exception e) { throw unavailable(); }
    }
    public String masked(String stored) {
        String value = decrypt(stored);
        if (value == null) return null;
        String digits = value.replace("-", "");
        return "****" + digits.substring(Math.max(0, digits.length() - 4));
    }
    private byte[] key(boolean create) throws Exception {
        String explicit = override.get();
        if (explicit != null) {
            byte[] key = Base64.getDecoder().decode(explicit);
            if (key.length != 32) throw unavailable();
            return key;
        }
        synchronized (KEY_LOCK) {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            }
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)
                    || !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) throw unavailable();
            Path lockPath = directory.resolve("key.lock");
            if (Files.isSymbolicLink(lockPath)) throw unavailable();
            try (FileChannel channel = FileChannel.open(lockPath, java.util.Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                 var lock = channel.lock()) {
                Path target = directory.resolve("account.key");
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    if (!create || accounts.hasEncryptedAccounts() || channel.size() > 0) throw unavailable();
                    byte[] generated = new byte[32]; random.nextBytes(generated);
                    Path temporary = Files.createTempFile(directory, "key-", ".new", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                    try {
                        Files.write(temporary, generated);
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                        channel.write(ByteBuffer.wrap(new byte[]{1})); channel.force(true);
                    } finally { Arrays.fill(generated, (byte) 0); Files.deleteIfExists(temporary); }
                }
                if (Files.isSymbolicLink(target) || !Files.isRegularFile(target)
                        || !Files.getPosixFilePermissions(target).equals(PosixFilePermissions.fromString("rw-------"))) throw unavailable();
                byte[] key = Files.readAllBytes(target);
                if (key.length != 32) throw unavailable();
                return key;
            }
        }
    }
    private ApiException unavailable() {
        return new ApiException(503, "ENCRYPTION_CONFIG_ERROR", "계좌 암호화 설정을 확인해 주세요. 저장된 키를 복구한 뒤 다시 시도해 주세요.");
    }
}
