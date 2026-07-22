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

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Styled button with popup design.
 *
 * @author VidTu
 */
final class PopupButton extends Button {

    /**
     * Creates a new button.
     *
     * @param x         Button X
     * @param y         Button Y
     * @param width     Button width
     * @param height    Button height
     * @param text      Button text
     * @param press     Button press handler
     * @param narration Button narration
     */
    PopupButton(int x, int y, int width, int height, Component text, OnPress press, CreateNarration narration) {
        super(x, y, width, height, text, press, narration);
    }

    /**
     * Sets the button color.
     *
     * @param red     Button R
     * @param green   Button G
     * @param blue    Button B
     * @param instant Whether the color change should be instant
     */
    void color(float red, float green, float blue, boolean instant) {
        // Kept for source compatibility with older popup call sites.
    }

    @Override
    //? if >=26.1 {
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} elif >=1.21.11 {
    /*protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    *///?} else
    /*protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        //? if >=26.1 {
        this.extractDefaultSprite(graphics);
        this.extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        //?} elif >=1.21.11 {
        /*super.renderContents(graphics, mouseX, mouseY, delta);*/
        //?} else
        /*super.renderWidget(graphics, mouseX, mouseY, delta);*/
    }

    @Override
    public String toString() {
        return "PopupButton{}";
    }
}
