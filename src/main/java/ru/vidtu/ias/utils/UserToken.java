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

import net.minecraft.client.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class UserToken {
    private UserToken() {
        throw new AssertionError("No instances.");
    }

    @Nullable
    public static String access(@NotNull User user) {
        for (String name : new String[]{"getAccessToken", "accessToken"}) {
            try {
                Method method = user.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(user);
                if (value instanceof String token && !token.isBlank()) return token;
            } catch (ReflectiveOperationException ignored) {
                // Try the next name.
            }
        }

        try {
            Field field = user.getClass().getDeclaredField("accessToken");
            field.setAccessible(true);
            Object value = field.get(user);
            return value instanceof String token && !token.isBlank() ? token : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
