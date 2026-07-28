package com.nationeconomy.shop.admin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * A server-side anvil GUI used as a text prompt: the player types into the
 * rename field and clicks the result. Used by the shop admin GUI for names,
 * exact prices, item ids and icons.
 *
 * <p>All clicks are cancelled — the input item can never be taken out
 * (also relevant for the anti-dupe guarantees: nothing ever leaves the GUI).
 */
public class TextInputGui extends AnvilScreenHandler {

    private static final int INPUT_SLOT = 0;
    private static final int RESULT_SLOT = 2;

    private final Consumer<String> onSubmit;

    public static void open(ServerPlayerEntity player, Text title, String initialValue, Consumer<String> onSubmit) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new TextInputGui(syncId, playerInventory, initialValue, onSubmit),
                title));
    }

    private TextInputGui(int syncId, PlayerInventory playerInventory, String initialValue, Consumer<String> onSubmit) {
        super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
        this.onSubmit = onSubmit;
        ItemStack placeholder = new ItemStack(Items.PAPER);
        placeholder.set(DataComponentTypes.CUSTOM_NAME, Text.literal(initialValue == null ? "" : initialValue));
        getSlot(INPUT_SLOT).setStack(placeholder);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Cancel everything; the only meaningful action is clicking the result.
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (slotIndex != RESULT_SLOT) {
            return;
        }
        ItemStack result = getSlot(RESULT_SLOT).getStack();
        Text name = result.get(DataComponentTypes.CUSTOM_NAME);
        if (name == null) {
            // Nothing typed yet — treat the current placeholder name as the value.
            Text current = getSlot(INPUT_SLOT).getStack().get(DataComponentTypes.CUSTOM_NAME);
            if (current == null) {
                return;
            }
            name = current;
        }
        String value = name.getString().trim();
        serverPlayer.closeScreenHandler();
        onSubmit.accept(value);
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
