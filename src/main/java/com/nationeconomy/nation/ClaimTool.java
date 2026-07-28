package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The golden shovel used to select land ({@code /claimland}).
 * Left-click a block for corner 1, right-click a block for corner 2,
 * then confirm with {@code /claimland confirm}.
 */
public final class ClaimTool {

    public static final String TOOL_NAME = "Land Claim Shovel";

    private ClaimTool() {
    }

    /** Creates a fresh claim shovel. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.GOLDEN_SHOVEL);
        stack.set(DataComponentTypes.CUSTOM_NAME, ColorUtils.legacy("&6&l" + TOOL_NAME));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                line("Left-click a block: corner 1"),
                line("Right-click a block: corner 2"),
                line("Then run /claimland confirm"))));
        stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false));
        return stack;
    }

    private static Text line(String text) {
        return Text.empty().setStyle(net.minecraft.text.Style.EMPTY.withItalic(false))
                .append(Text.literal(text).formatted(Formatting.GRAY));
    }

    /** Checks whether a stack is the claim shovel. */
    public static boolean isClaimTool(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.GOLDEN_SHOVEL)) {
            return false;
        }
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null && name.getString().equals(TOOL_NAME);
    }

    /** Gives the player the shovel when they don't already have one. */
    public static boolean giveIfMissing(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (isClaimTool(inventory.getStack(slot))) {
                return false;
            }
        }
        inventory.offerOrDrop(create());
        return true;
    }
}
