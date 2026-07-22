/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.vidtu.ias.config;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.MicrosoftAccount;
import ru.vidtu.ias.crypt.PasswordCrypt;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Process-local global password used to encrypt every Microsoft account.
 *
 * @author VidTu
 */
public final class GlobalPassword {
    private static final byte @NotNull [] MARKER = "IAS_GLOBAL_PASSWORD_V2".getBytes(StandardCharsets.US_ASCII);

    @NotNull
    private static final String VERIFIER_PATH = "_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden/global_password_v2";
    private static final String CHANGE_MARKER_PATH = "_IAS_ACCOUNTS_DO_NOT_SEND_TO_ANYONE/.hidden/password_change_pending";

    private static byte @Nullable [] verifier;

    @Nullable
    private static volatile String password;

    @Contract(value = "-> fail", pure = true)
    private GlobalPassword() {
        throw new AssertionError("No instances.");
    }

    public static synchronized void load(@NotNull Path gameDirectory) {
        password = null;
        recoverPasswordChange(gameDirectory);
        Path file = gameDirectory.resolve(VERIFIER_PATH);
        try {
            verifier = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(file) : null;
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load the IAS global password verifier.", t);
        }
    }

    public static synchronized void create(@NotNull Path gameDirectory, @NotNull String value) {
        if (!validForCreation(value)) {
            throw new FriendlyException("Global password does not meet the strength requirements.",
                    "ias.error.globalPasswordRequirements");
        }
        if (verifier != null) throw new IllegalStateException("Global password is already configured.");

        byte[] encrypted = new PasswordCrypt(value).encrypt(MARKER);
        writeVerifier(gameDirectory, encrypted);
        verifier = encrypted;
        password = value;
    }

    public static synchronized void change(@NotNull Path gameDirectory, @NotNull String current,
                                           @NotNull String replacement) {
        unlock(current);
        if (!validForCreation(replacement)) {
            throw new FriendlyException("Global password does not meet the strength requirements.",
                    "ias.error.globalPasswordRequirements");
        }
        if (current.equals(replacement)) {
            throw new FriendlyException("New global password matches the current password.",
                    "ias.error.globalPasswordSame");
        }

        PasswordCrypt oldCrypt = new PasswordCrypt(current);
        PasswordCrypt newCrypt = new PasswordCrypt(replacement);
        List<Account> original = List.copyOf(IASStorage.ACCOUNTS);
        List<Account> changed = new ArrayList<>(original.size());
        for (Account account : original) {
            changed.add(account instanceof MicrosoftAccount microsoft
                    ? microsoft.recrypt(oldCrypt, newCrypt)
                    : account);
        }

        Path verifierFile = gameDirectory.resolve(VERIFIER_PATH);
        Path accountsFile = gameDirectory.resolve(IASStorage.STORAGE_PATH);
        Path marker = gameDirectory.resolve(CHANGE_MARKER_PATH);
        Path verifierBackup = verifierFile.resolveSibling(verifierFile.getFileName() + ".password_change_backup");
        Path accountsBackup = accountsFile.resolveSibling(accountsFile.getFileName() + ".password_change_backup");
        try {
            Files.copy(verifierFile, verifierBackup, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            Files.copy(accountsFile, accountsBackup, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            Files.writeString(marker, "pending", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, LinkOption.NOFOLLOW_LINKS);

            IASStorage.ACCOUNTS.clear();
            IASStorage.ACCOUNTS.addAll(changed);
            IASStorage.save(gameDirectory);
            byte[] encrypted = newCrypt.encrypt(MARKER);
            writeVerifier(gameDirectory, encrypted);

            Files.delete(marker);
            verifier = encrypted;
            password = replacement;
            try {
                Files.deleteIfExists(verifierBackup);
                Files.deleteIfExists(accountsBackup);
            } catch (Throwable ignored) {
                // Stale backups are removed on the next startup.
            }
        } catch (Throwable t) {
            IASStorage.ACCOUNTS.clear();
            IASStorage.ACCOUNTS.addAll(original);
            recoverPasswordChange(gameDirectory);
            verifier = readVerifier(gameDirectory);
            password = current;
            throw new RuntimeException("Unable to change the IAS global password.", t);
        }
    }

    private static void writeVerifier(@NotNull Path gameDirectory, byte @NotNull [] encrypted) {
        Path file = gameDirectory.resolve(VERIFIER_PATH);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE, StandardOpenOption.SYNC, StandardOpenOption.DSYNC, LinkOption.NOFOLLOW_LINKS);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.setAttribute(file.getParent(), "dos:hidden", true, LinkOption.NOFOLLOW_LINKS);
                Files.setAttribute(file.getParent(), "dos:system", true, LinkOption.NOFOLLOW_LINKS);
            } catch (Throwable ignored) {
                // Hidden attributes are best-effort only.
            }
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(temporary);
            } catch (Throwable ignored) {
                // NO-OP
            }
            throw new RuntimeException("Unable to save the IAS global password verifier.", t);
        }

    }

    private static byte @Nullable [] readVerifier(@NotNull Path gameDirectory) {
        Path file = gameDirectory.resolve(VERIFIER_PATH);
        try {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(file) : null;
        } catch (Throwable t) {
            throw new RuntimeException("Unable to load the IAS global password verifier.", t);
        }
    }

    private static void recoverPasswordChange(@NotNull Path gameDirectory) {
        Path verifierFile = gameDirectory.resolve(VERIFIER_PATH);
        Path accountsFile = gameDirectory.resolve(IASStorage.STORAGE_PATH);
        Path marker = gameDirectory.resolve(CHANGE_MARKER_PATH);
        Path verifierBackup = verifierFile.resolveSibling(verifierFile.getFileName() + ".password_change_backup");
        Path accountsBackup = accountsFile.resolveSibling(accountsFile.getFileName() + ".password_change_backup");
        try {
            if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isRegularFile(verifierBackup, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(verifierBackup, verifierFile, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.isRegularFile(accountsBackup, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(accountsBackup, accountsFile, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.deleteIfExists(marker);
            } else {
                Files.deleteIfExists(verifierBackup);
                Files.deleteIfExists(accountsBackup);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to recover an interrupted IAS global password change.", t);
        }
    }

    public static synchronized void unlock(@NotNull String value) {
        byte[] current = verifier;
        if (current == null) throw new IllegalStateException("Global password is not configured.");
        if (value.isBlank()) throw new FriendlyException("Global password is blank.", "ias.error.globalPassword");

        try {
            byte[] decrypted = new PasswordCrypt(value).decrypt(current);
            if (!MessageDigest.isEqual(MARKER, decrypted)) {
                throw new FriendlyException("Global password marker does not match.", "ias.error.globalPassword");
            }
            password = value;
        } catch (FriendlyException e) {
            throw e;
        } catch (Throwable t) {
            throw new FriendlyException("Unable to unlock the IAS global password.", t, "ias.error.globalPassword");
        }
    }

    @Contract(pure = true)
    public static boolean validForCreation(@NotNull String value) {
        return value.length() > 16
                && value.chars().anyMatch(Character::isUpperCase)
                && value.chars().anyMatch(Character::isLowerCase)
                && value.chars().anyMatch(Character::isDigit)
                && value.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }

    @Contract(pure = true)
    public static synchronized boolean configured() {
        return verifier != null;
    }

    @Contract(pure = true)
    public static boolean unlocked() {
        return password != null;
    }

    @Contract(pure = true)
    @Nullable
    public static String password() {
        return password;
    }

    @Contract(pure = true)
    @NotNull
    public static PasswordCrypt crypt() {
        String current = password;
        if (current == null) throw new IllegalStateException("Global password is locked.");
        return new PasswordCrypt(current);
    }
}
