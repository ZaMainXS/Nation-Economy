package com.nationeconomy.nation;

import com.nationeconomy.util.ColorUtils;
import net.minecraft.core.component.DataComponents;

import net.minecraft.network.chat.Style;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import net.minecraft.world.item.component.ItemLore;

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
        stack.set(DataComponents.CUSTOM_NAME, ColorUtils.legacy("&6&l" + TOOL_NAME));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                line("Left-click a block: corner 1"),
                line("Right-click a block: corner 2"),
                line("Then run /claimland confirm"))));
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }

    private static Component line(String text) {
        return Component.empty().setStyle(Style.EMPTY.withItalic(false))
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** Checks whether a stack is the claim shovel. */
    public static boolean isClaimTool(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.GOLDEN_SHOVEL)) {
            return false;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return name != null && name.getString().equals(TOOL_NAME);
    }

    /** Gives the player the shovel when they don't already have one. */
    public static boolean giveIfMissing(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isClaimTool(inventory.getItem(slot))) {
                return false;
            }
        }
        inventory.placeItemBackInInventory(create());
        return true;
    }
}
