package ru.vidtu.ias.config;

import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.crypt.PasswordCrypt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

public final class GlobalPasswordPolicyCheck {
    public static void main(String[] args) {
        require(GlobalPassword.validForCreation("Correct-Horse-7-BATTERY"));
        require(!GlobalPassword.validForCreation("Aa1!aaaaaaaaaaaa"));
        require(!GlobalPassword.validForCreation("lowercase-only-123!"));
        require(!GlobalPassword.validForCreation("UPPERCASE-ONLY-123!"));
        require(!GlobalPassword.validForCreation("No-Digits-Allowed!!"));
        require(!GlobalPassword.validForCreation("NoSpecialCharacter123"));

        byte[] plain = "microsoft-refresh-token".getBytes(StandardCharsets.UTF_8);
        PasswordCrypt crypt = new PasswordCrypt("Correct-Horse-7-BATTERY");
        byte[] encrypted = crypt.encrypt(plain);
        require("ias:password_crypt_v2".equals(crypt.type()));
        require(!Arrays.equals(plain, encrypted));
        require(Arrays.equals(plain, crypt.decrypt(encrypted)));
        requireFails(() -> new PasswordCrypt("Wrong-Password-8-FAIL").decrypt(encrypted));
        encrypted[encrypted.length - 1] ^= 1;
        requireFails(() -> crypt.decrypt(encrypted));

        PasswordCrypt oldCrypt = new PasswordCrypt("Old-Password-7-STRONG");
        PasswordCrypt newCrypt = new PasswordCrypt("New-Password-8-STRONG");
        byte[] tokens = "access-token\0refresh-token".getBytes(StandardCharsets.UTF_8);
        MicrosoftAccount account = new MicrosoftAccount(false, UUID.randomUUID(), "TestAccount",
                typed(oldCrypt, oldCrypt.encrypt(tokens)));
        MicrosoftAccount changed = account.recrypt(oldCrypt, newCrypt);
        require(Arrays.equals(tokens, decrypt(changed, newCrypt)));
        requireFails(() -> account.recrypt(newCrypt, oldCrypt));

        checkPasswordChange(newCrypt, account, tokens);
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Global password policy check failed.");
    }

    private static void requireFails(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Encrypted data was accepted when it should have failed.");
    }

    private static byte[] typed(PasswordCrypt crypt, byte[] encrypted) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(crypt.type());
            out.write(encrypted);
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] decrypt(MicrosoftAccount account, PasswordCrypt crypt) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            account.write(out);
            try (DataInputStream accountIn = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                accountIn.readBoolean();
                accountIn.readLong();
                accountIn.readLong();
                accountIn.readUTF();
                byte[] data = new byte[accountIn.readUnsignedShort()];
                accountIn.readFully(data);
                try (DataInputStream encryptedIn = new DataInputStream(new ByteArrayInputStream(data))) {
                    require(crypt.type().equals(encryptedIn.readUTF()));
                    return crypt.decrypt(encryptedIn.readAllBytes());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkPasswordChange(PasswordCrypt newCrypt, MicrosoftAccount account, byte[] tokens) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("ias-password-change-check");
            GlobalPassword.load(directory);
            GlobalPassword.create(directory, "Old-Password-7-STRONG");
            IASStorage.ACCOUNTS.clear();
            IASStorage.ACCOUNTS.add(account);
            IASStorage.save(directory);

            GlobalPassword.change(directory, "Old-Password-7-STRONG", "New-Password-8-STRONG");
            require(Arrays.equals(tokens, decrypt((MicrosoftAccount) IASStorage.ACCOUNTS.getFirst(), newCrypt)));

            GlobalPassword.load(directory);
            GlobalPassword.unlock("New-Password-8-STRONG");
            requireFails(() -> GlobalPassword.unlock("Old-Password-7-STRONG"));
            IASStorage.ACCOUNTS.clear();
            IASStorage.load(directory);
            require(Arrays.equals(tokens, decrypt((MicrosoftAccount) IASStorage.ACCOUNTS.getFirst(), newCrypt)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IASStorage.ACCOUNTS.clear();
            if (directory != null) {
                try (var files = Files.walk(directory)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
