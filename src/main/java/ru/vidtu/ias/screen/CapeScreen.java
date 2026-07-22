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
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.auth.microsoft.MSAuth;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.mixins.MinecraftAccessor;
import ru.vidtu.ias.mixins.PlayerInfoAccessor;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.utils.CapeTextureCache;
import ru.vidtu.ias.utils.SkinCache;
import ru.vidtu.ias.utils.UserToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CapeScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/CapeScreen");

    @Nullable
    private final Screen parent;
    private final CapeSkinWidget.PreviewState previewPose =
            new CapeSkinWidget.PreviewState(CapeSkinWidget.PreviewPose.CAPE);
    @Nullable
    private String token;
    @Nullable
    private List<MSAuth.Cape> capes;
    @Nullable
    private MSAuth.Cape selectedCape;
    @Nullable
    private CapeList list;
    @Nullable
    private Button confirm;
    private boolean selectionInitialized;
    private boolean loading;
    private boolean saving;
    private Component status = Component.translatable("ias.capes.loading").withStyle(ChatFormatting.YELLOW);
    private int previewNameCenterX;
    private int previewNameY;
    private int previewNameMaxWidth;
    private float previewNameScale;

    public CapeScreen(@Nullable Screen parent) {
        super(Component.translatable("ias.capes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        assert this.minecraft != null;
        if (this.token == null) {
            User user = this.minecraft.getUser();
            this.token = user != null ? UserToken.access(user) : null;
        }

        int leftWidth = Math.max(130, this.width / 2 - 16);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> this.onClose())
                .bounds(10, this.height - 28, leftWidth, 20)
                .build());
        this.confirm = Button.builder(Component.translatable("ias.capes.confirm"), btn -> this.confirmSelection())
                .bounds(this.width - leftWidth - 10, this.height - 28, leftWidth, 20)
                .build();
        this.confirm.active = this.capes != null && !this.saving;
        this.addRenderableWidget(this.confirm);

        int previewX = leftWidth + 24;
        int previewWidth = Math.max(60, this.width - previewX - 14);
        int previewHeight = Math.max(90, this.height - 86);
        CapeSkinWidget preview = new CapeSkinWidget(previewWidth, previewHeight, this::previewSkin, this.previewPose);
        int previewY = 40;
        preview.setPosition(previewX, previewY);
        this.addRenderableWidget(preview);
        this.previewNameCenterX = previewX + previewWidth / 2;
        this.previewNameScale = Math.max(1.25F, Math.min(2.0F, previewHeight / 260.0F));
        int previewNameBottom = previewY + Math.max(1, Math.round(previewHeight * 0.09F) - 11)
                + this.font.lineHeight - Math.round(6.0F * this.previewNameScale);
        this.previewNameY = Math.round(previewNameBottom - this.font.lineHeight * this.previewNameScale);
        this.previewNameMaxWidth = Math.max(20, previewWidth - 8);

        if (this.token == null) {
            this.status = Component.translatable("ias.capes.noToken").withStyle(ChatFormatting.RED);
            this.confirm.active = false;
            return;
        }
        if (this.capes == null) {
            if (!this.loading) this.load();
            return;
        }

        if (!this.selectionInitialized) {
            this.selectedCape = this.capes.stream().filter(MSAuth.Cape::active).findFirst().orElse(null);
            this.selectionInitialized = true;
        }

        int listHeight = Math.max(24, this.height - 78);
        if (this.list == null) {
            this.list = new CapeList(this.minecraft, leftWidth, listHeight, 42, 82);
        }
        this.list.setRectangle(leftWidth, listHeight, 10, 42);
        this.list.columns = Math.max(1, Math.min(4, (leftWidth - 12) / 70));
        this.list.replace(this.capes);
        this.addRenderableWidget(this.list);
    }

    private void load() {
        assert this.minecraft != null;
        assert this.token != null;
        this.loading = true;
        MSAuth.capes(this.token).whenCompleteAsync((loaded, throwable) -> {
            this.loading = false;
            if (throwable != null) {
                LOGGER.error("IAS: Unable to load capes.", throwable);
                this.status = Component.translatable("ias.capes.error").withStyle(ChatFormatting.RED);
            } else {
                this.capes = loaded;
                this.status = loaded.isEmpty() ? Component.translatable("ias.capes.empty").withStyle(ChatFormatting.GRAY) : Component.empty();
            }
            this.init(this.width, this.height);
        }, this.minecraft);
    }

    private PlayerSkin previewSkin() {
        if (!this.selectionInitialized) return this.skinWithCape(null, false);
        return this.skinWithCape(this.selectedCape, true);
    }

    private PlayerSkin skinWithCape(@Nullable MSAuth.Cape cape, boolean replaceCape) {
        assert this.minecraft != null;
        User user = this.minecraft.getUser();
        PlayerSkin base = user == null
                ? DefaultPlayerSkin.get(IStonecutter.NIL_UUID)
                : SkinCache.skin(this.minecraft, user.getProfileId(), user.getName());
        if (!replaceCape) return base;
        ClientAsset.Texture texture = cape == null ? null : CapeTextureCache.texture(this.minecraft, cape.url());
        return new PlayerSkin(base.body(), texture, base.elytra(), base.model(), base.secure());
    }

    private void confirmSelection() {
        if (this.token == null || this.saving) return;
        this.saving = true;
        if (this.confirm != null) this.confirm.active = false;
        this.status = Component.translatable("ias.capes.saving").withStyle(ChatFormatting.YELLOW);
        if (this.selectedCape == null) {
            MSAuth.clearCape(this.token).whenCompleteAsync((ignored, throwable) -> this.saved(throwable), this.minecraft);
        } else {
            MSAuth.activateCape(this.token, this.selectedCape.id()).whenCompleteAsync((ignored, throwable) -> this.saved(throwable), this.minecraft);
        }
    }

    private void saved(@Nullable Throwable throwable) {
        this.saving = false;
        if (throwable != null) {
            LOGGER.error("IAS: Unable to save cape.", throwable);
            this.status = Component.translatable("ias.capes.error").withStyle(ChatFormatting.RED);
            if (this.confirm != null) this.confirm.active = true;
            return;
        }
        this.refreshSkin();
        this.capes = null;
        this.selectionInitialized = false;
        this.status = Component.translatable("ias.capes.saved").withStyle(ChatFormatting.GREEN);
        this.load();
    }

    private void refreshSkin() {
        assert this.minecraft != null;
        User user = this.minecraft.getUser();
        if (user == null) return;
        UUID uuid = user.getProfileId();
        CompletableFuture.supplyAsync(
                () -> this.minecraft.services().sessionService().fetchProfile(uuid, true), IAS.executor()
        ).thenAcceptAsync(profile -> {
            if (profile == null) return;
            ((MinecraftAccessor) this.minecraft).ias$profileFuture(CompletableFuture.completedFuture(profile));
            SkinCache.refresh(this.minecraft, profile.profile());
            if (this.minecraft.getConnection() == null) return;
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(uuid);
            if (info == null) return;
            info.getProfile().properties().clear();
            info.getProfile().properties().putAll(profile.profile().properties());
            ((PlayerInfoAccessor) info).ias$skinLookup(null);
        }, this.minecraft).exceptionally(throwable -> {
            LOGGER.warn("IAS: Unable to refresh skin after changing cape.", throwable);
            return null;
        });
    }

    @Override
    public void onClose() {
        assert this.minecraft != null;
        //$ set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFF_FF_FF_FF);
        if (this.status != Component.empty()) {
            graphics.centeredText(this.font, this.status, this.width / 2, 27, 0xFF_FF_FF_FF);
        }

        assert this.minecraft != null;
        User user = this.minecraft.getUser();
        if (user != null) {
            String name = user.getName();
            int logicalMaxWidth = Math.max(1, (int) (this.previewNameMaxWidth / this.previewNameScale) - 4);
            if (this.font.width(name) > logicalMaxWidth) {
                int end = name.length();
                while (end > 1 && this.font.width(name.substring(0, end) + "...") > logicalMaxWidth) end--;
                name = name.substring(0, end) + "...";
            }

            int textWidth = this.font.width(name);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(this.previewNameCenterX, this.previewNameY);
            pose.scale(this.previewNameScale, this.previewNameScale);
            graphics.fill(-textWidth / 2 - 2, -1, (textWidth + 1) / 2 + 2,
                    this.font.lineHeight, 0x60_00_00_00);
            graphics.centeredText(this.font, name, 0, 0, 0xFF_FF_FF_FF);
            pose.popMatrix();
        }
    }

    private final class CapeList extends ObjectSelectionList<CapeRow> {
        private int columns;

        CapeList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.columns = Math.max(1, Math.min(4, (width - 12) / 70));
        }

        void replace(List<MSAuth.Cape> capes) {
            List<MSAuth.Cape> choices = new ArrayList<>(capes.size() + 1);
            choices.add(null);
            choices.addAll(capes);
            List<CapeRow> rows = new ArrayList<>((choices.size() + this.columns - 1) / this.columns);
            for (int i = 0; i < choices.size(); i += this.columns) {
                rows.add(new CapeRow(new ArrayList<>(choices.subList(i, Math.min(i + this.columns, choices.size())))));
            }
            this.replaceEntries(rows);
            this.setSelected(rows.stream().filter(CapeRow::containsSelection).findFirst().orElse(rows.getFirst()));
        }

        @Override
        public int getRowWidth() {
            return this.getWidth() - 12;
        }
    }

    private final class CapeRow extends ObjectSelectionList.Entry<CapeRow> {
        private final List<MSAuth.Cape> capes;

        CapeRow(List<MSAuth.Cape> capes) {
            this.capes = capes;
        }

        boolean containsSelection() {
            return this.capes.stream().anyMatch(cape -> Objects.equals(cape, CapeScreen.this.selectedCape));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int gap = 4;
            int cardWidth = (this.getContentWidth() - gap * (CapeScreen.this.list.columns - 1)) / CapeScreen.this.list.columns;
            int cardHeight = this.getContentHeight() - 4;
            for (int i = 0; i < this.capes.size(); i++) {
                MSAuth.Cape cape = this.capes.get(i);
                int x = this.getContentX() + i * (cardWidth + gap);
                int y = this.getContentY() + 2;
                boolean cardHovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
                boolean selected = Objects.equals(cape, CapeScreen.this.selectedCape);
                graphics.fill(x, y, x + cardWidth, y + cardHeight, cardHovered ? 0xAA_33_33_33 : 0x88_11_11_11);
                if (selected) graphics.outline(x, y, cardWidth, cardHeight, 0xFF_FF_FF_FF);

                if (cape == null) {
                    graphics.centeredText(CapeScreen.this.font, "X", x + cardWidth / 2, y + cardHeight / 2 - 8, 0xFF_AA_AA_AA);
                } else {
                    CapeSkinWidget.extractPlayer(graphics, CapeScreen.this.skinWithCape(cape, true), 0.0F, 150.0F,
                            x + 2, y + 2, x + cardWidth - 2, y + cardHeight - 15);
                }

                String name = cape == null ? Component.translatable("ias.capes.none").getString() : cape.name();
                if (CapeScreen.this.font.width(name) > cardWidth - 6) {
                    int end = name.length();
                    while (end > 1 && CapeScreen.this.font.width(name.substring(0, end) + "...") > cardWidth - 6) end--;
                    name = name.substring(0, end) + "...";
                }
                graphics.centeredText(CapeScreen.this.font, name, x + cardWidth / 2, y + cardHeight - 12, selected ? 0xFF_FF_FF_A0 : 0xFF_FF_FF_FF);
            }
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            int gap = 4;
            int cardWidth = (this.getContentWidth() - gap * (CapeScreen.this.list.columns - 1)) / CapeScreen.this.list.columns;
            for (int i = 0; i < this.capes.size(); i++) {
                int x = this.getContentX() + i * (cardWidth + gap);
                if (event.x() < x || event.x() >= x + cardWidth) continue;
                CapeScreen.this.selectedCape = this.capes.get(i);
                CapeScreen.this.list.setSelected(this);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(this.capes.stream()
                    .map(cape -> cape == null ? Component.translatable("ias.capes.none").getString() : cape.name())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
        }
    }
}
