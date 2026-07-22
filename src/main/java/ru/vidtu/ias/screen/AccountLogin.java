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

package ru.vidtu.ias.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.account.OfflineAccount;
import ru.vidtu.ias.auth.LoginData;

public final class AccountLogin {
    private AccountLogin() {
        throw new AssertionError("No instances.");
    }

    public static void login(@NotNull Minecraft minecraft, @NotNull Screen parent, @NotNull Account account, boolean online, @Nullable Runnable onComplete) {
        if (online && account.canLogin()) {
            LoginPopupScreen login = new LoginPopupScreen(parent);
            //$ set_screen 'minecraft' 'login'
            minecraft.gui.setScreen(login);
            IAS.executor().execute(() -> account.login(login, onComplete));
            return;
        }

        LoginPopupScreen login = new LoginPopupScreen(parent);
        //$ set_screen 'minecraft' 'login'
        minecraft.gui.setScreen(login);
        String name = account.name();
        LoginData data = new LoginData(name, OfflineAccount.uuid(name), "ias:offline", false);
        login.success(data, false);
        if (onComplete != null) onComplete.run();
    }

    public static void login(@NotNull Minecraft minecraft, @NotNull Screen parent, @NotNull LoginData data) {
        LoginPopupScreen login = new LoginPopupScreen(parent);
        //$ set_screen 'minecraft' 'login'
        minecraft.gui.setScreen(login);
        login.success(data, false);
    }
}
