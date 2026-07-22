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

package ru.vidtu.ias.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import org.jetbrains.annotations.NotNull;
import ru.vidtu.ias.IAS;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else
/*import net.minecraft.client.resources.PlayerSkin;*/

public final class SkinCache {
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long REQUEST_TIMEOUT_SECONDS = 15L;
    private static final Map<UUID, CachedSkin> SKINS = new ConcurrentHashMap<>(4);

    private SkinCache() {
        throw new AssertionError("No instances.");
    }

    @NotNull
    public static PlayerSkin skin(@NotNull Minecraft minecraft, @NotNull UUID uuid, @NotNull String name) {
        PlayerSkin fallback = DefaultPlayerSkin.get(uuid);
        if (uuid.version() != 4) return fallback;
        long now = System.nanoTime();
        CachedSkin cached = SKINS.compute(uuid, (ignored, current) -> current == null || current.shouldRetry(now)
                ? load(minecraft, uuid, current == null ? fallback : current.skin)
                : current);
        return cached.skin;
    }

    @NotNull
    public static PlayerSkin skin(@NotNull Minecraft minecraft, @NotNull UUID uuid) {
        return skin(minecraft, uuid, "");
    }

    public static void refresh(@NotNull Minecraft minecraft, @NotNull GameProfile profile) {
        PlayerSkin fallback = DefaultPlayerSkin.get(profile.id());
        SKINS.compute(profile.id(), (ignored, current) -> load(required(
                        minecraft.getSkinManager().get(profile), profile.id()),
                current == null ? fallback : current.skin));
    }

    public static void clear() {
        SKINS.clear();
    }

    @NotNull
    private static CachedSkin load(@NotNull Minecraft minecraft, @NotNull UUID uuid, @NotNull PlayerSkin current) {
        CompletableFuture<PlayerSkin> request = CompletableFuture.supplyAsync(
                        () -> minecraft.services().sessionService().fetchProfile(uuid, true), IAS.executor())
                .thenCompose(result -> result == null
                        ? CompletableFuture.failedFuture(new IllegalStateException("Missing profile for skin: " + uuid))
                        : required(minecraft.getSkinManager().get(result.profile()), uuid));
        return load(request, current);
    }

    @NotNull
    private static CompletableFuture<PlayerSkin> required(
            @NotNull CompletableFuture<java.util.Optional<PlayerSkin>> request, @NotNull UUID uuid) {
        return request.thenApply(loaded -> loaded.orElseThrow(
                () -> new IllegalStateException("Missing loaded skin: " + uuid)));
    }

    @NotNull
    private static CachedSkin load(@NotNull CompletableFuture<PlayerSkin> request, @NotNull PlayerSkin current) {
        CachedSkin cached = new CachedSkin(current);
        request.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((skin, failure) -> {
            if (failure == null && skin != null) {
                cached.skin = skin;
                return;
            }

            cached.retryAfterNanos = System.nanoTime() + RETRY_DELAY_NANOS;
        });
        return cached;
    }

    private static final class CachedSkin {
        private volatile PlayerSkin skin;
        private volatile long retryAfterNanos = Long.MAX_VALUE;

        private CachedSkin(PlayerSkin skin) {
            this.skin = skin;
        }

        private boolean shouldRetry(long now) {
            return this.retryAfterNanos <= now;
        }
    }
}
