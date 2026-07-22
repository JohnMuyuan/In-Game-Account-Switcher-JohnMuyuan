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

import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.config.GlobalPassword;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.config.IASConfig;

import java.time.Duration;

public final class AccountScreen extends Screen {
    /**
     * Top edge of the main content area.
     */
    private static final int CONTENT_TOP = 34;

    /**
     * Space reserved for the unchanged two-row action area.
     */
    private static final int CONTENT_BOTTOM_MARGIN = 52;

    /**
     * Width-to-height ratio used by the player skin preview.
     */
    private static final float SKIN_ASPECT = 0.68F;

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountScreen");

    /**
     * Parent screen, {@code null} if none.
     */
    private final Screen parent;

    /**
     * Skin pose retained when a popup closes and this screen is initialized again.
     */
    private final CapeSkinWidget.PreviewState skinPose =
            new CapeSkinWidget.PreviewState(CapeSkinWidget.PreviewPose.FRONT);

    /**
     * Search widget.
     */
    private EditBox search;

    /**
     * Account list widget.
     */
    private AccountList list;

    /**
     * Player skin widget.
     */
    private CapeSkinWidget skin;

    /**
     * Contextual action below the player skin preview.
     */
    private Button skinAction;

    /**
     * Login button.
     */
    private Button login;

    /**
     * Offline login button.
     */
    private Button offlineLogin;

    /**
     * Edit button.
     */
    private Button edit;

    /**
     * Edit button.
     */
    private Button delete;

    /**
     * Creates a new screen.
     *
     * @param parent Parent screen, {@code null} if none
     */
    public AccountScreen(Screen parent) {
        super(Component.translatable("ias.accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Bruh.
        assert this.minecraft != null;

        // Disabled check.
        if (IAS.disabled()) {
            final Screen alert = new AlertScreen(this::onClose, Component.translatable("ias.disabled.title").withStyle(ChatFormatting.RED),
                    Component.translatable("ias.disabled.text"), CommonComponents.GUI_BACK, true);
            //$ set_screen 'this.minecraft' 'alert'
            this.minecraft.gui.setScreen(alert);
            return;
        }

        // Disclaimer.
        if (!IASStorage.gameDisclaimerShown) {
            final Screen alert = new AlertScreen(() -> {
                // Save disclaimer.
                try {
                    IAS.gameDisclaimerShownStorage();
                } catch (Throwable t) {
                    LOGGER.error("Unable to set or write game disclaimer state.", t);
                }

                // Set screen.
                //$ set_screen 'this.minecraft' this
                this.minecraft.gui.setScreen(this);
            }, Component.translatable("ias.disclaimer.title").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("ias.disclaimer.text"), CommonComponents.GUI_CONTINUE, false);
            //$ set_screen 'this.minecraft' 'alert'
            this.minecraft.gui.setScreen(alert);
            return;
        }

        int dividerX = this.contentDividerX();
        int searchWidth = Math.max(20, Math.min(150, this.width - 20));

        // Keep the search centered across the whole screen, as in the original layout.
        this.search = new EditBox(this.font, (this.width - searchWidth) / 2, 11, searchWidth, 20,
                this.search, Component.translatable("ias.accounts.search"));
        this.search.setHint(this.search.getMessage().copy().withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.search);

        // Scale the preview from both the column width and the available height.
        int contentBottom = Math.max(CONTENT_TOP, this.height - CONTENT_BOTTOM_MARGIN);
        int contentHeight = Math.max(1, contentBottom - CONTENT_TOP);
        int previewButtonHeight = 20;
        int previewGap = 6;
        int maxPreviewHeight = Math.max(1, contentHeight - previewButtonHeight - previewGap - 12);
        int maxPreviewWidth = Math.max(20, dividerX - 24);
        int preferredPreviewHeight = Math.min(320, Math.max(70, Math.round(contentHeight * 0.70F)));
        int widthLimitedHeight = Math.max(1, Math.round(maxPreviewWidth / SKIN_ASPECT));
        int skinHeight = Math.min(maxPreviewHeight, Math.min(preferredPreviewHeight, widthLimitedHeight));
        int skinWidth = Math.min(maxPreviewWidth, Math.max(20, Math.round(skinHeight * SKIN_ASPECT)));
        int previewGroupHeight = skinHeight + previewGap + previewButtonHeight;
        int skinX = Math.max(0, (dividerX - skinWidth) / 2);
        int skinY = CONTENT_TOP + Math.max(0, (contentHeight - previewGroupHeight) / 2);

        this.skin = new CapeSkinWidget(skinWidth, skinHeight, () -> {
            // Return default if list is removed. (for whatever reason)
            if (this.list == null) return this.currentSkin();

            AccountEntry selected = this.list.getSelected();
            if (selected == null) return this.currentSkin();

            // Return skin of selected.
            return this.list.skin(selected);
        }, this.skinPose);
        this.skin.setPosition(skinX, skinY);
        this.addRenderableWidget(this.skin);

        this.skinAction = Button.builder(Component.translatable("ias.capes"), btn -> this.runSkinAction())
                .bounds(skinX, skinY + skinHeight + previewGap, skinWidth, previewButtonHeight)
                .build();
        this.addRenderableWidget(this.skinAction);

        // Add login button.
        this.login = Button.builder(Component.translatable("ias.accounts.login"), btn -> {
            this.list.login(true, IASConfig.closeOnLogin ? () -> {
                //$ set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
        })
            .bounds(this.width / 2 - 50 - 100 - 4, this.height - 24 - 24, 100, 20).build();
        this.addRenderableWidget(this.login);

        int offlineX = this.width / 2 - 50 - 100 - 4;
        int offlineY = this.height - 24;
        Button changePassword = Button.builder(Component.literal("\u270E"), btn -> {
            //$ set_screen 'this.minecraft' 'new ChangeGlobalPasswordPopupScreen(this)'
            this.minecraft.gui.setScreen(new ChangeGlobalPasswordPopupScreen(this));
        }).bounds(offlineX, offlineY, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("ias.globalPassword.change")))
                .build();
        changePassword.active = GlobalPassword.configured();
        this.addRenderableWidget(changePassword);

        // Add offline login button.
        this.offlineLogin = Button.builder(Component.translatable("ias.accounts.offlineLogin"), btn -> {
            this.list.login(false, IASConfig.closeOnLogin ? () -> {
                //$ set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
        })
            .bounds(offlineX + 24, offlineY, 76, 20)
            .build();
        this.addRenderableWidget(this.offlineLogin);

        // Add edit button.
        this.edit = Button.builder(Component.translatable("ias.accounts.edit"), btn -> this.list.edit())
                .bounds(this.width / 2 - 50, this.height - 24 - 24, 100, 20)
                .build();
        this.addRenderableWidget(this.edit);

        // Add delete button.
        //? if >=1.21.10 {
        this.delete = Button.builder(Component.translatable("ias.accounts.delete"), btn -> this.list.delete(!this.minecraft.hasShiftDown()))
        //?} else
        /*this.delete = Button.builder(Component.translatable("ias.accounts.delete"), btn -> this.list.delete(!Screen.hasShiftDown()))*/
                .bounds(this.width / 2 - 50, this.height - 24, 100, 20)
                .build();
        this.addRenderableWidget(this.delete);

        // Add edit button.
        this.addRenderableWidget(Button.builder(Component.translatable("ias.accounts.add"), btn -> this.list.add())
                .bounds(this.width / 2 + 50 + 4, this.height - 24 - 24, 100, 20)
                .build());

        // Add delete button.
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, btn -> {
            //$ set_screen 'this.minecraft' 'this.parent'
            this.minecraft.gui.setScreen(this.parent);
        })
                .bounds(this.width / 2 + 50 + 4, this.height - 24, 100, 20)
                .build());

        // Add account list to the right column.
        int listX = dividerX + 1;
        int listWidth = this.width - listX;
        int listHeight = Math.max(0, this.height - CONTENT_BOTTOM_MARGIN - CONTENT_TOP);
        if (this.list != null) {
            this.list.setRectangle(listWidth, listHeight, listX, CONTENT_TOP);
        } else {
            this.list = new AccountList(this, this.minecraft, listWidth, listHeight, listX, CONTENT_TOP, 12);
        }
        this.addRenderableWidget(this.list);

        // Update the list.
        this.search.setResponder(this.list::update);
        this.list.update(this.search.getValue());
        this.updateSelected();
    }

    @Override
    public void onClose() {
        // Bruh.
        assert this.minecraft != null;

        // Close to parent.
        //$ set_screen 'this.minecraft' 'this.parent'
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    //? if >=26.1 {
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        // Render background and widgets.
        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        //?} else
        /*super.render(graphics, mouseX, mouseY, delta);*/

        // Render title.
        //? if >=26.1 {
        graphics.centeredText(this.font, this.title, this.width / 2, 1, 0xFF_FF_FF_FF);
        //?} else
        /*graphics.drawCenteredString(this.font, this.title, this.width / 2, 1, 0xFF_FF_FF_FF);*/
    }

    @Override
    //? if >=26.1 {
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    //?} else
    /*public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {*/
        // Preserve the parent screen panorama/world blur first.
        //? if >=26.1 {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        //?} else
        /*super.renderBackground(graphics, mouseX, mouseY, delta);*/

        int dividerX = this.contentDividerX();
        int contentBottom = Math.max(CONTENT_TOP, this.height - CONTENT_BOTTOM_MARGIN);

        // A lighter cool panel keeps the skin readable; the darker neutral panel
        // gives account names and selection states enough contrast.
        graphics.fill(0, CONTENT_TOP, dividerX, contentBottom, 0x58_0B_18_22);
        graphics.fill(dividerX + 1, CONTENT_TOP, this.width, contentBottom, 0x72_0B_0D_11);
        graphics.fill(dividerX, CONTENT_TOP, dividerX + 1, contentBottom, 0x70_A8_B8_C8);
        graphics.fill(0, CONTENT_TOP, this.width, CONTENT_TOP + 1, 0x38_FF_FF_FF);
        graphics.fill(0, contentBottom - 1, this.width, contentBottom, 0x38_FF_FF_FF);
    }

    /**
     * Gets the divider between the skin preview and account list columns.
     *
     * @return Divider X coordinate
     */
    private int contentDividerX() {
        return Math.max(100, Math.min(this.width / 2, this.width - 180));
    }

    /**
     * Gets the search.
     *
     * @return Search widget
     */
    EditBox search() {
        return this.search;
    }

    /**
     * Updates the selected entry.
     */
    void updateSelected() {
        // Get the selected.
        AccountEntry selected = this.list != null ? this.list.getSelected() : null;

        // Match the title-screen preview action: current account opens capes,
        // while any other selected account is switched to directly.
        boolean showingCurrent = selected == null || this.isCurrentAccount(selected.account());
        if (this.skinAction != null) {
            this.skinAction.setMessage(Component.translatable(showingCurrent ? "ias.capes" : "ias.accounts.switch"));
            this.skinAction.active = true;
        }

        // Nothing is selected.
        if (selected == null) {
            // Disable every button.
            this.login.active = this.offlineLogin.active = this.edit.active = this.delete.active = false;

            // Hide tooltip, if exists.
            this.login.setTooltip(null);

            // Show current skin.
            this.skin.visible = true;

            // Stop here.
            return;
        }

        // Enable always-on buttons.
        this.offlineLogin.active = this.edit.active = this.delete.active = true;

        // Enable online login button if we can log in.
        if (selected.account().canLogin()) {
            this.login.active = true;
            this.login.setTooltip(null);
        } else {
            this.login.active = false;
            this.login.setTooltip(Tooltip.create(Component.translatable("ias.accounts.login.offline")));
            this.login.setTooltipDelay(Duration.ZERO);
        }

        // Show skin.
        this.skin.visible = true;
    }

    private void runSkinAction() {
        assert this.minecraft != null;

        AccountEntry selected = this.list != null ? this.list.getSelected() : null;
        if (selected == null || this.isCurrentAccount(selected.account())) {
            //$ set_screen 'this.minecraft' 'new CapeScreen(this)'
            this.minecraft.gui.setScreen(new CapeScreen(this));
            return;
        }

        this.skinAction.active = false;
        this.list.login(selected.account().canLogin(), IASConfig.closeOnLogin ? () -> {
            //$set_screen 'this.minecraft' 'this.parent'
            this.minecraft.gui.setScreen(this.parent);
        } : null);
    }

    private boolean isCurrentAccount(Account account) {
        assert this.minecraft != null;
        net.minecraft.client.User user = this.minecraft.getUser();
        return user != null && account.uuid().equals(user.getProfileId());
    }

    private net.minecraft.world.entity.player.PlayerSkin currentSkin() {
        assert this.minecraft != null;
        net.minecraft.client.User user = this.minecraft.getUser();
        return user != null ? ru.vidtu.ias.utils.SkinCache.skin(this.minecraft, user.getProfileId(), user.getName()) : DefaultPlayerSkin.get(IStonecutter.NIL_UUID);
    }

    @Override
    //? if >=1.21.10 {
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        boolean control = event.hasControlDown();
        boolean select = event.isSelection();
    //?} else {
    /*public boolean keyPressed(int key, int scan, int mods) {
        boolean shift = Screen.hasShiftDown();
        boolean control = Screen.hasControlDown();
        boolean select = net.minecraft.client.gui.navigation.CommonInputs.selected(key);
    *///?}
        // Bruh.
        assert this.minecraft != null;

        // Shift+Down or Page Down to swap down.
        if ((key == GLFW.GLFW_KEY_DOWN && shift) || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.list.swapDown(this.list.getSelected());
            return true;
        }

        // Shift+Up or Page Up to swap up.
        if ((key == GLFW.GLFW_KEY_UP && shift) || key == GLFW.GLFW_KEY_PAGE_UP) {
            this.list.swapUp(this.list.getSelected());
            return true;
        }

        // Ctrl+C to copy name. (Ctrl+Shift+C to copy UUID) {
        if (key == GLFW.GLFW_KEY_C && control) {
            AccountEntry selected = this.list.getSelected();
            if (selected != null) {
                Account account = selected.account();
                this.minecraft.keyboardHandler.setClipboard(shift ? account.uuid().toString() : account.name());
                return true;
            }
        }

        // Skip if handled by super.
        //? if >=1.21.10 {
        if (super.keyPressed(event)) {
        //?} else
        /*if (super.keyPressed(key, scan, mods)) {*/
            return true;
        }

        // Enter or Numpad Enter to log in.
        if (select) {
            this.list.login(!shift, IASConfig.closeOnLogin ? () -> {
                //$set_screen 'this.minecraft' 'this.parent'
                this.minecraft.gui.setScreen(this.parent);
            } : null);
            return true;
        }

        // Delete or Numpad Minus to delete.
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            this.list.delete(!shift);
            return true;
        }

        // CTRL+N or Numpad Plus to add.
        if ((key == GLFW.GLFW_KEY_N && control) || key == GLFW.GLFW_KEY_KP_ADD) {
            this.list.add();
            return true;
        }

        // CTRL+R or Numpad Asterisk to edit.
        if ((key == GLFW.GLFW_KEY_R && control) || key == GLFW.GLFW_KEY_KP_MULTIPLY) {
            this.list.edit();
            return true;
        }

        // Not handled.
        return false;
    }


    /**
     * Gets the parent screen.
     *
     * @return Parent screen
     */
    Screen parent() {
        return this.parent;
    }

    @Override
    public String toString() {
        return "AccountScreen{" +
                "list=" + this.list +
                '}';
    }
}
