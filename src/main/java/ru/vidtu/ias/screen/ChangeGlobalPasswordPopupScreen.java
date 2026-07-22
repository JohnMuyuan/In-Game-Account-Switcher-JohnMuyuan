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

package ru.vidtu.ias.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.config.GlobalPassword;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ChangeGlobalPasswordPopupScreen extends Screen {
    private final Screen parent;
    private PopupBox current;
    private PopupBox replacement;
    private PopupBox confirmation;
    private PopupButton action;
    private PopupButton close;
    private MultiLineLabel tip;
    private Component error;
    private boolean busy;
    private boolean success;

    ChangeGlobalPasswordPopupScreen(Screen parent) {
        super(Component.translatable("ias.globalPassword.change"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        assert this.minecraft != null;
        if (this.parent != null && (this.parent.width != this.width || this.parent.height != this.height)) {
            this.parent.init(this.width, this.height);
        }

        int firstY = Math.max(58, this.height / 2 - 32);
        this.current = this.passwordBox(firstY, this.current,
                Component.translatable("ias.globalPassword.current"), null);
        this.replacement = this.passwordBox(firstY + 30, this.replacement,
                Component.translatable("ias.globalPassword.new"), null);
        this.confirmation = this.passwordBox(firstY + 60, this.confirmation,
                Component.translatable("ias.globalPassword.confirm"), this::submit);
        this.addRenderableWidget(this.current);
        this.addRenderableWidget(this.replacement);
        this.addRenderableWidget(this.confirmation);

        int actionY = firstY + 90;
        this.action = new PopupButton(this.width / 2 - 75, actionY, 74, 20,
                Component.translatable("ias.globalPassword.change.button"), button -> this.submit(), Supplier::get);
        this.addRenderableWidget(this.action);
        this.close = new PopupButton(this.width / 2 + 1, actionY, 74, 20,
                this.success ? CommonComponents.GUI_DONE : CommonComponents.GUI_CANCEL,
                button -> this.onClose(), Supplier::get);
        this.addRenderableWidget(this.close);

        Component forgot = Component.translatable("ias.globalPassword.change.forgot").withStyle(ChatFormatting.RED);
        this.tip = MultiLineLabel.create(this.font, Component.translatable("ias.globalPassword.change.tip", forgot)
                .withStyle(ChatFormatting.YELLOW), Math.min(420, this.width - 32));
        this.updateAction();
    }

    private PopupBox passwordBox(int y, PopupBox previous, Component title, Runnable enterAction) {
        PopupBox box = new PopupBox(this.font, this.width / 2 - 100, y, 200, 20,
                previous, title, enterAction, true);
        box.setHint(Component.translatable("ias.password.hint").withStyle(ChatFormatting.DARK_GRAY));
        box.addFormatter((value, index) -> IASConfig.passwordEchoing
                ? FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY)
                : FormattedCharSequence.EMPTY);
        box.setMaxLength(128);
        box.setResponder(value -> {
            this.error = null;
            this.updateAction();
        });
        return box;
    }

    private void updateAction() {
        if (this.action == null || this.current == null || this.replacement == null || this.confirmation == null) return;
        String oldValue = this.current.getValue();
        String newValue = this.replacement.getValue();
        this.action.active = !this.busy && !this.success
                && !oldValue.isBlank()
                && !newValue.isBlank()
                && !this.confirmation.getValue().isBlank();
        this.current.active = this.replacement.active = this.confirmation.active = !this.busy && !this.success;
    }

    private void submit() {
        assert this.minecraft != null;
        if (this.busy || this.current == null || this.replacement == null || this.confirmation == null) return;

        String oldValue = this.current.getValue();
        String newValue = this.replacement.getValue();
        if (oldValue.isBlank()) return;
        if (!GlobalPassword.validForCreation(newValue)) {
            this.error = Component.translatable("ias.error.globalPasswordRequirements").withStyle(ChatFormatting.RED);
            return;
        }
        if (oldValue.equals(newValue)) {
            this.error = Component.translatable("ias.error.globalPasswordSame").withStyle(ChatFormatting.RED);
            return;
        }
        if (!newValue.equals(this.confirmation.getValue())) {
            this.error = Component.translatable("ias.globalPassword.mismatch").withStyle(ChatFormatting.RED);
            return;
        }

        this.busy = true;
        this.error = null;
        this.updateAction();
        CompletableFuture.runAsync(() -> IAS.changeGlobalPassword(oldValue, newValue), IAS.executor())
                .whenCompleteAsync((ignored, throwable) -> {
                    if (this != this.currentScreen()) return;
                    if (throwable == null) {
                        this.busy = false;
                        this.success = true;
                        this.current.setValue("");
                        this.replacement.setValue("");
                        this.confirmation.setValue("");
                        this.error = Component.translatable("ias.globalPassword.change.success")
                                .withStyle(ChatFormatting.GREEN);
                        this.close.setMessage(CommonComponents.GUI_DONE);
                        this.updateAction();
                        return;
                    }

                    FriendlyException friendly = FriendlyException.friendlyInChain(throwable);
                    this.busy = false;
                    this.current.setValue("");
                    this.error = Component.translatable(friendly != null ? friendly.key() : "ias.error")
                            .withStyle(ChatFormatting.RED);
                    this.updateAction();
                }, this.minecraft);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int firstY = Math.max(58, this.height / 2 - 32);
        graphics.centeredText(this.font, this.title, this.width / 2, firstY - 52, 0xFF_FF_FF_FF);
        Component status = this.error;
        if (status != null) {
            graphics.centeredText(this.font, status, this.width / 2, firstY - 38,
                    this.success ? 0xFF_55_FF_55 : 0xFF_FF_55_55);
        } else if (this.tip != null) {
            IStonecutter.renderMultilineLabelCentered(this.tip, graphics, this.width / 2, firstY - 38);
        }
        graphics.centeredText(this.font, this.current.getMessage(), this.width / 2, this.current.getY() - 10, 0xFF_FF_FF_FF);
        graphics.centeredText(this.font, this.replacement.getMessage(), this.width / 2, this.replacement.getY() - 10, 0xFF_FF_FF_FF);
        graphics.centeredText(this.font, this.confirmation.getMessage(), this.width / 2, this.confirmation.getY() - 10, 0xFF_FF_FF_FF);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
