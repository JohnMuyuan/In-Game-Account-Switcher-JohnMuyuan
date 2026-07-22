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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package ru.vidtu.ias;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.config.GlobalPassword;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.config.IASStorage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main IAS class.
 *
 * @author VidTu
 */
public final class IAS {
    /**
     * IAS static Microsoft application ID.
     */
    @NotNull
    public static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";

    /**
     * Request timeout.
     */
    @NotNull
    public static final Duration TIMEOUT = Duration.ofSeconds(Long.getLong("ias.timeout", 15L));

    /**
     * User agent for HTTP requests.
     */
    @NotNull
    public static final String USER_AGENT = "IAS-JohnMuyuan/%s (https://github.com/JohnMuyuan/In-Game-Account-Switcher-JohnMuyuan)".formatted(IAS.class.getPackage().getImplementationVersion());

    /**
     * Logger for this class.
     */
    @NotNull
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS");

    /**
     * IAS executor.
     */
    @Nullable
    private static ScheduledExecutorService executor;

    /**
     * Current IAS game directory.
     */
    private static Path gameDirectory;

    /**
     * Current IAS config directory.
     */
    private static Path configDirectory;

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    @Contract(value = "-> fail", pure = true)
    private IAS() {
        throw new AssertionError("No instances.");
    }

    /**
     * Initializes the IAS.
     *
     * @param gamePath   Game directory
     * @param configPath Config directory
     */
    public static void init(@NotNull Path gamePath, @NotNull Path configPath) {
        // Log.
        LOGGER.info("IAS: Initializing IAS...");

        // Initialize the dirs.
        gameDirectory = gamePath;
        configDirectory = configPath;

        // Set up IAS.
        LOGGER.debug("IAS: Current user agent: {}", USER_AGENT);

        // Write the disclaimers.
        try {
            disclaimersStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to write disclaimers.", t);
        }

        // Read the config.
        try {
            loadConfig();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to load IAS config.", t);
        }

        // Read the storage.
        try {
            loadStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to load IAS storage.", t);
        }

        // Create the executor.
        executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "IAS"));

        // Log.
        LOGGER.info("IAS: IAS has been loaded.");
    }

    /**
     * Closes the IAS.
     */
    public static void close() {
        // Log.
        LOGGER.info("IAS: Closing IAS...");

        // Shutdown the executor.
        shutdown:
        try {
            // Skip if doesn't exist.
            ScheduledExecutorService executor = IAS.executor;
            if (executor == null) break shutdown;

            // Shutdown.
            LOGGER.info("IAS: Shutting down IAS executor...");
            executor.shutdown();
            if (executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                LOGGER.info("IAS: IAS executor shut down.");
                break shutdown;
            }

            // Shutdown forcefully.
            LOGGER.warn("IAS: Unable to shutdown IAS executor. Shutting down forcefully...");
            executor.shutdownNow();
            if (executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                LOGGER.info("IAS: IAS executor shut down forcefully.");
                break shutdown;
            }

            // Unable to shut down.
            LOGGER.error("IAS: Unable to shutdown IAS executor forcefully.");
        } catch (InterruptedException e) {
            // Log.
            LOGGER.error("IAS: IAS executor interrupted while shutting down. Shutting down forcefully...", e);

            // Kill, if exists.
            ScheduledExecutorService executor = IAS.executor;
            if (executor != null) {
                executor.shutdownNow();
            }

            // Preserve interruption.
            Thread.currentThread().interrupt();
        }
        executor = null;

        // Write the disclaimers, if we can.
        if (gameDirectory != null) {
            try {
                disclaimersStorage();
            } catch (Throwable ignored) {
                // NO-OP
            }
        }

        // Log.
        LOGGER.info("IAS: IAS has been unloaded.");
    }

    /**
     * Gets the async executor for IAS.
     *
     * @return IAS executor
     * @throws NullPointerException If the executor is not available
     */
    @Contract(pure = true)
    @NotNull
    public static ScheduledExecutorService executor() {
        ScheduledExecutorService executor = IAS.executor;
        Objects.requireNonNull(executor, "IAS executor is not available.");
        return executor;
    }

    /**
     * Gets the disabled state.
     *
     * @return Always {@code false}; this edited build has no remote disable control
     */
    @Contract(pure = true)
    public static boolean disabled() {
        return false;
    }

    /**
     * Delegates to {@link IASConfig#load(Path)} with {@link #configDirectory}.
     *
     * @throws RuntimeException If unable to load the config
     */
    public static void loadConfig() {
        IASConfig.load(configDirectory);
    }

    /**
     * Delegates to {@link IASConfig#save(Path)} with {@link #configDirectory}.
     *
     * @throws RuntimeException If unable to save the config
     */
    public static void saveConfig() {
        IASConfig.save(configDirectory);
    }

    /**
     * Delegates to {@link IASStorage#load(Path)} with {@link #gameDirectory}.
     *
     * @throws RuntimeException If unable to load the storage
     */
    public static void loadStorage() {
        GlobalPassword.load(gameDirectory);
        IASStorage.load(gameDirectory);
    }

    /**
     * Creates and unlocks the password shared by every stored Microsoft account.
     *
     * @param password Global account vault password
     */
    public static void createGlobalPassword(@NotNull String password) {
        GlobalPassword.create(gameDirectory, password);
    }

    public static void changeGlobalPassword(@NotNull String current, @NotNull String replacement) {
        GlobalPassword.change(gameDirectory, current, replacement);
    }

    /**
     * Delegates to {@link IASStorage#save(Path)} with {@link #gameDirectory}.
     *
     * @throws RuntimeException If unable to save the storage
     */
    public static void saveStorage() {
        IASStorage.save(gameDirectory);
    }

    /**
     * Delegates to {@link IASStorage#disclaimers(Path)} with {@link #gameDirectory}.
     *
     * @throws RuntimeException If unable to write the disclaimers
     */
    public static void disclaimersStorage() {
        IASStorage.disclaimers(gameDirectory);
    }

    /**
     * Delegates to {@link IASStorage#gameDisclaimerShown(Path)} with {@link #gameDirectory}.
     *
     * @throws RuntimeException If unable to set or write game disclaimer shown persistent state
     */
    public static void gameDisclaimerShownStorage() {
        IASStorage.gameDisclaimerShown(gameDirectory);
    }
}
