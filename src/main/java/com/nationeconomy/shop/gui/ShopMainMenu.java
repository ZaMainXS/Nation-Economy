package com.nationeconomy.shop.gui;

import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.shop.SellLogic;
import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The main shop menu ({@code /shop}).
 *
 * <p>The window is a 9x4 chest GUI: slots 0-29 are the category slots that
 * server owners can freely assign ({@code /shopadmin} GUI or
 * {@code /economycategory create}), the remaining 6 slots are utility
 * buttons.
 *
 * <p>Security: every click is cancelled — nothing can be dragged out,
 * shift-clicked, dropped (Q), number-key-swapped or pick-blocked.
 * Prices come from {@link ShopManager} at click time, never from the item
 * stacks in the menu, so a tampered/ghost client display can never affect
 * the server-side trade.
 */
public class ShopMainMenu extends ChestMenu {

    public static final int ROWS = 4;
    public static final int SIZE = ROWS * 9; // 36 slots, 30 of them are category slots

    private static final int SLOT_BALANCE = 31;
    private static final int SLOT_SELL_ALL = 33;
    private static final int SLOT_CLOSE = 35;

    private static final Component TITLE = ColorUtils.legacy("&6&l✦ Shop ✦");

    /** Currently open menus — refreshed when the shop data changes. */
    private static final Set<ShopMainMenu> OPEN_MENUS = new LinkedHashSet<>();

    /** Re-renders every open main menu (used by /sreload and the admin GUI). */
    public static void refreshOpenMenus() {
        for (ShopMainMenu menu : OPEN_MENUS) {
            menu.refresh();
        }
    }

    private final SimpleContainer inventory;
    private final Inventory playerInventory;
    private final Map<Integer, ShopCategory> categoriesBySlot = new HashMap<>();

    public static void open(ServerPlayer player) {
        SimpleContainer inventory = new SimpleContainer(SIZE);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new ShopMainMenu(syncId, playerInventory, inventory),
                TITLE));
    }

    private ShopMainMenu(int syncId, Inventory playerInventory, SimpleContainer inventory) {
        super(MenuType.GENERIC_9x4, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.playerInventory = playerInventory;
        OPEN_MENUS.add(this);
        refresh();
    }

    @Override
    public void removed(Player player) {
        OPEN_MENUS.remove(this);
        super.removed(player);
    }

    private ServerPlayer viewer() {
        return this.playerInventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    /** (Re)builds the whole menu content. */
    private void refresh() {
        categoriesBySlot.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }

        // Category icons
        for (ShopCategory category : ShopManager.get().categories()) {
            int slot = category.getSlot();
            if (slot < 0 || slot >= ShopManager.CATEGORY_SLOTS) {
                continue;
            }
            ItemStack icon = new ItemStack(ShopManager.itemOf(category.getIcon()));
            GuiElements.name(icon, ColorUtils.legacy(category.getName()));
            GuiElements.lore(icon, List.of(
                    Component.literal("Click to browse").withStyle(ChatFormatting.GRAY),
                    Component.literal(category.getItems().size() + " items for sale").withStyle(ChatFormatting.DARK_GRAY)));
            inventory.setItem(slot, icon);
            categoriesBySlot.put(slot, category);
        }

        // Balance display
        ServerPlayer viewer = viewer();
        ItemStack balance = new ItemStack(Items.SUNFLOWER);
        GuiElements.name(balance, Component.literal("Your Balance").withStyle(ChatFormatting.GOLD));
        GuiElements.lore(balance, List.of(
                Component.literal(MoneyUtil.format(viewer == null ? 0 : EconomyManager.get().balance(viewer.getUUID())))
                        .withStyle(ChatFormatting.YELLOW),
                Component.literal("Earn money by selling items").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("with /sellall or in the shop.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_BALANCE, balance);

        // Sell-all shortcut
        ItemStack sellAll = new ItemStack(Items.HOPPER);
        GuiElements.name(sellAll, Component.literal("Sell All Items").withStyle(ChatFormatting.YELLOW));
        GuiElements.lore(sellAll, List.of(
                Component.literal("Sells every sellable item").withStyle(ChatFormatting.GRAY),
                Component.literal("in your inventory.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_SELL_ALL, sellAll);

        // Close button
        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Component.literal("Close").withStyle(ChatFormatting.RED));
        inventory.setItem(SLOT_CLOSE, close);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        // Everything is cancelled on purpose: nothing here may be taken out,
        // moved around, dropped or cloned — clicks only trigger actions.
        if (!(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }

        ShopCategory category = categoriesBySlot.get(slotIndex);
        if (category != null) {
            serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            ShopCategoryMenu.open(serverPlayer, category.getId(), 0);
            return;
        }

        switch (slotIndex) {
            case SLOT_BALANCE -> refresh(); // updates the shown balance
            case SLOT_SELL_ALL -> {
                SellLogic.sellAll(serverPlayer);
                refresh();
            }
            case SLOT_CLOSE -> serverPlayer.closeContainer();
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY; // shift-clicking does nothing
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
