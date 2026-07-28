package com.nationeconomy.shop.gui;

import com.nationeconomy.combat.CombatManager;
import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.shop.SellLogic;
import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopItem;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
public class ShopCategoryMenu extends GenericContainerScreenHandler {

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

    private final SimpleInventory inventory;
    private final String categoryId;
    private final List<ShopItem> items = new ArrayList<>();
    private int page;

    public static void open(ServerPlayerEntity player, String categoryId, int page) {
        ShopCategory category = ShopManager.get().category(categoryId);
        if (category == null) {
            player.sendMessage(Text.literal("That shop category no longer exists.").formatted(Formatting.RED), false);
            ShopMainMenu.open(player);
            return;
        }
        Text title = ColorUtils.legacy(category.getName());
        SimpleInventory inventory = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new ShopCategoryMenu(syncId, playerInventory, inventory, categoryId, page),
                title));
    }

    private ShopCategoryMenu(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                             String categoryId, int page) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.categoryId = categoryId;
        this.page = Math.max(0, page);
        OPEN_MENUS.add(this);
        refresh();
    }

    @Override
    public void onClosed(PlayerEntity player) {
        OPEN_MENUS.remove(this);
        super.onClosed(player);
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
            inventory.setStack(slot, ItemStack.EMPTY);
        }

        // Content
        int start = page * CONTENT_SLOTS;
        for (int slot = 0; slot < CONTENT_SLOTS && start + slot < items.size(); slot++) {
            inventory.setStack(slot, displayStack(items.get(start + slot)));
        }

        // Bottom navigation row
        for (int slot = CONTENT_SLOTS; slot < SIZE; slot++) {
            inventory.setStack(slot, GuiElements.filler());
        }

        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Text.literal("« Back to the Shop").formatted(Formatting.YELLOW));
        inventory.setStack(SLOT_BACK, back);

        ItemStack previous = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(previous, Text.literal("« Previous Page").formatted(Formatting.AQUA));
        GuiElements.lore(previous, List.of(Text.literal("Page " + (page + 1) + " / " + pageCount()).formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_PREVIOUS, previous);

        ItemStack next = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(next, Text.literal("Next Page »").formatted(Formatting.AQUA));
        GuiElements.lore(next, List.of(Text.literal("Page " + (page + 1) + " / " + pageCount()).formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_NEXT, next);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(SLOT_CLOSE, close);
    }

    /** The item display with buy/sell prices in the hover tooltip. */
    private static ItemStack displayStack(ShopItem shopItem) {
        Item item = ShopManager.itemOf(shopItem.getItem());
        ItemStack stack = new ItemStack(item);

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal(" "));
        if (shopItem.isBuyable()) {
            lore.add(Text.literal("Buy: ").formatted(Formatting.GRAY)
                    .append(Text.literal(MoneyUtil.format(shopItem.getBuy())).formatted(Formatting.GREEN))
                    .append(Text.literal(" each").formatted(Formatting.DARK_GRAY)));
        } else {
            lore.add(Text.literal("Buy: —").formatted(Formatting.DARK_GRAY));
        }
        if (shopItem.isSellable()) {
            lore.add(Text.literal("Sell: ").formatted(Formatting.GRAY)
                    .append(Text.literal(MoneyUtil.format(shopItem.getSell())).formatted(Formatting.GOLD))
                    .append(Text.literal(" each").formatted(Formatting.DARK_GRAY)));
        } else {
            lore.add(Text.literal("Sell: —").formatted(Formatting.DARK_GRAY));
        }
        lore.add(Text.literal(" "));
        if (shopItem.isBuyable()) {
            lore.add(Text.literal("◂ Left-click: buy 1").formatted(Formatting.GRAY));
            lore.add(Text.literal("◂ Shift-left-click: buy a stack").formatted(Formatting.GRAY));
        }
        if (shopItem.isSellable()) {
            lore.add(Text.literal("▸ Right-click: sell 1").formatted(Formatting.GRAY));
            lore.add(Text.literal("▸ Shift-right-click: sell all").formatted(Formatting.GRAY));
        }
        GuiElements.lore(stack, lore);
        return stack;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // All clicks are cancelled; they only trigger buy/sell/navigation.
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
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
                serverPlayer.closeScreenHandler();
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
        boolean shift = actionType == SlotActionType.QUICK_MOVE;
        boolean rightClick = button == 1;

        if (!rightClick) {
            // Buy — a fresh ShopItem is resolved from the live manager data,
            // and combat-tagged players cannot buy.
            int amount = shift ? Math.max(1, new ItemStack(ShopManager.itemOf(shopItem.getItem())).getMaxCount()) : 1;
            buy(serverPlayer, shopItem, amount);
        } else {
            // Sell
            int amount = shift ? Integer.MAX_VALUE : 1;
            int sold = SellLogic.sell(serverPlayer, shopItem.getItem(), amount);
            if (sold == 0) {
                error(serverPlayer, shopItem.isSellable()
                        ? "You don't have any " + ShopManager.itemOf(shopItem.getItem()).getName().getString() + " to sell."
                        : "This item cannot be sold.");
            }
        }
    }

    private void buy(ServerPlayerEntity player, ShopItem shopItem, int amount) {
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
        if (!economy.has(player.getUuid(), cost)) {
            error(player, "You need " + MoneyUtil.format(cost) + " but only have "
                    + MoneyUtil.format(economy.balance(player.getUuid())) + ".");
            return;
        }
        economy.withdraw(player.getUuid(), cost);

        // Items are created from scratch — never taken from the menu display.
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, new ItemStack(item).getMaxCount());
            player.getInventory().offerOrDrop(new ItemStack(item, stackSize));
            remaining -= stackSize;
        }
        player.sendMessage(Text.literal("Bought ").formatted(Formatting.GRAY)
                .append(Text.literal(amount + "x ").formatted(Formatting.AQUA))
                .append(item.getName().copy().formatted(Formatting.AQUA))
                .append(Text.literal(" for ").formatted(Formatting.GRAY))
                .append(Text.literal(MoneyUtil.format(cost)).formatted(Formatting.GOLD)), false);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
    }

    private static void clickSound(ServerPlayerEntity player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
    }

    private static void error(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
        player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
