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

package ru.vidtu.ias.screen;

import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.config.GlobalPassword;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creates or unlocks the password shared by every Microsoft account.
 *
 * @author VidTu
 */
final class GlobalPasswordPopupScreen extends Screen {
    private final Screen parent;
    private final Consumer<Account> handler;
    private final boolean create;

    private PopupBox password;
    private PopupBox confirmation;
    private PopupButton action;
    private MultiLineLabel tip;
    private Component error;
    private boolean busy;
    private int titleY;
    private int tipY;

    GlobalPasswordPopupScreen(Screen parent, Consumer<Account> handler) {
        super(Component.translatable(GlobalPassword.configured()
                ? "ias.globalPassword.unlock"
                : "ias.globalPassword.create"));
        this.parent = parent;
        this.handler = handler;
        this.create = !GlobalPassword.configured();
    }

    @Override
    protected void init() {
        assert this.minecraft != null;

        if (this.parent != null && (this.parent.width != this.width || this.parent.height != this.height)) {
            //? if >=1.21.11 {
            this.parent.init(this.width, this.height);
            //?} else
            /*this.parent.init(this.minecraft, this.width, this.height);*/
        }

        Component tipText = this.create
                ? Component.translatable("ias.globalPassword.tip",
                        Component.translatable("ias.globalPassword.anyone").withStyle(ChatFormatting.RED))
                : Component.translatable("ias.globalPassword.unlock.tip");
        this.tip = MultiLineLabel.create(this.font, tipText.copy().withStyle(ChatFormatting.YELLOW),
                Math.min(420, this.width - 32));
        int tipHeight = this.tip.getLineCount() * 9;
        int blockHeight = (this.create ? 118 : 84) + tipHeight;
        this.titleY = Math.max(5, (this.height - blockHeight) / 2);
        this.tipY = this.titleY + 16;
        int passwordY = this.tipY + tipHeight + 18;
        this.password = this.passwordBox(passwordY, this.password,
                Component.translatable("ias.globalPassword.password"), this.create ? null : this::submit);
        this.addRenderableWidget(this.password);

        if (this.create) {
            this.confirmation = this.passwordBox(passwordY + 34, this.confirmation,
                    Component.translatable("ias.globalPassword.confirm"), this::submit);
            this.addRenderableWidget(this.confirmation);
        }

        int actionY = passwordY + (this.create ? 64 : 30);
        this.action = new PopupButton(this.width / 2 - 75, actionY, 74, 20,
                Component.translatable(this.create ? "ias.globalPassword.create.button" : "ias.globalPassword.unlock.button"),
                button -> this.submit(), Supplier::get);
        this.addRenderableWidget(this.action);

        this.addRenderableWidget(new PopupButton(this.width / 2 + 1, actionY, 74, 20,
                CommonComponents.GUI_CANCEL, button -> this.onClose(), Supplier::get));
        this.updateAction();
    }

    private PopupBox passwordBox(int y, PopupBox previous, Component title, Runnable enterAction) {
        PopupBox box = new PopupBox(this.font, this.width / 2 - 100, y, 200, 20, previous, title, enterAction, true);
        box.setHint(Component.translatable("ias.password.hint").withStyle(ChatFormatting.DARK_GRAY));
        //? if >=1.21.10 {
        box.addFormatter((value, index) -> IASConfig.passwordEchoing
                ? FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY)
                : FormattedCharSequence.EMPTY);
        //?} else
        /*box.setFormatter((value, index) -> IASConfig.passwordEchoing
                ? FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY)
                : FormattedCharSequence.EMPTY);*/
        box.setMaxLength(128);
        box.setResponder(value -> {
            this.error = null;
            this.updateAction();
        });
        return box;
    }

    private void updateAction() {
        if (this.action == null || this.password == null) return;
        boolean valid = !this.password.getValue().isBlank();
        if (this.create) {
            valid &= this.confirmation != null && !this.confirmation.getValue().isBlank();
        }
        this.action.active = valid && !this.busy;
        this.password.active = !this.busy;
        if (this.confirmation != null) this.confirmation.active = !this.busy;
    }

    private void submit() {
        assert this.minecraft != null;
        if (this.busy || this.password == null) return;

        String value = this.password.getValue();
        if (value.isBlank()) return;
        if (this.create && (this.confirmation == null || !value.equals(this.confirmation.getValue()))) {
            this.error = Component.translatable("ias.globalPassword.mismatch").withStyle(ChatFormatting.RED);
            this.updateAction();
            return;
        }
        if (this.create && !GlobalPassword.validForCreation(value)) {
            this.error = Component.translatable("ias.error.globalPasswordRequirements").withStyle(ChatFormatting.RED);
            this.updateAction();
            return;
        }

        this.busy = true;
        this.error = null;
        this.updateAction();
        CompletableFuture.runAsync(() -> {
            if (this.create) {
                IAS.createGlobalPassword(value);
            } else {
                GlobalPassword.unlock(value);
            }
        }, IAS.executor()).whenCompleteAsync((ignored, throwable) -> {
            if (this != this.currentScreen()) return;
            if (throwable == null) {
                //$set_screen 'this.minecraft' 'new MicrosoftPopupScreen(this.parent, this.handler)'
                this.minecraft.gui.setScreen(new MicrosoftPopupScreen(this.parent, this.handler));
                return;
            }

            FriendlyException friendly = FriendlyException.friendlyInChain(throwable);
            this.busy = false;
            this.password.setValue("");
            if (this.confirmation != null) this.confirmation.setValue("");
            this.error = Component.translatable(friendly != null ? friendly.key() : "ias.error")
                    .withStyle(ChatFormatting.RED);
            this.updateAction();
        }, this.minecraft);
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(this.font, this.title, this.width / 2, this.titleY, 0xFF_FF_FF_FF);
        if (this.error != null) {
            graphics.centeredText(this.font, this.error, this.width / 2, this.tipY, 0xFF_FF_55_55);
        } else if (this.tip != null) {
            IStonecutter.renderMultilineLabelCentered(this.tip, graphics, this.width / 2, this.tipY);
        }

        if (this.password != null) {
            graphics.centeredText(this.font, this.password.getMessage(), this.width / 2, this.password.getY() - 11, 0xFF_FF_FF_FF);
        }
        if (this.confirmation != null) {
            graphics.centeredText(this.font, this.confirmation.getMessage(), this.width / 2, this.confirmation.getY() - 11, 0xFF_FF_FF_FF);
        }

    }

    @Override
    //? if >=26.1 {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        assert this.minecraft != null;
        if (this.parent != null) {
            this.parent.extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, delta);
            graphics.nextStratum();
            graphics.fill(0, 0, this.width, this.height, 0x80_00_00_00);
        } else {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        //$set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    private Screen currentScreen() {
        //? if >=26.2 {
        return this.minecraft.gui.screen();
        //?} else {
        /*return this.minecraft.screen;
        *///?}
    }
}
