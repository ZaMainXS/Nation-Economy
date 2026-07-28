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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin editor for one shop category: configure its items, its slot inside
 * the 30-slot shop menu, its name and its icon, or delete it.
 */
public class CategoryAdminGui extends ChestMenu {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    private static final int CONTENT_SLOTS = 45;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_ADD_ITEM = 46;
    private static final int SLOT_MENU_SLOT = 48;
    private static final int SLOT_RENAME = 49;
    private static final int SLOT_ICON = 50;
    private static final int SLOT_DELETE = 52;
    private static final int SLOT_CLOSE = 53;

    /** Two-step confirmation for deletions: player -> timestamp. */
    private static final Map<UUID, Long> DELETE_CONFIRM = new ConcurrentHashMap<>();

    private final SimpleContainer inventory;
    private final String categoryId;
    private final List<String> itemIds = new ArrayList<>();
    private final Map<Integer, String> itemBySlot = new HashMap<>();
    private int page;

    public static void open(ServerPlayer player, String categoryId) {
        ShopCategory category = ShopManager.get().category(categoryId);
        if (category == null) {
            player.sendSystemMessage(Component.literal("That category no longer exists.").withStyle(ChatFormatting.RED), false);
            ShopAdminMainGui.open(player);
            return;
        }
        Component title = ColorUtils.legacy("&8Edit: ").append(ColorUtils.legacy(category.getName()));
        SimpleContainer inventory = new SimpleContainer(SIZE);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new CategoryAdminGui(syncId, playerInventory, inventory, categoryId),
                title));
    }

    private CategoryAdminGui(int syncId, Inventory playerInventory, SimpleContainer inventory, String categoryId) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.categoryId = categoryId;
        refresh();
    }

    private void refresh() {
        ShopCategory category = ShopManager.get().category(categoryId);
        itemBySlot.clear();
        itemIds.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }
        // Clear the work area (empty air looks like real shop slots)
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        if (category == null) {
            return;
        }

        // Items pages (admin GUI shows everything on extra pages via the arrows)
        itemIds.addAll(category.getItems().keySet());
        int start = page * CONTENT_SLOTS;
        for (int slot = 0; slot < CONTENT_SLOTS && start + slot < itemIds.size(); slot++) {
            String itemId = itemIds.get(start + slot);
            ShopItem shopItem = category.getItems().get(itemId);
            ItemStack display = new ItemStack(ShopManager.itemOf(itemId));
            GuiElements.name(display, Component.literal(ShopManager.itemOf(itemId).getDescription().getString())
                    .withStyle(ChatFormatting.YELLOW));
            GuiElements.lore(display, List.of(
                    Component.literal("Buy: " + (shopItem.isBuyable() ? MoneyUtil.format(shopItem.getBuy()) : "—"))
                            .withStyle(ChatFormatting.GREEN),
                    Component.literal("Sell: " + (shopItem.isSellable() ? MoneyUtil.format(shopItem.getSell()) : "—"))
                            .withStyle(ChatFormatting.GOLD),
                    Component.literal("Order #" + (start + slot + 1)).withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal(" "),
                    Component.literal("Click to configure").withStyle(ChatFormatting.AQUA)));
            inventory.setItem(slot, display);
            itemBySlot.put(slot, itemId);
        }

        // Bottom row controls
        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Component.literal("« Back to Shop Admin").withStyle(ChatFormatting.YELLOW));
        inventory.setItem(SLOT_BACK, back);

        ItemStack addItem = new ItemStack(Items.EMERALD);
        GuiElements.name(addItem, Component.literal("+ Add Item").withStyle(ChatFormatting.GREEN));
        GuiElements.lore(addItem, List.of(
                Component.literal("Type an item id (e.g. minecraft:stone)").withStyle(ChatFormatting.GRAY),
                Component.literal("in the anvil. Or hold it and run").withStyle(ChatFormatting.GRAY),
                Component.literal("/economyhanditem add <buy> <sell>.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_ADD_ITEM, addItem);

        ItemStack menuSlot = new ItemStack(Items.COMPARATOR);
        GuiElements.name(menuSlot, Component.literal("Menu Slot: " + category.getSlot()).withStyle(ChatFormatting.AQUA));
        GuiElements.lore(menuSlot, List.of(
                Component.literal("Where this category sits in the").withStyle(ChatFormatting.GRAY),
                Component.literal("30-slot shop menu (0-29).").withStyle(ChatFormatting.GRAY),
                Component.literal("◂ Left-click: -1").withStyle(ChatFormatting.YELLOW),
                Component.literal("▸ Right-click: +1").withStyle(ChatFormatting.YELLOW),
                Component.literal("Shift-click: jump by 9").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_MENU_SLOT, menuSlot);

        ItemStack rename = new ItemStack(Items.NAME_TAG);
        GuiElements.name(rename, Component.literal("Rename Category").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(rename, List.of(Component.literal("Supports & codes and &#RRGGBB.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_RENAME, rename);

        ItemStack icon = new ItemStack(Items.ITEM_FRAME);
        GuiElements.name(icon, Component.literal("Change Icon").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(icon, List.of(Component.literal("Type an item id in the anvil.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_ICON, icon);

        ItemStack delete = new ItemStack(Items.TNT);
        GuiElements.name(delete, Component.literal("Delete Category").withStyle(ChatFormatting.RED));
        GuiElements.lore(delete, List.of(Component.literal("Click twice to confirm!").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_DELETE, delete);

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
        if (category == null) {
            serverPlayer.closeContainer();
            ShopAdminMainGui.open(serverPlayer);
            return;
        }

        String clickedItem = itemBySlot.get(slotIndex);
        if (clickedItem != null) {
            clickSound(serverPlayer);
            ItemAdminGui.open(serverPlayer, categoryId, clickedItem);
            return;
        }

        switch (slotIndex) {
            case SLOT_BACK -> ShopAdminMainGui.open(serverPlayer);
            case SLOT_ADD_ITEM -> {
                clickSound(serverPlayer);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lItem id"), "minecraft:stone", id -> {
                    if (!ShopManager.isValidItem(id)) {
                        serverPlayer.sendSystemMessage(Component.literal("Unknown item '" + id + "'.").withStyle(ChatFormatting.RED), false);
                        return;
                    }
                    String normalized = id.contains(":") ? id.toLowerCase() : "minecraft:" + id.toLowerCase();
                    category.getItems().put(normalized, new ShopItem(normalized, 0, -1));
                    manager.markDirty();
                    manager.save();
                    ShopAdminMainGui.refreshShops();
                    ItemAdminGui.open(serverPlayer, categoryId, normalized);
                });
            }
            case SLOT_MENU_SLOT -> {
                int delta = actionType == ClickType.QUICK_MOVE ? 9 : 1;
                int newSlot = category.getSlot() + (button == 1 ? -delta : delta);
                if (newSlot < 0 || newSlot >= ShopManager.CATEGORY_SLOTS) {
                    error(serverPlayer, "Slots go from 0 to 29.");
                    return;
                }
                ShopCategory occupying = manager.categoryAtSlot(newSlot);
                if (occupying != null && occupying != category) {
                    error(serverPlayer, "Slot " + newSlot + " is used by '" + occupying.getId() + "'.");
                    return;
                }
                category.setSlot(newSlot);
                manager.markDirty();
                manager.save();
                ShopAdminMainGui.refreshShops();
                refresh();
            }
            case SLOT_RENAME -> {
                clickSound(serverPlayer);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lCategory Name"), category.getName(), name -> {
                    category.setName(name.isEmpty() ? "Category" : name);
                    manager.markDirty();
                    manager.save();
                    ShopAdminMainGui.refreshShops();
                    CategoryAdminGui.open(serverPlayer, categoryId);
                });
            }
            case SLOT_ICON -> {
                clickSound(serverPlayer);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lIcon item id"), category.getIcon(), id -> {
                    if (!ShopManager.isValidItem(id)) {
                        serverPlayer.sendSystemMessage(Component.literal("Unknown item '" + id + "'.").withStyle(ChatFormatting.RED), false);
                        return;
                    }
                    category.setIcon(id.contains(":") ? id.toLowerCase() : "minecraft:" + id.toLowerCase());
                    manager.markDirty();
                    manager.save();
                    ShopAdminMainGui.refreshShops();
                    CategoryAdminGui.open(serverPlayer, categoryId);
                });
            }
            case SLOT_DELETE -> {
                long now = System.currentTimeMillis();
                Long pending = DELETE_CONFIRM.get(serverPlayer.getUUID());
                if (pending == null || now - pending > 10_000) {
                    DELETE_CONFIRM.put(serverPlayer.getUUID(), now);
                    serverPlayer.sendSystemMessage(Component.literal("Click DELETE again to really delete category '"
                            + categoryId + "'!").withStyle(ChatFormatting.RED), false);
                    return;
                }
                DELETE_CONFIRM.remove(serverPlayer.getUUID());
                manager.removeCategory(categoryId);
                manager.save();
                ShopAdminMainGui.refreshShops();
                serverPlayer.sendSystemMessage(Component.literal("Deleted category '" + categoryId + "'.").withStyle(ChatFormatting.GREEN), false);
                ShopAdminMainGui.open(serverPlayer);
            }
            case SLOT_CLOSE -> serverPlayer.closeContainer();
            default -> {
            }
        }
    }

    private static void clickSound(ServerPlayer player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
    }

    private static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
        player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
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
