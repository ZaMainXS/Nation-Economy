package com.nationeconomy.shop.admin;

import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopItem;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.shop.gui.GuiElements;
import com.nationeconomy.util.ColorUtils;
import com.nationeconomy.util.MoneyUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin editor for one shop item: exact buy/sell prices, buyable/sellable
 * toggles, position ("slot") inside the category grid, or removal.
 */
public class ItemAdminGui extends GenericContainerScreenHandler {

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

    private final SimpleInventory inventory;
    private final String categoryId;
    private final String itemId;

    public static void open(ServerPlayerEntity player, String categoryId, String itemId) {
        ShopCategory category = ShopManager.get().category(categoryId);
        ShopItem shopItem = category == null ? null : category.getItems().get(itemId);
        if (shopItem == null) {
            player.sendMessage(Text.literal("That item no longer exists.").formatted(Formatting.RED), false);
            CategoryAdminGui.open(player, categoryId);
            return;
        }
        Text title = ColorUtils.legacy("&8Edit Item");
        SimpleInventory inventory = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new ItemAdminGui(syncId, playerInventory, inventory, categoryId, itemId),
                title));
    }

    private ItemAdminGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                         String categoryId, String itemId) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, ROWS);
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
            inventory.setStack(slot, GuiElements.filler());
        }
        if (shopItem == null) {
            return;
        }
        int position = new java.util.ArrayList<>(category.getItems().keySet()).indexOf(itemId);

        ItemStack display = new ItemStack(ShopManager.itemOf(itemId));
        GuiElements.name(display, Text.literal(ShopManager.itemOf(itemId).getName().getString())
                .formatted(Formatting.YELLOW));
        GuiElements.lore(display, List.of(
                Text.literal(itemId).formatted(Formatting.DARK_GRAY),
                Text.literal("Position in category: " + (position + 1)).formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_DISPLAY, display);

        // Buy price controls
        ItemStack buyAdjust = new ItemStack(Items.GOLD_NUGGET);
        GuiElements.name(buyAdjust, Text.literal("Buy: " + (shopItem.isBuyable()
                ? MoneyUtil.format(shopItem.getBuy()) : "disabled")).formatted(Formatting.GREEN));
        GuiElements.lore(buyAdjust, List.of(
                Text.literal("◂ Left-click: +1   ▸ Right-click: -1").formatted(Formatting.YELLOW),
                Text.literal("Shift: ±10").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_BUY_ADJUST, buyAdjust);

        ItemStack buyExact = new ItemStack(Items.OAK_SIGN);
        GuiElements.name(buyExact, Text.literal("Set Exact Buy Price").formatted(Formatting.GREEN));
        GuiElements.lore(buyExact, List.of(Text.literal("Type the number in the anvil.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_BUY_EXACT, buyExact);

        ItemStack buyToggle = new ItemStack(shopItem.isBuyable() ? Items.LIME_DYE : Items.GRAY_DYE);
        GuiElements.name(buyToggle, Text.literal(shopItem.isBuyable() ? "Buyable: ON" : "Buyable: OFF")
                .formatted(shopItem.isBuyable() ? Formatting.GREEN : Formatting.GRAY));
        GuiElements.lore(buyToggle, List.of(Text.literal("Click to toggle.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_BUY_TOGGLE, buyToggle);

        // Sell price controls
        ItemStack sellAdjust = new ItemStack(Items.IRON_NUGGET);
        GuiElements.name(sellAdjust, Text.literal("Sell: " + (shopItem.isSellable()
                ? MoneyUtil.format(shopItem.getSell()) : "disabled")).formatted(Formatting.GOLD));
        GuiElements.lore(sellAdjust, List.of(
                Text.literal("◂ Left-click: +1   ▸ Right-click: -1").formatted(Formatting.YELLOW),
                Text.literal("Shift: ±10").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_SELL_ADJUST, sellAdjust);

        ItemStack sellExact = new ItemStack(Items.SPRUCE_SIGN);
        GuiElements.name(sellExact, Text.literal("Set Exact Sell Price").formatted(Formatting.GOLD));
        GuiElements.lore(sellExact, List.of(
                Text.literal("Type the number in the anvil.").formatted(Formatting.GRAY),
                Text.literal("-1 disables selling.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_SELL_EXACT, sellExact);

        ItemStack sellToggle = new ItemStack(shopItem.isSellable() ? Items.LIME_DYE : Items.GRAY_DYE);
        GuiElements.name(sellToggle, Text.literal(shopItem.isSellable() ? "Sellable: ON" : "Sellable: OFF")
                .formatted(shopItem.isSellable() ? Formatting.GREEN : Formatting.GRAY));
        GuiElements.lore(sellToggle, List.of(Text.literal("Click to toggle.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_SELL_TOGGLE, sellToggle);

        // Order inside the category
        ItemStack moveUp = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(moveUp, Text.literal("▲ Move Earlier").formatted(Formatting.AQUA));
        GuiElements.lore(moveUp, List.of(Text.literal("Moves this item one slot earlier").formatted(Formatting.GRAY),
                Text.literal("inside the category page.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_MOVE_UP, moveUp);

        ItemStack moveDown = new ItemStack(Items.SPECTRAL_ARROW);
        GuiElements.name(moveDown, Text.literal("▼ Move Later").formatted(Formatting.AQUA));
        GuiElements.lore(moveDown, List.of(Text.literal("Moves this item one slot later").formatted(Formatting.GRAY),
                Text.literal("inside the category page.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_MOVE_DOWN, moveDown);

        ItemStack remove = new ItemStack(Items.TNT);
        GuiElements.name(remove, Text.literal("Remove Item").formatted(Formatting.RED));
        GuiElements.lore(remove, List.of(Text.literal("Removes it from the category.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_REMOVE, remove);

        // Navigation
        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Text.literal("« Back to Category").formatted(Formatting.YELLOW));
        inventory.setStack(SLOT_BACK, back);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(SLOT_CLOSE, close);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }
        ShopManager manager = ShopManager.get();
        ShopCategory category = manager.category(categoryId);
        ShopItem shopItem = category == null ? null : category.getItems().get(itemId);
        if (shopItem == null) {
            serverPlayer.closeScreenHandler();
            CategoryAdminGui.open(serverPlayer, categoryId);
            return;
        }

        switch (slotIndex) {
            case SLOT_BACK -> CategoryAdminGui.open(serverPlayer, categoryId);
            case SLOT_CLOSE -> serverPlayer.closeScreenHandler();
            case SLOT_BUY_ADJUST -> {
                double delta = actionType == SlotActionType.QUICK_MOVE ? 10 : 1;
                if (button == 1) {
                    delta = -delta;
                }
                shopItem.setBuy(Math.max(0, shopItem.getBuy() + delta));
                saveAndRefresh(manager);
            }
            case SLOT_SELL_ADJUST -> {
                double delta = actionType == SlotActionType.QUICK_MOVE ? 10 : 1;
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
                                serverPlayer.sendMessage(Text.literal("'" + text + "' is not a number.")
                                        .formatted(Formatting.RED), false);
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
                                serverPlayer.sendMessage(Text.literal("'" + text + "' is not a number.")
                                        .formatted(Formatting.RED), false);
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
                serverPlayer.sendMessage(Text.literal("Removed " + itemId + " from '" + categoryId + "'.")
                        .formatted(Formatting.GREEN), false);
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

    private static void clickSound(ServerPlayerEntity player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
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
