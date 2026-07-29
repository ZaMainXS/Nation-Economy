package com.nationeconomy.shop.admin;

import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopItem;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.shop.gui.GuiElements;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin editor for one shop item: exact buy/sell prices, buyable/sellable
 * toggles, position ("slot") inside the category grid, or removal.
 */
public class ItemAdminGui extends ChestMenu {

    public static final int ROWS = 3;
    public static final int SIZE = ROWS * 9; // 27

    private static final int SLOT_BACK = 9;
    private static final int SLOT_BUY_ADJUST = 10;
    private static final int SLOT_BUY_EXACT = 11;
    private static final int SLOT_BUY_TOGGLE = 12;
    private static final int SLOT_DISPLAY = 13;
    private static final int SLOT_SELL_TOGGLE = 14;
    private static final int SLOT_SELL_EXACT = 15;
    private static final int SLOT_SELL_ADJUST = 16;
    private static final int SLOT_MOVE_UP = 20;
    private static final int SLOT_REMOVE = 22;
    private static final int SLOT_MOVE_DOWN = 24;
    private static final int SLOT_CLOSE = 26;

    private final SimpleContainer inventory;
    private final String categoryId;
    private final String itemId;

    public static void open(ServerPlayer player, String categoryId, String itemId) {
        ShopCategory category = ShopManager.get().category(categoryId);
        ShopItem shopItem = category == null ? null : category.getItems().get(itemId);
        if (shopItem == null) {
            player.sendSystemMessage(Component.literal("That item no longer exists.").withStyle(ChatFormatting.RED), false);
            CategoryAdminGui.open(player, categoryId);
            return;
        }
        Component title = ColorUtils.legacy("&8Edit Item");
        SimpleContainer inventory = new SimpleContainer(SIZE);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new ItemAdminGui(syncId, playerInventory, inventory, categoryId, itemId),
                title));
    }

    private ItemAdminGui(int syncId, Inventory playerInventory, SimpleContainer inventory,
                         String categoryId, String itemId) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.categoryId = categoryId;
        this.itemId = itemId;
        refresh();
    }

    private void refresh() {
        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        ShopItem shopItem = category == null ? null : category.getItems().get(itemId);

        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }
        if (shopItem == null) {
            return;
        }
        int position = new java.util.ArrayList<>(category.getItems().keySet()).indexOf(itemId);

        ItemStack display = new ItemStack(ShopManager.itemOf(itemId));
        GuiElements.name(display, Component.translatable(ShopManager.itemOf(itemId).getDescriptionId())
                .withStyle(ChatFormatting.YELLOW));
        GuiElements.lore(display, List.of(
                Component.literal(itemId).withStyle(ChatFormatting.DARK_GRAY),
                Component.literal("Position in category: " + (position + 1)).withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_DISPLAY, display);

        // Buy price controls
        ItemStack buyAdjust = new ItemStack(Items.GOLD_NUGGET);
        GuiElements.name(buyAdjust, Component.literal("Buy: " + (shopItem.isBuyable()
                ? MoneyUtil.format(shopItem.getBuy()) : "disabled")).withStyle(ChatFormatting.GREEN));
        GuiElements.lore(buyAdjust, List.of(
                Component.literal("◂ Left-click: +1   ▸ Right-click: -1").withStyle(ChatFormatting.YELLOW),
                Component.literal("Shift: ±10").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_BUY_ADJUST, buyAdjust);

        ItemStack buyExact = new ItemStack(Items.OAK_SIGN);
        GuiElements.name(buyExact, Component.literal("Set Exact Buy Price").withStyle(ChatFormatting.GREEN));
        GuiElements.lore(buyExact, List.of(Component.literal("Type the number in the anvil.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_BUY_EXACT, buyExact);

        ItemStack buyToggle = new ItemStack(shopItem.isBuyable() ? Items.LIME_DYE : Items.GRAY_DYE);
        GuiElements.name(buyToggle, Component.literal(shopItem.isBuyable() ? "Buyable: ON" : "Buyable: OFF")
                .withStyle(shopItem.isBuyable() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        GuiElements.lore(buyToggle, List.of(Component.literal("Click to toggle.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_BUY_TOGGLE, buyToggle);

        // Sell price controls
        ItemStack sellAdjust = new ItemStack(Items.IRON_NUGGET);
        GuiElements.name(sellAdjust, Component.literal("Sell: " + (shopItem.isSellable()
                ? MoneyUtil.format(shopItem.getSell()) : "disabled")).withStyle(ChatFormatting.GOLD));
        GuiElements.lore(sellAdjust, List.of(
                Component.literal("◂ Left-click: +1   ▸ Right-click: -1").withStyle(ChatFormatting.YELLOW),
                Component.literal("Shift: ±10").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_SELL_ADJUST, sellAdjust);

        ItemStack sellExact = new ItemStack(Items.SPRUCE_SIGN);
        GuiElements.name(sellExact, Component.literal("Set Exact Sell Price").withStyle(ChatFormatting.GOLD));
        GuiElements.lore(sellExact, List.of(
                Component.literal("Type the number in the anvil.").withStyle(ChatFormatting.GRAY),
                Component.literal("-1 disables selling.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_SELL_EXACT, sellExact);

        ItemStack sellToggle = new ItemStack(shopItem.isSellable() ? Items.LIME_DYE : Items.GRAY_DYE);
        GuiElements.name(sellToggle, Component.literal(shopItem.isSellable() ? "Sellable: ON" : "Sellable: OFF")
                .withStyle(shopItem.isSellable() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        GuiElements.lore(sellToggle, List.of(Component.literal("Click to toggle.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_SELL_TOGGLE, sellToggle);

        // Order inside the category
        ItemStack moveUp = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(moveUp, Component.literal("▲ Move Earlier").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(moveUp, List.of(Component.literal("Moves this item one slot earlier").withStyle(ChatFormatting.GRAY),
                Component.literal("inside the category page.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_MOVE_UP, moveUp);

        ItemStack moveDown = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(moveDown, Component.literal("▼ Move Later").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(moveDown, List.of(Component.literal("Moves this item one slot later").withStyle(ChatFormatting.GRAY),
                Component.literal("inside the category page.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_MOVE_DOWN, moveDown);

        ItemStack remove = new ItemStack(Items.TNT);
        GuiElements.name(remove, Component.literal("Remove Item").withStyle(ChatFormatting.RED));
        GuiElements.lore(remove, List.of(Component.literal("Removes it from the category.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_REMOVE, remove);

        // Navigation
        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Component.literal("« Back to Category").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(SLOT_BACK, back);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Component.literal("Close").withStyle(ChatFormatting.RED));
        inventory.setItem(SLOT_CLOSE, close);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }
        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        ShopItem shopItem = category == null ? null : category.getItems().get(itemId);
        if (shopItem == null) {
            serverPlayer.closeContainer();
            CategoryAdminGui.open(serverPlayer, categoryId);
            return;
        }

        switch (slotIndex) {
            case SLOT_BACK -> CategoryAdminGui.open(serverPlayer, categoryId);
            case SLOT_CLOSE -> serverPlayer.closeContainer();
            case SLOT_BUY_ADJUST -> {
                double delta = actionType == ClickType.QUICK_MOVE ? 10 : 1;
                if (button == 1) {
                    delta = -delta;
                }
                shopItem.setBuy(Math.max(0, shopItem.getBuy() + delta));
                saveAndRefresh(manager);
            }
            case SLOT_SELL_ADJUST -> {
                double delta = actionType == ClickType.QUICK_MOVE ? 10 : 1;
                if (button == 1) {
                    delta = -delta;
                }
                shopItem.setSell(Math.max(-1, shopItem.getSell() + delta));
                saveAndRefresh(manager);
            }
            case SLOT_BUY_EXACT -> {
                clickSound(serverPlayer);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lExact buy price"),
                        String.valueOf(shopItem.getBuy()), text -> {
                            Double value = parsePrice(text);
                            if (value == null) {
                                serverPlayer.sendSystemMessage(Component.literal("'" + text + "' is not a number.")
                                        .withStyle(ChatFormatting.RED), false);
                            } else {
                                shopItem.setBuy(Math.max(0, value));
                                manager.markDirty();
                                manager.save();
                                ShopAdminMainGui.refreshShops();
                            }
                            ItemAdminGui.open(serverPlayer, categoryId, itemId);
                        });
            }
            case SLOT_SELL_EXACT -> {
                clickSound(serverPlayer);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lExact sell price"),
                        String.valueOf(shopItem.getSell()), text -> {
                            Double value = parsePrice(text);
                            if (value == null) {
                                serverPlayer.sendSystemMessage(Component.literal("'" + text + "' is not a number.")
                                        .withStyle(ChatFormatting.RED), false);
                            } else {
                                shopItem.setSell(Math.max(-1, value));
                                manager.markDirty();
                                manager.save();
                                ShopAdminMainGui.refreshShops();
                            }
                            ItemAdminGui.open(serverPlayer, categoryId, itemId);
                        });
            }
            case SLOT_BUY_TOGGLE -> {
                shopItem.setBuy(shopItem.isBuyable() ? -1 : 0);
                saveAndRefresh(manager);
            }
            case SLOT_SELL_TOGGLE -> {
                shopItem.setSell(shopItem.isSellable() ? -1 : 0);
                saveAndRefresh(manager);
            }
            case SLOT_MOVE_UP -> move(category, -1, manager);
            case SLOT_MOVE_DOWN -> move(category, 1, manager);
            case SLOT_REMOVE -> {
                category.getItems().remove(itemId);
                manager.markDirty();
                manager.save();
                ShopAdminMainGui.refreshShops();
                serverPlayer.sendSystemMessage(Component.literal("Removed " + itemId + " from '" + categoryId + "'.")
                        .withStyle(ChatFormatting.GREEN), false);
                CategoryAdminGui.open(serverPlayer, categoryId);
            }
            default -> {
            }
        }
    }

    /** Reorders the item inside the category (its "slot" in the shop grid). */
    private void move(ShopCategory category, int delta, ShopManager manager) {
        Map<String, ShopItem> items = category.getItems();
        List<String> order = new java.util.ArrayList<>(items.keySet());
        int index = order.indexOf(itemId);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        order.remove(index);
        order.add(target, itemId);
        Map<String, ShopItem> reordered = new LinkedHashMap<>();
        for (String id : order) {
            reordered.put(id, items.get(id));
        }
        category.setItems(reordered);
        saveAndRefresh(manager);
    }

    private void saveAndRefresh(ShopManager manager) {
        manager.markDirty();
        manager.save();
        ShopAdminMainGui.refreshShops();
        refresh();
    }

    private static Double parsePrice(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void clickSound(ServerPlayer player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
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
