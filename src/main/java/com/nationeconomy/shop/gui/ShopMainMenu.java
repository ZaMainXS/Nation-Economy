package com.nationeconomy.shop.gui;

import com.nationeconomy.economy.EconomyManager;
import com.nationeconomy.shop.SellLogic;
import com.nationeconomy.shop.ShopCategory;
import com.nationeconomy.shop.ShopManager;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main shop menu ({@code /shop}).
 *
 * <p>The window is a 9x4 chest GUI: slots 0-29 are the category slots that
 * server owners can freely assign with {@code /shopadmin category create},
 * the remaining 6 slots are utility buttons.
 */
public class ShopMainMenu extends GenericContainerScreenHandler {

    public static final int ROWS = 4;
    public static final int SIZE = ROWS * 9; // 36 slots, 30 of them are category slots

    private static final int SLOT_BALANCE = 31;
    private static final int SLOT_SELL_ALL = 33;
    private static final int SLOT_CLOSE = 35;

    private static final Text TITLE = ColorUtils.legacy("&6&l✦ Shop ✦");

    private final SimpleInventory inventory;
    private final PlayerInventory playerInventory;
    private final Map<Integer, ShopCategory> categoriesBySlot = new HashMap<>();

    public static void open(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new ShopMainMenu(syncId, playerInventory, inventory),
                TITLE));
    }

    private ShopMainMenu(int syncId, PlayerInventory playerInventory, SimpleInventory inventory) {
        super(ScreenHandlerType.GENERIC_9X4, syncId, playerInventory, inventory, ROWS);
        this.inventory = inventory;
        this.playerInventory = playerInventory;
        refresh();
    }

    private ServerPlayerEntity viewer() {
        return this.playerInventory.player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }

    /** (Re)builds the whole menu content. */
    private void refresh() {
        categoriesBySlot.clear();
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setStack(slot, GuiElements.filler());
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
                    Text.literal("Click to browse").formatted(Formatting.GRAY),
                    Text.literal(category.getItems().size() + " items for sale").formatted(Formatting.DARK_GRAY)));
            inventory.setStack(slot, icon);
            categoriesBySlot.put(slot, category);
        }

        // Balance display
        ServerPlayerEntity viewer = viewer();
        ItemStack balance = new ItemStack(Items.SUNFLOWER);
        GuiElements.name(balance, Text.literal("Your Balance").formatted(Formatting.GOLD));
        GuiElements.lore(balance, List.of(
                Text.literal(MoneyUtil.format(viewer == null ? 0 : EconomyManager.get().balance(viewer.getUuid())))
                        .formatted(Formatting.YELLOW),
                Text.literal("Earn money by selling items").formatted(Formatting.DARK_GRAY),
                Text.literal("with /sellall or in the shop.").formatted(Formatting.DARK_GRAY)));
        inventory.setStack(SLOT_BALANCE, balance);

        // Sell-all shortcut
        ItemStack sellAll = new ItemStack(Items.HOPPER);
        GuiElements.name(sellAll, Text.literal("Sell All Items").formatted(Formatting.YELLOW));
        GuiElements.lore(sellAll, List.of(
                Text.literal("Sells every sellable item").formatted(Formatting.GRAY),
                Text.literal("in your inventory.").formatted(Formatting.GRAY)));
        inventory.setStack(SLOT_SELL_ALL, sellAll);

        // Close button
        ItemStack close = new ItemStack(Items.BARRIER);
        GuiElements.name(close, Text.literal("Close").formatted(Formatting.RED));
        inventory.setStack(SLOT_CLOSE, close);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Everything is cancelled on purpose: nothing here may be taken out
        // or moved around, clicks only trigger actions.
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
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
            case SLOT_CLOSE -> serverPlayer.closeScreenHandler();
            default -> {
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY; // shift-clicking does nothing
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
