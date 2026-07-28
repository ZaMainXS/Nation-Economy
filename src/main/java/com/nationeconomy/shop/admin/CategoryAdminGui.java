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
public class CategoryAdminGui extends GenericContainerScreenHandler {

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

    private final SimpleInventory inventory;
    private final String categoryId;
    private final List<String> itemIds = new ArrayList<>();
    private final Map<Integer, String> itemBySlot = new HashMap<>();
    private int page;

    public static void open(ServerPlayerEntity player, String categoryId) {
        ShopCategory category = ShopManager.get().category(categoryId);
        if (category == null) {
            player.sendMessage(Text.literal("That category no longer exists.").formatted(Formatting.RED), false);
            ShopAdminMainGui.open(player);
            return;
        }
        Text title = ColorUtils.legacy("&8Edit: ").append(ColorUtils.legacy(category.getName()));
        SimpleInventory inventory = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new CategoryAdminGui(syncId, playerInventory, inventory, categoryId),
                title));
    }

    private CategoryAdminGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, String categoryId) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.categoryId = categoryId;
        refresh();
    }

    private void refresh() {
        ShopCategory category = ShopManager.get().category(categoryId);
        itemBySlot.clear();
        itemIds.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setStack(slot, GuiElements.filler());
        }
        // Clear the work area (empty air looks like real shop slots)
        for (int slot = 0; slot < CONTENT_SLOTS; slot++) {
            inventory.setStack(slot, ItemStack.EMPTY);
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
            GuiElements.name(display, Text.literal(ShopManager.itemOf(itemId).getName().getString())
                    .formatted(Formatting.YELLOW));
            GuiElements.lore(display, List.of(
                    Text.literal("Buy: " + (shopItem.isBuyable() ? MoneyUtil.format(shopItem.getBuy()) : "—"))
                            .formatted(Formatting.GREEN),
                    Text.literal("Sell: " + (shopItem.isSellable() ? MoneyUtil.format(shopItem.getSell()) : "—"))
                            .formatted(Formatting.GOLD),
                    Text.literal("Order #" + (start + slot + 1)).formatted(Formatting.DARK_GRAY),
                    Text.literal(" "),
                    Text.literal("Click to configure").formatted(Formatting.AQUA)));
            inventory.setStack(slot, display);
            itemBySlot.put(slot, itemId);
        }

        // Bottom row controls
        ItemStack back = new ItemStack(Items.ARROW);
        GuiElements.name(back, Text.literal("« Back to Shop Admin").formatted(Formatting.YELLOW));
        inventory.setStack(SLOT_BACK, back);

        ItemStack addItem = new ItemStack(Items.EMERALD);
        GuiElements.name(addItem, Text.literal("+ Add Item").formatted(Formatting.GREEN));
        GuiElements.lore(addItem, List.of(
                Text.literal("Type an item id (e.g. minecraft:stone)").formatted(Formatting.GRAY),
                Text.literal("in the anvil. Or hold it and run").formatted(Formatting.GRAY),
                Text.literal("/economyhanditem add <buy> <sell>.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_ADD_ITEM, addItem);

        ItemStack menuSlot = new ItemStack(Items.COMPARATOR);
        GuiElements.name(menuSlot, Text.literal("Menu Slot: " + category.getSlot()).formatted(Formatting.AQUA));
        GuiElements.lore(menuSlot, List.of(
                Text.literal("Where this category sits in the").formatted(Formatting.GRAY),
                Text.literal("30-slot shop menu (0-29).").formatted(Formatting.GRAY),
                Text.literal("◂ Left-click: -1").formatted(Formatting.YELLOW),
                Text.literal("▸ Right-click: +1").formatted(Formatting.YELLOW),
                Text.literal("Shift-click: jump by 9").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_MENU_SLOT, menuSlot);

        ItemStack rename = new ItemStack(Items.NAME_TAG);
        GuiElements.name(rename, Text.literal("Rename Category").formatted(Formatting.AQUA));
        GuiElements.lore(rename, List.of(Text.literal("Supports & codes and &#RRGGBB.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_RENAME, rename);

        ItemStack icon = new ItemStack(Items.ITEM_FRAME);
        GuiElements.name(icon, Text.literal("Change Icon").formatted(Formatting.AQUA));
        GuiElements.lore(icon, List.of(Text.literal("Type an item id in the anvil.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_ICON, icon);

        ItemStack delete = new ItemStack(Items.TNT);
        GuiElements.name(delete, Text.literal("Delete Category").formatted(Formatting.RED));
        GuiElements.lore(delete, List.of(Text.literal("Click twice to confirm!").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_DELETE, delete);

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
        if (category == null) {
            serverPlayer.closeScreenHandler();
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
                        serverPlayer.sendMessage(Text.literal("Unknown item '" + id + "'.").formatted(Formatting.RED), false);
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
                int delta = actionType == SlotActionType.QUICK_MOVE ? 9 : 1;
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
                        serverPlayer.sendMessage(Text.literal("Unknown item '" + id + "'.").formatted(Formatting.RED), false);
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
                Long pending = DELETE_CONFIRM.get(serverPlayer.getUuid());
                if (pending == null || now - pending > 10_000) {
                    DELETE_CONFIRM.put(serverPlayer.getUuid(), now);
                    serverPlayer.sendMessage(Text.literal("Click DELETE again to really delete category '"
                            + categoryId + "'!").formatted(Formatting.RED), false);
                    return;
                }
                DELETE_CONFIRM.remove(serverPlayer.getUuid());
                manager.removeCategory(categoryId);
                manager.save();
                ShopAdminMainGui.refreshShops();
                serverPlayer.sendMessage(Text.literal("Deleted category '" + categoryId + "'.").formatted(Formatting.GREEN), false);
                ShopAdminMainGui.open(serverPlayer);
            }
            case SLOT_CLOSE -> serverPlayer.closeScreenHandler();
            default -> {
            }
        }
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
