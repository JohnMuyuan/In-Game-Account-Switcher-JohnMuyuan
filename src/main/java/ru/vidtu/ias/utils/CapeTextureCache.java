/*
 * In-Game Account Switcher is a mod for Minecraft that allows you to change your logged in account in-game, without restarting Minecraft.
 * Copyright (C) 2015-2022 The_Fireplace
 * Copyright (C) 2021-2026 VidTu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.vidtu.ias.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import org.jetbrains.annotations.Nullable;
import ru.vidtu.ias.platform.IStonecutter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CapeTextureCache {
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long REQUEST_TIMEOUT_SECONDS = 20L;
    private static final Map<String, CachedTexture> TEXTURES = new ConcurrentHashMap<>();

    private CapeTextureCache() {
        throw new AssertionError("No instances.");
    }

    @Nullable
    public static ClientAsset.Texture texture(Minecraft minecraft, @Nullable String url) {
        if (url == null || url.isBlank()) return null;
        long now = System.nanoTime();
        CachedTexture cached = TEXTURES.compute(url, (ignored, current) -> current == null || current.shouldRetry(now)
                ? download(minecraft, url, current == null ? null : current.texture)
                : current);
        return cached.texture;
    }

    private static CachedTexture download(Minecraft minecraft, String url, @Nullable ClientAsset.Texture current) {
        String key = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)).toString();
        Path path = minecraft.gameDirectory.toPath().resolve("assets").resolve("ias-capes").resolve(key);
        SkinTextureDownloader downloader = new SkinTextureDownloader(minecraft.getProxy(), minecraft.getTextureManager(), minecraft);
        CachedTexture cached = new CachedTexture(current);
        downloader.downloadAndRegisterSkin(IStonecutter.identifier("cape/" + key), path, url, false)
                .orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((texture, failure) -> {
                    if (failure == null && texture != null) {
                        cached.texture = texture;
                    } else {
                        cached.retryAfterNanos = System.nanoTime() + RETRY_DELAY_NANOS;
                    }
                });
        return cached;
    }

    private static final class CachedTexture {
        @Nullable
        private volatile ClientAsset.Texture texture;
        private volatile long retryAfterNanos = Long.MAX_VALUE;

        private CachedTexture(@Nullable ClientAsset.Texture texture) {
            this.texture = texture;
        }

        private boolean shouldRetry(long now) {
            return this.retryAfterNanos <= now;
        }
    }
}
