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

package ru.vidtu.ias;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.auth.LoginData;
import ru.vidtu.ias.config.IASConfig;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.extension.MinecraftExtension;
import ru.vidtu.ias.mixins.MinecraftAccessor;
import ru.vidtu.ias.platform.IStonecutter;
import ru.vidtu.ias.screen.AccountLogin;
import ru.vidtu.ias.screen.AccountScreen;
import ru.vidtu.ias.screen.CapeScreen;
import ru.vidtu.ias.screen.CapeSkinWidget;
import ru.vidtu.ias.utils.Expression;
import ru.vidtu.ias.utils.IUtils;
import ru.vidtu.ias.utils.SkinCache;
import ru.vidtu.ias.utils.exceptions.FriendlyException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

//? if >=26.2 {
import com.mojang.authlib.yggdrasil.FriendsService;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
//?}

/**
 * Main IAS class for Minecraft.
 *
 * @author VidTu
 */
public final class IASMinecraft {
    /**
     * Toast for nick warning.
     */
    public static final SystemToast.SystemToastId NICK_WARN = new SystemToast.SystemToastId(10000L);

    /**
     * Button widget sprites.
     */
    public static final WidgetSprites BUTTON = new WidgetSprites(
            IStonecutter.identifier("button_plain"),
            IStonecutter.identifier("button_disabled"),
            IStonecutter.identifier("button_focus")
    );

    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/IASMinecraft");

    private static int textX;
    private static int multiplayerNameCenterX;
    private static int multiplayerNameY;
    private static boolean multiplayerPreviewVisible;

    /**
     * Text Y.
     */
    private static int textY;

    /**
     * Current text.
     */
    private static Component text = Component.translatable("ias.title", "(not loaded for some reason)");

    private static int titleNameCenterX;
    private static int titleNameY;
    private static int pauseNameCenterX;
    private static int pauseNameY;
    private static Account titlePreviewAccount;
    private static ImageButton titleOpenButton;
    private static Button titleActionButton;
    private static Button titleResetButton;
    private static int titleActionButtonY;
    private static LoginData titleFallbackSession;
    private static final Map<Screen, CapeSkinWidget.PreviewState> PREVIEW_POSES = new WeakHashMap<>();

    /**
     * An instance of this class cannot be created.
     *
     * @throws AssertionError Always
     */
    private IASMinecraft() {
        throw new AssertionError("No instances.");
    }

    /**
     * Initializes the IAS.
     */
    public static void init() {
        // Initialize the IAS.
        IAS.init(IStonecutter.GAME_DIRECTORY, IStonecutter.CONFIG_DIRECTORY);
    }

    /**
     * Called on title screen initialization.
     *
     * @param minecraft   Minecraft instance
     * @param screen      Target screen
     * @param buttonAdder Adder function
     */
    @SuppressWarnings({"ChainOfInstanceofChecks", "ConstantValue"}) // <- Abstraction for Minecraft is not possible, mods break user non-nullness.
    public static void onInit(Minecraft minecraft, Screen screen, Consumer<AbstractWidget> widgetAdder) {
        // Add title button.
        if (IASConfig.titleButton && screen instanceof TitleScreen) {
            // Calculate the position.
            int width = screen.width;
            int height = screen.height;
            Integer x = Expression.parsePosition(IASConfig.titleButtonX, width, height);
            Integer y = Expression.parsePosition(IASConfig.titleButtonY, width, height);
            boolean defaultPosition = x == null || y == null;

            // Couldn't parse position.
            if (x == null || y == null) {
                if (width >= 300 && height >= 180) {
                    boolean compact = width < 600 || height < 240;
                    int previewX = compact ? 24 : 58;
                    int previewHeight = compact ? 70 : 120;
                    int previewY = height / 2 - previewHeight / 2;
                    x = previewX - 24;
                    y = previewY + previewHeight + 4;
                } else {
                    x = width / 2 + 104;
                    y = height / 4 + 72;
                }
            }

            // Add the button.
            ImageButton button = new ImageButton(x, y, 20, 20, BUTTON, btn -> {
                //$ set_screen minecraft 'new AccountScreen(screen)'
                minecraft.gui.setScreen(new AccountScreen(screen));
            }, Component.literal("In-Game Account Switcher"));
            button.setTooltip(Tooltip.create(button.getMessage()));
            button.setTooltipDelay(Duration.ofMillis(250L));
            titleOpenButton = defaultPosition ? button : null;
            widgetAdder.accept(button);
        }

        if (screen instanceof TitleScreen) {
            IASStorage.ACCOUNTS.forEach(account -> SkinCache.skin(minecraft, account.skin(), account.name()));
            addTitlePreview(minecraft, screen, widgetAdder);
        } else if (screen instanceof PauseScreen) {
            addPausePreview(minecraft, screen, widgetAdder);
        }

        // Add servers button.
        //? if <1.21.10 {
        /*if (IASConfig.serversButton && screen instanceof JoinMultiplayerScreen) {
            // Calculate the position.
            int width = screen.width;
            int height = screen.height;
            Integer x = Expression.parsePosition(IASConfig.serversButtonX, width, height);
            Integer y = Expression.parsePosition(IASConfig.serversButtonY, width, height);

            // Couldn't parse position.
            if (x == null || y == null) {
                // Use default position.
                x = width / 2 + 158;
                y = height - 30;

                // Move out of any overlapping elements.
                for (int i = 0; i < 64; i++) {
                    boolean overlapping = false;
                    for (GuiEventListener child : screen.children()) {
                        // Skip if doesn't have pos.
                        if (!(child instanceof LayoutElement le) || child instanceof AbstractSelectionList<?>) continue;

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

            // Add the button.
            ImageButton button = new ImageButton(x, y, 20, 20, BUTTON, btn -> minecraft.setScreen(new AccountScreen(screen)), Component.literal("In-Game Account Switcher"));
            button.setTooltip(Tooltip.create(button.getMessage()));
            button.setTooltipDelay(Duration.ofMillis(250L));
            widgetAdder.accept(button);
        }
        *///?}

        // Add servers text.
        if (IASConfig.serversText && screen instanceof JoinMultiplayerScreen) {
            // Calculate the position.
            int width = screen.width;
            int height = screen.height;
            Integer cx = Expression.parsePosition(IASConfig.serversTextX, width, height);
            Integer cy = Expression.parsePosition(IASConfig.serversTextY, width, height);
            Font font = minecraft.font;
            User user = minecraft.getUser();
            text = Component.translatable("ias.title", user != null ? user.getName() : "(broken by mods)");
            textX = cx == null || cy == null ? (width - font.width(text)) / 2 : switch (IASConfig.serversTextAlign) {
                case LEFT -> cx;
                case CENTER -> cx - font.width(text) / 2;
                case RIGHT -> cx - font.width(text);
            };
            textY = cx == null || cy == null ? 5 : cy;
        }

        // Warn about invalid names.
        //? if >=26.2 {
        ToastManager manager = minecraft.gui.toastManager();
        //?} else {
        /*ToastManager manager = minecraft.getToastManager();
        *///?}
        if (!IASConfig.nickWarns || !(screen instanceof ConnectScreen) || manager.getToast(SystemToast.class, NICK_WARN) != null) return;
        User user = minecraft.getUser();
        // Mods break non-nullness.
        //noinspection ConstantValue
        String name = user != null ? user.getName() : "";
        String key = IUtils.warnKey(name);
        if (key == null) return;

        // Display the toast.
        //? if >=26.2 {
        final SystemToast toast = new SystemToast(NICK_WARN, Component.literal("In-Game Account Switcher"), Component.translatable(key, name));
        //?} else {
        /*final SystemToast toast = SystemToast.multiline(minecraft, NICK_WARN, Component.literal("In-Game Account Switcher"), Component.translatable(key, name));
        *///?}
        manager.addToast(toast);
    }

    private static void addTitlePreview(Minecraft minecraft, Screen screen, Consumer<AbstractWidget> widgetAdder) {
        if (screen.width < 300 || screen.height < 180) return;
        User current = minecraft.getUser();
        if (current != null && IASStorage.ACCOUNTS.stream().noneMatch(account -> account.uuid().equals(current.getProfileId()))
                && titleFallbackSession == null) {
            // The launcher session has no refresh token, so retain it in memory only.
            titleFallbackSession = new LoginData(current.getName(), current.getProfileId(), current.getAccessToken(), true);
        }
        if (titleActionButton == null || titlePreviewAccount != null && !IASStorage.ACCOUNTS.contains(titlePreviewAccount)) {
            titlePreviewAccount = currentTitleAccount(minecraft);
        }

        boolean compact = screen.width < 600 || screen.height < 240;
        int previewWidth = compact ? 40 : 85;
        int previewHeight = compact ? 70 : 120;
        int x = compact ? 24 : 58;
        int y = screen.height / 2 - previewHeight / 2;
        CapeSkinWidget skin = new CapeSkinWidget(previewWidth, previewHeight, () -> {
            Account selected = titlePreviewAccount;
            if (selected != null) return SkinCache.skin(minecraft, selected.skin(), selected.name());
            LoginData fallback = titleFallbackSession;
            if (fallback != null) return SkinCache.skin(minecraft, fallback.uuid(), fallback.name());
            User user = minecraft.getUser();
            return user == null ? DefaultPlayerSkin.get(IStonecutter.NIL_UUID) : SkinCache.skin(minecraft, user.getProfileId(), user.getName());
        }, previewPose(screen, CapeSkinWidget.PreviewPose.FRONT));
        skin.setPosition(x, y);
        widgetAdder.accept(skin);
        titleNameCenterX = x + previewWidth / 2;
        titleNameY = y - 12;

        Button[] actionButtons = new Button[2];
        boolean includeFallback = hasTitleFallback();
        boolean hasAlternative = IASStorage.ACCOUNTS.size() + (includeFallback ? 1 : 0) > 1;
        Button previous = Button.builder(Component.literal("<"), btn -> cycleTitleAccount(-1, minecraft, actionButtons))
                .bounds(x - 20, y + previewHeight / 2 - 10, 18, 20)
                .build();
        previous.visible = hasAlternative;
        widgetAdder.accept(previous);
        Button next = Button.builder(Component.literal(">"), btn -> cycleTitleAccount(1, minecraft, actionButtons))
                .bounds(x + previewWidth + 2, y + previewHeight / 2 - 10, 18, 20)
                .build();
        next.visible = hasAlternative;
        widgetAdder.accept(next);
        int buttonHeight = 20;
        int actionButtonY = y + previewHeight + 4;
        actionButtons[0] = Button.builder(Component.translatable("ias.capes"), btn -> {
            if (isCurrentTitleAccount(minecraft)) {
                //$ set_screen minecraft 'new CapeScreen(screen)'
                minecraft.gui.setScreen(new CapeScreen(screen));
                return;
            }

            Account selected = titlePreviewAccount;
            if (selected != null) {
                btn.active = false;
                AccountLogin.login(minecraft, screen, selected, selected.canLogin(), null);
            } else if (titleFallbackSession != null) {
                btn.active = false;
                AccountLogin.login(minecraft, screen, titleFallbackSession);
            }
        }).bounds(x, actionButtonY, previewWidth, buttonHeight).build();
        actionButtons[1] = Button.builder(Component.literal("\u21bb"), btn -> resetTitleAccount(minecraft, actionButtons))
                .bounds(x + previewWidth + 4, actionButtonY, buttonHeight, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("ias.accounts.current")))
                .build();
        titleActionButtonY = actionButtonY;
        titleActionButton = actionButtons[0];
        titleResetButton = actionButtons[1];
        updateTitleActions(minecraft, actionButtons);
        widgetAdder.accept(actionButtons[0]);
        widgetAdder.accept(actionButtons[1]);
    }

    private static void cycleTitleAccount(int direction, Minecraft minecraft, Button[] actionButtons) {
        int size = IASStorage.ACCOUNTS.size();
        if (size == 0) return;
        if (hasTitleFallback()) {
            int index = titlePreviewAccount == null ? 0 : IASStorage.ACCOUNTS.indexOf(titlePreviewAccount) + 1;
            int next = Math.floorMod(index + direction, size + 1);
            titlePreviewAccount = next == 0 ? null : IASStorage.ACCOUNTS.get(next - 1);
            updateTitleActions(minecraft, actionButtons);
            return;
        }
        int index = titlePreviewAccount == null ? -1 : IASStorage.ACCOUNTS.indexOf(titlePreviewAccount);
        if (index < 0) {
            index = direction > 0 ? 0 : size - 1;
        } else {
            index = Math.floorMod(index + direction, size);
        }
        titlePreviewAccount = IASStorage.ACCOUNTS.get(index);
        updateTitleActions(minecraft, actionButtons);
    }

    private static void resetTitleAccount(Minecraft minecraft, Button[] actionButtons) {
        titlePreviewAccount = currentTitleAccount(minecraft);
        updateTitleActions(minecraft, actionButtons);
    }

    private static Account currentTitleAccount(Minecraft minecraft) {
        User current = minecraft.getUser();
        return current == null ? null : IASStorage.ACCOUNTS.stream()
                .filter(account -> account.uuid().equals(current.getProfileId()))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasTitleFallback() {
        LoginData fallback = titleFallbackSession;
        return fallback != null && IASStorage.ACCOUNTS.stream()
                .noneMatch(account -> account.uuid().equals(fallback.uuid()));
    }

    private static void updateTitleActions(Minecraft minecraft, Button[] actionButtons) {
        boolean showingCurrent = isCurrentTitleAccount(minecraft);
        actionButtons[0].setMessage(Component.translatable(showingCurrent ? "ias.capes" : "ias.accounts.switch"));
        actionButtons[0].active = showingCurrent || titlePreviewAccount != null || titleFallbackSession != null;
        actionButtons[1].visible = actionButtons[1].active = !showingCurrent;
        alignTitleActions();
    }

    private static void alignTitleActions() {
        if (titleActionButton == null || titleResetButton == null) return;
        if (titleOpenButton != null) titleOpenButton.setY(titleActionButtonY);
        titleActionButton.setY(titleActionButtonY);
        titleResetButton.setY(titleActionButtonY);
    }

    private static boolean isCurrentTitleAccount(Minecraft minecraft) {
        User current = minecraft.getUser();
        if (titlePreviewAccount != null) {
            return current != null && titlePreviewAccount.uuid().equals(current.getProfileId());
        }
        LoginData fallback = titleFallbackSession;
        return fallback == null || current != null && fallback.uuid().equals(current.getProfileId());
    }

    private static void addPausePreview(Minecraft minecraft, Screen screen, Consumer<AbstractWidget> widgetAdder) {
        if (screen.width < 300 || screen.height < 180) return;
        boolean compact = screen.width < 600 || screen.height < 240;
        int previewWidth = compact ? 48 : 85;
        int previewHeight = compact ? 76 : 120;
        int x = compact ? 8 : 58;
        int y = screen.height / 2 - previewHeight / 2;
        CapeSkinWidget skin = new CapeSkinWidget(previewWidth, previewHeight, () -> {
            User user = minecraft.getUser();
            return user == null ? DefaultPlayerSkin.get(IStonecutter.NIL_UUID) : SkinCache.skin(minecraft, user.getProfileId(), user.getName());
        }, previewPose(screen, CapeSkinWidget.PreviewPose.FRONT));
        skin.setPosition(x, y);
        widgetAdder.accept(skin);
        pauseNameCenterX = x + previewWidth / 2;
        pauseNameY = y - 12;
        widgetAdder.accept(Button.builder(Component.translatable("ias.capes"), btn -> {
                    //$ set_screen minecraft 'new CapeScreen(screen)'
                    minecraft.gui.setScreen(new CapeScreen(screen));
                })
                .bounds(x, y + previewHeight + 4, previewWidth, 20)
                .build());
    }

    private static CapeSkinWidget.PreviewState previewPose(Screen screen, CapeSkinWidget.PreviewPose defaultPose) {
        return PREVIEW_POSES.computeIfAbsent(screen, ignored -> new CapeSkinWidget.PreviewState(defaultPose));
    }

    public static void setMultiplayerPreviewLayout(int centerX, int y, boolean visible) {
        multiplayerNameCenterX = centerX;
        multiplayerNameY = y;
        multiplayerPreviewVisible = visible;
    }

    /**
     * Called on title screen drawing.
     *
     * @param screen   Target screen
     * @param font     Screen font
     * @param graphics Drawing graphics
     */
    @SuppressWarnings("ChainOfInstanceofChecks") // <- Abstraction for Minecraft is not possible.
    //? if >=26.1 {
    public static void onDraw(Screen screen, Font font, GuiGraphicsExtractor graphics) {
    //?} else
    /*public static void onDraw(Screen screen, Font font, GuiGraphics graphics) {*/
        if (screen instanceof TitleScreen && screen.width >= 300 && screen.height >= 180) {
            alignTitleActions();
        }
        if (IASConfig.titleText && screen instanceof TitleScreen && screen.width >= 300 && screen.height >= 180) {
            User user = Minecraft.getInstance().getUser();
            LoginData fallback = titleFallbackSession;
            Component name = Component.literal(titlePreviewAccount != null ? titlePreviewAccount.name()
                    : fallback != null ? fallback.name() : user != null ? user.getName() : "Player");
            int nameWidth = font.width(name);
            //? if >=26.1 {
            graphics.textWithBackdrop(font, name, titleNameCenterX - nameWidth / 2, titleNameY, nameWidth, 0xFFFFFFFF);
            //?} else
            /*graphics.drawString(font, name, titleNameCenterX - nameWidth / 2, titleNameY, 0xFFFFFFFF);*/
        }
        if (screen instanceof PauseScreen && screen.width >= 300 && screen.height >= 180) {
            User user = Minecraft.getInstance().getUser();
            Component name = Component.literal(user != null ? user.getName() : "Player");
            int nameWidth = font.width(name);
            graphics.textWithBackdrop(font, name, pauseNameCenterX - nameWidth / 2, pauseNameY, nameWidth, 0xFFFFFFFF);
        }
        if (screen instanceof JoinMultiplayerScreen && multiplayerPreviewVisible) {
            User user = Minecraft.getInstance().getUser();
            Component name = user == null ? Component.empty() : Component.literal(user.getName());
            int nameWidth = font.width(name);
            //? if >=26.1 {
            graphics.textWithBackdrop(font, name, multiplayerNameCenterX - nameWidth / 2,
                    multiplayerNameY, nameWidth, 0xFFFFFFFF);
            //?} else
            /*graphics.drawString(font, name, multiplayerNameCenterX - nameWidth / 2, multiplayerNameY, 0xFFFFFFFF);*/
        }
    }

    /**
     * Logins into the minecraft.
     * Can be called from any thread.
     *
     * @param minecraft Minecraft instance
     * @param data      Login data
     * @return Future for logging in
     */
    public static CompletableFuture<Void> account(Minecraft minecraft, LoginData data) {
        // Check if not in-game.
        LOGGER.info("IAS: Received login request: {}", data);
        if (minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null ||
                minecraft.getCameraEntity() != null || minecraft.gameMode != null || minecraft.getSingleplayerServer() != null) {
            return CompletableFuture.failedFuture(new FriendlyException("Changing accounts in world.", "ias.error.world"));
        }

        // Create everything async, because it lags.
        return CompletableFuture.runAsync(() -> {
            // Create the user.
            LOGGER.info("IAS: Creating user...");
            // I have no idea what are the OPTIONAL fields and the game
            // works FINE without them, even with chat reporting and parental control, etc.
            // etc., it may be some telemetry, it may be something else. If something is broken by this
            // feel free to submit an issue, if someone knows what this is, feel free to PR a fix,
            // I'm too lazy to fix anything related to telemetry or chat signatures/reports.
            boolean online = data.online();
            //? if >=1.21.10 {
            User user = new User(data.name(), data.uuid(), data.token(), Optional.empty(), Optional.empty());
            //?} else
            /*User user = new User(data.name(), data.uuid(), data.token(), Optional.empty(), Optional.empty(), online ? User.Type.MSA : User.Type.LEGACY);*/

            // Create various services.
            //? if >=1.21.10 {
            YggdrasilAuthenticationService service = online ? new YggdrasilAuthenticationService(minecraft.getProxy()) : YggdrasilAuthenticationService.createOffline(minecraft.getProxy());
            Services services = Services.create(service, minecraft.gameDirectory);
            CompletableFuture<ProfileResult> profile = CompletableFuture.completedFuture(online ? services.sessionService().fetchProfile(data.uuid(), true) : null);
            //?} else {
            /*YggdrasilAuthenticationService service = new YggdrasilAuthenticationService(minecraft.getProxy());
            CompletableFuture<ProfileResult> profile = CompletableFuture.completedFuture(online ? minecraft.getMinecraftSessionService().fetchProfile(data.uuid(), true) : null);*/
            //?}
            @SuppressWarnings("CastToIncompatibleInterface") // <- Mixin Accessor.
            MinecraftAccessor accessor = (MinecraftAccessor) minecraft;
            final GameConfig originalConfig = ((MinecraftExtension) minecraft).ias_gameConfig();
            //? if >=1.21.10 {
            final GameConfig config = new GameConfig(new GameConfig.UserData(user, minecraft.getProxy()), originalConfig.display, originalConfig.location, originalConfig.game, originalConfig.quickPlay);
            //?} else {
            /*final GameConfig config = new GameConfig(new GameConfig.UserData(user, originalConfig.user.userProperties, originalConfig.user.profileProperties, minecraft.getProxy()), originalConfig.display, originalConfig.location, originalConfig.game, originalConfig.quickPlay);
            *///?}
            //? if >=26.2 {
            UserApiService apiService = online ? MinecraftAccessor.ias$createUserApiService(service, config) : UserApiService.OFFLINE;
            //?} else {
            /*UserApiService apiService = online ? accessor.ias$createUserApiService(service, config) : UserApiService.OFFLINE;
            *///?}
            UserApiService.UserProperties properties;
            try {
                properties = apiService.fetchProperties();
            } catch (Throwable ignored) {
                properties = UserApiService.OFFLINE_PROPERTIES;
            }
            CompletableFuture<UserApiService.UserProperties> propertiesFuture = CompletableFuture.completedFuture(properties);
            //? if >= 26.2 {
            FriendsService friends = service.createFriendsService(data.token());
            RemoteFriendListUpdateHandler friendList = new RemoteFriendListUpdateHandler(friends, minecraft);
            PlayerSocialManager social = new PlayerSocialManager(minecraft, apiService, friends, friendList);
            //?} else {
            /*PlayerSocialManager social = new PlayerSocialManager(minecraft, apiService);
            *///?}
            ClientTelemetryManager telemetry = new ClientTelemetryManager(minecraft, apiService, user);
            ProfileKeyPairManager keyPair = ProfileKeyPairManager.create(apiService, user, minecraft.gameDirectory.toPath());
            ReportingContext reporting = ReportingContext.create(ReportEnvironment.local(), apiService);

            // Schedule to the main thread
            minecraft.execute(() -> {
                // Flush everything.
                LOGGER.info("IAS: Flushing user...");
                //? if >=1.21.10 {
                accessor.ias$services(services);
                //?}
                accessor.ias$user(user);
                accessor.ias$profileFuture(profile);
                accessor.ias$userApiService(apiService);
                accessor.ias$userPropertiesFuture(propertiesFuture);
                accessor.ias$playerSocialManager(social);
                //? if >=26.2 {
                accessor.ias$remoteFriendListUpdateHandler().close();
                accessor.ias$remoteFriendListUpdateHandler(friendList);
                if (social.isFriendListEnabled()) {
                    friendList.start();
                }
                //?}
                accessor.ias$telemetryManager(telemetry);
                accessor.ias$profileKeyPairManager(keyPair);
                accessor.ias$reportingContext(reporting);
                profile.thenAccept(result -> {
                    if (result != null) SkinCache.refresh(minecraft, result.profile());
                });
                minecraft.updateTitle();
                LOGGER.info("IAS: Flushed user.");
            });
        }, IAS.executor()).exceptionally(t -> {
            // Log it.
            LOGGER.error("IAS: Unable to log in: {}.", data, t);

            // Rethrow.
            throw new RuntimeException("Unable to change account to: " + data, t);
        });
    }
}
