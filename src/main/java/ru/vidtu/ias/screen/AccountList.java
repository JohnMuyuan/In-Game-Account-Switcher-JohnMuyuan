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
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vidtu.ias.IAS;
import ru.vidtu.ias.account.Account;
import ru.vidtu.ias.config.IASStorage;
import ru.vidtu.ias.utils.SkinCache;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else
/*import net.minecraft.client.resources.PlayerSkin;*/

/**
 * Account GUI list.
 *
 * @author VidTu
 */
final class AccountList extends ObjectSelectionList<AccountEntry> {
    /**
     * Logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("IAS/AccountList");

    /**
     * Parent screen.
     */
    private final AccountScreen screen;

    /**
     * Creates a new accounts list widget.
     *
     * @param minecraft Minecraft instance
     * @param width     List width
     * @param height    List height
     * @param x         List X offset
     * @param y         List Y offset
     * @param item      Entry height
     */
    AccountList(AccountScreen screen, Minecraft minecraft, int width, int height, int x, int y, int item) {
        super(minecraft, width, height, y, item);
        this.screen = screen;
        this.setX(x);
        this.update(this.screen.search().getValue());
    }

    @Override
    public int getRowWidth() {
        return Math.min(super.getRowWidth(), Math.max(0, this.getWidth() - 20));
    }

    @Override
    public void setSelected(@Nullable AccountEntry entry) {
        // Select.
        super.setSelected(entry);

        // Notify parent.
        this.screen.updateSelected();
    }

    /**
     * Update the list by query.
     *
     * @param query Search query
     */
    void update(String query) {
        // Add all if blank.
        if (query == null || query.isBlank()) {
            // Add every account.
            AccountEntry selectedEntry = this.getSelected();
            UUID selected = selectedEntry == null ? null : selectedEntry.account().uuid();
            this.replaceEntries(IASStorage.ACCOUNTS.stream()
                    .map(account -> new AccountEntry(this.minecraft, this, account))
                    .toList());
            this.setSelected(this.children().stream()
                    .filter(entry -> selected != null && selected.equals(entry.account().uuid()))
                    .findFirst().orElse(null));

            // Notify the root.
            this.screen.updateSelected();

            // Don't process search.
            return;
        }

        // Lowercase query.
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        // Add every account.
        AccountEntry selectedEntry = this.getSelected();
        UUID selected = selectedEntry == null ? null : selectedEntry.account().uuid();
        this.replaceEntries(IASStorage.ACCOUNTS.stream()
                .filter(account -> account.name().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .sorted((f, s) -> Boolean.compare(
                        s.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery),
                        f.name().toLowerCase(Locale.ROOT).startsWith(lowerQuery)
                ))
                .map(account -> new AccountEntry(this.minecraft, this, account))
                .toList());
        this.setSelected(this.children().stream()
                .filter(entry -> selected != null && selected.equals(entry.account().uuid()))
                .findFirst().orElse(null));

        // Notify the root.
        this.screen.updateSelected();
    }

    /**
     * Log in to this account.
     *
     * @param online Whether to try using online authentication
     * @apiNote The {@code online} parameter may be ignored if the current account doesn't support online authentication
     */
    void login(boolean online, Runnable onComplete) {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        AccountLogin.login(this.minecraft, this.screen, account, online, onComplete);
    }

    void edit() {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        int index = this.children().indexOf(selected);
        if (index < 0 || index >= IASStorage.ACCOUNTS.size()) return;

        // Replace in storage.
        final Screen add = new AddPopupScreen(this.screen, true, account -> {
            //$ set_screen 'this.minecraft' 'this.screen'
            this.minecraft.gui.setScreen(this.screen);

            // Add the account and save it.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            if (index >= IASStorage.ACCOUNTS.size()) {
                IASStorage.ACCOUNTS.add(account);
            } else {
                IASStorage.ACCOUNTS.set(index, account);
            }

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update the list.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' add
        this.minecraft.gui.setScreen(add);
    }

    /**
     * Deletes the selected account.
     * Does nothing if nothing is selected.
     *
     * @param confirm Whether to show the confirmation
     */
    void delete(boolean confirm) {
        // Skip if nothing is selected.
        AccountEntry selected = this.getSelected();
        if (selected == null) return;
        Account account = selected.account();

        // Skip confirmation if shift is pressed.
        if (!confirm) {
            // Remove.
            IASStorage.ACCOUNTS.remove(account);

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update.
            this.update(this.screen.search().getValue());
            return;
        }

        // Display confirmation screen.
        final Screen delete = new DeletePopupScreen(this.screen, account, () -> {
            // Delete if confirmed.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' delete
        this.minecraft.gui.setScreen(delete);
    }

    /**
     * Opens the account adding screen.
     */
    void add() {
        final Screen add = new AddPopupScreen(this.screen, false, account -> {
            // Set to this.
            //$ set_screen 'this.minecraft' 'this.screen'
            this.minecraft.gui.setScreen(this.screen);

            // Add the account.
            IASStorage.ACCOUNTS.removeIf(Predicate.isEqual(account));
            IASStorage.ACCOUNTS.add(account);

            // Save storage.
            try {
                IAS.disclaimersStorage();
                IAS.saveStorage();
            } catch (Throwable t) {
                LOGGER.error("IAS: Unable to save storage.", t);
            }

            // Update the list.
            this.update(this.screen.search().getValue());
        });
        //$ set_screen 'this.minecraft' 'add'
        this.minecraft.gui.setScreen(add);
    }

    /**
     * Gets the skin for the account entry.
     *
     * @param entry Target account entry
     * @return Player skin, fetched or default
     */
    PlayerSkin skin(AccountEntry entry) {
        Account account = entry.account();
        UUID uuid = account.skin();
        return SkinCache.skin(this.minecraft, uuid, account.name());
    }

    /**
     * Swaps the entry with the account above, if possible.
     *
     * @param entry Target entry
     */
    void swapUp(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int upIdx = idx - 1;
        if (upIdx < 0) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(upIdx));
        IASStorage.ACCOUNTS.set(upIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        //? if <1.21.10 {
        /*this.children().set(idx, this.children().get(upIdx));
        this.children().set(upIdx, entry);
        this.setSelected(entry);
        *///?} else
        this.swap(idx, upIdx);
    }

    /**
     * Swaps the entry with the account below, if possible.
     *
     * @param entry Target entry
     */
    void swapDown(AccountEntry entry) {
        // Get and validate indexes.
        int idx = this.children().indexOf(entry);
        if (idx < 0 || idx >= IASStorage.ACCOUNTS.size()) return;
        int downIdx = idx + 1;
        if (downIdx >= this.children().size() || downIdx >= IASStorage.ACCOUNTS.size()) return;

        // Move storage.
        IASStorage.ACCOUNTS.set(idx, IASStorage.ACCOUNTS.get(downIdx));
        IASStorage.ACCOUNTS.set(downIdx, entry.account());

        // Save storage.
        try {
            IAS.disclaimersStorage();
            IAS.saveStorage();
        } catch (Throwable t) {
            LOGGER.error("IAS: Unable to save storage.", t);
        }

        // Move elements.
        //? if <1.21.10 {
        /*this.children().set(idx, this.children().get(downIdx));
        this.children().set(downIdx, entry);
        this.setSelected(entry);
        *///?} else
        this.swap(idx, downIdx);
    }

    /**
     * Gets the screen.
     *
     * @return Parent accounts screen
     */
    AccountScreen screen() {
        return this.screen;
    }

    @Override
    public String toString() {
        return "AccountList{" +
                "children=" + this.children() +
                '}';
    }
}
