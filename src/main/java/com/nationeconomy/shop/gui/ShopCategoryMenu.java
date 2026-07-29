package com.nationeconomy.shop.gui;

import com.nationeconomy.combat.CombatManager;
import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.shop.SellLogic;
import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopItem;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Paginated view of a single shop category.
 *
 * <p>9x6 chest GUI: the top 45 slots contain the items (hovering shows the
 * buy and sell price), the bottom row is navigation. Click actions:
 * <ul>
 *     <li>Left-click: buy 1</li>
 *     <li>Shift + left-click: buy a full stack</li>
 *     <li>Right-click: sell 1 (from your inventory)</li>
 *     <li>Shift + right-click: sell all of that item</li>
 * </ul>
 *
 * <p>Anti-dupe measures: all clicks are cancelled (no item can leave or
 * enter the menu), prices/balances are always read from the live
 * {@link ShopManager}/{@link EconomyManager} state at click time, and
 * purchases are atomic (balance check + withdraw, then item creation from
 * scratch — never cloned from the menu display).
 */
public class ShopCategoryMenu extends ChestMenu {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9; // 54
    public static final int CONTENT_SLOTS = 45;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;

    /** Currently open category menus. */
    private static final Set<ShopCategoryMenu> OPEN_MENUS = new LinkedHashSet<>();

    /** Re-renders every open category menu. */
    public static void refreshOpenMenus() {
        for (ShopCategoryMenu menu : OPEN_MENUS) {
            menu.refresh();
        }
    }

    private final SimpleContainer inventory;
    private final String categoryId;
    private final List<ShopItem> items = new ArrayList<>();
    private int page;

    public static void open(ServerPlayer player, String categoryId, int page) {
        ShopCategory category = ShopManager.get().category(categoryId);
        if (category == null) {
            player.sendSystemMessage(Component.literal("That shop category no longer exists.").withStyle(ChatFormatting.RED), false);
            ShopMainMenu.open(player);
            return;
        }
        Component title = ColorUtils.legacy(category.getName());
        SimpleContainer inventory = new SimpleContainer(SIZE);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new ShopCategoryMenu(syncId, playerInventory, inventory, categoryId, page),
                title));
    }

    private ShopCategoryMenu(int syncId, Inventory playerInventory, SimpleContainer inventory,
                             String categoryId, int page) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.categoryId = categoryId;
        this.page = Math.max(0, page);
        OPEN_MENUS.add(this);
        refresh();
    }

    @Override
    public void removed(Player player) {
        OPEN_MENUS.remove(this);
        super.removed(player);
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) CONTENT_SLOTS));
    }

    /** (Re)builds the current page. */
    private void refresh() {
        ShopCategory category = ShopManager.get().category(categoryId);
        items.clear();
        if (category != null) {
            items.addAll(category.getItems().values());
        }
        if (page >= pageCount()) {
            page = pageCount() - 1;
        }

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        // Content
        int start = page * CONTENT_SLOTS;
        for (int slot = 0; slot < CONTENT_SLOTS && start + slot < items.size(); slot++) {
            inventory.setItem(slot, displayStack(items.get(start + slot)));
        }

        // Bottom navigation row
        for (int slot = CONTENT_SLOTS; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }

        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Component.literal("« Back to the Shop").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(SLOT_BACK, back);

        ItemStack previous = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(previous, Component.literal("« Previous Page").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(previous, List.of(Component.literal("Page " + (page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_PREVIOUS, previous);

        ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(next, Component.literal("Next Page »").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(next, List.of(Component.literal("Page " + (page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_NEXT, next);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Component.literal("Close").withStyle(ChatFormatting.RED));
        inventory.setItem(SLOT_CLOSE, close);
    }

    /** The item display with buy/sell prices in the hover tooltip. */
    private static ItemStack displayStack(ShopItem shopItem) {
        Item item = ShopManager.itemOf(shopItem.getItem());
        ItemStack stack = new ItemStack(item);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(" "));
        if (shopItem.isBuyable()) {
            lore.add(Component.literal("Buy: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyUtil.format(shopItem.getBuy())).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" each").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            lore.add(Component.literal("Buy: —").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (shopItem.isSellable()) {
            lore.add(Component.literal("Sell: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyUtil.format(shopItem.getSell())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" each").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            lore.add(Component.literal("Sell: —").withStyle(ChatFormatting.DARK_GRAY));
        }
        lore.add(Component.literal(" "));
        if (shopItem.isBuyable()) {
            lore.add(Component.literal("◂ Left-click: buy 1").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("◂ Shift-left-click: buy a stack").withStyle(ChatFormatting.GRAY));
        }
        if (shopItem.isSellable()) {
            lore.add(Component.literal("▸ Right-click: sell 1").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("▸ Shift-right-click: sell all").withStyle(ChatFormatting.GRAY));
        }
        GuiElements.lore(stack, lore);
        return stack;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        // All clicks are cancelled; they only trigger buy/sell/navigation.
        if (!(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }

        switch (slotIndex) {
            case SLOT_BACK -> {
                ShopMainMenu.open(serverPlayer);
                return;
            }
            case SLOT_PREVIOUS -> {
                if (page > 0) {
                    page--;
                    refresh();
                    clickSound(serverPlayer);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (page < pageCount() - 1) {
                    page++;
                    refresh();
                    clickSound(serverPlayer);
                }
                return;
            }
            case SLOT_CLOSE -> {
                serverPlayer.closeContainer();
                return;
            }
            default -> {
            }
        }

        if (slotIndex >= CONTENT_SLOTS) {
            return;
        }
        int index = page * CONTENT_SLOTS + slotIndex;
        if (index < 0 || index >= items.size()) {
            return;
        }
        ShopItem shopItem = items.get(index);
        boolean shift = actionType == ClickType.QUICK_MOVE;
        boolean rightClick = button == 1;

        if (!rightClick) {
            // Buy — a fresh ShopItem is resolved from the live manager data,
            // and combat-tagged players cannot buy.
            int amount = shift ? Math.max(1, new ItemStack(ShopManager.itemOf(shopItem.getItem())).getMaxStackSize()) : 1;
            buy(serverPlayer, shopItem, amount);
        } else {
            // Sell
            int amount = shift ? Integer.MAX_VALUE : 1;
            int sold = SellLogic.sell(serverPlayer, shopItem.getItem(), amount);
            if (sold == 0) {
                error(serverPlayer, shopItem.isSellable()
                        ? "You don't have any " + ShopManager.itemOf(shopItem.getItem()).getDescription().getString() + " to sell."
                        : "This item cannot be sold.");
            }
        }
    }

    private void buy(ServerPlayer player, ShopItem shopItem, int amount) {
        Item item = ShopManager.itemOf(shopItem.getItem());
        if (CombatManager.denyIfTagged(player, "buy from the shop")) {
            return;
        }
        if (!shopItem.isBuyable()) {
            error(player, "This item cannot be bought.");
            return;
        }
        double cost = shopItem.getBuy() * amount;
        EconomyManager economy = EconomyManager.get();
        if (!economy.has(player.getUUID(), cost)) {
            error(player, "You need " + MoneyUtil.format(cost) + " but only have "
                    + MoneyUtil.format(economy.balance(player.getUUID())) + ".");
            return;
        }
        economy.withdraw(player.getUUID(), cost);

        // Items are created from scratch — never taken from the menu display.
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, new ItemStack(item).getMaxStackSize());
            player.getInventory().placeItemBackInInventory(new ItemStack(item, stackSize));
            remaining -= stackSize;
        }
        player.sendSystemMessage(Component.literal("Bought ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(amount + "x ").withStyle(ChatFormatting.AQUA))
                .append(item.getDescription().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(MoneyUtil.format(cost)).withStyle(ChatFormatting.GOLD)), false);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private static void clickSound(ServerPlayer player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
    }

    private static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
        player.playSound(SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
