package com.nationeconomy.shop.admin;

import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopManager;
import com.nationeconomy.shop.gui.GuiElements;
import com.nationeconomy.shop.gui.ShopCategoryMenu;
import com.nationeconomy.shop.gui.ShopMainMenu;
import com.nationeconomy.util.ColorUtils;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The op-only shop administration GUI ({@code /shopadmin}).
 *
 * <p>Everything in one place: manage categories (click one), create new
 * categories, and push live updates to anyone viewing the shop.
 */
public class ShopAdminMainGui extends GenericContainerScreenHandler {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9; // 54

    private static final int SLOT_CREATE = 45;
    private static final int SLOT_PUSH = 49;
    private static final int SLOT_HELP = 50;
    private static final int SLOT_CLOSE = 53;

    private static final Text TITLE = ColorUtils.legacy("&8&lShop Admin");

    private final SimpleInventory inventory;
    private final Map<Integer, ShopCategory> categoriesBySlot = new HashMap<>();

    public static void open(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new ShopAdminMainGui(syncId, playerInventory, inventory),
                TITLE));
    }

    private ShopAdminMainGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        refresh();
    }

    private void refresh() {
        categoriesBySlot.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setStack(slot, GuiElements.filler());
        }

        int index = 0;
        for (ShopCategory category : ShopManager.get().categories()) {
            int slot = index++;
            if (slot >= 45) {
                break;
            }
            ItemStack icon = new ItemStack(ShopManager.itemOf(category.getIcon()));
            GuiElements.name(icon, Text.empty().append(ColorUtils.legacy(category.getName()))
                    .append(Text.literal("  #" + category.getId()).formatted(Formatting.DARK_GRAY)));
            GuiElements.lore(icon, List.of(
                    Text.literal("id: " + category.getId()).formatted(Formatting.GRAY),
                    Text.literal("Menu slot: " + category.getSlot()).formatted(Formatting.GRAY),
                    Text.literal(category.getItems().size() + " items").formatted(Formatting.GRAY),
                    Text.literal(" "),
                    Text.literal("Click to manage").formatted(Formatting.YELLOW)));
            inventory.setStack(slot, icon);
            categoriesBySlot.put(slot, category);
        }

        ItemStack create = new ItemStack(Items.EMERALD);
        GuiElements.name(create, Text.literal("+ Create Category").formatted(Formatting.GREEN));
        GuiElements.lore(create, List.of(
                Text.literal("Type the name in the anvil,").formatted(Formatting.GRAY),
                Text.literal("it gets the first free slot.").formatted(Formatting.GRAY),
                Text.literal("Formatting codes like &a&l and &#FF8800 work.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_CREATE, create);

        ItemStack push = new ItemStack(Items.SUNFLOWER);
        GuiElements.name(push, Text.literal("Push Changes to Open Shops").formatted(Formatting.GOLD));
        GuiElements.lore(push, List.of(
                Text.literal("Refreshes every open shop GUI.").formatted(Formatting.GRAY),
                Text.literal("(Same as /sreload confirm, in-game)").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_PUSH, push);

        ItemStack help = new ItemStack(Items.BOOK);
        GuiElements.name(help, Text.literal("How the Shop Works").formatted(Formatting.AQUA));
        GuiElements.lore(help, List.of(
                Text.literal("• Players use /shop to browse categories.").formatted(Formatting.GRAY),
                Text.literal("• Hover an item to see its buy/sell price.").formatted(Formatting.GRAY),
                Text.literal("• Left-click buys, right-click sells.").formatted(Formatting.GRAY),
                Text.literal("• Categories live in the 30-slot main menu.").formatted(Formatting.GRAY),
                Text.literal("• Click a category here to edit items,").formatted(Formatting.GRAY),
                Text.literal("  prices, slots, icon and name.").formatted(Formatting.GRAY),
                Text.literal("• Add items fast with /economyhanditem").formatted(Formatting.GRAY),
                Text.literal("  add <buy> <sell> while holding the item.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_HELP, help);

        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(SLOT_CLOSE, close);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
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
                        serverPlayer.sendMessage(Text.literal("All 30 category slots are taken!")
                                .formatted(Formatting.RED), false);
                        return;
                    }
                    ShopCategory created = manager.createCategory(name, "minecraft:chest", slot);
                    manager.save();
                    refreshShops();
                    serverPlayer.sendMessage(Text.literal("Created category '" + created.getId() + "' in slot " + slot + ".")
                            .formatted(Formatting.GREEN), false);
                    CategoryAdminGui.open(serverPlayer, created.getId());
                });
            }
            case SLOT_PUSH -> refreshShops();
            case SLOT_HELP -> serverPlayer.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.0f);
            case SLOT_CLOSE -> serverPlayer.closeScreenHandler();
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
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
