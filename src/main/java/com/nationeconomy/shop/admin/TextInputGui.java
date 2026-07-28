package com.nationeconomy.shop.admin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerAccess;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A server-side anvil GUI used as a text prompt: the player types into the
 * rename field and clicks the result. Used by the shop admin GUI for names,
 * exact prices, item ids and icons.
 *
 * <p>All clicks are cancelled — the input item can never be taken out
 * (also relevant for the anti-dupe guarantees: nothing ever leaves the GUI).
 */
public class TextInputGui extends AnvilMenu {

    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 2;

    private final Consumer<String> onSubmit;

    public static void open(ServerPlayer player, Component title, String initialValue, Consumer<String> onSubmit) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, p) -> new TextInputGui(syncId, playerInventory, initialValue, onSubmit),
                title));
    }

    private TextInputGui(int syncId, Inventory playerInventory, String initialValue, Consumer<String> onSubmit) {
        super(syncId, playerInventory, ContainerAccess.EMPTY);
        this.onSubmit = onSubmit;
        ItemStack placeholder = new ItemStack(Items.PAPER);
        placeholder.set(DataComponents.CUSTOM_NAME, Component.literal(initialValue == null ? "" : initialValue));
        getSlot(INPUT_SLOT).set(placeholder);
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        // Cancel everything; the only meaningful action is clicking the result.
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (slotIndex != RESULT_SLOT) {
            return;
        }
        ItemStack result = getSlot(RESULT_SLOT).getItem();
        Component name = result.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            // Nothing typed yet — treat the current placeholder name as the value.
            Component current = getSlot(INPUT_SLOT).getItem().get(DataComponents.CUSTOM_NAME);
            if (current == null) {
                return;
            }
            name = current;
        }
        String value = name.getString().trim();
        serverPlayer.closeContainer();
        onSubmit.accept(value);
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
