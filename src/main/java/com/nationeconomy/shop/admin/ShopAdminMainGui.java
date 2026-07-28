package com.nationeconomy.shop.admin;

import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.shop.gui.GuiElements;
import com.nationeconomy.shop.gui.ShopCategoryMenu;
import com.nationeconomy.shop.gui.ShopMainMenu;
import com.nationeconomy.util.ColorUtils;
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
import java.util.List;
import java.util.Map;

/**
 * The op-only shop administration GUI ({@code /shopadmin}).
 *
 * <p>Everything in one place: manage categories (click one), create new
 * categories, and push live updates to anyone viewing the shop.
 */
public class ShopAdminMainGui extends ChestMenu {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9; // 54

    private static final int SLOT_CREATE = 45;
    private static final int SLOT_PUSH = 49;
    private static final int SLOT_HELP = 50;
    private static final int SLOT_CLOSE = 53;

    private static final Component TITLE = ColorUtils.legacy("&8&lShop Admin");

    private final SimpleContainer inventory;
    private final Map<Integer, ShopCategory> categoriesBySlot = new HashMap<>();

    public static void open(ServerPlayer player) {
        SimpleContainer inventory = new SimpleContainer(SIZE);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new ShopAdminMainGui(syncId, playerInventory, inventory),
                TITLE));
    }

    private ShopAdminMainGui(int syncId, Inventory playerInventory, SimpleContainer inventory) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        refresh();
    }

    private void refresh() {
        categoriesBySlot.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, GuiElements.filler());
        }

        int index = 0;
        for (ShopCategory category : ShopManager.get().categories()) {
            int slot = index++;
            if (slot >= 45) {
                break;
            }
            ItemStack icon = new ItemStack(ShopManager.itemOf(category.getIcon()));
            GuiElements.name(icon, Component.empty().append(ColorUtils.legacy(category.getName()))
                    .append(Component.literal("  #" + category.getId()).withStyle(ChatFormatting.DARK_GRAY)));
            GuiElements.lore(icon, List.of(
                    Component.literal("id: " + category.getId()).withStyle(ChatFormatting.GRAY),
                    Component.literal("Menu slot: " + category.getSlot()).withStyle(ChatFormatting.GRAY),
                    Component.literal(category.getItems().size() + " items").withStyle(ChatFormatting.GRAY),
                    Component.literal(" "),
                    Component.literal("Click to manage").withStyle(ChatFormatting.YELLOW)));
            inventory.setItem(slot, icon);
            categoriesBySlot.put(slot, category);
        }

        ItemStack create = new ItemStack(Items.EMERALD);
        GuiElements.name(create, Component.literal("+ Create Category").withStyle(ChatFormatting.GREEN));
        GuiElements.lore(create, List.of(
                Component.literal("Type the name in the anvil,").withStyle(ChatFormatting.GRAY),
                Component.literal("it gets the first free slot.").withStyle(ChatFormatting.GRAY),
                Component.literal("Color codes like &a&l and &#FF8800 work.").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_CREATE, create);

        ItemStack push = new ItemStack(Items.SUNFLOWER);
        GuiElements.name(push, Component.literal("Push Changes to Open Shops").withStyle(ChatFormatting.GOLD));
        GuiElements.lore(push, List.of(
                Component.literal("Refreshes every open shop GUI.").withStyle(ChatFormatting.GRAY),
                Component.literal("(Same as /sreload confirm, in-game)").withStyle(ChatFormatting.DARK_GRAY)));
        inventory.setItem(SLOT_PUSH, push);

        ItemStack help = new ItemStack(Items.BOOK);
        GuiElements.name(help, Component.literal("How the Shop Works").withStyle(ChatFormatting.AQUA));
        GuiElements.lore(help, List.of(
                Component.literal("• Players use /shop to browse categories.").withStyle(ChatFormatting.GRAY),
                Component.literal("• Hover an item to see its buy/sell price.").withStyle(ChatFormatting.GRAY),
                Component.literal("• Left-click buys, right-click sells.").withStyle(ChatFormatting.GRAY),
                Component.literal("• Categories live in the 30-slot main menu.").withStyle(ChatFormatting.GRAY),
                Component.literal("• Click a category here to edit items,").withStyle(ChatFormatting.GRAY),
                Component.literal("  prices, slots, icon and name.").withStyle(ChatFormatting.GRAY),
                Component.literal("• Add items fast with /economyhanditem").withStyle(ChatFormatting.GRAY),
                Component.literal("  add <buy> <sell> while holding the item.").withStyle(ChatFormatting.GRAY)));
        inventory.setItem(SLOT_HELP, help);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Component.literal("Close").withStyle(ChatFormatting.RED));
        inventory.setItem(SLOT_CLOSE, close);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            return;
        }

        ShopCategory category = categoriesBySlot.get(slotIndex);
        if (category != null) {
            serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            CategoryAdminGui.open(serverPlayer, category.getId());
            return;
        }

        switch (slotIndex) {
            case SLOT_CREATE -> {
                serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
                TextInputGui.open(serverPlayer, ColorUtils.legacy("&8&lNew Category Name"), "New Category", name -> {
                    ShopManager manager = ShopManager.get();
                    int slot = manager.firstFreeSlot();
                    if (slot < 0) {
                        serverPlayer.sendSystemMessage(Component.literal("All 30 category slots are taken!")
                                .withStyle(ChatFormatting.RED), false);
                        return;
                    }
                    ShopCategory created = manager.createCategory(name, "minecraft:chest", slot);
                    manager.save();
                    refreshShops();
                    serverPlayer.sendSystemMessage(Component.literal("Created category '" + created.getId() + "' in slot " + slot + ".")
                            .withStyle(ChatFormatting.GREEN), false);
                    CategoryAdminGui.open(serverPlayer, created.getId());
                });
            }
            case SLOT_PUSH -> refreshShops();
            case SLOT_HELP -> serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            case SLOT_CLOSE -> serverPlayer.closeContainer();
            default -> {
            }
        }
    }

    /** Pushes the current shop data to everyone viewing a shop. */
    public static void refreshShops() {
        ShopMainMenu.refreshOpenMenus();
        ShopCategoryMenu.refreshOpenMenus();
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
