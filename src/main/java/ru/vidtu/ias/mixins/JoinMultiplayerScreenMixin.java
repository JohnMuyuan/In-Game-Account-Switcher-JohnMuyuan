/*
 * In-Game Account Switcher is a third-party mod for Minecraft Java Edition that
 * allows you to change your logged in account in-game, without restarting it.
 *
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

//? if >=1.21.10 {
package ru.vidtu.ias.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vidtu.ias.IASMinecraft;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.screen.AccountScreen;
import ru.vidtu.ias.screen.CapeSkinWidget;
import ru.vidtu.ias.utils.Expression;
import ru.vidtu.ias.utils.SkinCache;

import java.time.Duration;

/**
 * A dirty hacky mixin that adds the button on the server list properly, despite Mojang not clearing the widgets.
 *
 * @author VidTu
 * @apiNote Internal use only
 */
@ApiStatus.Internal
@Mixin(JoinMultiplayerScreen.class)
@NullMarked
public final class JoinMultiplayerScreenMixin extends Screen {
    /**
     * Previously added button, {@code null} if none.
     */
    @Nullable
    @Unique
    private Button ias_button;

    @Nullable
    @Unique
    private CapeSkinWidget ias_preview;

    @Unique
    private CapeSkinWidget.PreviewState ias_previewState;

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     * @deprecated Always throws
     */
    // @ApiStatus.ScheduledForRemoval // Can't annotate this without logging in the console.
    @Deprecated
    @Contract(value = "-> fail", pure = true)
    private JoinMultiplayerScreenMixin() {
        //noinspection DataFlowIssue // <- Never called. (Mixin)
        super(null);
        throw new AssertionError("IAS: No instances.");
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void ias_repositionElements_return(CallbackInfo ci) {
        this.ias_repositionPreview();

        // Skip adding, if disabled.
        if (!IASConfig.serversButton) return;

        // Calculate the position.
        Integer x = Expression.parsePosition(IASConfig.serversButtonX, this.width, this.height);
        Integer y = Expression.parsePosition(IASConfig.serversButtonY, this.width, this.height);

        // Couldn't parse position.
        if (x == null || y == null) {
            // Use default position.
            x = width / 2 + 158;
            y = height - 30;

            // Move out of any overlapping elements.
            for (int i = 0; i < 64; i++) {
                boolean overlapping = false;
                for (GuiEventListener child : this.children()) {
                    // Skip if doesn't have pos.
                    if (!(child instanceof LayoutElement le) || child instanceof AbstractSelectionList<?> || child == this.ias_button) continue;

                    // Skip if not overlapping.
                    int x1 = le.getX() - 4;
                    int y1 = le.getY() - 4;
                    int x2 = x1 + le.getWidth() + 8;
                    int y2 = y1 + le.getHeight() + 8;
                    if (x < x1 || y < y1 || (x + 20) > x2 || (y + 20) > y2) continue;

                    // Otherwise move.
                    x = Math.max(x, x2);
                    overlapping = true;
                }
                if (overlapping) continue;
                break;
            }
        }

        // Add or move the button.
        if (this.ias_button != null) {
            this.ias_button.setX(x);
            this.ias_button.setY(y);
        } else {
            Button button = this.ias_button = new ImageButton(x, y, 20, 20, IASMinecraft.BUTTON, btn -> {
                //$ set_screen minecraft 'new AccountScreen(this)'
                minecraft.gui.setScreen(new AccountScreen(this));
            }, Component.literal("In-Game Account Switcher"));
            button.setTooltip(Tooltip.create(button.getMessage()));
            button.setTooltipDelay(Duration.ofMillis(250L));
            this.addRenderableWidget(button);
        }
    }

    @Unique
    private void ias_repositionPreview() {
        Minecraft minecraft = this.minecraft;
        boolean visible = IASConfig.serversText && minecraft != null && this.width >= 300 && this.height >= 180;
        if (!visible) {
            if (this.ias_preview != null) {
                this.ias_preview.visible = false;
                this.ias_preview.active = false;
            }
            IASMinecraft.setMultiplayerPreviewLayout(0, 0, false);
            return;
        }

        boolean compact = this.width < 600 || this.height < 240;
        int previewWidth = compact ? 40 : 85;
        int previewHeight = compact ? 70 : 120;
        int x = compact ? 8 : 58;
        int y = this.height / 2 - previewHeight / 2;

        CapeSkinWidget preview = this.ias_preview;
        if (preview == null || preview.getWidth() != previewWidth || preview.getHeight() != previewHeight) {
            if (preview != null) this.removeWidget(preview);
            CapeSkinWidget.PreviewState state = this.ias_previewState;
            if (state == null) {
                state = new CapeSkinWidget.PreviewState(CapeSkinWidget.PreviewPose.FRONT);
                this.ias_previewState = state;
            }
            preview = new CapeSkinWidget(previewWidth, previewHeight, () -> {
                User user = minecraft.getUser();
                return user == null ? DefaultPlayerSkin.get(IStonecutter.NIL_UUID)
                        : SkinCache.skin(minecraft, user.getProfileId(), user.getName());
            }, state);
            this.ias_preview = preview;
            this.addRenderableWidget(preview);
        }

        preview.setPosition(x, y);
        preview.visible = true;
        preview.active = true;
        User user = minecraft.getUser();
        if (user != null) SkinCache.skin(minecraft, user.getProfileId(), user.getName());
        IASMinecraft.setMultiplayerPreviewLayout(x + previewWidth / 2, y - 12, true);
    }
}
//?}
