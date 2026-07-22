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

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

public final class CapeSkinWidget extends AbstractWidget {
    public enum PreviewPose {
        FRONT(-30.0F, -5.0F),
        CAPE(150.0F, 0.0F);

        private final float bodyRotation;
        private final float cameraPitch;

        PreviewPose(float bodyRotation, float cameraPitch) {
            this.bodyRotation = bodyRotation;
            this.cameraPitch = cameraPitch;
        }
    }

    /**
     * Mutable pose shared by replacement widgets when their screen is initialized again.
     */
    public static final class PreviewState {
        private final float cameraPitch;
        private float bodyRotation;
        private float lookX;
        private float lookY;

        public PreviewState(PreviewPose pose) {
            this.cameraPitch = pose.cameraPitch;
            this.bodyRotation = pose.bodyRotation;
        }
    }

    private final Supplier<PlayerSkin> skin;
    private final PreviewState state;

    public CapeSkinWidget(int width, int height, Supplier<PlayerSkin> skin) {
        this(width, height, skin, new PreviewState(PreviewPose.FRONT));
    }

    public CapeSkinWidget(int width, int height, Supplier<PlayerSkin> skin, PreviewPose pose) {
        this(width, height, skin, new PreviewState(pose));
    }

    public CapeSkinWidget(int width, int height, Supplier<PlayerSkin> skin, PreviewState state) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        this.skin = skin;
        this.state = state;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (mouseX != 0 || mouseY != 0) {
            this.state.lookY = (float) Math.atan(((this.getX() + this.getRight()) / 2.0F - mouseX) / 40.0F) * 20.0F;
            this.state.lookX = (float) Math.atan(((this.getY() + this.getBottom()) / 2.0F - mouseY) / 40.0F) * 20.0F;
        }
        extractPlayer(graphics, this.skin.get(), this.state.lookX + this.state.cameraPitch, this.state.bodyRotation,
                -this.state.lookX, this.state.lookY,
                this.getX(), this.getY(), this.getRight(), this.getBottom(), true);
    }

    public static void extractPlayer(GuiGraphicsExtractor graphics, PlayerSkin current, float rotationX, float rotationY,
                                      int x, int y, int right, int bottom) {
        extractPlayer(graphics, current, rotationX, rotationY, 0.0F, 0.0F,
                x, y, right, bottom, false);
    }

    private static void extractPlayer(GuiGraphicsExtractor graphics, PlayerSkin current, float cameraRotationX,
                                      float bodyRotationY, float headRotationX, float headRotationY,
                                      int x, int y, int right, int bottom, boolean interactive) {
        AvatarRenderState state = new AvatarRenderState();
        //? if >=26.2 {
        state.entityType = EntityTypes.PLAYER;
        //?} else {
        /*state.entityType = EntityType.PLAYER;
        *///?}
        state.skin = current;
        state.showCape = current.cape() != null;
        state.bodyRot = 180.0F + bodyRotationY;
        state.yRot = interactive ? headRotationY : 0.0F;
        state.xRot = interactive ? headRotationX : 0.0F;
        state.scale = 1.0F;
        state.ageScale = 1.0F;
        state.pose = Pose.STANDING;
        state.boundingBoxWidth = 0.6F;
        state.boundingBoxHeight = 1.8F;
        state.eyeHeight = 1.62F;
        state.mainArm = HumanoidArm.RIGHT;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftHandItemStack = ItemStack.EMPTY;
        state.rightHandItemStack = ItemStack.EMPTY;
        state.headEquipment = ItemStack.EMPTY;
        state.chestEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;

        float scale = 0.97F * (bottom - y) / 2.125F;
        Quaternionf camera = new Quaternionf().rotateX(cameraRotationX * Mth.DEG_TO_RAD);
        Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).mul(camera);
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
        graphics.entity(state, scale, translation, rotation, camera, x, y, right, bottom);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        this.state.bodyRotation = Mth.wrapDegrees(this.state.bodyRotation - (float) dragX);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
    }
}
